package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.util.ArrayDeque
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.data.mapper.toPoseSequenceDto
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.HoldBoundingBox
import com.ddgo.app.domain.model.HoldPoint
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.Hold
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
import com.ddgo.app.domain.repository.PoseEstimator
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.analyzePolygonHoldContacts
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.assignHoldNumbers
import com.ddgo.app.domain.usecase.summarizeHoldReachResults
import com.ddgo.app.domain.usecase.toAttemptHoldReachResult
import com.ddgo.app.domain.usecase.toHolds
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import javax.inject.Inject
import java.time.LocalDateTime
import kotlin.math.sqrt

/**
 * flow
 * AttemptUploadScreen      : 영상 업로드
 * ChallengeCreateScreen    : 클라이밍장 이름 찾기 -> 난이도 -> 홀드색
 *  - in : 클라이밍장 이름(id), 난이도 레벨, 홀드 컬러
 * ChallengeHoldScreen      : 인식된 홀드 선택
 *  - in : 홀드 위치? 홀드 범위? 홀드 정보
 *
 * AttemptUploadScreen      : 챌린지에 대한 또 다른 영상 업로드
 *  - in : 추가 영상
 * AttemptResultScreen      : 모든 업로드에 대한 분석 영상
 *  - out : 영상에 대한 것들 결과들 보기
 */

private const val TAG = "UploadViewModel"
private const val HOLD_CONTACT_ANALYSIS_TAG = "HoldContactAnalysis"
private const val HOLD_CONTACT_LOG_PREFIX = "[DDGO_HOLD_CONTACT]"

