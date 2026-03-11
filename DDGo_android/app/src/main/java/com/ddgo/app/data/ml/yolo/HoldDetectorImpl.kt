package com.ddgo.app.data.ml.yolo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.ddgo.app.data.mapper.VisionMapper
import com.ddgo.app.data.ml.common.TFLiteInferenceUtils
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.HoldDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import wseemann.media.FFmpegMediaMetadataRetriever
import javax.inject.Inject

/**
 * YOLO/TFLite를 사용한 HoldDetector 구현체.
 *
 * 프레임 탐색 전략: MediaExtractor 순차 순회 (Python cap.read() 동일 원리)
 *   - 계산된 타임스탬프(소수점 오차 → 없는 프레임 참조) 대신
 *   - MediaExtractor.advance()로 컨테이너를 순서대로 걸으며
 *     실제 존재하는 프레임 PTS만 수집 → FFmpegMediaMetadataRetriever에 그 PTS 전달
 *   → getFrameAtIndex null 문제 원천 차단
 *
 * 단일 패스 설계:
 *   1) 실제 PTS 목록으로 각 프레임에 person 모델 적용 → best Bitmap 보관
 *   2) 루프 종료 후 best Bitmap에 hold 모델 적용
 */
class HoldDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HoldDetector {

    companion object {
        private const val TAG = "HoldDetectorImpl"

        private const val PERSON_MODEL_PATH = "models/person_detect_v0n_320.tflite"
        private const val HOLD_MODEL_PATH   = "models/best_float32_v8_640.tflite"
        private const val PERSON_SIZE       = 320
        private const val HOLD_SIZE         = 640

        private const val CONF_THRESHOLD = 0.25f
        private const val IOU_THRESHOLD  = 0.45f

        // edge/middle 샘플링 간격 (μs 단위)
        private const val EDGE_WINDOW_US = 3_000_000L  // 앞뒤 3초
        private const val EDGE_STEP_US   =   200_000L  // edge 구간 200ms 간격
        private const val MIDDLE_STEP_US = 1_000_000L  // 중간 구간 1초 간격
    }

