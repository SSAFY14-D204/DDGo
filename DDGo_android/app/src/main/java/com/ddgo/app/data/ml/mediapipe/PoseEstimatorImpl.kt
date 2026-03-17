package com.ddgo.app.data.ml.mediapipe

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.repository.PoseEstimator
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
// PoseLandmarkerOptions은 PoseLandmarker의 정적 내부 클래스입니다.
// 별도 import 없이 PoseLandmarker.PoseLandmarkerOptions 로 접근합니다.
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wseemann.media.FFmpegMediaMetadataRetriever
import javax.inject.Inject

/**
 * MediaPipe Tasks Vision을 사용한 PoseEstimator 구현체.
 *
 * ✅ 모델 파일: app/src/main/assets/models/pose_landmarker_lite.task
 *
 * 두 가지 실행 모드:
 *   - IMAGE 모드 : estimateFromFrame() → 결과 화면 실시간 스켈레톤 오버레이
 *   - VIDEO 모드 : estimateFromVideo() → 전체 비디오 일괄 처리 (향후 서버 연동용)
 *
 * MediaPipe API 참고:
 *   - PoseLandmarkerOptions  → PoseLandmarker 의 inner static class
 *   - PoseLandmarkerResult.landmarks() → List<List<NormalizedLandmark>>
 *   - NormalizedLandmark.x(), .y(), .z() → Float (0~1 정규화 좌표)
 */
class PoseEstimatorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PoseEstimator {

    // ── IMAGE 모드: 실시간 단일 프레임 추론 ──────────────────────────────────
    // x86_64 에뮬레이터는 MediaPipe JNI를 지원하지 않으므로 null로 폴백
    private val imageLandmarker: PoseLandmarker? by lazy {
        try {
            PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "MediaPipe JNI 미지원 환경(x86_64?), IMAGE 모드 비활성화: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "imageLandmarker 초기화 실패: ${e.message}")
            null
        }
    }

    // ── VIDEO 모드: 전체 비디오 일괄 처리 ──────────────────────────────────
    // x86_64 에뮬레이터는 MediaPipe JNI를 지원하지 않으므로 null로 폴백
    private val videoLandmarker: PoseLandmarker? by lazy {
        try {
            PoseLandmarker.createFromOptions(
                context,
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetPath(MODEL_ASSET)
                            .build()
                    )
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumPoses(1)
                    .setMinPoseDetectionConfidence(0.5f)
                    .setMinPosePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .build()
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "MediaPipe JNI 미지원 환경(x86_64?), VIDEO 모드 비활성화: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "videoLandmarker 초기화 실패: ${e.message}")
            null
        }
    }

    // IMAGE 모드는 단일 스레드 순차 호출 필요 → 동시 실행 방지 플래그
    @Volatile private var isRunning = false

    // ── 실시간 단일 프레임 추론 ─────────────────────────────────────────────

    override suspend fun estimateFromFrame(bitmap: Bitmap): List<PoseLandmark> =
        withContext(Dispatchers.Default) {
            val landmarker = imageLandmarker ?: return@withContext emptyList()
            if (isRunning) return@withContext emptyList()
            isRunning = true
            val mpImage = BitmapImageBuilder(bitmap).build()
            try {
                val result = landmarker.detect(mpImage)
                // landmarks(): List<List<NormalizedLandmark>> — 첫 번째 감지 포즈 사용
                if (result.landmarks().isEmpty()) emptyList()
                else result.landmarks()[0].mapIndexed { idx, lm: NormalizedLandmark ->
                    PoseLandmark(index = idx, x = lm.x(), y = lm.y(), z = lm.z())
                }
            } catch (e: Exception) {
                Log.e(TAG, "estimateFromFrame 실패: ${e.message}")
                emptyList()
            } finally {
                mpImage.close()
                isRunning = false
            }
        }

    // ── 전체 비디오 일괄 처리 ───────────────────────────────────────────────

    override suspend fun estimateFromVideo(videoUri: String): List<Pose> =
        withContext(Dispatchers.IO) {
            val uri       = Uri.parse(videoUri)
            val extractor = MediaExtractor()
            val retriever = FFmpegMediaMetadataRetriever()
            try {
                setupExtractor(extractor, uri)
                setupRetriever(retriever, uri)

                val landmarker = videoLandmarker ?: run {
                    Log.w(TAG, "estimateFromVideo: videoLandmarker 미지원 환경, 빈 결과 반환")
                    return@withContext emptyList()
                }

                val timestamps = collectSampledTimestamps(extractor, intervalMs = 500L)
                Log.d(TAG, "estimateFromVideo: ${timestamps.size}프레임 처리 시작")

                timestamps.mapIndexedNotNull { i, pts ->
                    val bmp = retriever.getFrameAtTime(
                        pts, FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: return@mapIndexedNotNull null

                    try {
                        val result = landmarker.detectForVideo(
                            BitmapImageBuilder(bmp).build(),
                            pts / 1000L   // μs → ms
                        )
                        if (result.landmarks().isEmpty()) null
                        else Pose(
                            frameTimeMs = pts / 1000L,
                            landmarks   = result.landmarks()[0].mapIndexed { idx, lm: NormalizedLandmark ->
                                PoseLandmark(index = idx, x = lm.x(), y = lm.y(), z = lm.z())
                            }
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "프레임[$i] PTS=${pts / 1000}ms 추론 실패: ${e.message}")
                        null
                    }
                }.also { Log.d(TAG, "estimateFromVideo 완료: ${it.size}포즈 반환") }

            } catch (e: Exception) {
                Log.e(TAG, "estimateFromVideo 실패: ${e.message}")
                emptyList()
            } finally {
                extractor.release()
                retriever.release()
            }
        }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private fun setupExtractor(extractor: MediaExtractor, uri: Uri) {
        if (uri.scheme == "file") {
            extractor.setDataSource(requireNotNull(uri.path) { "null path" })
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("openFileDescriptor 실패")
            extractor.setDataSource(pfd.fileDescriptor)
            pfd.close()
        }
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            ) {
                extractor.selectTrack(i)
                return
            }
        }
        error("비디오 트랙 없음")
    }

    private fun setupRetriever(retriever: FFmpegMediaMetadataRetriever, uri: Uri) {
        if (uri.scheme == "file") {
            retriever.setDataSource(requireNotNull(uri.path) { "null path" })
        } else {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("openFileDescriptor 실패")
            retriever.setDataSource(pfd.fileDescriptor)
            pfd.close()
        }
    }

    private fun collectSampledTimestamps(
        extractor: MediaExtractor,
        intervalMs: Long
    ): List<Long> {
        val intervalUs = intervalMs * 1_000L
        val pts        = mutableListOf<Long>()
        var lastAddUs  = Long.MIN_VALUE

        while (true) {
            val sampleUs = extractor.sampleTime
            if (sampleUs < 0) break
            if (sampleUs - lastAddUs >= intervalUs) {
                pts.add(sampleUs)
                lastAddUs = sampleUs
            }
            if (!extractor.advance()) break
        }
        return pts
    }

    companion object {
        private const val TAG         = "PoseEstimatorImpl"
        private const val MODEL_ASSET = "models/pose_landmarker_lite.task"
    }
}