private data class AttemptPoseAnalysis(
    val holdReachResult: AttemptHoldReachResult,
    val poseSequenceDto: PoseSequenceDto,
    val poses: List<Pose>,
    val polygonHoldContactDebugResult: PolygonHoldContactDebugResult
)

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personDetector: PersonDetector,
    private val holdDetector: HoldDetector,
    private val poseEstimator: PoseEstimator,
    private val holdColorClassifier: HoldColorClassifier,
    private val searchNearbyClimbingGymsUseCase: SearchNearbyClimbingGymsUseCase,
    private val resolveGymUseCase: ResolveGymUseCase,
    private val createChallengeUseCase: CreateChallengeUseCase,
    private val saveChallengeHoldsUseCase: SaveChallengeHoldsUseCase,
    private val uploadAttemptVideoUseCase: UploadAttemptVideoUseCase,
    private val endAttemptUseCase: EndAttemptUseCase
) : ViewModel() {

    // UI 레이어에 노출할 상태 (로딩, 성공, 실패 등)
    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // --- 1. AttemptUploadScreen (초기 영상 업로드) ---
    var videoUri by mutableStateOf<String?>(null)
        private set
        
    var additionalVideoUris by mutableStateOf<List<String>>(emptyList())
        private set

    var attemptOnlyVideoUris by mutableStateOf<List<String>>(emptyList())
        private set

    private var uploadFlowMode by mutableStateOf(UploadFlowMode.FullChallenge)
    private var allowLocalAnalysisWithoutChallenge by mutableStateOf(false)

    // 전체 시도 영상 (초기 + 추가 영상 리스트)
    val allAttemptUris: List<String>
        get() = when (uploadFlowMode) {
            UploadFlowMode.FullChallenge -> listOfNotNull(videoUri) + additionalVideoUris
            UploadFlowMode.AttemptOnly -> attemptOnlyVideoUris
        }

    /**
     * Result screen playback should stay on the original local files used for upload.
     * This avoids switching playback to any backend/object-storage URL after upload succeeds.
     */
    var resultPlaybackUris by mutableStateOf<List<String>>(emptyList())
        private set

    val playbackAttemptUris: List<String>
        get() = resultPlaybackUris.ifEmpty { allAttemptUris }

    val isAttemptOnlyUploadMode: Boolean
        get() = uploadFlowMode == UploadFlowMode.AttemptOnly

    val canBypassChallengeCreationForDev: Boolean
        get() = allowLocalAnalysisWithoutChallenge

    // 썸네일 / 메타데이터 (PersonDetector 방식으로 추출 → 다음 화면에서 활용)
    var thumbnail by mutableStateOf<Bitmap?>(null)
        private set
    var videoFileName by mutableStateOf<String?>(null)
        private set
    var videoDuration by mutableStateOf<String?>(null)
        private set

    // --- 2. ChallengeCreateScreen (클라이밍장, 난이도, 홀드색) ---
    var gymId by mutableStateOf<Int?>(null)
        private set
    var gymName by mutableStateOf("")
        private set
    var difficultyLevel by mutableStateOf("")
        private set
    var holdColor by mutableStateOf("")
        private set
    var selectedHoldColorKey by mutableStateOf<String?>(null)
        private set
    var selectedLevelSortOrder by mutableStateOf<Int?>(null)
        private set
    var selectedGymGradeId by mutableStateOf<Long?>(null)
        private set
    var selectedGymGrade by mutableStateOf<GymGrade?>(null)
        private set
    var createdChallenge by mutableStateOf<ChallengeSession?>(null)
        private set
    var challengeId by mutableStateOf<Long?>(null)
        private set
    var savedChallengeHolds by mutableStateOf<SavedChallengeHolds?>(null)
        private set
    var uploadedAttemptVideos by mutableStateOf<List<UploadedAttemptVideo>>(emptyList())
        private set

    private val _challengeCreationUiState =
        MutableStateFlow<ChallengeCreationUiState>(ChallengeCreationUiState.Idle)
    val challengeCreationUiState = _challengeCreationUiState.asStateFlow()

    private val _uploadSubmissionUiState =
        MutableStateFlow<UploadSubmissionUiState>(UploadSubmissionUiState.Idle)
    val uploadSubmissionUiState = _uploadSubmissionUiState.asStateFlow()

    var selectionGeneration by mutableStateOf(0L)
        private set

    var prePoseBatchState by mutableStateOf(PrePoseBatchState())
        private set

    private var primaryManagedVideo by mutableStateOf<ManagedAttemptVideo?>(null)
    private var additionalManagedVideos by mutableStateOf<List<ManagedAttemptVideo>>(emptyList())
    private var attemptOnlyManagedVideos by mutableStateOf<List<ManagedAttemptVideo>>(emptyList())

    private var primarySelectionJob: Job? = null
    private var additionalSelectionJob: Job? = null
    private var attemptOnlySelectionJob: Job? = null

    private var prePoseCacheEntries by mutableStateOf<Map<String, PrePoseCacheEntry>>(emptyMap())
    private val prePoseTaskQueue = ArrayDeque<PrePoseTask>()
    private var prePoseWorkerJob: Job? = null
    private var nextPrePoseTaskId = 0L
    private val managedTempFilePaths = mutableSetOf<String>()
    private val managedVideosByPlaybackUri = mutableMapOf<String, ManagedAttemptVideo>()
    private val activePrePosePlaybackUris = mutableSetOf<String>()
    private var publishedAttemptResultSession: PublishedAttemptResultSession? = null

    /**
     * 주변 암장 검색 UI 상태.
     *
     * 역할:
     * - 검색 전/로딩/성공/실패 상태를 화면에 전달합니다.
     */
    private val _gymSearchUiState = MutableStateFlow<GymSearchUiState>(GymSearchUiState.Idle)
    val gymSearchUiState = _gymSearchUiState.asStateFlow()

    /**
     * 선택한 장소의 gym resolve UI 상태.
     *
     * 역할:
     * - 사용자가 장소를 선택한 뒤 서버 resolve 진행 상태를 화면에 전달합니다.
     */
    private val _gymResolveUiState = MutableStateFlow<GymResolveUiState>(GymResolveUiState.Idle)
    val gymResolveUiState = _gymResolveUiState.asStateFlow()

    /**
     * Kakao Local API에서 가져온 주변 장소 목록.
     */
    var nearbyPlaces by mutableStateOf<List<NearbyPlace>>(emptyList())
        private set

    /**
     * 사용자가 선택한 장소.
     */
    var selectedNearbyPlace by mutableStateOf<NearbyPlace?>(null)
        private set

    /**
     * 서버 resolve 결과.
     */
    var resolvedGym by mutableStateOf<ResolvedGym?>(null)
        private set

    /**
     * resolve 결과로 내려온 gym grade 목록.
     *
     * 다음 단계에서 gymGradeId 기반 선택 UI로 바꿀 때 사용합니다.
     */
    var resolvedGymGrades by mutableStateOf<List<GymGrade>>(emptyList())
        private set

    /**
     * 마지막 검색 위치.
     */
    var lastSearchLatitude by mutableStateOf<Double?>(null)
        private set

    var lastSearchLongitude by mutableStateOf<Double?>(null)
        private set

    // --- 3. ChallengeHoldScreen (홀드 탐지 결과) ---
    /** PersonDetector가 선택한 최적 프레임 (홀드 탐지에 사용된 실제 이미지) */
    var bestFrameBitmap by mutableStateOf<Bitmap?>(null)
        private set

    /** YOLO가 탐지한 전체 홀드 (색상 필터링 전). 수동 추가 후보 풀로 사용 */
    var allRawHolds by mutableStateOf<List<Hold>>(emptyList())
        private set

    /** 색상 필터링 + 수동 추가된 홀드 목록 (화면에 표시) */
    var detectedHolds by mutableStateOf<List<Hold>>(emptyList())
        private set

    /** 수동 추가 팝업에 표시할 후보 홀드 목록 */
    var candidateHolds by mutableStateOf<List<Hold>>(emptyList())
        private set

    /** 수동 추가 팝업 표시 여부 */
    var showCandidatePopup by mutableStateOf(false)
        private set

    /** 사용자가 선택한 시작 홀드 */
    var selectedStartHold by mutableStateOf<Hold?>(null)
        private set

    /** 사용자가 선택한 끝 홀드 */
    var selectedEndHold by mutableStateOf<Hold?>(null)
        private set

    /** 시작/끝 홀드 기준으로 번호가 부여된 홀드 목록 */
    var numberedHolds by mutableStateOf<List<HoldNumbered>>(emptyList())
        private set

    /** 시도별 최고 도달 홀드 분석 결과 */
    var attemptHoldReachResults by mutableStateOf<List<AttemptHoldReachResult>>(emptyList())
        private set

    /** 시도별 MediaPipe Pose DTO */
    var attemptPoseDtos by mutableStateOf<List<PoseSequenceDto>>(emptyList())
        private set

    /** 시도별 분석용 MediaPipe Pose 프레임 */
    var attemptAnalyzedPoses by mutableStateOf<List<List<Pose>>>(emptyList())
        private set

    /** 시도별 폴리곤 홀드 접촉 디버그 결과 */
    var attemptPolygonHoldContactDebugResults by mutableStateOf<List<PolygonHoldContactDebugResult>>(emptyList())
        private set

    /** 시도별 pre-pose 시퀀스 캐시 */
    val attemptPoseSequences: List<List<Pose>>
        get() = playbackAttemptUris.map { playbackUri ->
            prePoseCacheEntries[playbackUri]
                ?.takeIf { it.status == PrePoseStatus.Ready }
                ?.poses
                .orEmpty()
        }

    /** 여러 시도의 평균 도달 홀드 요약 */
    var overallHoldReachSummary by mutableStateOf<OverallHoldReachSummary?>(null)
        private set

    // --- 4. AttemptResultScreen (포즈 오버레이 + 분석 타임라인) ---
    
    // N차 시도를 추적하는 인덱스 (0이 1차 시도)
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
    }

    /** 현재 재생 프레임의 MediaPipe 33 랜드마크 (실시간 업데이트) */
    var currentPoseLandmarks by mutableStateOf<List<PoseLandmark>>(emptyList())
        private set

    /** 현재 선택된 시도의 pre-pose 시퀀스 */
    val currentAttemptPoseSequence: List<Pose>
        get() = currentAttemptPrePoseEntry
            ?.takeIf { it.status == PrePoseStatus.Ready }
            ?.poses
            .orEmpty()

    val currentAttemptPrePoseEntry: PrePoseCacheEntry?
        get() = playbackAttemptUris
            .getOrNull(currentAttemptIndex)
            ?.let(prePoseCacheEntries::get)

    /** 현재 선택된 시도의 최고 도달 홀드 결과 */
    val currentAttemptHoldReachResult: AttemptHoldReachResult?
        get() = attemptHoldReachResults.getOrNull(currentAttemptIndex)

    /** 현재 선택된 시도의 MediaPipe Pose DTO */
    val currentAttemptPoseDto: PoseSequenceDto?
        get() = attemptPoseDtos.getOrNull(currentAttemptIndex)

    /** 현재 선택된 시도의 분석용 MediaPipe Pose 프레임 */
    val currentAttemptAnalyzedPoses: List<Pose>
        get() = attemptAnalyzedPoses.getOrNull(currentAttemptIndex).orEmpty()

    /** 현재 선택된 시도의 폴리곤 홀드 접촉 디버그 결과 */
    val currentAttemptPolygonHoldContactDebugResult: PolygonHoldContactDebugResult?
        get() = attemptPolygonHoldContactDebugResults.getOrNull(currentAttemptIndex)

    /** 최종 분석 화면에 표시할 평균 도달 홀드 번호(반올림) */
    val averageReachedHoldNo: Int
        get() = overallHoldReachSummary?.roundedAverageHighestReachedHoldNo ?: 0

    /** 최종 분석 화면 분모로 사용할 전체 선택 홀드 수 */
    val totalSelectedHoldCount: Int
        get() = overallHoldReachSummary?.totalHoldCount ?: numberedHolds.size

    /**
     * 분석 피드백 포인트 목록.
     * MVP에서는 플레이스홀더 데이터를 사용하며, 서버 연동 시 updateAnalysisPoints()로 교체합니다.
     */
    var analysisPoints by mutableStateOf<List<AnalysisPoint>>(
        listOf(
            AnalysisPoint(1, 21_000L, "2지점 상태가 길었어요"),
            AnalysisPoint(2, 48_000L, "오른쪽 팔에 과도한\n무게가 실렸어요"),
            AnalysisPoint(3, 66_000L, "무게 이동이 늦어졌어요")
        )
    )
        private set
        
    // (임시) N차 시도별로 다른 결과를 보여주기 위한 더미 데이터 모델 확장
    // 백엔드 연동 시 AnalysisResult 등을 리스트 형태로 관리
    val attemptDummyResults = listOf(
        Pair(false, listOf(
            AnalysisPoint(1, 21_000L, "2지점 상태가 길었어요"),
            AnalysisPoint(2, 48_000L, "오른쪽 팔에 과도한\n무게가 실렸어요"),
            AnalysisPoint(3, 66_000L, "무게 이동이 늦어졌어요")
        )),
        Pair(true, listOf( // 성공 케이스
            AnalysisPoint(1, 15_000L, "안정적인 스타트 구간입니다"),
            AnalysisPoint(2, 35_000L, "크럭스 지점을 잘 통과했어요"),
            AnalysisPoint(3, 50_000L, "완등 지점")
        ))
    )

    // --- 5. AttemptUploadScreen (추가 영상 업로드) ---
    fun updateAdditionalVideoUris(uris: List<String>) {
        val generation = beginSelectionUpdate(preservePublishedResult = isAttemptOnlyUploadMode)

        if (isAttemptOnlyUploadMode) {
            attemptOnlySelectionJob?.cancel()
            if (uris.isEmpty()) {
                attemptOnlyManagedVideos = emptyList()
                attemptOnlyVideoUris = emptyList()
                refreshCurrentSelectionPrePoseTargets(generation)
                cleanupUnusedManagedTempFiles()
                return
            }

            attemptOnlySelectionJob = viewModelScope.launch(Dispatchers.IO) {
                val preparedVideos = prepareManagedVideos(
                    uris = uris,
                    filePrefix = "attempt_only"
                )

                withContext(Dispatchers.Main) {
                    if (generation != selectionGeneration) {
                        deleteManagedVideos(preparedVideos)
                        return@withContext
                    }

                    registerManagedVideos(preparedVideos)
                    attemptOnlyManagedVideos = preparedVideos
                    attemptOnlyVideoUris = preparedVideos.map { it.playbackUri }
                    refreshCurrentSelectionPrePoseTargets(generation)
                    cleanupUnusedManagedTempFiles()
                }
            }
        } else {
            additionalSelectionJob?.cancel()
            if (uris.isEmpty()) {
                additionalManagedVideos = emptyList()
                additionalVideoUris = emptyList()
                refreshCurrentSelectionPrePoseTargets(generation)
                cleanupUnusedManagedTempFiles()
                return
            }

            additionalSelectionJob = viewModelScope.launch(Dispatchers.IO) {
                val preparedVideos = prepareManagedVideos(
                    uris = uris,
                    filePrefix = "attempt"
                )

                withContext(Dispatchers.Main) {
                    if (generation != selectionGeneration) {
                        deleteManagedVideos(preparedVideos)
                        return@withContext
                    }

                    registerManagedVideos(preparedVideos)
                    additionalManagedVideos = preparedVideos
                    additionalVideoUris = preparedVideos.map { it.playbackUri }
                    refreshCurrentSelectionPrePoseTargets(generation)
                    cleanupUnusedManagedTempFiles()
                }
            }
        }
    }

    /**
     * 새 챌린지 생성 흐름으로 진입합니다.
     *
     * 역할:
     * - 기존 추가 시도 업로드 모드가 켜져 있었다면 기본 업로드 모드로 되돌립니다.
     * - 새 업로드 배치를 시작할 때 이전 attempt-only 선택값을 비웁니다.
     */
    fun beginNewChallengeUploadFlow() {
        uploadFlowMode = UploadFlowMode.FullChallenge
        allowLocalAnalysisWithoutChallenge = false
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        additionalVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        additionalManagedVideos = emptyList()
        primaryManagedVideo = null
        videoUri = null
        resultPlaybackUris = emptyList()
        nearbyPlaces = emptyList()
        selectedNearbyPlace = null
        resolvedGym = null
        resolvedGymGrades = emptyList()
        gymId = null
        gymName = ""
        lastSearchLatitude = null
        lastSearchLongitude = null
        uploadedAttemptVideos = emptyList()
        currentAttemptIndex = 0
        publishedAttemptResultSession = null
        clearChallengeFlowState()
        clearHoldReachAnalysis()
        clearPosePrecomputeState()
        _gymSearchUiState.value = GymSearchUiState.Idle
        _gymResolveUiState.value = GymResolveUiState.Idle
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
        cleanupUnusedManagedTempFiles(forceDeleteAll = true)
    }

    /**
     * 이미 생성된 challenge에 추가 시도만 업로드하는 모드로 전환합니다.
     *
     * 규칙:
     * - challenge가 이미 있어야 합니다.
     * - 기존 홀드/챌린지 정보는 유지하고, 추가 시도 영상 선택 상태만 초기화합니다.
     */
    fun enterAttemptOnlyUploadMode(): Boolean {
        val currentChallengeId = challengeId
        if (currentChallengeId == null || currentChallengeId <= 0L) {
            return false
        }

        captureCurrentAttemptResultSession()
        uploadFlowMode = UploadFlowMode.AttemptOnly
        allowLocalAnalysisWithoutChallenge = false
        resetAllSelectionPreparationJobs()
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
        refreshCurrentSelectionPrePoseTargets()
        cleanupUnusedManagedTempFiles()
        return true
    }

    /**
     * 기존 challenge 상세 화면에서 추가 시도 업로드 플로우를 시작할 때 사용할 진입점입니다.
     *
     * 역할:
     * - 서버에 이미 생성된 challenge 정보를 현재 업로드 ViewModel에 주입합니다.
     * - 이후 바로 추가 시도 업로드 모드로 진입할 수 있는 상태를 만듭니다.
     */
    fun prepareExistingChallengeAttemptUpload(challenge: ChallengeSession) {
        createdChallenge = challenge
        challengeId = challenge.challengeId
        gymId = challenge.gymId.toInt()
        gymName = challenge.gymName
        selectedGymGradeId = challenge.gymGradeId
        difficultyLevel = challenge.gradeLabel ?: challenge.problemColor
        selectedHoldColorKey = resolveHoldColorKey(
            colorName = challenge.problemColor,
            colorHex = null
        )
        holdColor = resolveHoldColorDisplayName(
            colorName = challenge.problemColor,
            colorHex = null
        )
        uploadFlowMode = UploadFlowMode.AttemptOnly
        allowLocalAnalysisWithoutChallenge = false
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
        clearPosePrecomputeState()
        _challengeCreationUiState.value = ChallengeCreationUiState.Success(challenge)
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
        cleanupUnusedManagedTempFiles()
    }

    /**
     * 추가 시도 업로드 모드를 취소하고 원래 챌린지 흐름 모드로 돌아갑니다.
     */
    fun cancelAttemptOnlyUploadMode() {
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
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
        cleanupUnusedManagedTempFiles()
    }

    fun setLocalAnalysisWithoutChallengeEnabled(enabled: Boolean) {
        allowLocalAnalysisWithoutChallenge = enabled
        if (enabled) {
            uploadFlowMode = UploadFlowMode.FullChallenge
            _challengeCreationUiState.value = ChallengeCreationUiState.Idle
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
        }
    }

    // ====== 상태 업데이트 메서드 (이벤트 핸들러) ======

    /**
     * 영상 URI를 저장하고, 백그라운드에서 썸네일·메타데이터를 추출합니다.
     *
     * ⚠️ Photo Picker URI 대응:
     *   Android 13+ PickVisualMedia가 반환하는 content://media/picker/0/... URI는
     *   FFmpegMediaMetadataRetriever.getFrameAtTime()이 FileDescriptor 방식으로 열면
     *   null을 반환하는 알려진 문제가 있습니다.
     *   → 선택 직후 앱 캐시 디렉토리에 복사 → file:// URI로 변환 후 파이프라인 진행.
     *
     * 썸네일 추출 전략: PersonDetectorImpl과 동일한 방식
     *   1. MediaExtractor.advance()로 컨테이너를 순서대로 순회 → 첫 번째 실제 PTS 수집
     *   2. 수집한 PTS를 FFmpegMediaMetadataRetriever.OPTION_CLOSEST 에 전달
     */
    fun updateVideoUri(uri: String) {
        val generation = beginSelectionUpdate()
        uploadFlowMode = UploadFlowMode.FullChallenge
        attemptOnlyVideoUris = emptyList()
        attemptOnlyManagedVideos = emptyList()
        additionalManagedVideos = emptyList()
        additionalVideoUris = emptyList()

        primarySelectionJob?.cancel()
        primarySelectionJob = viewModelScope.launch(Dispatchers.IO) {
            val managedVideo = normalizeToManagedVideo(
                uri = Uri.parse(uri),
                filePrefix = "primary"
            )

            withContext(Dispatchers.Main) {
                if (generation != selectionGeneration) {
                    deleteManagedVideo(managedVideo)
                    return@withContext
                }

                registerManagedVideo(managedVideo)
                primaryManagedVideo = managedVideo
                videoUri = managedVideo.playbackUri
                refreshCurrentSelectionPrePoseTargets(generation)
                cleanupUnusedManagedTempFiles()
            }

            if (generation == selectionGeneration) {
                extractVideoMetadata(Uri.parse(managedVideo.playbackUri))
            }
        }
    }

    /** 선택한 URI를 분석/재생용 managed video로 정규화합니다. */
    private suspend fun prepareManagedVideos(
        uris: List<String>,
        filePrefix: String
    ): List<ManagedAttemptVideo> {
        return uris.mapIndexed { index, uriString ->
            normalizeToManagedVideo(
                uri = Uri.parse(uriString),
                filePrefix = "${filePrefix}_${index + 1}"
            )
        }
    }

    private fun normalizeToManagedVideo(
        uri: Uri,
        filePrefix: String
    ): ManagedAttemptVideo {
        if (uri.scheme == "file") {
            return ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = uri.toString(),
                tempFilePath = null
            )
        }

        return try {
            val displayName = resolveDisplayName(uri)
            val extension = displayName.substringAfterLast('.', "mp4")
                .takeIf { it.isNotBlank() }
                ?.let { ".${it.lowercase()}" }
                ?: ".mp4"
            val tempFile = File(
                context.cacheDir,
                "${filePrefix}_${UUID.randomUUID()}$extension"
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            managedTempFilePaths += tempFile.absolutePath
            Log.d(TAG, "영상 캐시 복사 완료: ${tempFile.absolutePath}")

            ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = Uri.fromFile(tempFile).toString(),
                tempFilePath = tempFile.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "캐시 복사 실패, 원본 URI 사용: ${e.message}")
            ManagedAttemptVideo(
                sourceUri = uri.toString(),
                playbackUri = uri.toString(),
                tempFilePath = null
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "selected_video"
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else fallback
        } ?: fallback
    }

    private fun deleteManagedVideo(video: ManagedAttemptVideo?) {
        deleteManagedVideos(listOfNotNull(video))
    }

    private fun deleteManagedVideos(videos: List<ManagedAttemptVideo>) {
        videos.mapNotNull { it.tempFilePath }.forEach(::deleteManagedTempFile)
    }

    private fun deleteManagedTempFile(path: String) {
        managedTempFilePaths.remove(path)
        managedVideosByPlaybackUri.entries.removeAll { (_, video) ->
            video.tempFilePath == path
        }
        runCatching {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }.onFailure { error ->
            Log.w(TAG, "임시 영상 삭제 실패: $path", error)
        }
    }

    private fun registerManagedVideo(video: ManagedAttemptVideo) {
        if (video.tempFilePath != null) {
            managedVideosByPlaybackUri[video.playbackUri] = video
        }
    }

    private fun registerManagedVideos(videos: List<ManagedAttemptVideo>) {
        videos.forEach(::registerManagedVideo)
    }

    private fun beginSelectionUpdate(
        preservePublishedResult: Boolean = false
    ): Long {
        selectionGeneration += 1
        if (preservePublishedResult) {
            currentAttemptIndex = publishedAttemptResultSession?.currentAttemptIndex ?: currentAttemptIndex
        } else {
            clearAttemptResultState(clearPublishedSession = true)
        }
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
        updatePrePoseBatchState()
        return selectionGeneration
    }

    private fun refreshCurrentSelectionPrePoseTargets(generation: Long = selectionGeneration) {
        val currentUris = allAttemptUris.distinct()
        val currentUriSet = currentUris.toSet()
        val updatedEntries = prePoseCacheEntries.toMutableMap()

        prePoseTaskQueue.removeAll { task -> task.playbackUri !in currentUriSet }

        currentUris.forEach { playbackUri ->
            val existingEntry = updatedEntries[playbackUri]
            if (existingEntry == null) {
                val taskId = nextPrePoseTaskId()
                updatedEntries[playbackUri] = PrePoseCacheEntry(
                    playbackUri = playbackUri,
                    selectionGeneration = generation,
                    status = PrePoseStatus.Pending,
                    taskId = taskId
                )
                prePoseTaskQueue.addLast(
                    PrePoseTask(
                        playbackUri = playbackUri,
                        taskId = taskId
                    )
                )
                return@forEach
            }

            updatedEntries[playbackUri] = existingEntry.copy(
                selectionGeneration = generation
            )

            if (
                existingEntry.status == PrePoseStatus.Pending &&
                existingEntry.taskId != null &&
                prePoseTaskQueue.none { queued ->
                    queued.playbackUri == playbackUri && queued.taskId == existingEntry.taskId
                }
            ) {
                prePoseTaskQueue.addLast(
                    PrePoseTask(
                        playbackUri = playbackUri,
                        taskId = existingEntry.taskId
                    )
                )
            }
        }

        val keepUris = currentUriSet + resultPlaybackUris.toSet()
        prePoseCacheEntries = updatedEntries.filter { (playbackUri, entry) ->
            playbackUri in keepUris || entry.status == PrePoseStatus.Running
        }

        updatePrePoseBatchState()
        ensurePrePoseWorkerRunning()
    }

    private fun ensurePrePoseWorkerRunning() {
        if (prePoseWorkerJob?.isActive == true) return

        prePoseWorkerJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val task = if (prePoseTaskQueue.isEmpty()) {
                    null
                } else {
                    prePoseTaskQueue.removeFirst()
                } ?: break
                val currentEntry = prePoseCacheEntries[task.playbackUri] ?: continue

                if (
                    currentEntry.status != PrePoseStatus.Pending ||
                    currentEntry.taskId != task.taskId
                ) {
                    continue
                }

                prePoseCacheEntries = prePoseCacheEntries.toMutableMap().apply {
                    put(
                        task.playbackUri,
                        currentEntry.copy(status = PrePoseStatus.Running)
                    )
                }
                activePrePosePlaybackUris += task.playbackUri
                updatePrePoseBatchState()

                val result = runCatching {
                    poseEstimator.estimateFromVideo(task.playbackUri)
                }

                val latestEntry = prePoseCacheEntries[task.playbackUri]
                if (latestEntry?.taskId != task.taskId) {
                    activePrePosePlaybackUris -= task.playbackUri
                    continue
                }

                prePoseCacheEntries = prePoseCacheEntries.toMutableMap().apply {
                    put(
                        task.playbackUri,
                        if (result.isSuccess) {
                            latestEntry.copy(
                                status = PrePoseStatus.Ready,
                                poses = result.getOrDefault(emptyList()),
                                errorMessage = null,
                                taskId = null
                            )
                        } else {
                            latestEntry.copy(
                                status = PrePoseStatus.Failed,
                                poses = emptyList(),
                                errorMessage = result.exceptionOrNull()?.message,
                                taskId = null
                            )
                        }
                    )
                }
                activePrePosePlaybackUris -= task.playbackUri
                updatePrePoseBatchState()
                cleanupUnusedManagedTempFiles()
            }
        }
    }

    private suspend fun awaitActiveSelectionPreparation() {
        listOfNotNull(
            primarySelectionJob,
            additionalSelectionJob,
            attemptOnlySelectionJob
        ).forEach { job ->
            job.join()
        }
    }

    private suspend fun awaitPrePoseTerminal(playbackUris: List<String>): TerminalPrePoseSnapshot {
        if (playbackUris.isEmpty()) {
            return TerminalPrePoseSnapshot(
                generation = selectionGeneration,
                entriesByPlaybackUri = emptyMap()
            )
        }

        refreshCurrentSelectionPrePoseTargets(selectionGeneration)

        while (true) {
            val entries = playbackUris.mapNotNull { prePoseCacheEntries[it] }
            val activeCount = entries.count { entry ->
                entry.status == PrePoseStatus.Pending || entry.status == PrePoseStatus.Running
            }
            if (entries.size == playbackUris.size && activeCount == 0) {
                updatePrePoseBatchState()
                return TerminalPrePoseSnapshot(
                    generation = selectionGeneration,
                    entriesByPlaybackUri = playbackUris.associateWith { playbackUri ->
                        prePoseCacheEntries[playbackUri]?.toTerminalEntry()
                            ?: TerminalPrePoseEntry(
                                playbackUri = playbackUri,
                                selectionGeneration = selectionGeneration,
                                status = PrePoseStatus.Failed,
                                poses = emptyList(),
                                errorMessage = "Missing pre-pose cache entry."
                            )
                    }
                )
            }

            val completedCount = entries.count { entry ->
                entry.status == PrePoseStatus.Ready || entry.status == PrePoseStatus.Failed
            }
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                "pre-pose 준비 중입니다. (${completedCount}/${playbackUris.size})"
            )
            delay(100L)
        }
    }

    private fun updatePrePoseBatchState() {
        val currentUris = allAttemptUris.distinct()
        if (currentUris.isEmpty()) {
            prePoseBatchState = PrePoseBatchState()
            return
        }

        val entries = currentUris.mapNotNull { prePoseCacheEntries[it] }
        prePoseBatchState = PrePoseBatchState(
            generation = selectionGeneration,
            totalCount = currentUris.size,
            pendingCount = entries.count { it.status == PrePoseStatus.Pending },
            runningCount = entries.count { it.status == PrePoseStatus.Running },
            readyCount = entries.count { it.status == PrePoseStatus.Ready },
            failedCount = entries.count { it.status == PrePoseStatus.Failed }
        )
    }

    private fun clearPosePrecomputeState(
        preservePlaybackUris: Set<String> = emptySet()
    ) {
        selectionGeneration += 1
        if (preservePlaybackUris.isEmpty()) {
            prePoseTaskQueue.clear()
            prePoseCacheEntries = emptyMap()
            prePoseBatchState = PrePoseBatchState()
            return
        }

        prePoseTaskQueue.removeAll { task -> task.playbackUri !in preservePlaybackUris }
        prePoseCacheEntries = prePoseCacheEntries.filterKeys { playbackUri ->
            playbackUri in preservePlaybackUris
        }
        updatePrePoseBatchState()
    }

    private fun resetAllSelectionPreparationJobs() {
        primarySelectionJob?.cancel()
        additionalSelectionJob?.cancel()
        attemptOnlySelectionJob?.cancel()
        primarySelectionJob = null
        additionalSelectionJob = null
        attemptOnlySelectionJob = null
    }

    private fun cleanupUnusedManagedTempFiles(forceDeleteAll: Boolean = false) {
        val referencedTempPaths = buildSet {
            if (!forceDeleteAll) {
                listOfNotNull(primaryManagedVideo)
                    .plus(additionalManagedVideos)
                    .plus(attemptOnlyManagedVideos)
                    .mapNotNullTo(this) { it.tempFilePath }

                resultPlaybackUris.mapNotNullTo(this) { playbackUri ->
                    managedVideosByPlaybackUri[playbackUri]?.tempFilePath
                }
                publishedAttemptResultSession
                    ?.resultPlaybackUris
                    ?.mapNotNullTo(this) { playbackUri ->
                        managedVideosByPlaybackUri[playbackUri]?.tempFilePath
                    }
            }

            activePrePosePlaybackUris.mapNotNullTo(this) { playbackUri ->
                managedVideosByPlaybackUri[playbackUri]?.tempFilePath
            }
        }

        managedTempFilePaths
            .toList()
            .filterNot { it in referencedTempPaths }
            .forEach(::deleteManagedTempFile)
    }

    private fun nextPrePoseTaskId(): Long {
        nextPrePoseTaskId += 1L
        return nextPrePoseTaskId
    }

    private fun extractVideoMetadata(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // ── Step 1: 파일명 추출 ─────────────────────────────────────
                // file:// URI(복사된 임시 파일)는 path에서 직접 추출
                // content:// URI는 ContentResolver query 사용
                val name = if (uri.scheme == "file") {
                    uri.path?.let { File(it).name }
                } else {
                    context.contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }

                // ── Step 2: MediaExtractor → 첫 번째 실제 PTS 수집 ──────────
                val firstPts = getFirstActualPts(uri)
                Log.d(TAG, "   첫 번째 실제 PTS: ${firstPts / 1000}ms")

                // ── Step 3: FFmpegMediaMetadataRetriever로 안정적 프레임 추출 ─
                val retriever = FFmpegMediaMetadataRetriever()
                val (durationStr, frame) = try {
                    if (!setRetrieverDataSource(retriever, uri)) return@runCatching null

                    val durationMs = retriever
                        .extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLong() ?: 0L
                    val duration = "%d:%02d".format(durationMs / 1000 / 60, (durationMs / 1000) % 60)

                    val bitmap = retriever.getFrameAtTime(
                        firstPts,
                        FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                    )
                    Log.d(TAG, if (bitmap != null) "   ✅ 썸네일 추출 성공" else "   ⚠️ 썸네일 null")

                    Pair(duration, bitmap)
                } finally {
                    retriever.release()
                }

                Triple(name, durationStr, frame)

            }.onSuccess { triple ->
                withContext(Dispatchers.Main) {
                    videoFileName = triple?.first
                    videoDuration = triple?.second
                    thumbnail    = triple?.third
                }
            }.onFailure { e ->
                Log.e(TAG, "❌ extractVideoMetadata 실패", e)
            }
        }
    }

    private fun setRetrieverDataSource(
        retriever: FFmpegMediaMetadataRetriever,
        uri: Uri
    ): Boolean {
        return try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path ?: return false)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
                retriever.setDataSource(pfd.fileDescriptor)
                pfd.close()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ setRetrieverDataSource 실패 (scheme=${uri.scheme}): ${e.message}")
            false
        }
    }

    private fun getFirstActualPts(uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: return 0L)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0L
                extractor.setDataSource(pfd.fileDescriptor)
                pfd.close()
            }

            var videoTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) { videoTrack = i; break }
            }
            if (videoTrack == -1) {
                Log.e(TAG, "❌ 비디오 트랙 없음")
                return 0L
            }
            extractor.selectTrack(videoTrack)

            extractor.sampleTime.coerceAtLeast(0L)

        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaExtractor 실패: ${e.message}")
            0L
        } finally {
            extractor.release()
        }
    }

    fun updateGymInfo(id: Int, name: String) {
        gymId = id
        gymName = name
        clearChallengeFlowState()
    }

    /**
     * 현재 위치 기준으로 주변 암장을 검색합니다.
     *
     * 동작:
     * 1. 마지막 검색 좌표 저장
     * 2. 이전 선택/resolve 상태 초기화
     * 3. UseCase를 통해 Kakao Local API 검색
     * 4. 결과를 UI 상태로 반영
     */
    fun searchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        query: String = "",
        nearbyOnly: Boolean = false
    ) {
        lastSearchLatitude = latitude
        lastSearchLongitude = longitude

        val normalizedQuery = query.trim()

        Log.d(
            TAG,
            "searchNearbyPlaces: latitude=$latitude, longitude=$longitude, query=$normalizedQuery, nearbyOnly=$nearbyOnly"
        )

        selectedNearbyPlace = null
        resolvedGym = null
        resolvedGymGrades = emptyList()
        gymId = null
        gymName = ""
        clearChallengeFlowState()
        _gymResolveUiState.value = GymResolveUiState.Idle

        viewModelScope.launch {
            _gymSearchUiState.value = GymSearchUiState.Loading

            searchNearbyClimbingGymsUseCase(
                latitude = latitude,
                longitude = longitude,
                query = normalizedQuery,
                nearbyOnly = nearbyOnly
            )
                .onSuccess { places ->
                    nearbyPlaces = places
                    Log.d(TAG, "searchNearbyPlaces: success, placeCount=${places.size}")
                    places.forEachIndexed { index, place ->
                        Log.d(
                            TAG,
                            "searchNearbyPlaces[$index]: name=${place.placeName}, " +
                                "address=${place.roadAddressName ?: place.addressName}, " +
                                "distance=${place.distanceMeters}, " +
                                "lat=${place.latitude}, lng=${place.longitude}, " +
                                "externalPlaceId=${place.externalPlaceId}"
                        )
                    }
                    _gymSearchUiState.value = GymSearchUiState.Success(places)
                }
                .onFailure { throwable ->
                    nearbyPlaces = emptyList()
                    Log.e(TAG, "searchNearbyPlaces: failed", throwable)
                    _gymSearchUiState.value = GymSearchUiState.Error(
                        throwable.message ?: "Failed to search nearby gyms."
                    )
                }
        }
    }

    /**
     * 사용자가 선택한 장소를 DDGo backend에 resolve 요청합니다.
     *
     * 동작:
     * 1. 선택한 장소 저장
     * 2. resolve API 호출
     * 3. gymId, gymName, resolved grades 반영
     */
    fun resolveSelectedPlace(place: NearbyPlace) {
        selectedNearbyPlace = place
        Log.d(
            TAG,
            "resolveSelectedPlace: name=${place.placeName}, " +
                "address=${place.roadAddressName ?: place.addressName}, " +
                "lat=${place.latitude}, lng=${place.longitude}, " +
                "externalPlaceId=${place.externalPlaceId}"
        )

        viewModelScope.launch {
            _gymResolveUiState.value = GymResolveUiState.Loading

            resolveGymUseCase(place)
                .onSuccess { resolved ->
                    resolvedGym = resolved
                    resolvedGymGrades = resolved.grades
                    gymId = resolved.gymId
                    gymName = resolved.gym.displayName
                    clearChallengeFlowState()
                    Log.d(
                        TAG,
                        "resolveSelectedPlace: success, gymId=${resolved.gymId}, " +
                            "displayName=${resolved.gym.displayName}, gradeCount=${resolved.grades.size}, " +
                            "matched=${resolved.matched}, matchStatus=${resolved.matchStatus}, " +
                            "gradeSource=${resolved.gradeSource}"
                    )
                    _gymResolveUiState.value = GymResolveUiState.Success(resolved)
                }
                .onFailure { throwable ->
                    resolvedGym = null
                    resolvedGymGrades = emptyList()
                    gymId = null
                    gymName = ""
                    clearChallengeFlowState()
                    Log.e(TAG, "resolveSelectedPlace: failed", throwable)
                    _gymResolveUiState.value = GymResolveUiState.Error(
                        throwable.message ?: "Failed to resolve gym."
                    )
                }
        }
    }

    fun selectGymLevel(sortOrder: Int) {
        selectedLevelSortOrder = sortOrder

        val matchingGrades = resolvedGymGrades.filter { it.sortOrder == sortOrder }
        difficultyLevel = matchingGrades.firstOrNull()
            ?.let(::formatSelectedLevelLabel)
            ?: "V$sortOrder"

        val nextSelectedGrade = selectedGymGrade
            ?.takeIf { it.sortOrder == sortOrder }
            ?: matchingGrades.firstOrNull()

        if (nextSelectedGrade != null) {
            selectedGymGrade = nextSelectedGrade
            selectedGymGradeId = nextSelectedGrade.gymGradeId.toLong()
        } else {
            selectedGymGrade = null
            selectedGymGradeId = null
        }

        clearCreatedChallengeOnly()
    }

    fun updateHoldColor(colorKey: String) {
        selectedHoldColorKey = colorKey.takeIf { it.isNotBlank() }
        holdColor = resolveHoldColorDisplayName(
            colorName = colorKey,
            colorHex = null
        )
    }

    fun updateSelectedStartHold(hold: Hold) {
        selectedStartHold = hold
        selectedEndHold = null
        numberedHolds = emptyList()
        clearHoldReachAnalysis()
    }

    fun updateSelectedEndHold(hold: Hold) {
        selectedEndHold = hold
        recomputeHoldNumbers()
    }

    /**
     * resolve된 암장 난이도 목록에서 사용자가 선택한 gym grade를 저장합니다.
     *
     * 규칙:
     * - 이제 사용자는 하드코딩된 로컬 난이도가 아니라 실제 gymGradeId를 선택합니다.
     * - 선택된 grade는 홀드 감지 시 사용할 색상 필터에도 반영됩니다.
     */
    fun selectGymGrade(grade: GymGrade) {
        selectedLevelSortOrder = grade.sortOrder
        selectedGymGrade = grade
        selectedGymGradeId = grade.gymGradeId.toLong()
        difficultyLevel = formatSelectedLevelLabel(grade)
        clearCreatedChallengeOnly()
    }

    /** 현재 선택된 암장과 난이도로 챌린지를 생성합니다. */
    fun createChallengeFromSelection() {
        val currentGymId = gymId?.toLong()
        val currentGymGradeId = selectedGymGradeId

        if (currentGymId == null || currentGymId <= 0L) {
            _challengeCreationUiState.value =
                ChallengeCreationUiState.Error("암장 선택이 필요합니다.")
            return
        }

        if (currentGymGradeId == null || currentGymGradeId <= 0L) {
            _challengeCreationUiState.value =
                ChallengeCreationUiState.Error("난이도 선택이 필요합니다.")
            return
        }

        val existingChallenge = createdChallenge
        if (
            existingChallenge != null &&
            existingChallenge.gymId == currentGymId &&
            existingChallenge.gymGradeId == currentGymGradeId
        ) {
            challengeId = existingChallenge.challengeId
            _challengeCreationUiState.value = ChallengeCreationUiState.Success(existingChallenge)
            return
        }

        viewModelScope.launch {
            _challengeCreationUiState.value = ChallengeCreationUiState.Loading

            createChallengeUseCase(
                gymId = currentGymId,
                gymGradeId = currentGymGradeId,
                startedAt = LocalDateTime.now().toString()
            )
                .onSuccess { challenge ->
                    createdChallenge = challenge
                    challengeId = challenge.challengeId
                    allowLocalAnalysisWithoutChallenge = false
                    difficultyLevel = challenge.gradeLabel ?: (selectedGymGrade?.gradeLabel ?: challenge.problemColor)
                    if (selectedHoldColorKey == null) {
                        mapGymColorToClassifierColor(challenge.problemColor)
                            ?.let(::updateHoldColor)
                    }
                    _challengeCreationUiState.value = ChallengeCreationUiState.Success(challenge)
                    Log.d(
                        TAG,
                        "createChallengeFromSelection: success, challengeId=${challenge.challengeId}, " +
                            "gymId=${challenge.gymId}, gymGradeId=${challenge.gymGradeId}, " +
                            "problemColor=${challenge.problemColor}"
                    )
                }
                .onFailure { throwable ->
                    createdChallenge = null
                    challengeId = null
                    Log.e(TAG, "createChallengeFromSelection: failed", throwable)
                    _challengeCreationUiState.value = ChallengeCreationUiState.Error(
                        throwable.message ?: "Failed to create challenge."
                    )
                }
        }
    }

    /** 화면 이동이 한 번만 일어나도록 챌린지 생성 성공 상태를 소비합니다. */
    fun consumeChallengeCreationResult() {
        if (_challengeCreationUiState.value is ChallengeCreationUiState.Success) {
            _challengeCreationUiState.value = ChallengeCreationUiState.Idle
        }
    }

    /**
     * 터치 지점(정규화 좌표) 근처에서 후보 홀드를 탐색하여 팝업 상태를 업데이트합니다.
     * Screen에서 화면 좌표 → 정규화 좌표로 변환 후 호출합니다.
     * 이미 선택된 홀드도 포함하여 팝업에서 선택/취소를 모두 처리할 수 있습니다.
     *
     * @param tapNormX 터치 x 좌표 (0~1, 이미지 정규화 좌표)
     * @param tapNormY 터치 y 좌표 (0~1, 이미지 정규화 좌표)
     */
    fun findCandidatesNearTap(tapNormX: Float, tapNormY: Float) {
        val candidates = findNearbyCandidates(tapNormX, tapNormY)
        if (candidates.isNotEmpty()) {
            candidateHolds = candidates
            showCandidatePopup = true
        }
    }

    /**
     * 팝업에서 확인 시 호출. 추가/제거를 한 번에 적용합니다.
     */
    fun applyHoldChanges(toAdd: List<Hold>, toRemove: List<Hold>) {
        toAdd.forEach { addManualHold(it) }
        toRemove.forEach { removeHold(it) }
        dismissCandidatePopup()
    }

    /** 후보 홀드 팝업을 닫습니다. */
    fun dismissCandidatePopup() {
        showCandidatePopup = false
        candidateHolds = emptyList()
    }

    /**
     * detectedHolds에서 홀드를 제거합니다.
     */
    fun removeHold(hold: Hold) {
        detectedHolds = detectedHolds.filter { existing ->
            existing.boundingBox != hold.boundingBox
        }
        clearSelectedHoldSelection()
        Log.d(TAG, "❌ 홀드 제거: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
    }

    /**
     * 수동으로 홀드를 detectedHolds에 추가합니다.
     * allRawHolds에는 있지만 색상 필터링으로 누락된 홀드를 복구할 때 사용합니다.
     */
    private fun addManualHold(hold: Hold) {
        val alreadyExists = detectedHolds.any { existing ->
            existing.boundingBox == hold.boundingBox
        }
        if (!alreadyExists) {
            detectedHolds = detectedHolds + hold
            clearSelectedHoldSelection()
            Log.d(TAG, "✅ 수동 홀드 추가: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
        }
    }

    /**
     * 터치 지점 근처의 홀드를 반환합니다.
     * 이미 선택된 홀드도 포함하여 팝업에서 선택/취소를 모두 처리할 수 있도록 합니다.
     *
     * @param tapNormX 터치 x 좌표 (0~1)
     * @param tapNormY 터치 y 좌표 (0~1)
     * @param searchRadius 탐색 반경 (정규화 좌표 기준)
     */
    private fun findNearbyCandidates(
        tapNormX: Float,
        tapNormY: Float,
        searchRadius: Float = 0.12f
    ): List<Hold> {
        return allRawHolds
            .filter { hold ->
                val cx = (hold.boundingBox.left + hold.boundingBox.right) / 2f
                val cy = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
                val dist = sqrt(
                    (cx - tapNormX) * (cx - tapNormX) + (cy - tapNormY) * (cy - tapNormY)
                )
                dist <= searchRadius
            }
            .sortedBy { hold ->
                val cx = (hold.boundingBox.left + hold.boundingBox.right) / 2f
                val cy = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
                sqrt((cx - tapNormX) * (cx - tapNormX) + (cy - tapNormY) * (cy - tapNormY))
            }
            .take(8)
    }

    fun updateAnalysisPoints(points: List<AnalysisPoint>) {
        analysisPoints = points
    }

    /**
     * ExoPlayer TextureView 캡처 프레임으로 실시간 포즈 추론을 실행합니다.
     * AttemptResultScreen의 LaunchedEffect 루프에서 150ms 간격으로 호출됩니다.
     * 이전 추론이 진행 중이면 자동으로 건너뜁니다 (PoseEstimatorImpl 내부 플래그).
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

    // (기존 단일 추가 URI 메서드는 삭제하고 updateAdditionalVideoUris 사용)

    // ====== 비즈니스 로직 ======

    /**
     * PersonDetector → 최적 프레임 탐색 → HoldDetector → 홀드 탐지 전체 파이프라인.
     */
    fun runHoldDetection() {
        val uri = videoUri ?: run {
            Log.e(TAG, "❌ videoUri 없음 - 홀드 탐지 불가")
            _uiState.value = UploadUiState.Error("영상을 먼저 선택해주세요.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UploadUiState.Loading

            runCatching {
                withContext(Dispatchers.IO) {
                    Log.d(TAG, "▶ [1/3] PersonDetector 시작")
                    val bestTimeUs = personDetector.findBestFrameTime(uri)
                    Log.d(TAG, "✅ [1/3] 최적 프레임: ${bestTimeUs / 1000}ms")

                    Log.d(TAG, "▶ [2/3] 프레임 추출 시작 (PTS=${bestTimeUs / 1000}ms, uri=${uri})")
                    val retriever = FFmpegMediaMetadataRetriever()
                    val bitmap = try {
                        val parsedUri = Uri.parse(uri)
                        if (!setRetrieverDataSource(retriever, parsedUri)) {
                            throw IllegalStateException("setDataSource 실패 (scheme=${parsedUri.scheme})")
                        }
                        retriever.getFrameAtTime(
                            bestTimeUs,
                            FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                        ) ?: throw IllegalStateException("getFrameAtTime 반환 null (PTS=${bestTimeUs / 1000}ms)")
                    } finally {
                        retriever.release()
                    }
                    Log.d(TAG, "✅ [2/3] 프레임 추출 성공 (${bitmap.width}x${bitmap.height})")

                    Log.d(TAG, "▶ [3/4] HoldDetector 시작")
                    val rawHolds = holdDetector.detectFromFrame(bitmap)
                    Log.d(TAG, "✅ [3/4] 홀드 탐지 완료: ${rawHolds.size}개")

                    // 전체 홀드에 색상 분류 적용 (수동 추가 후보 풀용)
                    val classifiedAll = rawHolds.map { holdColorClassifier.classifySingle(bitmap, it) }

                    val detectionTargetColor = resolveDetectionTargetHoldColor()
                    Log.d(TAG, "▶ [4/4] 색상 필터링 시작 (목표 색: '$detectionTargetColor')")
                    val holds = if (detectionTargetColor.isBlank()) {
                        holdColorClassifier.classifyAll(bitmap, rawHolds)
                    } else {
                        holdColorClassifier.classifyAndFilter(
                            bitmap          = bitmap,
                            holds           = rawHolds,
                            targetColorName = detectionTargetColor,
                            scoreThreshold  = 0.25f
                        )
                    }
                    Log.d(TAG, "✅ [4/4] 색상 필터 완료: ${rawHolds.size}개 → ${holds.size}개")

                    Triple(bitmap, classifiedAll, holds)
                }
            }.onSuccess { (bitmap, allHolds, filteredHolds) ->
                bestFrameBitmap = bitmap
                allRawHolds     = allHolds
                detectedHolds   = filteredHolds
                clearSelectedHoldSelection()
                _uiState.value  = UploadUiState.Success
            }.onFailure { e ->
                Log.e(TAG, "❌ runHoldDetection 실패", e)
                _uiState.value = UploadUiState.Error(e.message ?: "홀드 탐지 중 오류가 발생했습니다.")
            }
        }
    }

    /**
     * 최종 챌린지 또는 영상을 서버에 제출합니다.
     */
    fun submitUpload() {
        if (_uploadSubmissionUiState.value is UploadSubmissionUiState.Loading) {
            return
        }

        val currentChallengeId = challengeId
        val useLocalAnalysisOnly = allowLocalAnalysisWithoutChallenge &&
            !isAttemptOnlyUploadMode &&
            (currentChallengeId == null || currentChallengeId <= 0L)

        if (!useLocalAnalysisOnly && (currentChallengeId == null || currentChallengeId <= 0L)) {
            _uploadSubmissionUiState.value =
                UploadSubmissionUiState.Error("생성된 challenge가 없습니다.")
            return
        }

        val currentBitmap = bestFrameBitmap
        val numberedHoldsForAnalysis = numberedHolds.takeIf { it.isNotEmpty() }

        if (!isAttemptOnlyUploadMode) {
            if (currentBitmap == null) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Error("홀드 기준 이미지가 없습니다.")
                return
            }

            if (detectedHolds.isEmpty()) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Error("저장할 홀드가 없습니다.")
                return
            }

            if (numberedHoldsForAnalysis == null) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Error("시작 홀드와 끝 홀드를 먼저 선택해주세요.")
                return
            }
        }

        viewModelScope.launch {
            awaitActiveSelectionPreparation()

            val attemptUris = allAttemptUris
            if (attemptUris.isEmpty()) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Error("업로드할 영상이 없습니다.")
                return@launch
            }

            if (!isAttemptOnlyUploadMode && !useLocalAnalysisOnly) {
                val bitmapForHoldSave = currentBitmap ?: run {
                    _uploadSubmissionUiState.value =
                        UploadSubmissionUiState.Error("홀드 기준 이미지가 없습니다.")
                    return@launch
                }

                if (detectedHolds.isEmpty()) {
                    _uploadSubmissionUiState.value =
                        UploadSubmissionUiState.Error("저장할 홀드가 없습니다.")
                    return@launch
                }

                val holdCoordinates = buildChallengeHoldCoordinates()

                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Loading("홀드 정보를 저장하고 있습니다.")

                saveChallengeHoldsUseCase(
                    challengeId = currentChallengeId!!,
                    holds = holdCoordinates
                )
                    .onSuccess { saved ->
                        savedChallengeHolds = saved
                        Log.d(
                            TAG,
                            "submitUpload: holds saved, challengeId=${saved.challengeId}, holdCount=${saved.holdCount}"
                        )
                    }
                    .onFailure { throwable ->
                        Log.e(TAG, "submitUpload: save holds failed", throwable)
                        _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                            throwable.message ?: "Failed to save challenge holds."
                        )
                        return@launch
                }
            }

            val uploadedVideos = mutableListOf<UploadedAttemptVideo>()
            if (useLocalAnalysisOnly) {
                Log.d(TAG, "submitUpload: dev local analysis mode, skipping challenge save and video upload")
            } else {
                attemptUris.forEachIndexed { index, uri ->
                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                        "영상 업로드 중입니다. (${index + 1}/${attemptUris.size})"
                    )

                    uploadAttemptVideoUseCase(
                        challengeId = currentChallengeId!!,
                        videoUri = uri
                    )
                        .onSuccess { uploaded ->
                            endAttemptUseCase(
                                challengeId = currentChallengeId,
                                attemptId = uploaded.attemptId,
                                attemptResult = null
                            )
                                .onFailure { throwable ->
                                    Log.e(TAG, "submitUpload: end attempt failed", throwable)
                                    _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                                        throwable.message ?: "Failed to end attempt."
                                    )
                                    return@launch
                                }

                            uploadedVideos += uploaded
                            Log.d(
                                TAG,
                                "submitUpload: attempt upload success, attemptId=${uploaded.attemptId}, " +
                                    "attemptNo=${uploaded.attemptNo}, objectKey=${uploaded.objectKey}"
                            )
                        }
                        .onFailure { throwable ->
                            Log.e(TAG, "submitUpload: attempt upload failed", throwable)
                            _uploadSubmissionUiState.value = UploadSubmissionUiState.Error(
                                throwable.message ?: "Failed to upload attempt video."
                            )
                            return@launch
                        }
                }
            }

            if (numberedHoldsForAnalysis != null) {
                _uploadSubmissionUiState.value =
                    UploadSubmissionUiState.Loading("최고 도달 홀드를 분석하고 있습니다.")

                analyzeAllAttemptHoldReach(
                    attemptUris = attemptUris,
                    holds = numberedHoldsForAnalysis
                )
            } else {
                awaitPrePoseTerminal(attemptUris)
                clearHoldReachAnalysis()
            }

            publishAttemptResultSession(
                playbackUris = attemptUris,
                uploadedVideos = uploadedVideos,
                currentAttemptIndex = 0,
                holdReachResults = attemptHoldReachResults,
                overallSummary = overallHoldReachSummary
            )
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Success(uploadedVideos)
        }
    }

    fun resetState() {
        _uiState.value = UploadUiState.Idle
    }

    private fun recomputeHoldNumbers() {
        val startHold = selectedStartHold ?: return
        val endHold = selectedEndHold ?: return
        clearHoldReachAnalysis()

        runCatching {
            assignHoldNumbers(
                holds = detectedHolds,
                startHold = startHold,
                endHold = endHold
            )
        }.onSuccess { numbered ->
            numberedHolds = numbered
            detectedHolds = numbered.toHolds()
            selectedStartHold = numbered.firstOrNull { it.isStart }?.hold
            selectedEndHold = numbered.firstOrNull { it.isEnd }?.hold
            Log.d(TAG, "✅ 홀드 번호 부여 완료: ${numbered.size}개")
        }.onFailure { throwable ->
            Log.e(TAG, "❌ 홀드 번호 부여 실패", throwable)
            numberedHolds = emptyList()
        }
    }

    private fun clearSelectedHoldSelection() {
        selectedStartHold = null
        selectedEndHold = null
        numberedHolds = emptyList()
        clearHoldReachAnalysis()
    }

    fun resetUploadSubmissionState() {
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
    }

    private fun clearChallengeFlowState() {
        selectedLevelSortOrder = null
        selectedGymGradeId = null
        selectedGymGrade = null
        difficultyLevel = ""
        selectedHoldColorKey = null
        holdColor = ""
        clearSelectedHoldSelection()
        clearCreatedChallengeOnly()
    }

    private fun clearCreatedChallengeOnly() {
        createdChallenge = null
        challengeId = null
        savedChallengeHolds = null
        uploadedAttemptVideos = emptyList()
        clearHoldReachAnalysis()
        resultPlaybackUris = emptyList()
        publishedAttemptResultSession = null
        _challengeCreationUiState.value = ChallengeCreationUiState.Idle
        _uploadSubmissionUiState.value = UploadSubmissionUiState.Idle
    }

    private suspend fun analyzeAllAttemptHoldReach(
        attemptUris: List<String>,
        holds: List<HoldNumbered>
    ) {
        if (attemptUris.isEmpty() || holds.isEmpty()) {
            clearHoldReachAnalysis()
            return
        }

        val terminalSnapshot = awaitPrePoseTerminal(attemptUris)

        val analyses = attemptUris.mapIndexed { index, uri ->
            _uploadSubmissionUiState.value = UploadSubmissionUiState.Loading(
                "최고 도달 홀드를 분석하고 있습니다. (${index + 1}/${attemptUris.size})"
            )

            analyzeSingleAttemptPoseAnalysis(
                playbackUri = uri,
                poses = terminalSnapshot.entriesByPlaybackUri[uri]?.poses.orEmpty(),
                holds = holds
            )
        }

        attemptHoldReachResults = analyses.map(AttemptPoseAnalysis::holdReachResult)
        attemptPoseDtos = analyses.map(AttemptPoseAnalysis::poseSequenceDto)
        attemptAnalyzedPoses = analyses.map(AttemptPoseAnalysis::poses)
        attemptPolygonHoldContactDebugResults =
            analyses.map(AttemptPoseAnalysis::polygonHoldContactDebugResult)
        overallHoldReachSummary = summarizeHoldReachResults(
            results = attemptHoldReachResults,
            totalHoldCount = holds.size
        )
    }

    private fun analyzeSingleAttemptPoseAnalysis(
        playbackUri: String,
        poses: List<Pose>,
        holds: List<HoldNumbered>
    ): AttemptPoseAnalysis {
        val stablePoses = poses
        val videoUri = playbackUri
        val poseSequenceDto = stablePoses.toPoseSequenceDto()

        Log.i(
            HOLD_CONTACT_ANALYSIS_TAG,
            "$HOLD_CONTACT_LOG_PREFIX HoldContactAnalysis 전달: " +
                "uri=$videoUri, " +
                "poseCount=${stablePoses.size}, holdCount=${holds.size}, " +
                "dtoFrameCount=${poseSequenceDto.poses.size}"
        )

        val polygonHoldContactDebugResult = analyzePolygonHoldContacts(
            poses = stablePoses,
            holds = holds
        )

        val holdReachResult = polygonHoldContactDebugResult
            .toAttemptHoldReachResult(holds = holds)
            .also { result ->
            Log.d(
                TAG,
                "✅ 최고 도달 홀드 분석 완료(Polygon Main): uri=$videoUri, " +
                    "highestHoldNo=${result.highestReachedHoldNo}, " +
                    "contacted=${result.contactedHoldNos}"
            )
        }

        return AttemptPoseAnalysis(
            holdReachResult = holdReachResult,
            poseSequenceDto = poseSequenceDto,
            poses = stablePoses,
            polygonHoldContactDebugResult = polygonHoldContactDebugResult
        )
    }

    private fun clearHoldReachAnalysis() {
        attemptHoldReachResults = emptyList()
        attemptPoseDtos = emptyList()
        attemptAnalyzedPoses = emptyList()
        attemptPolygonHoldContactDebugResults = emptyList()
        overallHoldReachSummary = null
        currentPoseLandmarks = emptyList()
    }

    private fun clearAttemptResultState(clearPublishedSession: Boolean) {
        currentAttemptIndex = 0
        resultPlaybackUris = emptyList()
        uploadedAttemptVideos = emptyList()
        clearHoldReachAnalysis()
        if (clearPublishedSession) {
            publishedAttemptResultSession = null
        }
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
        resultPlaybackUris = playbackUris
        uploadedAttemptVideos = uploadedVideos
        this.currentAttemptIndex = currentAttemptIndex.coerceIn(
            minimumValue = 0,
            maximumValue = playbackUris.lastIndex.coerceAtLeast(0)
        )
        attemptHoldReachResults = holdReachResults
        attemptPoseDtos = poseDtos
        attemptAnalyzedPoses = analyzedPoses
        attemptPolygonHoldContactDebugResults = polygonHoldContactDebugResults
        overallHoldReachSummary = overallSummary
        publishedAttemptResultSession = PublishedAttemptResultSession(
            resultPlaybackUris = playbackUris,
            uploadedAttemptVideos = uploadedVideos,
            currentAttemptIndex = this.currentAttemptIndex,
            holdReachResults = holdReachResults,
            attemptPoseDtos = poseDtos,
            attemptAnalyzedPoses = analyzedPoses,
            attemptPolygonHoldContactDebugResults = polygonHoldContactDebugResults,
            overallHoldReachSummary = overallSummary
        )
    }

    private fun captureCurrentAttemptResultSession() {
        val playbackUris = resultPlaybackUris.takeIf { it.isNotEmpty() } ?: return
        publishedAttemptResultSession = PublishedAttemptResultSession(
            resultPlaybackUris = playbackUris,
            uploadedAttemptVideos = uploadedAttemptVideos,
            currentAttemptIndex = currentAttemptIndex.coerceIn(
                minimumValue = 0,
                maximumValue = playbackUris.lastIndex.coerceAtLeast(0)
            ),
            holdReachResults = attemptHoldReachResults,
            attemptPoseDtos = attemptPoseDtos,
            attemptAnalyzedPoses = attemptAnalyzedPoses,
            attemptPolygonHoldContactDebugResults = attemptPolygonHoldContactDebugResults,
            overallHoldReachSummary = overallHoldReachSummary
        )
    }

    private fun restorePublishedAttemptResultSession() {
        val session = publishedAttemptResultSession ?: run {
            clearAttemptResultState(clearPublishedSession = false)
            return
        }

        resultPlaybackUris = session.resultPlaybackUris
        uploadedAttemptVideos = session.uploadedAttemptVideos
        currentAttemptIndex = session.currentAttemptIndex.coerceIn(
            minimumValue = 0,
            maximumValue = session.resultPlaybackUris.lastIndex.coerceAtLeast(0)
        )
        attemptHoldReachResults = session.holdReachResults
        attemptPoseDtos = session.attemptPoseDtos
        attemptAnalyzedPoses = session.attemptAnalyzedPoses
        attemptPolygonHoldContactDebugResults = session.attemptPolygonHoldContactDebugResults
        overallHoldReachSummary = session.overallHoldReachSummary
        currentPoseLandmarks = emptyList()
    }

    private fun publishedResultPlaybackUris(): Set<String> =
        publishedAttemptResultSession?.resultPlaybackUris?.toSet().orEmpty()

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