    override suspend fun detectFromVideo(videoUri: String): List<Hold> {
        Log.d(TAG, "▶ detectFromVideo 시작  URI: $videoUri")

        val uri          = Uri.parse(videoUri)
        val retriever    = FFmpegMediaMetadataRetriever()
        val personInterp = TFLiteInferenceUtils.createInterpreter(context, PERSON_MODEL_PATH)
        val holdInterp   = TFLiteInferenceUtils.createInterpreter(context, HOLD_MODEL_PATH)

        try {
            // URI 종류에 따라 setDataSource 분기
            //   file:// → 경로 직접 전달 (FileDescriptor는 FFmpegMediaMetadataRetriever에서 abort 유발 가능)
            //   content:// → FileDescriptor 경유 (ContentResolver 필수)
            if (!setDataSource(retriever, uri)) return emptyList()

            val durationMs = retriever
                .extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (durationMs == null) {
                Log.e(TAG, "❌ durationMs 추출 실패")
                return emptyList()
            }
            Log.d(TAG, "   영상 길이: ${durationMs}ms")

            // ── Step 1: MediaExtractor로 실제 PTS 수집 ───────────────────────────
            val sampleTimestampsUs = getActualSampleTimestampsUs(uri, durationMs * 1000L)
            if (sampleTimestampsUs.isEmpty()) {
                Log.e(TAG, "❌ 유효한 프레임 타임스탬프 없음")
                return emptyList()
            }
            Log.d(TAG, "   샘플 수: ${sampleTimestampsUs.size}개")

            // ── Step 2: 실제 PTS로 person detection → best Bitmap 탐색 ──────────
            var bestBitmap  : Bitmap? = null
            var bestCount   = Int.MAX_VALUE
            var bestConfSum = Float.MAX_VALUE
            var successFrames = 0

            for (timestampUs in sampleTimestampsUs) {
                val frame = retriever.getFrameAtTime(
                    timestampUs,
                    FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                )

                if (frame == null) {
                    Log.w(TAG, "   [${timestampUs / 1000}ms] ⚠️ 프레임 null (PTS 불일치?)")
                    continue
                }
                successFrames++
                saveDebugFrame(frame, timestampUs / 1000)

                val (detections, _) = TFLiteInferenceUtils.runInference(
                    bitmap              = frame,
                    interpreter         = personInterp,
                    modelSize           = PERSON_SIZE,
                    confidenceThreshold = CONF_THRESHOLD,
                    iouThreshold        = IOU_THRESHOLD
                )

                val count   = detections.size
                val confSum = detections.sumOf { it[4].toDouble() }.toFloat()
                Log.d(TAG, "   [${timestampUs / 1000}ms] ${frame.width}x${frame.height}" +
                    "  사람 ${count}명  confSum=${String.format("%.3f", confSum)}")

                val isBetter = count < bestCount || (count == bestCount && confSum < bestConfSum)
                if (isBetter) {
                    bestBitmap?.recycle()
                    bestBitmap  = frame
                    bestCount   = count
                    bestConfSum = confSum
                    Log.d(TAG, "   ✅ best 갱신: count=$count  confSum=${String.format("%.3f", confSum)}")
                    if (count == 0) {
                        Log.d(TAG, "   🚀 Early Stop: 사람 0명")
                        break
                    }
                } else {
                    frame.recycle()
                }
            }

            Log.d(TAG, "   person detection 완료: 성공 프레임 ${successFrames}개, bestCount=$bestCount")

            val bestFrame = bestBitmap
            if (bestFrame == null) {
                Log.e(TAG, "❌ bestBitmap null → 모든 프레임 추출 실패")
                return emptyList()
            }

            // ── Step 3: best Bitmap에 hold detection 적용 ────────────────────────
            Log.d(TAG, "▶ hold detection 시작 (CONF=${CONF_THRESHOLD})")
            val (holdDetections, _) = TFLiteInferenceUtils.runInference(
                bitmap              = bestFrame,
                interpreter         = holdInterp,
                modelSize           = HOLD_SIZE,
                confidenceThreshold = CONF_THRESHOLD,
                iouThreshold        = IOU_THRESHOLD
            )
            bestFrame.recycle()

            Log.d(TAG, "✅ 감지된 홀드 수: ${holdDetections.size}")
            if (holdDetections.isEmpty()) {
                Log.w(TAG, "⚠️ 홀드 0개 — CONF_THRESHOLD=${CONF_THRESHOLD} 너무 높거나 구도 문제")
            }

            return holdDetections.map { d ->
                VisionMapper.toHold(
                    left       = d[0],
                    top        = d[1],
                    right      = d[2],
                    bottom     = d[3],
                    confidence = d[4]
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ detectFromVideo 예외: ${e.javaClass.simpleName}", e)
            return emptyList()
        } finally {
            personInterp.close()
            holdInterp.close()
            retriever.release()
            Log.d(TAG, "   리소스 해제 완료")
        }
    }

    /**
     * MediaExtractor로 컨테이너를 순서대로 순회하여
     * edge/middle 전략에 맞는 실제 프레임 PTS(μs) 목록을 반환합니다.
     *
     * MediaExtractor.advance()는 디코딩 없이 패킷 헤더만 읽으므로 매우 빠릅니다.
     */
    private fun getActualSampleTimestampsUs(uri: Uri, durationUs: Long): List<Long> {
        val extractor = MediaExtractor()
        try {
            if (!setupExtractor(extractor, uri)) return emptyList()

            var videoTrack = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) { videoTrack = i; break }
            }
            if (videoTrack == -1) {
                Log.e(TAG, "❌ 비디오 트랙 없음")
                return emptyList()
            }
            extractor.selectTrack(videoTrack)

            val result      = mutableListOf<Long>()
            var lastAddedUs = Long.MIN_VALUE

            do {
                val ts = extractor.sampleTime
                if (ts < 0) break

                val stepUs = if (ts <= EDGE_WINDOW_US || ts >= durationUs - EDGE_WINDOW_US) {
                    EDGE_STEP_US
                } else {
                    MIDDLE_STEP_US
                }

                if (lastAddedUs == Long.MIN_VALUE || ts - lastAddedUs >= stepUs) {
                    result.add(ts)
                    lastAddedUs = ts
                }
            } while (extractor.advance())

            Log.d(TAG, "   MediaExtractor: ${result.size}개 실제 PTS 수집")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaExtractor 순회 실패: ${e.message}")
            return emptyList()
        } finally {
            extractor.release()
        }
    }

    /**
     * URI 종류에 따라 FFmpegMediaMetadataRetriever의 데이터 소스를 설정합니다.
     *   file:// → uri.path 직접 전달 (FileDescriptor 경유 시 FFmpeg native abort 발생 가능)
     *   content:// → ContentResolver를 통한 FileDescriptor 경유
     */
    private fun setDataSource(retriever: FFmpegMediaMetadataRetriever, uri: Uri): Boolean {
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
            Log.e(TAG, "❌ setDataSource 실패: ${e.message}")
            false
        }
    }

    /**
     * URI 종류에 따라 MediaExtractor의 데이터 소스를 설정합니다.
     */
    private fun setupExtractor(extractor: MediaExtractor, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: return false)
            } else {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
                extractor.setDataSource(pfd.fileDescriptor)
                pfd.close()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ MediaExtractor setDataSource 실패: ${e.message}")
            false
        }
    }

    private fun saveDebugFrame(bitmap: Bitmap, timestampMs: Long) {
        try {
            val debugDir = File(context.getExternalFilesDir(null), "debug_frames")
            if (!debugDir.exists()) debugDir.mkdirs()

            val file = File(debugDir, "frame_${String.format("%06d", timestampMs)}ms.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            Log.d(TAG, "📸 프레임 저장 완료: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 프레임 저장 실패: ${e.message}")
        }
    }
}
