package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import javax.inject.Inject

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
    @ApplicationContext private val context: Context
) : ViewModel() {

    // UI 레이어에 노출할 상태 (로딩, 성공, 실패 등)
    private val _uiState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // --- 1. AttemptUploadScreen (초기 영상 업로드) ---
    var videoUri by mutableStateOf<String?>(null)
        private set

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

    // --- 3. ChallengeHoldScreen (홀드 정보) ---
    // TODO: 홀드 데이터 형태에 맞춰 타입 변경 (예: 데이터 클래스)
    var selectedHoldInfo by mutableStateOf<String?>(null)
        private set

    // --- 4. AttemptUploadScreen (추가 영상 업로드) ---
    var additionalVideoUri by mutableStateOf<String?>(null)
        private set

    // ====== 상태 업데이트 메서드 (이벤트 핸들러) ======

    /**
     * 영상 URI를 저장하고, 백그라운드에서 썸네일·메타데이터를 추출합니다.
     *
     * 썸네일 추출 전략: PersonDetectorImpl과 동일한 방식
     *   1. MediaExtractor.advance()로 컨테이너를 순서대로 순회 → 첫 번째 실제 PTS 수집
     *      (계산된 임의 타임스탬프가 아닌 실제 존재하는 PTS → null 프레임 원천 차단)
     *   2. 수집한 PTS를 FFmpegMediaMetadataRetriever.OPTION_CLOSEST 에 전달
     *      (Python cap.read()의 순차 읽기와 동일 원리)
     */
    fun updateVideoUri(uri: String) {
        videoUri = uri
        extractVideoMetadata(Uri.parse(uri))
    }

    private fun extractVideoMetadata(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                // ── Step 1: 파일명 추출 ─────────────────────────────────────
                val name = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }

                // ── Step 2: MediaExtractor → 첫 번째 실제 PTS 수집 ──────────
                // PersonDetectorImpl.getActualSampleTimestampsUs()와 동일 원리:
                // seek 없이 컨테이너 앞에서부터 advance()로 순서대로 걸어 실제 PTS를 얻음
                val firstPts = getFirstActualPts(uri)
                Log.d(TAG, "   첫 번째 실제 PTS: ${firstPts / 1000}ms")

                // ── Step 3: FFmpegMediaMetadataRetriever로 안정적 프레임 추출 ─
                // PersonDetectorImpl과 동일: OPTION_CLOSEST + 실제 PTS → null 없음
                val retriever = FFmpegMediaMetadataRetriever()
                val (durationStr, frame) = try {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: return@runCatching null
                    retriever.setDataSource(pfd.fileDescriptor)
                    pfd.close()

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

    /**
     * MediaExtractor로 컨테이너를 순서대로 순회해 첫 번째 실제 비디오 PTS를 반환합니다.
     *
     * PersonDetectorImpl.getActualSampleTimestampsUs() 에서 첫 PTS만 뽑는 경량 버전.
     * 디코딩 없이 패킷 헤더만 읽으므로 빠릅니다.
     */
    private fun getFirstActualPts(uri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return 0L
            extractor.setDataSource(pfd.fileDescriptor)
            pfd.close()

            // 비디오 트랙 선택
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

            // 컨테이너의 첫 번째 실제 PTS (음수면 0 처리)
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

    fun updateAdditionalVideoUri(uri: String) {
        additionalVideoUri = uri
    }

    // ====== 비즈니스 로직 ======

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