data class ManagedAttemptVideo(
    val sourceUri: String,
    val playbackUri: String,
    val tempFilePath: String?
)

enum class PrePoseStatus {
    Pending,
    Running,
    Ready,
    Failed
}

data class PrePoseCacheEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val status: PrePoseStatus,
    val poses: List<Pose> = emptyList(),
    val errorMessage: String? = null,
    val taskId: Long? = null
)

private fun PrePoseCacheEntry.toTerminalEntry(): TerminalPrePoseEntry = TerminalPrePoseEntry(
    playbackUri = playbackUri,
    selectionGeneration = selectionGeneration,
    status = status,
    poses = poses,
    errorMessage = errorMessage
)

data class TerminalPrePoseSnapshot(
    val generation: Long,
    val entriesByPlaybackUri: Map<String, TerminalPrePoseEntry>
)

data class TerminalPrePoseEntry(
    val playbackUri: String,
    val selectionGeneration: Long,
    val status: PrePoseStatus,
    val poses: List<Pose>,
    val errorMessage: String?
)

data class PrePoseBatchState(
    val generation: Long = 0L,
    val totalCount: Int = 0,
    val pendingCount: Int = 0,
    val runningCount: Int = 0,
    val readyCount: Int = 0,
    val failedCount: Int = 0
) {
    val isTerminal: Boolean
        get() = totalCount > 0 && pendingCount == 0 && runningCount == 0
}

