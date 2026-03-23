package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.dev.DevOptions
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.HoldPoint
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.HoldBoundingBox
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.repository.PrePoseVideoAnalysisProvider
import com.ddgo.app.domain.repository.PoseEstimator
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.AnalyzeAttemptWithAiUseCase
import com.ddgo.app.domain.usecase.AnalyzeHandPeakAndEndUseCase
import com.ddgo.app.domain.usecase.AttachAiRealtimeContextUseCase
import com.ddgo.app.domain.usecase.CloseChallengeUseCase
import com.ddgo.app.domain.usecase.FinalizeAiRealtimeSessionUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.DetectStablePersonObservationUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * flow
 * AttemptUploadScreen      : ?곸긽 ?낅줈??
 * ChallengeCreateScreen    : ?대씪?대컢???대쫫 李얘린 -> ?쒖씠??-> ??쒖깋
 *  - in : ?대씪?대컢???대쫫(id), ?쒖씠???덈꺼, ???而щ윭
 * ChallengeHoldScreen      : ?몄떇??????좏깮
 *  - in : ????꾩튂? ???踰붿쐞? ????뺣낫
 *
 * AttemptUploadScreen      : 梨뚮┛吏????????ㅻⅨ ?곸긽 ?낅줈??
 *  - in : 異붽? ?곸긽
 * AttemptResultScreen      : 紐⑤뱺 ?낅줈?쒖뿉 ???遺꾩꽍 ?곸긽
 *  - out : ?곸긽?????寃껊뱾 寃곌낵??蹂닿린
 */

private const val TAG = "UploadViewModel"

/**
 * Graph-scoped facade and cross-delegate orchestration owner.
 *
 * UploadSessionDelegate owns retention/pre-pose/result-session state.
 * UploadSubmissionDelegate owns submit/AI/result publishing logic.
 */
