package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.repository.PoseEstimator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import javax.inject.Inject
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

@HiltViewModel
class UploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val personDetector: PersonDetector,
    private val holdDetector: HoldDetector,
    private val poseEstimator: PoseEstimator,
    private val holdColorClassifier: HoldColorClassifier
) : ViewModel() {

    // UI 레이어에 노출할 상태 (로딩, 성공, 실패 등)
    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // --- 1. AttemptUploadScreen (초기 영상 업로드) ---
    var videoUri by mutableStateOf<String?>(null)
        private set
        
    var additionalVideoUris by mutableStateOf<List<String>>(emptyList())
        private set

    // 전체 시도 영상 (초기 + 추가 영상 리스트)
    val allAttemptUris: List<String>
        get() = listOfNotNull(videoUri) + additionalVideoUris

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

    // TODO: 홀드 데이터 형태에 맞춰 타입 변경 (예: 데이터 클래스)
    /** 사용자가 선택한 시작 홀드 */
    var selectedHoldInfo by mutableStateOf<String?>(null)
        private set

    /** 사용자가 선택한 끝 홀드 */
    var selectedEndHoldInfo by mutableStateOf<String?>(null)
        private set

    // --- 4. AttemptResultScreen (포즈 오버레이 + 분석 타임라인) ---
    
    // N차 시도를 추적하는 인덱스 (0이 1차 시도)
    var currentAttemptIndex by mutableStateOf(0)
        private set
        
    fun nextAttempt() {
        if (currentAttemptIndex < allAttemptUris.size - 1) {
            currentAttemptIndex++
        }
    }

    /** 현재 재생 프레임의 MediaPipe 33 랜드마크 (실시간 업데이트) */
    var currentPoseLandmarks by mutableStateOf<List<PoseLandmark>>(emptyList())
        private set

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
        additionalVideoUris = uris
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
        viewModelScope.launch(Dispatchers.IO) {
            val fileUri = copyToTempFileIfNeeded(Uri.parse(uri))
            withContext(Dispatchers.Main) {
                videoUri = fileUri
            }
            extractVideoMetadata(Uri.parse(fileUri))
        }
    }

    /**
     * content:// URI를 앱 캐시 디렉토리의 임시 파일로 복사해 file:// URI 문자열로 반환합니다.
     *
     * file:// URI(테스트 코드 방식)는 그대로 반환합니다.
     * 복사 실패 시 원본 URI 문자열을 반환합니다(폴백).
     */
    private fun copyToTempFileIfNeeded(uri: Uri): String {
        if (uri.scheme == "file") return uri.toString()

        return try {
            val tempFile = File(context.cacheDir, "temp_climbing_video.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "📋 영상 캐시 복사 완료: ${tempFile.absolutePath}")
            Uri.fromFile(tempFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "❌ 캐시 복사 실패 → 원본 URI 사용: ${e.message}")
            uri.toString()
        }
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
    }

    fun updateDifficulty(level: String) {
        difficultyLevel = level
    }

    fun updateHoldColor(color: String) {
        holdColor = color
    }

    fun updateSelectedHoldInfo(info: String) {
        selectedHoldInfo = info
    }

    fun updateSelectedEndHoldInfo(info: String) {
        selectedEndHoldInfo = info
    }

    /**
     * 터치 지점(정규화 좌표) 근처에서 후보 홀드를 탐색하여 팝업 상태를 업데이트합니다.
     * Screen에서 화면 좌표 → 정규화 좌표로 변환 후 호출합니다.
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
     * 후보 팝업에서 홀드를 선택하여 detectedHolds에 추가하고 팝업을 닫습니다.
     */
    fun selectManualHold(hold: Hold) {
        addManualHold(hold)
        dismissCandidatePopup()
    }

    /** 후보 홀드 팝업을 닫습니다. */
    fun dismissCandidatePopup() {
        showCandidatePopup = false
        candidateHolds = emptyList()
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
            Log.d(TAG, "✅ 수동 홀드 추가: bbox=${hold.boundingBox}, color=${hold.colorLabel}")
        }
    }

    /**
     * 터치 지점 근처에서 현재 detectedHolds에 없는 후보 홀드를 필터링합니다.
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
        val existingBoxes = detectedHolds.map { it.boundingBox }.toSet()
        return allRawHolds
            .filter { it.boundingBox !in existingBoxes }
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
            val landmarks = poseEstimator.estimateFromFrame(bitmap)
            if (landmarks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    currentPoseLandmarks = landmarks
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

                    Log.d(TAG, "▶ [4/4] 색상 필터링 시작 (목표 색: '$holdColor')")
                    val holds = if (holdColor.isBlank()) {
                        holdColorClassifier.classifyAll(bitmap, rawHolds)
                    } else {
                        holdColorClassifier.classifyAndFilter(
                            bitmap          = bitmap,
                            holds           = rawHolds,
                            targetColorName = holdColor,
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
        viewModelScope.launch {
            _uiState.value = UploadUiState.Loading
            try {
                // TODO: API 호출 등 비즈니스 로직 연동
                // _uiState.value = UploadUiState.Success
            } catch (e: Exception) {
                _uiState.value = UploadUiState.Error(e.message ?: "알 수 없는 에러가 발생했습니다.")
            }
        }
    }

    fun resetState() {
        _uiState.value = UploadUiState.Idle
    }
}

sealed class UploadUiState {
    object Idle : UploadUiState()
    object Loading : UploadUiState()
    // TODO: 결과 화면 (AttemptResultScreen)에서 보여줄 분석 결과를 파라미터로 넣을 수도 있습니다.
    object Success : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}