private data class PrePoseTask(
    val playbackUri: String,
    val taskId: Long
)

private data class PublishedAttemptResultSession(
    val resultPlaybackUris: List<String>,
    val uploadedAttemptVideos: List<UploadedAttemptVideo>,
    val currentAttemptIndex: Int,
    val holdReachResults: List<AttemptHoldReachResult>,
    val attemptPoseDtos: List<PoseSequenceDto>,
    val attemptAnalyzedPoses: List<List<Pose>>,
    val attemptPolygonHoldContactDebugResults: List<PolygonHoldContactDebugResult>,
    val overallHoldReachSummary: OverallHoldReachSummary?
)

sealed class UploadUiState {
    object Idle : UploadUiState()
    object Loading : UploadUiState()
    // TODO: 결과 화면 (AttemptResultScreen)에서 보여줄 분석 결과를 파라미터로 넣을 수도 있습니다.
    object Success : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}

/**
 * 주변 암장 검색 UI 상태.
 */
sealed class GymSearchUiState {
    object Idle : GymSearchUiState()
    object Loading : GymSearchUiState()
    data class Success(val places: List<NearbyPlace>) : GymSearchUiState()
    data class Error(val message: String) : GymSearchUiState()
}

/**
 * gym resolve UI 상태.
 */
sealed class GymResolveUiState {
    object Idle : GymResolveUiState()
    object Loading : GymResolveUiState()
    data class Success(val resolvedGym: ResolvedGym) : GymResolveUiState()
    data class Error(val message: String) : GymResolveUiState()
}

/** 챌린지 생성 UI 상태입니다. */
sealed class ChallengeCreationUiState {
    object Idle : ChallengeCreationUiState()
    object Loading : ChallengeCreationUiState()
    data class Success(val challenge: ChallengeSession) : ChallengeCreationUiState()
    data class Error(val message: String) : ChallengeCreationUiState()
}

/**
 * 업로드 제출 UI 상태입니다.
 *
 * 역할:
 * - 홀드 선택 이후 로딩 화면을 구동합니다.
 * - 홀드 저장과 시도 영상 업로드 단계를 함께 표현합니다.
 */
sealed class UploadSubmissionUiState {
    object Idle : UploadSubmissionUiState()
    data class Loading(val message: String) : UploadSubmissionUiState()
    data class Success(val uploadedAttempts: List<UploadedAttemptVideo>) : UploadSubmissionUiState()
    data class Error(val message: String) : UploadSubmissionUiState()
}

private enum class UploadFlowMode {
    FullChallenge,
    AttemptOnly
}