@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personDetector: PersonDetector,
    private val holdDetector: HoldDetector,
    private val poseEstimator: PoseEstimator,
    private val prePoseVideoAnalysisProvider: PrePoseVideoAnalysisProvider,
    private val holdColorClassifier: HoldColorClassifier,
    private val searchNearbyClimbingGymsUseCase: SearchNearbyClimbingGymsUseCase,
    private val resolveGymUseCase: ResolveGymUseCase,
    private val createChallengeUseCase: CreateChallengeUseCase,
    private val closeChallengeUseCase: CloseChallengeUseCase,
    private val saveChallengeHoldsUseCase: SaveChallengeHoldsUseCase,
    private val uploadAttemptVideoUseCase: UploadAttemptVideoUseCase,
    private val endAttemptUseCase: EndAttemptUseCase,
    private val analyzeHandPeakAndEndUseCase: AnalyzeHandPeakAndEndUseCase,
    private val detectStablePersonObservationUseCase: DetectStablePersonObservationUseCase,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val analyzeAttemptWithAiUseCase: AnalyzeAttemptWithAiUseCase,
    private val attachAiRealtimeContextUseCase: AttachAiRealtimeContextUseCase,
    private val finalizeAiRealtimeSessionUseCase: FinalizeAiRealtimeSessionUseCase
) : ViewModel() {

    // UI ?덉씠?댁뿉 ?몄텧???곹깭 (濡쒕뵫, ?깃났, ?ㅽ뙣 ??
    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // Keep the screen contract stable while internal ownership stays in delegates.
    private val challengeDelegate = UploadChallengeDelegate(
        searchNearbyClimbingGymsUseCase = searchNearbyClimbingGymsUseCase,
        resolveGymUseCase = resolveGymUseCase,
        createChallengeUseCase = createChallengeUseCase
    )
    private val holdDetectionDelegate = UploadHoldDetectionDelegate(
        context = context,
        personDetector = personDetector,
        holdDetector = holdDetector,
        holdColorClassifier = holdColorClassifier
    )
    private val sessionDelegate = UploadSessionDelegate(
        context = context,
        prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
        analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase,
        detectStablePersonObservationUseCase = detectStablePersonObservationUseCase,
        scope = viewModelScope
    )
    private val submissionDelegate = UploadSubmissionDelegate(
        saveChallengeHoldsUseCase = saveChallengeHoldsUseCase,
        uploadAttemptVideoUseCase = uploadAttemptVideoUseCase,
        endAttemptUseCase = endAttemptUseCase,
        getMyInfoUseCase = getMyInfoUseCase,
        analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
        attachAiRealtimeContextUseCase = attachAiRealtimeContextUseCase,
        finalizeAiRealtimeSessionUseCase = finalizeAiRealtimeSessionUseCase
    )
    private var closedChallengeId by mutableStateOf<Long?>(null)
    private var closingChallengeId by mutableStateOf<Long?>(null)

    var videoUri: String?
        get() = sessionDelegate.videoUri
        private set(value) {
            sessionDelegate.videoUri = value
        }

    var additionalVideoUris: List<String>
        get() = sessionDelegate.additionalVideoUris
        private set(value) {
            sessionDelegate.additionalVideoUris = value
        }

    var attemptOnlyVideoUris: List<String>
        get() = sessionDelegate.attemptOnlyVideoUris
        private set(value) {
            sessionDelegate.attemptOnlyVideoUris = value
        }

    private var uploadFlowMode: UploadFlowMode
        get() = sessionDelegate.uploadFlowMode
        set(value) {
            sessionDelegate.uploadFlowMode = value
        }
    private var allowLocalAnalysisWithoutChallenge by mutableStateOf(false)

    // ?꾩껜 ?쒕룄 ?곸긽 (珥덇린 + 異붽? ?곸긽 由ъ뒪??
    val allAttemptUris: List<String>
        get() = sessionDelegate.allAttemptUris

    /**
     * Result screen playback should stay on the original local files used for upload.
     * This avoids switching playback to any backend/object-storage URL after upload succeeds.
     */
    var resultPlaybackUris: List<String>
        get() = sessionDelegate.resultPlaybackUris
        private set(value) {
            sessionDelegate.resultPlaybackUris = value
        }

    val playbackAttemptUris: List<String>
        get() = sessionDelegate.playbackAttemptUris

    val isAttemptOnlyUploadMode: Boolean
        get() = sessionDelegate.isAttemptOnlyUploadMode

    val canBypassChallengeCreationForDev: Boolean
        get() = allowLocalAnalysisWithoutChallenge

    // ?몃꽕??/ 硫뷀??곗씠??(PersonDetector 諛⑹떇?쇰줈 異붿텧 ???ㅼ쓬 ?붾㈃?먯꽌 ?쒖슜)
    var thumbnail: Bitmap?
        get() = sessionDelegate.thumbnail
        private set(value) {
            sessionDelegate.thumbnail = value
        }
    var videoFileName: String?
        get() = sessionDelegate.videoFileName
        private set(value) {
            sessionDelegate.videoFileName = value
        }
    var videoDuration: String?
        get() = sessionDelegate.videoDuration
        private set(value) {
            sessionDelegate.videoDuration = value
        }

    // --- 2. ChallengeCreateScreen (?대씪?대컢?? ?쒖씠?? ??쒖깋) ---
    var gymId: Int?
        get() = challengeDelegate.gymId
        private set(value) {
            challengeDelegate.gymId = value
        }
    var gymName: String
        get() = challengeDelegate.gymName
        private set(value) {
            challengeDelegate.gymName = value
        }
    var difficultyLevel: String
        get() = challengeDelegate.difficultyLevel
        private set(value) {
            challengeDelegate.difficultyLevel = value
        }
    var holdColor: String
        get() = challengeDelegate.holdColor
        private set(value) {
            challengeDelegate.holdColor = value
        }
    var selectedHoldColorKey: String?
        get() = challengeDelegate.selectedHoldColorKey
        private set(value) {
            challengeDelegate.selectedHoldColorKey = value
        }
    var selectedLevelSortOrder: Int?
        get() = challengeDelegate.selectedLevelSortOrder
        private set(value) {
            challengeDelegate.selectedLevelSortOrder = value
        }
    var selectedGymGradeId: Long?
        get() = challengeDelegate.selectedGymGradeId
        private set(value) {
            challengeDelegate.selectedGymGradeId = value
        }
    var selectedGymGrade: GymGrade?
        get() = challengeDelegate.selectedGymGrade
        private set(value) {
            challengeDelegate.selectedGymGrade = value
        }
    var createdChallenge: ChallengeSession?
        get() = challengeDelegate.createdChallenge
        private set(value) {
            challengeDelegate.createdChallenge = value
        }
    var challengeId: Long?
        get() = challengeDelegate.challengeId
        private set(value) {
            challengeDelegate.challengeId = value
        }
    var savedChallengeHolds by mutableStateOf<SavedChallengeHolds?>(null)
        private set
    var uploadedAttemptVideos: List<UploadedAttemptVideo>
        get() = submissionDelegate.uploadedAttemptVideos
        private set(value) {
            submissionDelegate.uploadedAttemptVideos = value
        }

    val challengeCreationUiState = challengeDelegate.challengeCreationUiState

    val uploadSubmissionUiState = submissionDelegate.uploadSubmissionUiState
    val finalAnalysisPreparationUiState = submissionDelegate.finalAnalysisPreparationUiState
    val backgroundUploadState = submissionDelegate.backgroundUploadState
    val backgroundUploadNotice = submissionDelegate.backgroundUploadNotice
    var analysisLoadingPhase by mutableStateOf(AnalysisLoadingPhase.AttemptResultPreparation)
        private set

    // Thin bridge so delegates never call each other directly.
    private val submissionCallbacks = object : UploadSubmissionCallbacks {
        override suspend fun awaitSubmitReadyPrePose(
            playbackUris: List<String>,
            emitLoading: Boolean
        ): TerminalPrePoseSnapshot {
            return this@UploadViewModel.awaitSubmitReadyPrePose(
                playbackUris = playbackUris,
                emitLoading = emitLoading
            )
        }

        override fun currentAttemptIndex(): Int = currentAttemptIndex

        override fun setCurrentAttemptIndex(index: Int) {
            currentAttemptIndex = index
        }

        override fun clearCurrentPoseLandmarks() {
            currentPoseLandmarks = emptyList()
        }

        override fun syncDisplayedAnalysisPoints() {
            this@UploadViewModel.syncDisplayedAnalysisPoints()
        }

        override fun resetDisplayedAnalysisPoints() {
            analysisPoints = defaultUploadAnalysisPoints()
        }

        override fun sessionResultPlaybackUris(): List<String> = resultPlaybackUris

        override fun setSessionResultPlaybackUris(uris: List<String>) {
            resultPlaybackUris = uris
        }

        override fun publishedSession(): PublishedAttemptResultSession? = publishedAttemptResultSession

        override fun setPublishedSession(session: PublishedAttemptResultSession?) {
            publishedAttemptResultSession = session
        }

        override fun setSavedChallengeHolds(saved: SavedChallengeHolds?) {
            savedChallengeHolds = saved
        }
    }

    // Thin bridge for session-driven commands that must update presentation state.
    private val sessionCallbacks = object : UploadSessionCallbacks {
        override fun clearAttemptResultState(clearPublishedSession: Boolean) {
            this@UploadViewModel.clearAttemptResultState(clearPublishedSession)
        }

        override fun resetUploadSubmissionState() {
            submissionDelegate.resetUploadSubmissionState()
        }

        override fun setUploadSubmissionLoading(message: String) {
            submissionDelegate.setUploadSubmissionLoading(message)
        }

        override fun currentAttemptIndex(): Int = currentAttemptIndex

        override fun setCurrentAttemptIndex(index: Int) {
            currentAttemptIndex = index
        }

        override fun clearCurrentPoseLandmarks() {
            currentPoseLandmarks = emptyList()
        }

        override fun syncDisplayedAnalysisPoints() {
            this@UploadViewModel.syncDisplayedAnalysisPoints()
        }

        override fun onPrimaryVideoPrepared(generation: Long, playbackUri: String) {
            this@UploadViewModel.onPrimaryVideoPrepared(
                generation = generation,
                playbackUri = playbackUri
            )
        }

        override fun onPrePoseBatchStateChanged() {
            this@UploadViewModel.maybeStartPrimaryPrePoseAfterHoldPrecompute()
        }
    }

    var selectionGeneration: Long
        get() = sessionDelegate.selectionGeneration
        private set(value) {
            sessionDelegate.selectionGeneration = value
        }

    var prePoseBatchState: PrePoseBatchState
        get() = sessionDelegate.prePoseBatchState
        private set(value) {
            sessionDelegate.prePoseBatchState = value
        }

    private var primaryManagedVideo: ManagedAttemptVideo?
        get() = sessionDelegate.primaryManagedVideo
        set(value) {
            sessionDelegate.primaryManagedVideo = value
        }
    private var additionalManagedVideos: List<ManagedAttemptVideo>
        get() = sessionDelegate.additionalManagedVideos
        set(value) {
            sessionDelegate.additionalManagedVideos = value
        }
    private var attemptOnlyManagedVideos: List<ManagedAttemptVideo>
        get() = sessionDelegate.attemptOnlyManagedVideos
        set(value) {
            sessionDelegate.attemptOnlyManagedVideos = value
        }

    private var prePoseCacheEntries: Map<String, PrePoseCacheEntry>
        get() = sessionDelegate.prePoseCacheEntries
        set(value) {
            sessionDelegate.prePoseCacheEntries = value
        }
    private var holdPrecomputeRequestedGeneration by mutableStateOf<Long?>(null)
    private var pendingPrimaryPrePoseGeneration by mutableStateOf<Long?>(null)
    private var holdPrecomputeObservationJob: Job? = null
    private var holdDetectionEnsureJob: Job? = null
    private var publishedAttemptResultSession: PublishedAttemptResultSession?
        get() = sessionDelegate.publishedAttemptResultSession
        set(value) {
            sessionDelegate.publishedAttemptResultSession = value
        }

    /**
     * 二쇰? ?붿옣 寃??UI ?곹깭.
     *
     * ??븷:
     * - 寃????濡쒕뵫/?깃났/?ㅽ뙣 ?곹깭瑜??붾㈃???꾨떖?⑸땲??
     */
    val gymSearchUiState = challengeDelegate.gymSearchUiState

    /**
     * ?좏깮???μ냼??gym resolve UI ?곹깭.
     *
     * ??븷:
     * - ?ъ슜?먭? ?μ냼瑜??좏깮?????쒕쾭 resolve 吏꾪뻾 ?곹깭瑜??붾㈃???꾨떖?⑸땲??
     */
    val gymResolveUiState = challengeDelegate.gymResolveUiState

    /**
     * Kakao Local API?먯꽌 媛?몄삩 二쇰? ?μ냼 紐⑸줉.
     */
    var nearbyPlaces: List<NearbyPlace>
        get() = challengeDelegate.nearbyPlaces
        private set(value) {
            challengeDelegate.nearbyPlaces = value
        }

    /**
     * ?ъ슜?먭? ?좏깮???μ냼.
     */
    var selectedNearbyPlace: NearbyPlace?
        get() = challengeDelegate.selectedNearbyPlace
        private set(value) {
            challengeDelegate.selectedNearbyPlace = value
        }

    /**
     * ?쒕쾭 resolve 寃곌낵.
     */
    var resolvedGym: ResolvedGym?
        get() = challengeDelegate.resolvedGym
        private set(value) {
            challengeDelegate.resolvedGym = value
        }

    /**
     * resolve 寃곌낵濡??대젮??gym grade 紐⑸줉.
     *
     * ?ㅼ쓬 ?④퀎?먯꽌 gymGradeId 湲곕컲 ?좏깮 UI濡?諛붽? ???ъ슜?⑸땲??
     */
    var resolvedGymGrades: List<GymGrade>
        get() = challengeDelegate.resolvedGymGrades
        private set(value) {
            challengeDelegate.resolvedGymGrades = value
        }

    /**
     * 留덉?留?寃???꾩튂.
     */
    var lastSearchLatitude: Double?
        get() = challengeDelegate.lastSearchLatitude
        private set(value) {
            challengeDelegate.lastSearchLatitude = value
        }

    var lastSearchLongitude: Double?
        get() = challengeDelegate.lastSearchLongitude
        private set(value) {
            challengeDelegate.lastSearchLongitude = value
        }

    // --- 3. ChallengeHoldScreen (????먯? 寃곌낵) ---
    /** PersonDetector媛 ?좏깮??理쒖쟻 ?꾨젅??(????먯????ъ슜???ㅼ젣 ?대?吏) */
    var bestFrameBitmap: Bitmap?
        get() = holdDetectionDelegate.bestFrameBitmap
        private set(value) {
            holdDetectionDelegate.bestFrameBitmap = value
        }

    /** ?붾쾭洹몄슜?쇰줈 ?섎룞 ?좏깮??best frame ?대?吏 URI */
    var debugBestFrameImageUri: String?
        get() = holdDetectionDelegate.debugBestFrameImageUri
        private set(value) {
            holdDetectionDelegate.debugBestFrameImageUri = value
        }

    /** YOLO媛 ?먯????꾩껜 ???(?됱긽 ?꾪꽣留???. ?섎룞 異붽? ?꾨낫 ?濡??ъ슜 */
    var allRawHolds: List<Hold>
        get() = holdDetectionDelegate.allRawHolds
        private set(value) {
            holdDetectionDelegate.allRawHolds = value
        }

    /** ?됱긽 ?꾪꽣留?+ ?섎룞 異붽??????紐⑸줉 (?붾㈃???쒖떆) */
    var detectedHolds: List<Hold>
        get() = holdDetectionDelegate.detectedHolds
        private set(value) {
            holdDetectionDelegate.detectedHolds = value
        }

    /** ?섎룞 異붽? ?앹뾽???쒖떆???꾨낫 ???紐⑸줉 */
    var candidateHolds: List<Hold>
        get() = holdDetectionDelegate.candidateHolds
        private set(value) {
            holdDetectionDelegate.candidateHolds = value
        }

    /** ?섎룞 異붽? ?앹뾽 ?쒖떆 ?щ? */
    var showCandidatePopup: Boolean
        get() = holdDetectionDelegate.showCandidatePopup
        private set(value) {
            holdDetectionDelegate.showCandidatePopup = value
        }

    /** ?ъ슜?먭? ?좏깮???쒖옉 ???*/
    var selectedStartHold: Hold?
        get() = holdDetectionDelegate.selectedStartHold
        private set(value) {
            holdDetectionDelegate.selectedStartHold = value
        }

    /** ?ъ슜?먭? ?좏깮???????*/
    var selectedEndHold: Hold?
        get() = holdDetectionDelegate.selectedEndHold
        private set(value) {
            holdDetectionDelegate.selectedEndHold = value
        }

    /** ?쒖옉/?????湲곗??쇰줈 踰덊샇媛 遺?щ맂 ???紐⑸줉 */
    var numberedHolds: List<HoldNumbered>
        get() = holdDetectionDelegate.numberedHolds
        private set(value) {
            holdDetectionDelegate.numberedHolds = value
        }

    /** ?쒕룄蹂?理쒓퀬 ?꾨떖 ???遺꾩꽍 寃곌낵 */
    var attemptHoldReachResults: List<AttemptHoldReachResult>
        get() = submissionDelegate.attemptHoldReachResults
        private set(value) {
            submissionDelegate.attemptHoldReachResults = value
        }

    /** ?쒕룄蹂?MediaPipe Pose DTO */
    var attemptPoseDtos: List<PoseSequenceDto>
        get() = submissionDelegate.attemptPoseDtos
        private set(value) {
            submissionDelegate.attemptPoseDtos = value
        }

    /** ?쒕룄蹂?遺꾩꽍??MediaPipe Pose ?꾨젅??*/
    var attemptAnalyzedPoses: List<List<Pose>>
        get() = submissionDelegate.attemptAnalyzedPoses
        private set(value) {
            submissionDelegate.attemptAnalyzedPoses = value
        }

    /** ?쒕룄蹂??대━怨?????묒큺 ?붾쾭洹?寃곌낵 */
    var attemptPolygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>
        get() = submissionDelegate.attemptPolygonHoldContactDebugResults
        private set(value) {
            submissionDelegate.attemptPolygonHoldContactDebugResults = value
        }

    /** ?쒕룄蹂?pre-pose ?쒗??罹먯떆 */
    val attemptPoseSequences: List<List<Pose>>
        get() = playbackAttemptUris.map { playbackUri ->
            prePoseCacheEntries[playbackUri]
                ?.takeIf { it.status == PrePoseStatus.Ready }
                ?.poses
                .orEmpty()
        }

    /** ?щ윭 ?쒕룄???됯퇏 ?꾨떖 ????붿빟 */
    var overallHoldReachSummary: OverallHoldReachSummary?
        get() = submissionDelegate.overallHoldReachSummary
        private set(value) {
            submissionDelegate.overallHoldReachSummary = value
        }

    // --- 4. AttemptResultScreen (?ъ쫰 ?ㅻ쾭?덉씠 + 遺꾩꽍 ??꾨씪?? ---
    
    // N李??쒕룄瑜?異붿쟻?섎뒗 ?몃뜳??(0??1李??쒕룄)
    var currentAttemptIndex by mutableStateOf(0)
        private set
        
    fun nextAttempt() {
        if (currentAttemptIndex < playbackAttemptUris.size - 1) {
            selectAttempt(currentAttemptIndex + 1)
        }
    }

    fun selectAttempt(index: Int) {
        currentAttemptIndex = index.coerceIn(
            minimumValue = 0,
            maximumValue = (playbackAttemptUris.size - 1).coerceAtLeast(0)
        )
        currentPoseLandmarks = emptyList()
        syncDisplayedAnalysisPoints()
    }

    /** ?꾩옱 ?ъ깮 ?꾨젅?꾩쓽 MediaPipe 33 ?쒕뱶留덊겕 (?ㅼ떆媛??낅뜲?댄듃) */
    var currentPoseLandmarks by mutableStateOf<List<PoseLandmark>>(emptyList())
        private set

    /** ?꾩옱 ?좏깮???쒕룄??pre-pose ?쒗??*/
    val currentAttemptPoseSequence: List<Pose>
        get() = currentAttemptPrePoseEntry
            ?.takeIf { it.status == PrePoseStatus.Ready }
            ?.poses
            .orEmpty()

    internal val currentAttemptFilteredPoseSequence: List<Pose>
        get() = currentAttemptPrePoseEntry
            ?.takeIf { it.status == PrePoseStatus.Ready }
            ?.filteredPoses
            .orEmpty()

    internal val currentAttemptSmoothedPoseSequence: List<Pose>
        get() = currentAttemptPrePoseEntry
            ?.takeIf { it.status == PrePoseStatus.Ready }
            ?.smoothedPoses
            .orEmpty()

    internal val currentAttemptOverlayCache: AttemptPoseOverlayCache?
        get() = currentAttemptPrePoseEntry
            ?.takeIf { it.status == PrePoseStatus.Ready }
            ?.overlayCache

    internal val currentAttemptPrePoseEntry: PrePoseCacheEntry?
        get() = playbackAttemptUris
            .getOrNull(currentAttemptIndex)
            ?.let(prePoseCacheEntries::get)

    /** ?꾩옱 ?좏깮???쒕룄??理쒓퀬 ?꾨떖 ???寃곌낵 */
    val currentAttemptHoldReachResult: AttemptHoldReachResult?
        get() = attemptHoldReachResults.getOrNull(currentAttemptIndex)

    var attemptAiAnalysisResults: List<AiAnalysisResult?>
        get() = submissionDelegate.attemptAiAnalysisResults
        private set(value) {
            submissionDelegate.attemptAiAnalysisResults = value
        }

    val selectedAiAnalysisMode: AiAnalysisMode
        get() = DevOptions.aiAnalysisMode

    val currentAttemptAiAnalysisResult: AiAnalysisResult?
        get() = attemptAiAnalysisResults.getOrNull(currentAttemptIndex)

    val attemptPresentationResults: List<Pair<Boolean, List<AnalysisPoint>>>
        get() {
            val attemptCount = playbackAttemptUris.ifEmpty { allAttemptUris }.size
            val fallbackResults = attemptDummyResults.ifEmpty {
                listOf(false to defaultUploadAnalysisPoints())
            }

            if (attemptCount <= 0) {
                return fallbackResults
            }

            return List(attemptCount) { index ->
                val fallback = fallbackResults[index % fallbackResults.size]
                val prePoseEntry = playbackAttemptUris
                    .getOrNull(index)
                    ?.let(prePoseCacheEntries::get)
                val aiPoints = attemptAiAnalysisResults
                    .getOrNull(index)
                    ?.toAnalysisPoints()
                    .orEmpty()

                resolveAttemptSuccess(
                    index = index,
                    fallback = fallback.first
                ) to when {
                    prePoseEntry == null -> if (aiPoints.isNotEmpty()) {
                        aiPoints
                    } else {
                        fallback.second.ifEmpty { defaultUploadAnalysisPoints() }
                    }

                    prePoseEntry.status == PrePoseStatus.Ready -> prePoseEntry.timelinePoints
                    else -> emptyList()
                }
            }
        }

    /** ?꾩옱 ?좏깮???쒕룄??MediaPipe Pose DTO */
    val currentAttemptPoseDto: PoseSequenceDto?
        get() = attemptPoseDtos.getOrNull(currentAttemptIndex)

    /** ?꾩옱 ?좏깮???쒕룄??遺꾩꽍??MediaPipe Pose ?꾨젅??*/
    val currentAttemptAnalyzedPoses: List<Pose>
        get() = attemptAnalyzedPoses.getOrNull(currentAttemptIndex).orEmpty()

    /** ?꾩옱 ?좏깮???쒕룄???대━怨?????묒큺 ?붾쾭洹?寃곌낵 */
    val currentAttemptPolygonHoldContactDebugResult: PolygonHoldContactDebugResult?
        get() = attemptPolygonHoldContactDebugResults.getOrNull(currentAttemptIndex)

    /** 理쒖쥌 遺꾩꽍 ?붾㈃???쒖떆???됯퇏 ?꾨떖 ???踰덊샇(諛섏삱由? */
    val averageReachedHoldNo: Int
        get() = overallHoldReachSummary?.roundedAverageHighestReachedHoldNo ?: 0

    /** 理쒖쥌 遺꾩꽍 ?붾㈃ 遺꾨え濡??ъ슜???꾩껜 ?좏깮 ?????*/
    val totalSelectedHoldCount: Int
        get() = overallHoldReachSummary?.totalHoldCount ?: numberedHolds.size

    /**
     * 遺꾩꽍 ?쇰뱶諛??ъ씤??紐⑸줉.
     * MVP?먯꽌???뚮젅?댁뒪????곗씠?곕? ?ъ슜?섎ŉ, ?쒕쾭 ?곕룞 ??updateAnalysisPoints()濡?援먯껜?⑸땲??
     */
    var analysisPoints by mutableStateOf<List<AnalysisPoint>>(defaultUploadAnalysisPoints())
        private set
        
    // (?꾩떆) N李??쒕룄蹂꾨줈 ?ㅻⅨ 寃곌낵瑜?蹂댁뿬二쇨린 ?꾪븳 ?붾? ?곗씠??紐⑤뜽 ?뺤옣
    // 諛깆뿏???곕룞 ??AnalysisResult ?깆쓣 由ъ뒪???뺥깭濡?愿由?
    val attemptDummyResults = listOf(
        Pair(false, defaultUploadAnalysisPoints()),
        Pair(true, listOf(
            AnalysisPoint(1, 15_000L, "안정적인 스타트 구간이에요"),
            AnalysisPoint(2, 35_000L, "오른쪽으로 무게 중심이 이동했어요"),
            AnalysisPoint(3, 50_000L, "상단 구간을 공략했어요")
        ))
    )

    // --- 5. AttemptUploadScreen (異붽? ?곸긽 ?낅줈?? ---
    fun updateAdditionalVideoUris(uris: List<String>) {
        invalidateSubmissionAnalysisPrewarm()
        sessionDelegate.updateAdditionalVideoUris(
            uris = uris,
            callbacks = sessionCallbacks
        )
    }

    /**
     * ??梨뚮┛吏 ?앹꽦 ?먮쫫?쇰줈 吏꾩엯?⑸땲??
     *
     * ??븷:
     * - 湲곗〈 異붽? ?쒕룄 ?낅줈??紐⑤뱶媛 耳쒖졇 ?덉뿀?ㅻ㈃ 湲곕낯 ?낅줈??紐⑤뱶濡??섎룎由쎈땲??
     * - ???낅줈??諛곗튂瑜??쒖옉?????댁쟾 attempt-only ?좏깮媛믪쓣 鍮꾩썎?덈떎.
     */
    fun beginNewChallengeUploadFlow() {
        invalidateSubmissionAnalysisPrewarm()
        uploadFlowMode = UploadFlowMode.FullChallenge
        allowLocalAnalysisWithoutChallenge = false
        clearHoldPrecomputeState()
        holdDetectionDelegate.resetHoldDetectionState(clearDebugSource = true)
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        additionalVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        additionalManagedVideos = emptyList()
        primaryManagedVideo = null
        videoUri = null
        resultPlaybackUris = emptyList()
        uploadedAttemptVideos = emptyList()
        currentAttemptIndex = 0
        publishedAttemptResultSession = null
        challengeDelegate.resetSearchState()
        clearChallengeFlowState()
        clearHoldReachAnalysis()
        clearAiAnalysisState()
        clearPosePrecomputeState()
        resetUploadSubmissionState()
        cleanupUnusedManagedTempFiles(forceDeleteAll = true)
    }

    /**
     * ?대? ?앹꽦??challenge??異붽? ?쒕룄留??낅줈?쒗븯??紐⑤뱶濡??꾪솚?⑸땲??
     *
     * 洹쒖튃:
     * - challenge媛 ?대? ?덉뼱???⑸땲??
     * - 湲곗〈 ???梨뚮┛吏 ?뺣낫???좎??섍퀬, 異붽? ?쒕룄 ?곸긽 ?좏깮 ?곹깭留?珥덇린?뷀빀?덈떎.
     */
    fun enterAttemptOnlyUploadMode(): Boolean {
        val currentChallengeId = challengeId
        if (currentChallengeId == null || currentChallengeId <= 0L) {
            return false
        }

        invalidateSubmissionAnalysisPrewarm()
        captureCurrentAttemptResultSession()
        uploadFlowMode = UploadFlowMode.AttemptOnly
        allowLocalAnalysisWithoutChallenge = false
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        resetUploadSubmissionState()
        refreshCurrentSelectionPrePoseTargets()
        cleanupUnusedManagedTempFiles()
        return true
    }

    /**
     * 湲곗〈 challenge ?곸꽭 ?붾㈃?먯꽌 異붽? ?쒕룄 ?낅줈???뚮줈?곕? ?쒖옉?????ъ슜??吏꾩엯?먯엯?덈떎.
     *
     * ??븷:
     * - ?쒕쾭???대? ?앹꽦??challenge ?뺣낫瑜??꾩옱 ?낅줈??ViewModel??二쇱엯?⑸땲??
     * - ?댄썑 諛붾줈 異붽? ?쒕룄 ?낅줈??紐⑤뱶濡?吏꾩엯?????덈뒗 ?곹깭瑜?留뚮벊?덈떎.
     */
    fun prepareExistingChallengeAttemptUpload(challenge: ChallengeSession) {
        invalidateSubmissionAnalysisPrewarm()
        challengeDelegate.applyExistingChallenge(
            challenge = challenge,
            resolveHoldColorKey = { colorName ->
                resolveHoldColorKey(
                    colorName = colorName,
                    colorHex = null
                )
            },
            resolveHoldColorDisplayName = { colorName ->
                resolveHoldColorDisplayName(
                    colorName = colorName,
                    colorHex = null
                )
            }
        )
        uploadFlowMode = UploadFlowMode.AttemptOnly
        allowLocalAnalysisWithoutChallenge = false
        clearHoldPrecomputeState()
        holdDetectionDelegate.resetHoldDetectionState(clearDebugSource = true)
        resetAllSelectionPreparationJobs()
        videoUri = null
        primaryManagedVideo = null
        additionalVideoUris = emptyList()
        additionalManagedVideos = emptyList()
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        resultPlaybackUris = emptyList()
        uploadedAttemptVideos = emptyList()
        currentAttemptIndex = 0
        publishedAttemptResultSession = null
        clearHoldReachAnalysis()
        clearAiAnalysisState()
        clearPosePrecomputeState()
        resetUploadSubmissionState()
        cleanupUnusedManagedTempFiles()
    }

    /**
     * 異붽? ?쒕룄 ?낅줈??紐⑤뱶瑜?痍⑥냼?섍퀬 ?먮옒 梨뚮┛吏 ?먮쫫 紐⑤뱶濡??뚯븘媛묐땲??
     */
    fun cancelAttemptOnlyUploadMode() {
        invalidateSubmissionAnalysisPrewarm()
        uploadFlowMode = UploadFlowMode.FullChallenge
        allowLocalAnalysisWithoutChallenge = false
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        clearPosePrecomputeState(
            preservePlaybackUris = allAttemptUris.toSet() + publishedResultPlaybackUris()
        )
        restorePublishedAttemptResultSession()
        refreshCurrentSelectionPrePoseTargets(selectionGeneration)
        resetUploadSubmissionState()
        cleanupUnusedManagedTempFiles()
    }

    fun setLocalAnalysisWithoutChallengeEnabled(enabled: Boolean) {
        invalidateSubmissionAnalysisPrewarm()
        allowLocalAnalysisWithoutChallenge = enabled
        if (enabled) {
            uploadFlowMode = UploadFlowMode.FullChallenge
            challengeDelegate.resetChallengeCreationUiState()
            resetUploadSubmissionState()
        }
    }

    fun searchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        query: String,
        nearbyOnly: Boolean = false
    ) {
        viewModelScope.launch {
            challengeDelegate.searchNearbyPlaces(
                latitude = latitude,
                longitude = longitude,
                query = query,
                nearbyOnly = nearbyOnly,
                onChallengeFlowCleared = ::clearChallengeSelectionStatePreservingHoldPrecompute
            )
        }
    }

    fun resolveSelectedPlace(place: NearbyPlace) {
        viewModelScope.launch {
            challengeDelegate.resolveSelectedPlace(
                place = place,
                onChallengeFlowCleared = ::clearChallengeSelectionStatePreservingHoldPrecompute
            )
        }
    }

    fun selectGymLevel(sortOrder: Int) {
        challengeDelegate.selectGymLevel(
            sortOrder = sortOrder,
            formatSelectedLevelLabel = ::formatSelectedLevelLabel,
            onCreatedChallengeCleared = ::clearCreatedChallengeOnly
        )
    }

    fun updateHoldColor(colorKey: String) {
        invalidateSubmissionAnalysisPrewarm()
        challengeDelegate.updateHoldColor(colorKey) { colorName ->
            resolveHoldColorDisplayName(
                colorName = colorName,
                colorHex = null
            )
        }
    }

    fun markHoldPrecomputeEligibleForCurrentSelection() {
        if (isAttemptOnlyUploadMode) {
            return
        }
        holdPrecomputeRequestedGeneration = selectionGeneration
        holdDetectionDelegate.requestHoldPrecompute(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = videoUri
        )
        maybeStartHoldPrecomputeForCurrentSelection()
    }

    fun finalizeHoldDetectionColorSelection() {
        val sourceVideoUri = videoUri
        val reusedCachedFilter = tryApplyCachedHoldFilter(updateUiStateOnSuccess = false)
        if (!reusedCachedFilter) {
            val precomputeReady = holdDetectionDelegate.isPrecomputeReady(
                selectionGeneration = selectionGeneration,
                sourceVideoUri = sourceVideoUri
            )
            val precomputeRunning = holdDetectionDelegate.isPrecomputeRunning(
                selectionGeneration = selectionGeneration,
                sourceVideoUri = sourceVideoUri
            )
            if (!precomputeReady && !precomputeRunning) {
                markHoldPrecomputeEligibleForCurrentSelection()
            }
        }
    }

    fun updateSelectedStartHold(hold: Hold) {
        holdDetectionDelegate.updateSelectedStartHold(hold)
        clearHoldReachAnalysis()
        UploadAiTraceLogger.log(
            event = "START_HOLD_SELECTED",
            generation = selectionGeneration,
            playbackUri = videoUri,
            details = mapOf(
                "holdNo" to hold.holdNo,
                "detectedHoldCount" to detectedHolds.size
            )
        )
    }

    fun updateSelectedEndHold(hold: Hold) {
        holdDetectionDelegate.updateSelectedEndHold(hold)
        clearHoldReachAnalysis()
        UploadAiTraceLogger.log(
            event = "END_HOLD_SELECTED",
            generation = selectionGeneration,
            playbackUri = videoUri,
            details = mapOf(
                "holdNo" to hold.holdNo,
                "numberedHoldCount" to numberedHolds.size
            )
        )
        if (numberedHolds.isNotEmpty()) {
            UploadAiTraceLogger.log(
                event = "NUMBERED_HOLDS_READY",
                generation = selectionGeneration,
                playbackUri = videoUri,
                details = mapOf("numberedHoldCount" to numberedHolds.size)
            )
        }
        maybeStartSubmissionAnalysisPrewarmForCurrentSelection()
    }

    fun selectGymGrade(grade: GymGrade) {
        challengeDelegate.selectGymGrade(
            grade = grade,
            formatSelectedLevelLabel = ::formatSelectedLevelLabel,
            onCreatedChallengeCleared = ::clearCreatedChallengeOnly
        )
    }

    fun createChallengeFromSelection() {
        delegateCreateChallengeFromSelection()
    }

    fun consumeChallengeCreationResult() {
        challengeDelegate.consumeChallengeCreationResult()
    }
    // ====== ?곹깭 ?낅뜲?댄듃 硫붿꽌??(?대깽???몃뱾?? ======

    /**
     * 화면 계약은 유지하고, 실제 video normalization / metadata / pre-pose 준비는
     * session delegate에 위임합니다.
     */
    fun updateVideoUri(
        uri: String,
        realtimeSessionId: String? = null
    ) {
        clearHoldPrecomputeState()
        invalidateSubmissionAnalysisPrewarm()
        holdDetectionDelegate.resetHoldDetectionState(clearDebugSource = true)
        sessionDelegate.updateVideoUri(
            uri = uri,
            realtimeSessionId = realtimeSessionId,
            callbacks = sessionCallbacks
        )
    }

    fun useDebugBestFrameImage(uri: String) {
        clearHoldPrecomputeState()
        invalidateSubmissionAnalysisPrewarm()
        holdDetectionDelegate.useDebugBestFrameImage(uri)
        clearHoldReachAnalysis()
        _uiState.value = UploadUiState.Idle
    }

    private fun refreshCurrentSelectionPrePoseTargets(generation: Long = selectionGeneration) {
        sessionDelegate.refreshCurrentSelectionPrePoseTargets(generation)
    }

    private suspend fun awaitActiveSelectionPreparation() {
        sessionDelegate.awaitActiveSelectionPreparation()
    }

    private suspend fun awaitPrePoseTerminal(playbackUris: List<String>): TerminalPrePoseSnapshot {
        return sessionDelegate.awaitPrePoseTerminal(
            playbackUris = playbackUris,
            callbacks = sessionCallbacks
        )
    }

    private suspend fun awaitSubmitReadyPrePose(
        playbackUris: List<String>,
        emitLoading: Boolean = true
    ): TerminalPrePoseSnapshot {
        return sessionDelegate.awaitSubmitReadyPrePose(
            playbackUris = playbackUris,
            callbacks = sessionCallbacks,
            emitLoading = emitLoading
        )
    }

    private fun onPrimaryVideoPrepared(
        generation: Long,
        playbackUri: String
    ) {
        if (generation != selectionGeneration || uploadFlowMode != UploadFlowMode.FullChallenge) {
            return
        }

        videoUri = playbackUri
        pendingPrimaryPrePoseGeneration = generation
        holdPrecomputeRequestedGeneration = generation
        UploadAiTraceLogger.log(
            event = "PRIMARY_VIDEO_PREPARED",
            generation = generation,
            playbackUri = playbackUri,
            details = mapOf("uploadFlowMode" to uploadFlowMode.name)
        )
        UploadAiTraceLogger.log(
            event = "PREPOSE_PENDING_MARKED",
            generation = generation,
            playbackUri = playbackUri,
            status = "pending"
        )
        invalidateSubmissionAnalysisPrewarm()
        holdDetectionDelegate.requestHoldPrecompute(
            selectionGeneration = generation,
            sourceVideoUri = playbackUri
        )
        UploadAiTraceLogger.log(
            event = "HOLD_PRECOMPUTE_REQUESTED",
            generation = generation,
            playbackUri = playbackUri
        )
        maybeStartHoldPrecomputeForCurrentSelection()
    }

    private fun maybeStartHoldPrecomputeForCurrentSelection() {
        val requestedGeneration = holdPrecomputeRequestedGeneration ?: return
        if (requestedGeneration != selectionGeneration) {
            return
        }

        val sourceVideoUri = videoUri
        if (sourceVideoUri == null && debugBestFrameImageUri == null) {
            UploadAiTraceLogger.log(
                event = "HOLD_PRECOMPUTE_SKIP_NO_SOURCE",
                generation = selectionGeneration,
                playbackUri = null
            )
            return
        }

        holdDetectionDelegate.requestHoldPrecompute(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceVideoUri
        )
        when (
            holdDetectionDelegate.ensurePrecomputeStarted(
                scope = viewModelScope,
                selectionGeneration = selectionGeneration,
                sourceVideoUri = sourceVideoUri
            )
        ) {
            UploadHoldDetectionDelegate.HoldDetectionPrecomputeStartResult.MissingSource -> {
                UploadAiTraceLogger.log(
                    event = "HOLD_PRECOMPUTE_SKIP_NO_SOURCE",
                    generation = selectionGeneration,
                    playbackUri = null
                )
            }

            UploadHoldDetectionDelegate.HoldDetectionPrecomputeStartResult.ReusedReady -> {
                UploadAiTraceLogger.log(
                    event = "HOLD_PRECOMPUTE_REUSE_READY",
                    generation = selectionGeneration,
                    playbackUri = sourceVideoUri,
                    status = "ready"
                )
                maybeStartPrimaryPrePoseAfterHoldPrecompute()
            }

            UploadHoldDetectionDelegate.HoldDetectionPrecomputeStartResult.ReusedRunning -> {
                UploadAiTraceLogger.log(
                    event = "HOLD_PRECOMPUTE_REUSE_RUNNING",
                    generation = selectionGeneration,
                    playbackUri = sourceVideoUri,
                    status = "running"
                )
                observeHoldPrecomputeTerminal(
                    generation = selectionGeneration,
                    sourceVideoUri = sourceVideoUri
                )
                maybeStartPrimaryPrePoseAfterHoldPrecompute()
            }

            UploadHoldDetectionDelegate.HoldDetectionPrecomputeStartResult.Started -> {
                UploadAiTraceLogger.log(
                    event = "HOLD_PRECOMPUTE_START",
                    generation = selectionGeneration,
                    playbackUri = sourceVideoUri
                )
                observeHoldPrecomputeTerminal(
                    generation = selectionGeneration,
                    sourceVideoUri = sourceVideoUri
                )
            }
        }
    }

    private fun observeHoldPrecomputeTerminal(
        generation: Long,
        sourceVideoUri: String?
    ) {
        holdPrecomputeObservationJob?.cancel()
        holdPrecomputeObservationJob = viewModelScope.launch {
            when (
                holdDetectionDelegate.awaitPrecomputeTerminal(
                    selectionGeneration = generation,
                    sourceVideoUri = sourceVideoUri
                )
            ) {
                UploadHoldDetectionDelegate.HoldDetectionPrecomputeTerminalResult.Ready -> {
                    UploadAiTraceLogger.log(
                        event = "HOLD_PRECOMPUTE_DONE",
                        generation = generation,
                        playbackUri = sourceVideoUri,
                        status = "success"
                    )
                    maybeStartPrimaryPrePoseAfterHoldPrecompute()
                    tryApplyCachedHoldFilter(updateUiStateOnSuccess = false)
                }

                UploadHoldDetectionDelegate.HoldDetectionPrecomputeTerminalResult.Failed -> {
                    UploadAiTraceLogger.log(
                        event = "HOLD_PRECOMPUTE_DONE",
                        generation = generation,
                        playbackUri = sourceVideoUri,
                        status = "failed"
                    )
                    maybeStartPrimaryPrePoseAfterHoldPrecompute()
                }

                UploadHoldDetectionDelegate.HoldDetectionPrecomputeTerminalResult.Missing -> Unit
            }
        }
    }

    private fun maybeStartPrimaryPrePoseAfterHoldPrecompute() {
        val pendingGeneration = pendingPrimaryPrePoseGeneration ?: return
        if (pendingGeneration != selectionGeneration) {
            pendingPrimaryPrePoseGeneration = null
            return
        }

        val sourceVideoUri = videoUri ?: return
        val holdReady = holdDetectionDelegate.isPrecomputeReady(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceVideoUri
        )
        val holdRunning = holdDetectionDelegate.isPrecomputeRunning(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceVideoUri
        )
        if (holdRunning) {
            UploadAiTraceLogger.log(
                event = "PREPOSE_WAIT_HOLD_RUNNING",
                generation = selectionGeneration,
                playbackUri = sourceVideoUri,
                status = "waiting"
            )
            return
        }

        if (!holdReady) {
            Log.d(TAG, "Hold precompute finished without ready state. Starting pre-pose anyway.")
            UploadAiTraceLogger.log(
                event = "PREPOSE_START_AFTER_HOLD_FAILED_OR_NOT_READY",
                generation = selectionGeneration,
                playbackUri = sourceVideoUri,
                status = "hold_not_ready"
            )
        } else {
            UploadAiTraceLogger.log(
                event = "PREPOSE_START_AFTER_HOLD_TERMINAL",
                generation = selectionGeneration,
                playbackUri = sourceVideoUri,
                status = "hold_ready"
            )
        }
        pendingPrimaryPrePoseGeneration = null
        refreshCurrentSelectionPrePoseTargets(selectionGeneration)
    }

    private fun maybeStartSubmissionAnalysisPrewarmForCurrentSelection() {
        if (isAttemptOnlyUploadMode || uploadFlowMode != UploadFlowMode.FullChallenge) {
            UploadAiTraceLogger.log(
                event = "ANALYSIS_PREWARM_SKIP_ATTEMPT_ONLY",
                generation = selectionGeneration,
                playbackUri = videoUri,
                status = uploadFlowMode.name
            )
            return
        }
        val request = buildCurrentSubmissionRequestOrNull() ?: return
        if (request.numberedHolds.isEmpty() || request.bestFrameBitmap == null) {
            UploadAiTraceLogger.log(
                event = if (request.numberedHolds.isEmpty()) {
                    "ANALYSIS_PREWARM_SKIP_MISSING_NUMBERED_HOLDS"
                } else {
                    "ANALYSIS_PREWARM_SKIP_MISSING_BITMAP"
                },
                generation = request.selectionGeneration,
                playbackUri = request.attemptUris.firstOrNull(),
                details = mapOf(
                    "numberedHoldCount" to request.numberedHolds.size,
                    "hasBestFrame" to (request.bestFrameBitmap != null)
                )
            )
            return
        }

        submissionDelegate.requestAnalysisPrewarm(
            scope = viewModelScope,
            request = request,
            callbacks = submissionCallbacks
        )
    }

    private fun buildCurrentSubmissionRequestOrNull(): UploadSubmissionRequest? {
        val currentVideoUri = videoUri
        if (currentVideoUri == null && allAttemptUris.isEmpty()) {
            return null
        }

        val currentChallengeId = challengeId
        val useLocalAnalysisOnly = allowLocalAnalysisWithoutChallenge &&
            !isAttemptOnlyUploadMode &&
            (currentChallengeId == null || currentChallengeId <= 0L)

        return UploadSubmissionRequest(
            selectionGeneration = selectionGeneration,
            challengeId = currentChallengeId,
            useLocalAnalysisOnly = useLocalAnalysisOnly,
            isAttemptOnlyUploadMode = isAttemptOnlyUploadMode,
            attemptUris = allAttemptUris,
            detectedHolds = detectedHolds,
            numberedHolds = numberedHolds,
            bestFrameBitmap = bestFrameBitmap,
            aiMode = selectedAiAnalysisMode,
            primaryRealtimeSessionId = primaryManagedVideo?.realtimeSessionId?.takeIf { it.isNotBlank() },
            holdCoordinates = buildChallengeHoldCoordinates()
        )
    }

    private fun tryApplyCachedHoldFilter(updateUiStateOnSuccess: Boolean): Boolean {
        val detectionTargetColor = resolveDetectionTargetHoldColor()
        if (detectionTargetColor.isBlank()) {
            return false
        }
        return holdDetectionDelegate.applyHoldColorFilter(
            selectionGeneration = selectionGeneration,
            detectionTargetColor = detectionTargetColor
        ).fold(
            onSuccess = { filterChanged ->
                if (filterChanged) {
                    clearHoldReachAnalysis()
                }
                if (updateUiStateOnSuccess) {
                    _uiState.value = UploadUiState.Success
                }
                true
            },
            onFailure = {
                false
            }
        )
    }

    private fun invalidateSubmissionAnalysisPrewarm() {
        submissionDelegate.invalidateAnalysisPrewarm()
    }

    private fun clearPosePrecomputeState(
        preservePlaybackUris: Set<String> = emptySet()
    ) {
        sessionDelegate.clearPosePrecomputeState(preservePlaybackUris)
    }

    private fun clearHoldPrecomputeState() {
        holdPrecomputeRequestedGeneration = null
        pendingPrimaryPrePoseGeneration = null
        holdPrecomputeObservationJob?.cancel()
        holdPrecomputeObservationJob = null
        holdDetectionDelegate.cancelPrecompute(clearDebugSource = false)
        holdDetectionEnsureJob?.cancel()
        holdDetectionEnsureJob = null
        invalidateSubmissionAnalysisPrewarm()
    }

    private fun resetAllSelectionPreparationJobs() {
        sessionDelegate.resetAllSelectionPreparationJobs()
    }

    private fun cleanupUnusedManagedTempFiles(forceDeleteAll: Boolean = false) {
        sessionDelegate.cleanupUnusedManagedTempFiles(forceDeleteAll)
    }

    private fun delegateCreateChallengeFromSelection() {
        viewModelScope.launch {
            val challenge = challengeDelegate.createChallengeFromSelection(
                startedAt = LocalDateTime.now().toString(),
                resolveDefaultHoldColorKey = { colorName ->
                    mapGymColorToClassifierColor(colorName)
                },
                resolveHoldColorDisplayName = { colorName ->
                    resolveHoldColorDisplayName(
                        colorName = colorName,
                        colorHex = null
                    )
                }
            )
            if (challenge != null) {
                allowLocalAnalysisWithoutChallenge = false
            }
        }
    }

    fun findCandidatesNearTap(tapNormX: Float, tapNormY: Float) {
        holdDetectionDelegate.findCandidatesNearTap(tapNormX, tapNormY)
    }

    /**
     * ?앹뾽?먯꽌 ?뺤씤 ???몄텧. 異붽?/?쒓굅瑜???踰덉뿉 ?곸슜?⑸땲??
     */
    fun applyHoldChanges(toAdd: List<Hold>, toRemove: List<Hold>) {
        holdDetectionDelegate.applyHoldChanges(toAdd, toRemove)
        clearHoldReachAnalysis()
    }

    /** ?꾨낫 ????앹뾽???レ뒿?덈떎. */
    fun dismissCandidatePopup() {
        holdDetectionDelegate.dismissCandidatePopup()
    }

    /**
     * detectedHolds?먯꽌 ??쒕? ?쒓굅?⑸땲??
     */
    fun removeHold(hold: Hold) {
        holdDetectionDelegate.removeHold(hold)
        clearHoldReachAnalysis()
    }

    /**
     * ?섎룞?쇰줈 ??쒕? detectedHolds??異붽??⑸땲??
     * allRawHolds?먮뒗 ?덉?留??됱긽 ?꾪꽣留곸쑝濡??꾨씫????쒕? 蹂듦뎄?????ъ슜?⑸땲??
     */
    fun updateAnalysisPoints(points: List<AnalysisPoint>) {
        analysisPoints = points
    }

    /**
     * ExoPlayer TextureView 罹≪쿂 ?꾨젅?꾩쑝濡??ㅼ떆媛??ъ쫰 異붾줎???ㅽ뻾?⑸땲??
     * AttemptResultScreen??LaunchedEffect 猷⑦봽?먯꽌 150ms 媛꾧꺽?쇰줈 ?몄텧?⑸땲??
     * ?댁쟾 異붾줎??吏꾪뻾 以묒씠硫??먮룞?쇰줈 嫄대꼫?곷땲??(PoseEstimatorImpl ?대? ?뚮옒洹?.
     */
    fun updatePoseFrame(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val landmarks = poseEstimator.estimateFromFrame(bitmap)
                withContext(Dispatchers.Main) {
                    currentPoseLandmarks = landmarks
                }
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    // (湲곗〈 ?⑥씪 異붽? URI 硫붿꽌?쒕뒗 ??젣?섍퀬 updateAdditionalVideoUris ?ъ슜)

    // ====== 鍮꾩쫰?덉뒪 濡쒖쭅 ======

    /**
     * PersonDetector 湲곕컲 理쒖쟻 ?꾨젅???먯깋 ?먮뒗 ?붾쾭洹??대?吏 ?좏깮 ??
     * HoldDetector ???됱긽 ?꾪꽣留곴퉴吏 ?섑뻾?섎뒗 ?꾩껜 ?뚯씠?꾨씪??
     */
    fun ensureHoldDetectionReadyForCurrentColor() {
        val debugImageUri = debugBestFrameImageUri
        val sourceVideoUri = videoUri

        if (debugImageUri == null && sourceVideoUri == null) {
            Log.e(TAG, "No video source available for hold detection")
            _uiState.value = UploadUiState.Error("?곸긽??癒쇱? ?좏깮?댁＜?몄슂.")
            return
        }

        holdDetectionEnsureJob?.cancel()
        markHoldPrecomputeEligibleForCurrentSelection()
        if (tryApplyCachedHoldFilter(updateUiStateOnSuccess = true)) {
            return
        }

        val runningPrecompute = holdDetectionDelegate.isPrecomputeRunning(
            selectionGeneration = selectionGeneration,
            sourceVideoUri = sourceVideoUri
        )

        if (runningPrecompute) {
            _uiState.value = UploadUiState.Loading
            holdDetectionEnsureJob = viewModelScope.launch {
                when (
                    holdDetectionDelegate.awaitPrecomputeTerminal(
                        selectionGeneration = selectionGeneration,
                        sourceVideoUri = sourceVideoUri
                    )
                ) {
                    UploadHoldDetectionDelegate.HoldDetectionPrecomputeTerminalResult.Ready -> {
                        if (tryApplyCachedHoldFilter(updateUiStateOnSuccess = true)) {
                            return@launch
                        }
                        runHoldDetection()
                    }

                    UploadHoldDetectionDelegate.HoldDetectionPrecomputeTerminalResult.Failed,
                    UploadHoldDetectionDelegate.HoldDetectionPrecomputeTerminalResult.Missing -> {
                        runHoldDetection()
                    }
                }
            }
            return
        }

        runHoldDetection()
    }

    fun runHoldDetection() {
        val debugImageUri = debugBestFrameImageUri
        val sourceVideoUri = videoUri

        if (debugImageUri == null && sourceVideoUri == null) {
            Log.e(TAG, "No video source available for hold detection")
            _uiState.value = UploadUiState.Error("?곸긽??癒쇱? ?좏깮?댁＜?몄슂.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UploadUiState.Loading

            holdDetectionDelegate.runHoldDetection(
                scope = viewModelScope,
                sourceVideoUri = sourceVideoUri,
                detectionTargetColor = resolveDetectionTargetHoldColor(),
                selectionGeneration = selectionGeneration
            ).onSuccess {
                clearHoldReachAnalysis()
                _uiState.value = UploadUiState.Success
            }.onFailure { throwable ->
                Log.e(TAG, "runHoldDetection failed", throwable)
                _uiState.value = UploadUiState.Error(
                    throwable.message ?: "????먯? 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎."
                )
            }
        }
    }
    /**
     * 理쒖쥌 梨뚮┛吏 ?먮뒗 ?곸긽???쒕쾭???쒖텧?⑸땲??
     */
    fun submitUpload() {
        when (analysisLoadingPhase) {
            AnalysisLoadingPhase.AttemptResultPreparation -> submitUploadForAttemptResult()
            AnalysisLoadingPhase.FinalAnalysisPreparation -> ensureFinalAnalysisReady()
        }
    }

    private fun submitUploadForAttemptResult() {
        if (uploadSubmissionUiState.value is UploadSubmissionUiState.Loading) {
            return
        }

        viewModelScope.launch {
            awaitActiveSelectionPreparation()
            val request = buildCurrentSubmissionRequestOrNull() ?: return@launch

            submissionDelegate.submitUploadForAttemptResult(
                scope = viewModelScope,
                request = request,
                callbacks = submissionCallbacks
            )
        }
    }

    fun ensureFinalAnalysisReady() {
        if (finalAnalysisPreparationUiState.value is FinalAnalysisPreparationUiState.Loading) {
            return
        }

        viewModelScope.launch {
            awaitActiveSelectionPreparation()
            val request = buildCurrentSubmissionRequestOrNull() ?: return@launch

            submissionDelegate.ensureFinalAnalysisReady(
                request = request,
                callbacks = submissionCallbacks
            )
        }
    }

    fun closeChallengeForFinalAnalysis(
        challengeResult: String,
        averageCenterStabilityRatio: Double?,
        mostCruxHoldNo: Int?,
        maxCruxDurationMs: Int?,
        finalComment: String?
    ) {
        val currentChallengeId = challengeId ?: return
        if (currentChallengeId <= 0L) {
            return
        }
        if (closedChallengeId == currentChallengeId || closingChallengeId == currentChallengeId) {
            return
        }

        viewModelScope.launch {
            closingChallengeId = currentChallengeId
            closeChallengeUseCase(
                challengeId = currentChallengeId,
                challengeResult = challengeResult,
                averageCenterStabilityRatio = averageCenterStabilityRatio,
                mostCruxHoldNo = mostCruxHoldNo,
                maxCruxDurationMs = maxCruxDurationMs,
                finalComment = finalComment
            ).onSuccess { closedChallenge ->
                closedChallengeId = currentChallengeId
                createdChallenge = createdChallenge?.copy(
                    challengeStatus = closedChallenge.challengeStatus
                )
            }.onFailure { throwable ->
                Log.e(TAG, "closeChallengeForFinalAnalysis: failed", throwable)
            }

            if (closingChallengeId == currentChallengeId) {
                closingChallengeId = null
            }
        }
    }

    fun retryCurrentAnalysisLoadingPhase() {
        when (analysisLoadingPhase) {
            AnalysisLoadingPhase.AttemptResultPreparation -> {
                submissionDelegate.resetUploadSubmissionState()
                submitUploadForAttemptResult()
            }

            AnalysisLoadingPhase.FinalAnalysisPreparation -> {
                submissionDelegate.resetFinalAnalysisPreparationState()
                ensureFinalAnalysisReady()
            }
        }
    }

    fun resetState() {
        holdDetectionEnsureJob?.cancel()
        _uiState.value = UploadUiState.Idle
    }

    private fun clearSelectedHoldSelection() {
        holdDetectionDelegate.clearSelectedHoldSelection()
        clearHoldReachAnalysis()
    }

    fun resetUploadSubmissionState() {
        submissionDelegate.resetUploadSubmissionState()
    }

    fun resetFinalAnalysisPreparationState() {
        submissionDelegate.resetFinalAnalysisPreparationState()
    }

    fun prepareAttemptResultAnalysisLoading() {
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        submissionDelegate.resetUploadSubmissionState()
    }

    fun prepareFinalAnalysisLoading() {
        analysisLoadingPhase = AnalysisLoadingPhase.FinalAnalysisPreparation
        submissionDelegate.resetFinalAnalysisPreparationState()
    }

    fun consumeBackgroundUploadNotice(id: Long) {
        submissionDelegate.consumeBackgroundUploadNotice(id)
    }

    fun retryBackgroundAttemptUpload() {
        submissionDelegate.retryBackgroundAttemptUpload(
            scope = viewModelScope,
            callbacks = submissionCallbacks
        )
    }

    private fun clearChallengeFlowState() {
        challengeDelegate.clearSelectionState()
        clearHoldPrecomputeState()
        clearSelectedHoldSelection()
        clearCreatedChallengeOnly()
    }

    private fun clearChallengeSelectionStatePreservingHoldPrecompute() {
        holdDetectionEnsureJob?.cancel()
        holdDetectionEnsureJob = null
        challengeDelegate.clearSelectionState()
        holdDetectionDelegate.clearAppliedHoldStatePreservingSourceCache()
        clearCreatedChallengeOnly()
        _uiState.value = UploadUiState.Idle
    }

    private fun clearCreatedChallengeOnly() {
        challengeDelegate.clearCreatedChallengeState()
        closedChallengeId = null
        closingChallengeId = null
        savedChallengeHolds = null
        uploadedAttemptVideos = emptyList()
        clearHoldReachAnalysis()
        clearAiAnalysisState()
        resultPlaybackUris = emptyList()
        publishedAttemptResultSession = null
        resetUploadSubmissionState()
    }

    private fun resolveAttemptSuccess(
        index: Int,
        fallback: Boolean
    ): Boolean {
        val totalHoldCount = totalSelectedHoldCount.takeIf { it > 0 } ?: numberedHolds.size
        return submissionDelegate.resolveAttemptSuccess(
            index = index,
            fallback = fallback,
            totalHoldCount = totalHoldCount
        )
    }

    private fun clearAiAnalysisState() {
        invalidateSubmissionAnalysisPrewarm()
        submissionDelegate.clearAiAnalysisState(submissionCallbacks)
    }

    private fun syncDisplayedAnalysisPoints() {
        analysisPoints = attemptPresentationResults
            .getOrNull(currentAttemptIndex)
            ?.second
            ?: attemptPresentationResults.firstOrNull()?.second
            ?: defaultUploadAnalysisPoints()
    }

    private fun clearHoldReachAnalysis() {
        invalidateSubmissionAnalysisPrewarm()
        submissionDelegate.clearHoldReachAnalysis(submissionCallbacks)
    }

    private fun clearAttemptResultState(clearPublishedSession: Boolean) {
        invalidateSubmissionAnalysisPrewarm()
        submissionDelegate.clearAttemptResultState(
            callbacks = submissionCallbacks,
            clearPublishedSession = clearPublishedSession
        )
    }

    private fun publishAttemptResultSession(
        playbackUris: List<String>,
        uploadedVideos: List<UploadedAttemptVideo>,
        currentAttemptIndex: Int,
        holdReachResults: List<AttemptHoldReachResult>,
        poseDtos: List<PoseSequenceDto>,
        analyzedPoses: List<List<Pose>>,
        polygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
        overallSummary: OverallHoldReachSummary?
    ) {
        submissionDelegate.publishAttemptResultSession(
            callbacks = submissionCallbacks,
            playbackUris = playbackUris,
            uploadedVideos = uploadedVideos,
            currentAttemptIndex = currentAttemptIndex,
            holdReachResults = holdReachResults,
            poseDtos = poseDtos,
            analyzedPoses = analyzedPoses,
            polygonHoldContactDebugResults = polygonHoldContactDebugResults,
            overallSummary = overallSummary
        )
    }

    private fun captureCurrentAttemptResultSession() {
        submissionDelegate.captureCurrentAttemptResultSession(submissionCallbacks)
    }

    private fun restorePublishedAttemptResultSession() {
        submissionDelegate.restorePublishedAttemptResultSession(submissionCallbacks)
    }

    private fun publishedResultPlaybackUris(): Set<String> =
        sessionDelegate.publishedResultPlaybackUris()

    private fun buildChallengeHoldCoordinates(): List<ChallengeHoldCoordinate> {
        return detectedHolds.mapIndexed { index, hold ->
            ChallengeHoldCoordinate(
                holdNo = hold.holdNo.takeIf { it > 0 } ?: (index + 1),
                boundingBox = HoldBoundingBox(
                    x1 = hold.boundingBox.left,
                    x2 = hold.boundingBox.right,
                    y1 = hold.boundingBox.top,
                    y2 = hold.boundingBox.bottom
                ),
                polygon = hold.polygon.map { point ->
                    HoldPoint(x = point.x, y = point.y)
                }
            )
        }
    }

    private fun resolveDetectionTargetHoldColor(): String {
        return selectedHoldColorKey
            ?.let { resolveClassifierHoldColor(colorName = it, colorHex = null) ?: it }
            ?: holdColor.takeIf { it.isNotBlank() }?.let(::mapGymColorToClassifierColor)
            ?: createdChallenge?.let { mapGymColorToClassifierColor(it.problemColor) }
            ?: ""
    }

    private fun mapGymGradeToClassifierColor(grade: GymGrade): String {
        return mapGymColorToClassifierColor(
            colorName = grade.colorName,
            colorHex = grade.colorHex
        ) ?: ""
    }

    private fun mapGymColorToClassifierColor(
        colorName: String,
        colorHex: String? = null
    ): String? {
        return resolveClassifierHoldColor(
            colorName = colorName,
            colorHex = colorHex
        ) ?: colorName.trim().lowercase().takeIf { it.isNotBlank() }
    }

    private fun formatSelectedLevelLabel(grade: GymGrade): String {
        return grade.gradeLabel
            ?.takeIf { it.isNotBlank() }
            ?: "V${grade.sortOrder}"
    }
}

private fun defaultUploadAnalysisPoints(): List<AnalysisPoint> = listOf(
    AnalysisPoint(1, 21_000L, "두 손 지지가 길어졌어요"),
    AnalysisPoint(2, 48_000L, "오른쪽 팔에 무게가 실렸어요"),
    AnalysisPoint(3, 66_000L, "무게 이동이 늦어졌어요")
)

private fun AiAnalysisResult.toAnalysisPoints(): List<AnalysisPoint> {
    val candidates = cruxResult.topCandidates.ifEmpty {
        cruxResult.allCandidates.take(3)
    }

    return candidates.take(3).mapIndexed { index, candidate ->
        val reasonText = candidate.reasonTags
            .firstOrNull()
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val description = buildString {
            append("홀드 ${candidate.holdId}")
            append(": ")
            append(
                reasonText ?: when (mode) {
                    AiAnalysisMode.FAST -> "머무른 시간이 길었어요"
                    AiAnalysisMode.PHYSICS -> "부하가 무겁게 걸렸어요"
                }
            )
        }

        AnalysisPoint(
            index = index + 1,
            timeMs = candidate.bestSegment?.startTimeMs ?: ((index + 1) * 15_000L),
            description = description
        )
    }
}

internal fun normalizeVideoRotationDegrees(rotationDegrees: Int): Int {
    val normalized = ((rotationDegrees % 360) + 360) % 360
    return when (normalized) {
        90, 180, 270 -> normalized
        else -> 0
    }
}


