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
import com.ddgo.app.domain.usecase.CloseChallengeUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.DetectStallSegmentFromPoseUseCase
import com.ddgo.app.domain.usecase.DetectWallArrivalTimeUseCase
import com.ddgo.app.domain.usecase.DetectStablePersonObservationUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ddgo.app.feature.climbing.record.presentation.HeartRatePoint
import com.ddgo.app.feature.climbing.record.presentation.RecordedAttemptDraft
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 업로드 기반 클라이밍 분석 플로우 요약.
 *
 * AttemptUploadScreen   : 영상 업로드
 * ChallengeCreateScreen : 암장 선택 -> 난이도 선택 -> 홀드 색 선택
 * ChallengeHoldScreen   : 검출된 홀드 확인 및 시작/종료 홀드 선택
 * AdditionalUploadScreen: 추가 시도 영상 업로드
 * AttemptResultScreen   : 업로드한 시도 영상 분석 결과 확인
 */

private const val TAG = "UploadViewModel"
private const val ATTEMPT_HOLD_ALIGNMENT_LOG_PREFIX = "[DDGO_ATTEMPT_HOLD_ALIGN]"

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
    private val detectStallSegmentFromPoseUseCase: DetectStallSegmentFromPoseUseCase,
    private val detectWallArrivalTimeUseCase: DetectWallArrivalTimeUseCase,
    private val detectStablePersonObservationUseCase: DetectStablePersonObservationUseCase,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val analyzeAttemptWithAiUseCase: AnalyzeAttemptWithAiUseCase
) : ViewModel() {

    // UI 레이어에 노출할 상태
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
        detectStallSegmentFromPoseUseCase = detectStallSegmentFromPoseUseCase,
        detectWallArrivalTimeUseCase = detectWallArrivalTimeUseCase,
        detectStablePersonObservationUseCase = detectStablePersonObservationUseCase,
        scope = viewModelScope
    )
    private val submissionDelegate = UploadSubmissionDelegate(
        saveChallengeHoldsUseCase = saveChallengeHoldsUseCase,
        uploadAttemptVideoUseCase = uploadAttemptVideoUseCase,
        endAttemptUseCase = endAttemptUseCase,
        getMyInfoUseCase = getMyInfoUseCase,
        analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase
    )
    private val attemptHoldAlignmentDelegate = UploadAttemptHoldAlignmentDelegate(
        context = context,
        personDetector = personDetector,
        holdDetector = holdDetector,
        holdColorClassifier = holdColorClassifier,
        scope = viewModelScope
    )
    private var pendingRealtimeHeartRateSeriesBySourceUri by mutableStateOf<Map<String, List<HeartRatePoint>>>(emptyMap())
    private var realtimeHeartRateSeriesByPlaybackUri by mutableStateOf<Map<String, List<HeartRatePoint>>>(emptyMap())
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

    // 전체 시도 영상 목록(기본 영상 + 추가 영상)
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

    private var entryMode: UploadEntryMode
        get() = sessionDelegate.entryMode
        private set(value) {
            sessionDelegate.entryMode = value
        }

    val isRealtimeEntryMode: Boolean
        get() = entryMode == UploadEntryMode.Realtime

    var realtimeAttemptActionState by mutableStateOf<RealtimeAttemptActionState>(RealtimeAttemptActionState.Idle)
        private set

    val canBypassChallengeCreationForDev: Boolean
        get() = allowLocalAnalysisWithoutChallenge

    val realtimeOverlayUiState: UploadRealtimeOverlayUiState
        get() = UploadRealtimeOverlayUiState(
            setupStep = realtimeSetupStep,
            gymId = gymId,
            gymName = gymName,
            searchQuery = realtimeGymSearchQuery,
            nearbyPlaces = sortedNearbyPlaces,
            selectedNearbyPlace = selectedNearbyPlace,
            nearbyPlaceSortMode = challengeDelegate.nearbyPlaceSortMode,
            gymSearchUiState = gymSearchUiState.value,
            gymResolveUiState = gymResolveUiState.value,
            resolvedGym = resolvedGym,
            resolvedGymGrades = resolvedGymGrades,
            selectedLevelSortOrder = selectedLevelSortOrder,
            selectedGymGrade = selectedGymGrade,
            difficultyLabel = difficultyLevel.ifBlank {
                selectedGymGrade?.let(::formatSelectedLevelLabel).orEmpty()
            },
            selectedHoldColorKey = selectedHoldColorKey,
            holdColor = holdColor,
            holdColorOptions = realtimeHoldColorOptions,
            challengeCreationUiState = challengeCreationUiState.value,
            isHoldColorSheetVisible = realtimeHoldColorSheetVisible,
            isSetupVisible = isRealtimeEntryMode &&
                (realtimeSetupStep != RealtimeSetupStep.Ready ||
                    challengeCreationUiState.value is ChallengeCreationUiState.Loading),
            isChallengeReady = isRealtimeCaptureReady,
            isRetakePrepared = realtimeAttemptActionState == RealtimeAttemptActionState.RetakeRequested,
            canFinishChallenge = canFinishRealtimeChallenge,
            canRetakeAttempt = canRetakeRealtimeAttempt,
            lastSearchLatitude = lastSearchLatitude,
            lastSearchLongitude = lastSearchLongitude
        )

    val sortedNearbyPlaces: List<NearbyPlace>
        get() = challengeDelegate.sortedNearbyPlaces

    private val isRealtimeCaptureReady: Boolean
        get() = isRealtimeEntryMode &&
            (challengeId ?: 0L) > 0L &&
            (gymId != null || resolvedGym != null) &&
            selectedGymGradeId != null &&
            (selectedHoldColorKey != null || holdColor.isNotBlank())

    val canFinishRealtimeChallenge: Boolean
        get() = isRealtimeCaptureReady &&
            uploadedAttemptVideos.isNotEmpty() &&
            (challengeId ?: 0L) > 0L

    val canRetakeRealtimeAttempt: Boolean
        get() = isRealtimeCaptureReady && uploadedAttemptVideos.isNotEmpty()

    private fun hasRealtimeGymSelection(): Boolean {
        return gymId != null || selectedNearbyPlace != null || gymName.isNotBlank()
    }

    private var realtimeGymSearchQuery by mutableStateOf("")
    private var realtimeHoldColorSheetVisible by mutableStateOf(false)
    private var realtimeSetupStep by mutableStateOf(RealtimeSetupStep.GymPrompt)

    // 썸네일/메타데이터(PersonDetector 기반 대표 프레임과 파일 정보)
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

    // --- 2. ChallengeCreateScreen (암장/난이도/홀드 색 선택) ---
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
            this@UploadViewModel.maybeStartHoldPrecomputeForCurrentSelection()
            this@UploadViewModel.refreshAttemptHoldAlignmentTargets()
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
    val attemptHoldAlignmentBatchState: AttemptHoldAlignmentBatchState
        get() = attemptHoldAlignmentDelegate.attemptHoldAlignmentBatchState

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
     * 주변 암장 검색 UI 상태.
     *
     * 로딩/성공/실패 상태를 화면에 전달합니다.
     */
    val gymSearchUiState = challengeDelegate.gymSearchUiState

    /**
     * 선택한 장소의 gym resolve UI 상태.
     *
     * 사용자가 장소를 선택했을 때 서버 resolve 진행 상태를 화면에 전달합니다.
     */
    val gymResolveUiState = challengeDelegate.gymResolveUiState

    /** Kakao Local API에서 가져온 주변 장소 목록 */
    var nearbyPlaces: List<NearbyPlace>
        get() = challengeDelegate.nearbyPlaces
        private set(value) {
            challengeDelegate.nearbyPlaces = value
        }

    /** 사용자가 선택한 장소 */
    var selectedNearbyPlace: NearbyPlace?
        get() = challengeDelegate.selectedNearbyPlace
        private set(value) {
            challengeDelegate.selectedNearbyPlace = value
        }

    /** 서버 resolve 결과 */
    var resolvedGym: ResolvedGym?
        get() = challengeDelegate.resolvedGym
        private set(value) {
            challengeDelegate.resolvedGym = value
        }

    /**
     * resolve 결과로 내려온 gym grade 목록.
     *
     * 다음 단계에서 gymGradeId 기반 선택 UI에 사용합니다.
     */
    var resolvedGymGrades: List<GymGrade>
        get() = challengeDelegate.resolvedGymGrades
        private set(value) {
            challengeDelegate.resolvedGymGrades = value
        }

    /** 마지막 검색 위치 */
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

    // --- 3. ChallengeHoldScreen (홀드 검출 결과) ---
    /** PersonDetector가 찾은 최적 프레임 비트맵 */
    var bestFrameBitmap: Bitmap?
        get() = holdDetectionDelegate.bestFrameBitmap
        private set(value) {
            holdDetectionDelegate.bestFrameBitmap = value
        }

    /** 디버그용으로 수동 선택한 best frame 이미지 URI */
    var debugBestFrameImageUri: String?
        get() = holdDetectionDelegate.debugBestFrameImageUri
        private set(value) {
            holdDetectionDelegate.debugBestFrameImageUri = value
        }

    /** YOLO가 검출한 전체 홀드 목록 */
    var allRawHolds: List<Hold>
        get() = holdDetectionDelegate.allRawHolds
        private set(value) {
            holdDetectionDelegate.allRawHolds = value
        }

    /** 색상 필터링과 수동 수정이 반영된 화면용 홀드 목록 */
    var detectedHolds: List<Hold>
        get() = holdDetectionDelegate.detectedHolds
        private set(value) {
            holdDetectionDelegate.detectedHolds = value
        }

    /** 수동 추가 팝업에 표시할 후보 홀드 목록 */
    var candidateHolds: List<Hold>
        get() = holdDetectionDelegate.candidateHolds
        private set(value) {
            holdDetectionDelegate.candidateHolds = value
        }

    /** 수동 추가 후보 팝업 표시 여부 */
    var showCandidatePopup: Boolean
        get() = holdDetectionDelegate.showCandidatePopup
        private set(value) {
            holdDetectionDelegate.showCandidatePopup = value
        }

    /** 사용자가 선택한 시작 홀드 */
    var selectedStartHold: Hold?
        get() = holdDetectionDelegate.selectedStartHold
        private set(value) {
            holdDetectionDelegate.selectedStartHold = value
        }

    /** 사용자가 선택한 종료 홀드 */
    var selectedEndHold: Hold?
        get() = holdDetectionDelegate.selectedEndHold
        private set(value) {
            holdDetectionDelegate.selectedEndHold = value
        }

    /** 시작/중간/종료 역할 기준으로 번호가 부여된 홀드 목록 */
    var numberedHolds: List<HoldNumbered>
        get() = holdDetectionDelegate.numberedHolds
        private set(value) {
            holdDetectionDelegate.numberedHolds = value
        }

    /** 시도별 최고 도달 홀드 분석 결과 */
    var attemptHoldReachResults: List<AttemptHoldReachResult>
        get() = submissionDelegate.attemptHoldReachResults
        private set(value) {
            submissionDelegate.attemptHoldReachResults = value
        }

    /** 시도별 MediaPipe Pose DTO */
    var attemptPoseDtos: List<PoseSequenceDto>
        get() = submissionDelegate.attemptPoseDtos
        private set(value) {
            submissionDelegate.attemptPoseDtos = value
        }

    /** 시도별 분석용 MediaPipe Pose 프레임 */
    var attemptAnalyzedPoses: List<List<Pose>>
        get() = submissionDelegate.attemptAnalyzedPoses
        private set(value) {
            submissionDelegate.attemptAnalyzedPoses = value
        }

    private var attemptAlignedHoldSets: List<AttemptAlignedHoldSet>
        get() = submissionDelegate.attemptAlignedHoldSets
        private set(value) {
            submissionDelegate.attemptAlignedHoldSets = value
        }

    /** 시도별 폴리곤 홀드 접촉 디버그 결과 */
    var attemptPolygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>
        get() = submissionDelegate.attemptPolygonHoldContactDebugResults
        private set(value) {
            submissionDelegate.attemptPolygonHoldContactDebugResults = value
        }

    /** 시도별 pre-pose 포즈 시퀀스 캐시 */
    val attemptPoseSequences: List<List<Pose>>
        get() = playbackAttemptUris.map { playbackUri ->
            prePoseCacheEntries[playbackUri]
                ?.takeIf { it.status == PrePoseStatus.Ready }
                ?.poses
                .orEmpty()
        }

    /** 여러 시도의 평균 도달 홀드 요약 */
    var overallHoldReachSummary: OverallHoldReachSummary?
        get() = submissionDelegate.overallHoldReachSummary
        private set(value) {
            submissionDelegate.overallHoldReachSummary = value
        }

    // --- 4. AttemptResultScreen (재생 오버레이 + 분석 타임라인) ---

    // N차 시도를 추적하는 인덱스(0은 첫 번째 시도)
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

    /** 현재 재생 프레임의 MediaPipe 33 랜드마크 */
    var currentPoseLandmarks by mutableStateOf<List<PoseLandmark>>(emptyList())
        private set

    /** 현재 선택한 시도의 pre-pose 포즈 시퀀스 */
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

    /** 현재 선택한 시도의 최고 도달 홀드 결과 */
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

    /** 현재 선택한 시도의 MediaPipe Pose DTO */
    val currentAttemptPoseDto: PoseSequenceDto?
        get() = attemptPoseDtos.getOrNull(currentAttemptIndex)

    /** 현재 선택한 시도의 분석용 MediaPipe Pose 프레임 */
    val currentAttemptAnalyzedPoses: List<Pose>
        get() = attemptAnalyzedPoses.getOrNull(currentAttemptIndex).orEmpty()

    /** 현재 선택한 시도의 폴리곤 홀드 접촉 디버그 결과 */
    val currentAttemptPolygonHoldContactDebugResult: PolygonHoldContactDebugResult?
        get() = attemptPolygonHoldContactDebugResults.getOrNull(currentAttemptIndex)
    internal val currentAttemptAlignedSelection: AttemptAlignedHoldSet?
        get() = playbackAttemptUris
            .getOrNull(currentAttemptIndex)
            ?.let { playbackUri ->
                attemptAlignedHoldSets.firstOrNull { it.playbackUri == playbackUri }
                    ?: attemptHoldAlignmentDelegate.alignedHoldSetFor(playbackUri)
            }
    internal val currentAttemptCropBounds: RawVerticalCropBounds?
        get() {
            val currentPlaybackUri = playbackAttemptUris.getOrNull(currentAttemptIndex)
            val rawBounds = currentAttemptAlignedSelection?.rawCropBounds
                ?: currentPlaybackUri
                    ?.takeIf { playbackUri -> playbackUri == videoUri }
                    ?.let { calculateRawVerticalCropBounds(allRawHolds) }

            return resolveHybridVerticalCropBounds(
                rawBounds = rawBounds,
                selectedHolds = currentAttemptDisplayHolds
            )
        }
    val currentAttemptDisplayHolds: List<HoldNumbered>
        get() = currentAttemptAlignedSelection?.alignedHolds.orEmpty()
            .ifEmpty { numberedHolds }

    /** 최종 분석 화면에 표시할 평균 도달 홀드 번호 */
    val averageReachedHoldNo: Int
        get() = overallHoldReachSummary?.roundedAverageHighestReachedHoldNo ?: 0

    /** 최종 분석 화면 분모로 사용할 전체 선택 홀드 수 */
    val totalSelectedHoldCount: Int
        get() = overallHoldReachSummary?.totalHoldCount ?: numberedHolds.size

    /**
     * 분석 피드백 타임라인 목록.
     * MVP 단계에서는 프런트 샘플 데이터를 사용하고, 서버 연동 후 updateAnalysisPoints()로 교체합니다.
     */
    var analysisPoints by mutableStateOf<List<AnalysisPoint>>(defaultUploadAnalysisPoints())
        private set

    // 임시 더미 결과. 여러 시도별로 다른 타임라인을 보여주기 위한 샘플 데이터다.
    val attemptDummyResults = listOf(
        Pair(false, defaultUploadAnalysisPoints()),
        Pair(true, listOf(
            AnalysisPoint(1, 15_000L, "안정적인 스타트 구간이에요"),
            AnalysisPoint(2, 35_000L, "오른쪽으로 무게 중심이 이동했어요"),
            AnalysisPoint(3, 50_000L, "상단 구간을 공략했어요")
        ))
    )

    // --- 5. AttemptUploadScreen (추가 영상 업로드) ---
    fun updateAdditionalVideoUris(uris: List<String>) {
        invalidateSubmissionAnalysisPrewarm()
        sessionDelegate.updateAdditionalVideoUris(
            uris = uris,
            callbacks = sessionCallbacks
        )
    }

    /**
     * 새 챌린지 생성 흐름으로 진입합니다.
     *
     * 기존 추가 시도 업로드 모드가 켜져 있더라도 기본 업로드 모드로 되돌리고,
     * 이전 attempt-only 선택 상태를 비웁니다.
     */
    suspend fun beginNewChallengeUploadFlow(): Boolean {
        if (!abandonCurrentChallengeIfNeeded()) {
            return false
        }
        beginNewChallengeUploadFlowInternal()
        return true
    }

    suspend fun beginRealtimeChallengeUploadFlow(): Boolean {
        if (!beginNewChallengeUploadFlow()) {
            return false
        }
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Realtime
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        realtimeGymSearchQuery = ""
        realtimeHoldColorSheetVisible = false
        realtimeSetupStep = RealtimeSetupStep.GymPrompt
        return true
    }

    fun beginRealtimeRetakeUploadFlow() {
        if (!isRealtimeEntryMode) {
            return
        }

        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Realtime
        uploadFlowMode = UploadFlowMode.AttemptOnly
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        realtimeHoldColorSheetVisible = false
        realtimeGymSearchQuery = gymName
        realtimeSetupStep = RealtimeSetupStep.Ready
        allowLocalAnalysisWithoutChallenge = false
        captureCurrentAttemptResultSession()
        clearHoldPrecomputeState()
        clearAttemptResultState(clearPublishedSession = false)
        submissionDelegate.resetFinalAnalysisPreparationState()
        sessionDelegate.resetAllSelectionPreparationJobs()
        clearPosePrecomputeState(preservePlaybackUris = publishedResultPlaybackUris())
        cleanupUnusedManagedTempFiles()
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
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Gallery
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        uploadFlowMode = UploadFlowMode.AttemptOnly
        allowLocalAnalysisWithoutChallenge = false
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        resetUploadSubmissionState()
        resetFinalAnalysisPreparationState()
        refreshCurrentSelectionPrePoseTargets()
        refreshAttemptHoldAlignmentTargets()
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
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Gallery
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        realtimeGymSearchQuery = challenge.gymName
        realtimeHoldColorSheetVisible = false
        uploadFlowMode = UploadFlowMode.AttemptOnly
        allowLocalAnalysisWithoutChallenge = false
        clearHoldPrecomputeState()
        clearAttemptHoldAlignmentState()
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
        resetFinalAnalysisPreparationState()
        cleanupUnusedManagedTempFiles()
    }

    /**
     * 異붽? ?쒕룄 ?낅줈??紐⑤뱶瑜?痍⑥냼?섍퀬 ?먮옒 梨뚮┛吏 ?먮쫫 紐⑤뱶濡??뚯븘媛묐땲??
     */
    fun cancelAttemptOnlyUploadMode() {
        invalidateSubmissionAnalysisPrewarm()
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Gallery
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        realtimeHoldColorSheetVisible = false
        uploadFlowMode = UploadFlowMode.FullChallenge
        allowLocalAnalysisWithoutChallenge = false
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        clearPosePrecomputeState(
            preservePlaybackUris = allAttemptUris.toSet() + publishedResultPlaybackUris()
        )
        clearAttemptHoldAlignmentState(
            preservePlaybackUris = allAttemptUris.toSet() + publishedResultPlaybackUris()
        )
        restorePublishedAttemptResultSession()
        refreshCurrentSelectionPrePoseTargets(selectionGeneration)
        refreshAttemptHoldAlignmentTargets()
        resetUploadSubmissionState()
        resetFinalAnalysisPreparationState()
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
        val isRealtimeSearch = isRealtimeEntryMode
        val onChallengeFlowCleared =
            if (isRealtimeSearch) {
                ::clearRealtimeChallengeSelectionStatePreservingHoldPrecompute
            } else {
                ::clearChallengeSelectionStatePreservingHoldPrecompute
            }
        viewModelScope.launch {
            if (!abandonCurrentChallengeIfNeeded()) {
                return@launch
            }
            if (isRealtimeSearch) {
                realtimeSetupStep = RealtimeSetupStep.GymList
            }
            realtimeGymSearchQuery = query.trim()
            challengeDelegate.searchNearbyPlaces(
                latitude = latitude,
                longitude = longitude,
                query = query,
                nearbyOnly = nearbyOnly,
                onChallengeFlowCleared = onChallengeFlowCleared
            )
        }
    }

    fun resolveSelectedPlace(place: NearbyPlace) {
        val isRealtimeSearch = isRealtimeEntryMode
        val onChallengeFlowCleared =
            if (isRealtimeSearch) {
                ::clearRealtimeChallengeSelectionStatePreservingHoldPrecompute
            } else {
                ::clearChallengeSelectionStatePreservingHoldPrecompute
            }
        viewModelScope.launch {
            if (!abandonCurrentChallengeIfNeeded()) {
                return@launch
            }
            realtimeGymSearchQuery = place.placeName
            challengeDelegate.resolveSelectedPlace(
                place = place,
                onChallengeFlowCleared = onChallengeFlowCleared
            )
            if (isRealtimeSearch && resolvedGym != null) {
                realtimeSetupStep = RealtimeSetupStep.ChallengeCreate
            }
        }
    }

    fun selectGymLevel(sortOrder: Int) {
        val isRealtimeSelection = isRealtimeEntryMode
        viewModelScope.launch {
            if (!abandonCurrentChallengeIfNeeded()) {
                return@launch
            }
            challengeDelegate.selectGymLevel(
                sortOrder = sortOrder,
                formatSelectedLevelLabel = ::formatSelectedLevelLabel,
                onCreatedChallengeCleared = if (isRealtimeSelection) {
                    ::clearRealtimeCreatedChallengeOnly
                } else {
                    ::clearCreatedChallengeOnly
                }
            )
            ensureRealtimeDefaultHoldColorSetup()
            if (isRealtimeSelection) {
                realtimeHoldColorSheetVisible = false
                realtimeSetupStep = RealtimeSetupStep.ChallengeCreate
            }
        }
    }

    fun updateHoldColor(colorKey: String) {
        invalidateSubmissionAnalysisPrewarm()
        challengeDelegate.updateHoldColor(colorKey) { colorName ->
            resolveHoldColorDisplayName(
                colorName = colorName,
                colorHex = null
            )
        }
        refreshAttemptHoldAlignmentTargets()
    }

    fun onRealtimeGymSearchQueryChanged(query: String) {
        realtimeGymSearchQuery = query
    }

    fun openRealtimeGymList() {
        entryMode = UploadEntryMode.Realtime
        realtimeSetupStep = RealtimeSetupStep.GymList
    }

    fun onRealtimeNearbyPlaceSortChanged(sortMode: NearbyPlaceSortMode) {
        challengeDelegate.updateNearbyPlaceSortMode(sortMode)
    }

    fun onRealtimeNearbyPlaceSelected(place: NearbyPlace) {
        realtimeGymSearchQuery = place.placeName
        resolveSelectedPlace(place)
    }

    fun onRealtimeDifficultySelected(sortOrder: Int) {
        selectGymLevel(sortOrder)
    }

    fun onRealtimeGymGradeSelected(grade: GymGrade) {
        entryMode = UploadEntryMode.Realtime
        selectGymGrade(grade)
    }

    fun updateRealtimeHoldColorSheetVisible(visible: Boolean) {
        if (visible && !isRealtimeCaptureReady) {
            return
        }
        realtimeHoldColorSheetVisible = visible
    }

    fun toggleRealtimeHoldColorSheetVisible() {
        updateRealtimeHoldColorSheetVisible(!realtimeHoldColorSheetVisible)
    }

    fun onRealtimeHoldColorSelected(colorKey: String) {
        updateHoldColor(colorKey)
        if (realtimeSetupStep == RealtimeSetupStep.Ready) {
            realtimeHoldColorSheetVisible = false
        }
    }

    fun completeRealtimeChallengeSetup() {
        if (!isRealtimeEntryMode || realtimeSetupStep != RealtimeSetupStep.ChallengeCreate) {
            return
        }

        finalizeHoldDetectionColorSelection()
        delegateCreateChallengeFromSelection(
            onSuccess = {
                realtimeSetupStep = RealtimeSetupStep.Ready
                realtimeHoldColorSheetVisible = false
            }
        )
    }

    fun ensureRealtimeDefaultHoldColorSetup() {
        if (selectedHoldColorKey != null && holdColor.isNotBlank()) {
            return
        }

        val candidateColorName = selectedGymGrade?.colorName?.takeIf { it.isNotBlank() }
            ?: resolvedGymGrades.firstOrNull()?.colorName?.takeIf { it.isNotBlank() }
            ?: createdChallenge?.problemColor?.takeIf { it.isNotBlank() }

        val resolvedKey = candidateColorName
            ?.let { resolveHoldColorKey(colorName = it, colorHex = null) }
            ?: candidateColorName

        if (!resolvedKey.isNullOrBlank()) {
            updateHoldColor(resolvedKey)
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
        refreshAttemptHoldAlignmentTargets()
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
        refreshAttemptHoldAlignmentTargets()
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
        val isRealtimeSelection = isRealtimeEntryMode
        viewModelScope.launch {
            if (!abandonCurrentChallengeIfNeeded()) {
                return@launch
            }
            challengeDelegate.selectGymGrade(
                grade = grade,
                formatSelectedLevelLabel = ::formatSelectedLevelLabel,
                onCreatedChallengeCleared = if (isRealtimeSelection) {
                    ::clearRealtimeCreatedChallengeOnly
                } else {
                    ::clearCreatedChallengeOnly
                }
            )
            ensureRealtimeDefaultHoldColorSetup()
            if (isRealtimeSelection) {
                realtimeHoldColorSheetVisible = false
                realtimeSetupStep = RealtimeSetupStep.ChallengeCreate
            }
        }
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
        uri: String
    ) {
        val isRealtimeAttempt = entryMode == UploadEntryMode.Realtime
        clearHoldPrecomputeState()
        submissionDelegate.resetFinalAnalysisPreparationState()
        clearAttemptHoldAlignmentState()
        if (isRealtimeAttempt) {
            entryMode = UploadEntryMode.Realtime
            sessionDelegate.updateRealtimeVideoUri(
                uri = uri,
                callbacks = sessionCallbacks
            )
            realtimeAttemptActionState = RealtimeAttemptActionState.Idle
            return
        }

        entryMode = UploadEntryMode.Gallery
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        invalidateSubmissionAnalysisPrewarm()
        holdDetectionDelegate.resetHoldDetectionState(clearDebugSource = true)
        sessionDelegate.updateVideoUri(
            uri = uri,
            callbacks = sessionCallbacks
        )
    }

    fun updateRealtimeVideoUri(
        uri: String
    ) {
        entryMode = UploadEntryMode.Realtime
        updateVideoUri(uri = uri)
    }

    fun registerRealtimeRecordedAttempt(draft: RecordedAttemptDraft) {
        if (draft.heartRateSeries.isEmpty()) {
            pendingRealtimeHeartRateSeriesBySourceUri =
                pendingRealtimeHeartRateSeriesBySourceUri - draft.videoUri
            return
        }
        pendingRealtimeHeartRateSeriesBySourceUri =
            pendingRealtimeHeartRateSeriesBySourceUri + (draft.videoUri to draft.heartRateSeries)
    }

    fun heartRateSeriesForPlaybackUri(playbackUri: String?): List<HeartRatePoint> {
        val uri = playbackUri?.takeIf { it.isNotBlank() } ?: return emptyList()
        return realtimeHeartRateSeriesByPlaybackUri[uri].orEmpty()
    }

    fun needsRealtimeHoldSelection(): Boolean {
        return isRealtimeEntryMode &&
            (numberedHolds.isEmpty() || bestFrameBitmap == null)
    }

    fun useDebugBestFrameImage(uri: String) {
        clearHoldPrecomputeState()
        clearAttemptHoldAlignmentState()
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

    private suspend fun awaitAttemptHoldAlignmentTerminal(playbackUris: List<String>) {
        if (playbackUris.isEmpty()) {
            return
        }

        Log.d(
            TAG,
            "$ATTEMPT_HOLD_ALIGNMENT_LOG_PREFIX await terminal start: playbackCount=${playbackUris.size}"
        )
        attemptHoldAlignmentDelegate.awaitTerminal(playbackUris) { loadingMessage ->
            submissionDelegate.setUploadSubmissionLoading(loadingMessage)
        }
        Log.d(
            TAG,
            "$ATTEMPT_HOLD_ALIGNMENT_LOG_PREFIX await terminal done: " +
                "ready=${attemptHoldAlignmentBatchState.readyCount}, " +
                "failed=${attemptHoldAlignmentBatchState.failedCount}"
        )
    }

    private fun onPrimaryVideoPrepared(
        generation: Long,
        playbackUri: String
    ) {
        if (generation != selectionGeneration || uploadFlowMode != UploadFlowMode.FullChallenge) {
            return
        }

        primaryManagedVideo?.sourceUri?.let { sourceUri ->
            pendingRealtimeHeartRateSeriesBySourceUri[sourceUri]?.let { series ->
                realtimeHeartRateSeriesByPlaybackUri =
                    realtimeHeartRateSeriesByPlaybackUri + (playbackUri to series)
                pendingRealtimeHeartRateSeriesBySourceUri =
                    pendingRealtimeHeartRateSeriesBySourceUri - sourceUri
            }
        }

        videoUri = playbackUri
        val shouldReuseSelectedHolds =
            entryMode == UploadEntryMode.Realtime &&
                numberedHolds.isNotEmpty() &&
                bestFrameBitmap != null
        if (shouldReuseSelectedHolds) {
            pendingPrimaryPrePoseGeneration = null
            holdPrecomputeRequestedGeneration = null
            UploadAiTraceLogger.log(
                event = "PRIMARY_VIDEO_PREPARED_REUSE_SELECTED_HOLDS",
                generation = generation,
                playbackUri = playbackUri,
                details = mapOf("numberedHoldCount" to numberedHolds.size)
            )
            invalidateSubmissionAnalysisPrewarm()
            refreshCurrentSelectionPrePoseTargets(generation)
            return
        }
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
        val request = buildCurrentSubmissionRequestOrNull(includePublishedAttempts = false) ?: return
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

    private fun buildCurrentSubmissionRequestOrNull(
        includePublishedAttempts: Boolean
    ): UploadSubmissionRequest? {
        val currentVideoUri = videoUri
        val attemptUris = if (includePublishedAttempts) {
            playbackAttemptUris.ifEmpty { allAttemptUris }
        } else {
            allAttemptUris
        }

        if (currentVideoUri == null && attemptUris.isEmpty()) {
            return null
        }

        val currentChallengeId = challengeId
        val useLocalAnalysisOnly = allowLocalAnalysisWithoutChallenge &&
            !isAttemptOnlyUploadMode &&
            (currentChallengeId == null || currentChallengeId <= 0L)
        val attemptAlignedHoldSetsByPlaybackUri =
            attemptAlignedHoldSets.associateBy(AttemptAlignedHoldSet::playbackUri) +
                alignedHoldSetsSnapshot()

        return UploadSubmissionRequest(
            selectionGeneration = selectionGeneration,
            challengeId = currentChallengeId,
            useLocalAnalysisOnly = useLocalAnalysisOnly,
            isAttemptOnlyUploadMode = isAttemptOnlyUploadMode,
            attemptUris = attemptUris,
            attemptAlignedHoldSets = attemptAlignedHoldSetsByPlaybackUri,
            detectedHolds = detectedHolds,
            numberedHolds = numberedHolds,
            bestFrameBitmap = bestFrameBitmap,
            aiMode = selectedAiAnalysisMode,
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
                    refreshAttemptHoldAlignmentTargets()
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

    private fun clearAttemptHoldAlignmentState(
        preservePlaybackUris: Set<String> = emptySet()
    ) {
        attemptHoldAlignmentDelegate.clearState(preservePlaybackUris = preservePlaybackUris)
    }

    private fun resetAllSelectionPreparationJobs() {
        sessionDelegate.resetAllSelectionPreparationJobs()
    }

    private fun cleanupUnusedManagedTempFiles(forceDeleteAll: Boolean = false) {
        sessionDelegate.cleanupUnusedManagedTempFiles(forceDeleteAll)
    }

    private fun delegateCreateChallengeFromSelection(
        onSuccess: (() -> Unit)? = null
    ) {
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
                onSuccess?.invoke()
            }
        }
    }

    fun findCandidatesNearTap(tapNormX: Float, tapNormY: Float) {
        holdDetectionDelegate.findCandidatesNearTap(tapNormX, tapNormY)
    }

    /**
     * 후보 홀드 팝업에서 선택을 확정합니다.
     * 추가/제거 목록을 실제 홀드 목록에 반영합니다.
     */
    fun applyHoldChanges(toAdd: List<Hold>, toRemove: List<Hold>) {
        holdDetectionDelegate.applyHoldChanges(toAdd, toRemove)
        clearHoldReachAnalysis()
        refreshAttemptHoldAlignmentTargets()
    }

    /** 후보 홀드 팝업을 닫습니다. */
    fun dismissCandidatePopup() {
        holdDetectionDelegate.dismissCandidatePopup()
    }

    /**
     * detectedHolds 에서 홀드를 제거합니다.
     */
    fun removeHold(hold: Hold) {
        holdDetectionDelegate.removeHold(hold)
        clearHoldReachAnalysis()
        refreshAttemptHoldAlignmentTargets()
    }

    /**
     * 분석 포인트 타임라인을 교체합니다.
     */
    fun updateAnalysisPoints(points: List<AnalysisPoint>) {
        analysisPoints = points
    }

    /**
     * ExoPlayer TextureView 캡처 프레임으로 현재 포즈를 추정합니다.
     * AttemptResultScreen 의 LaunchedEffect 루프에서 주기적으로 호출됩니다.
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

    // 기존 개별 추가 URI 메서드는 제거하고 updateAdditionalVideoUris 를 사용합니다.

    // ====== 비즈니스 로직 ======

    /**
     * PersonDetector 기반 대표 프레임 탐색과 HoldDetector 색상 필터링 준비를 보장합니다.
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
            refreshAttemptHoldAlignmentTargets()
            awaitAttemptHoldAlignmentTerminal(attemptAlignmentTargetUris())
            val request = buildCurrentSubmissionRequestOrNull(includePublishedAttempts = false)
            if (request == null) {
                Log.e(
                    TAG,
                    "$ATTEMPT_HOLD_ALIGNMENT_LOG_PREFIX attempt result request build failed: no upload targets"
                )
                submissionDelegate.setUploadSubmissionError("분석할 업로드 영상이 없습니다.")
                return@launch
            }

            submissionDelegate.submitUploadForAttemptResult(
                scope = viewModelScope,
                request = request,
                callbacks = submissionCallbacks
            )
            if (
                entryMode == UploadEntryMode.Realtime &&
                uploadSubmissionUiState.value is UploadSubmissionUiState.Success
            ) {
                restorePublishedAttemptResultSession()
            }
        }
    }

    fun ensureFinalAnalysisReady() {
        if (finalAnalysisPreparationUiState.value is FinalAnalysisPreparationUiState.Loading) {
            return
        }

        viewModelScope.launch {
            awaitActiveSelectionPreparation()
            refreshAttemptHoldAlignmentTargets()
            awaitAttemptHoldAlignmentTerminal(attemptAlignmentTargetUris())
            val request = buildCurrentSubmissionRequestOrNull(includePublishedAttempts = true)
            if (request == null) {
                Log.e(
                    TAG,
                    "$ATTEMPT_HOLD_ALIGNMENT_LOG_PREFIX final analysis request build failed: no upload targets"
                )
                submissionDelegate.setFinalAnalysisPreparationError("최종 분석에 필요한 영상이 없습니다.")
                return@launch
            }

            submissionDelegate.ensureFinalAnalysisReady(
                request = request,
                callbacks = submissionCallbacks
            )
        }
    }

    suspend fun closeChallengeForFinalAnalysis(
        challengeResult: String,
        averageCenterStabilityRatio: Double?,
        mostCruxHoldNo: Int?,
        maxCruxDurationMs: Int?,
        finalComment: String?
    ): Boolean {
        val currentChallengeId = challengeId ?: return false
        if (currentChallengeId <= 0L) {
            return false
        }
        if (closedChallengeId == currentChallengeId) {
            return true
        }
        if (closingChallengeId == currentChallengeId) {
            return false
        }

        closingChallengeId = currentChallengeId
        return try {
            closeChallengeUseCase(
                challengeId = currentChallengeId,
                challengeResult = challengeResult,
                averageCenterStabilityRatio = averageCenterStabilityRatio,
                mostCruxHoldNo = mostCruxHoldNo,
                maxCruxDurationMs = maxCruxDurationMs,
                finalComment = finalComment
            ).fold(
                onSuccess = { closedChallenge ->
                    closedChallengeId = currentChallengeId
                    createdChallenge = createdChallenge?.copy(
                        challengeStatus = closedChallenge.challengeStatus
                    )
                    realtimeAttemptActionState = RealtimeAttemptActionState.Idle
                    true
                },
                onFailure = { throwable ->
                    Log.e(TAG, "closeChallengeForFinalAnalysis: failed", throwable)
                    false
                }
            )
        } finally {
            if (closingChallengeId == currentChallengeId) {
                closingChallengeId = null
            }
        }
    }

    suspend fun abandonCurrentChallengeIfNeeded(): Boolean {
        val currentChallengeId = challengeId ?: return true
        if (currentChallengeId <= 0L) {
            return true
        }
        val abandonResult = if (submissionDelegate.finalizedAttemptCount() > 0) {
            "FAIL"
        } else {
            "UNKNOWN"
        }

        val closed = closeChallengeForFinalAnalysis(
            challengeResult = abandonResult,
            averageCenterStabilityRatio = null,
            mostCruxHoldNo = null,
            maxCruxDurationMs = null,
            finalComment = null
        )
        if (!closed) {
            return false
        }
        return true
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
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Gallery
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        realtimeGymSearchQuery = ""
        realtimeHoldColorSheetVisible = false
        _uiState.value = UploadUiState.Idle
        submissionDelegate.resetUploadSubmissionState()
        submissionDelegate.resetFinalAnalysisPreparationState()
    }

    private fun beginNewChallengeUploadFlowInternal() {
        invalidateSubmissionAnalysisPrewarm()
        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Gallery
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        realtimeGymSearchQuery = ""
        realtimeHoldColorSheetVisible = false
        uploadFlowMode = UploadFlowMode.FullChallenge
        allowLocalAnalysisWithoutChallenge = false
        clearHoldPrecomputeState()
        clearAttemptHoldAlignmentState()
        holdDetectionDelegate.resetHoldDetectionState(clearDebugSource = true)
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        additionalVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        additionalManagedVideos = emptyList()
        primaryManagedVideo = null
        pendingRealtimeHeartRateSeriesBySourceUri = emptyMap()
        realtimeHeartRateSeriesByPlaybackUri = emptyMap()
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
        resetFinalAnalysisPreparationState()
        cleanupUnusedManagedTempFiles(forceDeleteAll = true)
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
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        submissionDelegate.resetUploadSubmissionState()
        submissionDelegate.resetFinalAnalysisPreparationState()
    }

    fun prepareFinalAnalysisLoading() {
        analysisLoadingPhase = AnalysisLoadingPhase.FinalAnalysisPreparation
        realtimeAttemptActionState = RealtimeAttemptActionState.FinalAnalysisRequested
        realtimeHoldColorSheetVisible = false
        submissionDelegate.resetFinalAnalysisPreparationState()
    }

    fun prepareRealtimeRetake() {
        if (!isRealtimeEntryMode) {
            return
        }

        analysisLoadingPhase = AnalysisLoadingPhase.AttemptResultPreparation
        entryMode = UploadEntryMode.Realtime
        uploadFlowMode = UploadFlowMode.AttemptOnly
        realtimeHoldColorSheetVisible = false
        realtimeGymSearchQuery = gymName
        realtimeSetupStep = RealtimeSetupStep.Ready
        allowLocalAnalysisWithoutChallenge = false
        captureCurrentAttemptResultSession()
        clearHoldPrecomputeState()
        clearAttemptResultState(clearPublishedSession = false)
        realtimeAttemptActionState = RealtimeAttemptActionState.RetakeRequested
        submissionDelegate.resetFinalAnalysisPreparationState()
        sessionDelegate.resetAllSelectionPreparationJobs()
        clearPosePrecomputeState(preservePlaybackUris = publishedResultPlaybackUris())
        cleanupUnusedManagedTempFiles()
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
        clearAttemptHoldAlignmentState()
        clearSelectedHoldSelection()
        clearCreatedChallengeOnly()
        realtimeGymSearchQuery = ""
        realtimeHoldColorSheetVisible = false
    }

    private fun clearChallengeSelectionStatePreservingHoldPrecompute() {
        clearChallengeSelectionStatePreservingHoldPrecompute(
            preserveRealtimeMode = false,
            preserveSearchQuery = false
        )
    }

    private fun clearRealtimeChallengeSelectionStatePreservingHoldPrecompute() {
        clearChallengeSelectionStatePreservingHoldPrecompute(
            preserveRealtimeMode = true,
            preserveSearchQuery = true
        )
    }

    private fun clearChallengeSelectionStatePreservingHoldPrecompute(
        preserveRealtimeMode: Boolean,
        preserveSearchQuery: Boolean
    ) {
        holdDetectionEnsureJob?.cancel()
        holdDetectionEnsureJob = null
        challengeDelegate.clearSelectionState()
        holdDetectionDelegate.clearAppliedHoldStatePreservingSourceCache()
        clearCreatedChallengeOnly(
            preserveRealtimeMode = preserveRealtimeMode,
            preserveSearchQuery = preserveSearchQuery
        )
        if (!preserveSearchQuery) {
            realtimeGymSearchQuery = ""
        }
        realtimeHoldColorSheetVisible = false
        _uiState.value = UploadUiState.Idle
    }

    private fun clearCreatedChallengeOnly() {
        clearCreatedChallengeOnly(
            preserveRealtimeMode = false,
            preserveSearchQuery = false
        )
    }

    private fun clearRealtimeCreatedChallengeOnly() {
        clearCreatedChallengeOnly(
            preserveRealtimeMode = true,
            preserveSearchQuery = true
        )
    }

    private fun clearCreatedChallengeOnly(
        preserveRealtimeMode: Boolean,
        preserveSearchQuery: Boolean
    ) {
        challengeDelegate.clearCreatedChallengeState()
        closedChallengeId = null
        closingChallengeId = null
        savedChallengeHolds = null
        entryMode = if (preserveRealtimeMode) {
            UploadEntryMode.Realtime
        } else {
            UploadEntryMode.Gallery
        }
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
        if (!preserveSearchQuery) {
            realtimeGymSearchQuery = ""
        }
        realtimeHoldColorSheetVisible = false
        uploadedAttemptVideos = emptyList()
        clearHoldReachAnalysis()
        clearAiAnalysisState()
        resultPlaybackUris = emptyList()
        publishedAttemptResultSession = null
        clearAttemptHoldAlignmentState()
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
        realtimeAttemptActionState = RealtimeAttemptActionState.Idle
    }

    private fun publishAttemptResultSession(
        playbackUris: List<String>,
        uploadedVideos: List<UploadedAttemptVideo>,
        currentAttemptIndex: Int,
        attemptAlignedHoldSets: List<AttemptAlignedHoldSet>,
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
            attemptAlignedHoldSets = attemptAlignedHoldSets,
            holdReachResults = holdReachResults,
            attemptAiAnalysisResults = attemptAiAnalysisResults,
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
        refreshRealtimeAttemptActionStateForPublishedSession()
    }

    private fun refreshRealtimeAttemptActionStateForPublishedSession() {
        realtimeAttemptActionState =
            if (
                entryMode == UploadEntryMode.Realtime &&
                submissionCallbacks.publishedSession() != null
            ) {
                RealtimeAttemptActionState.ShowingOptions
            } else {
                RealtimeAttemptActionState.Idle
            }
    }

    private fun publishedResultPlaybackUris(): Set<String> =
        sessionDelegate.publishedResultPlaybackUris()

    private fun buildChallengeHoldCoordinates(): List<ChallengeHoldCoordinate> {
        val holdsForSave = numberedHolds
            .sortedBy { it.holdNo }
            .map { it.hold }

        return holdsForSave.mapIndexed { index, hold ->
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

    private fun attemptAlignmentTargetUris(): List<String> {
        return if (isAttemptOnlyUploadMode) {
            allAttemptUris.distinct()
        } else {
            additionalVideoUris.distinct()
        }
    }

    private fun refreshAttemptHoldAlignmentTargets() {
        val targetUris = attemptAlignmentTargetUris()
        if (targetUris.isEmpty()) {
            clearAttemptHoldAlignmentState()
            return
        }

        val referenceHolds = referenceHoldsForAttemptAlignment()
        val detectionTargetColor = resolveDetectionTargetHoldColor()
        Log.d(
            TAG,
            "$ATTEMPT_HOLD_ALIGNMENT_LOG_PREFIX refresh targets: " +
                "generation=$selectionGeneration, targetCount=${targetUris.size}, " +
                "referenceHoldCount=${referenceHolds.size}, color=${detectionTargetColor.ifBlank { "<all>" }}"
        )
        attemptHoldAlignmentDelegate.refreshTargets(
            selectionGeneration = selectionGeneration,
            referenceVideoUri = videoUri,
            referenceFrameWidthPx = bestFrameBitmap?.width,
            referenceFrameHeightPx = bestFrameBitmap?.height,
            playbackUris = targetUris,
            referenceHolds = referenceHolds,
            detectionTargetColor = detectionTargetColor
        )
    }

    private fun alignedHoldSetsSnapshot(): Map<String, AttemptAlignedHoldSet> {
        return attemptAlignmentTargetUris()
            .mapNotNull(attemptHoldAlignmentDelegate::alignedHoldSetFor)
            .associateBy(AttemptAlignedHoldSet::playbackUri)
    }

    private fun referenceHoldsForAttemptAlignment(): List<HoldNumbered> {
        if (numberedHolds.isNotEmpty()) {
            return numberedHolds
        }

        val saved = savedChallengeHolds?.holds.orEmpty()
        if (saved.isEmpty()) {
            return emptyList()
        }

        val maxHoldNo = saved.maxOf { hold -> hold.holdNo }
        return saved
            .sortedBy { hold -> hold.holdNo }
            .map { hold ->
                HoldNumbered(
                    hold = Hold(
                        holdNo = hold.holdNo,
                        boundingBox = Hold.BoundingBox(
                            left = hold.boundingBox.x1,
                            top = hold.boundingBox.y1,
                            right = hold.boundingBox.x2,
                            bottom = hold.boundingBox.y2
                        ),
                        confidence = 1f,
                        polygon = hold.polygon.map { point ->
                            Hold.Point(x = point.x, y = point.y)
                        },
                        colorLabel = resolveDetectionTargetHoldColor(),
                        colorScore = 1f
                    ),
                    progress = (hold.holdNo - 1).toFloat(),
                    axisDistance = 0f,
                    role = when (hold.holdNo) {
                        1 -> HoldRole.START
                        maxHoldNo -> HoldRole.END
                        else -> HoldRole.NORMAL
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


