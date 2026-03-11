package com.ddgo.app.data.ml.persondetect

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import wseemann.media.FFmpegMediaMetadataRetriever
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import com.ddgo.app.data.ml.common.TFLiteInferenceUtils
import com.ddgo.app.domain.repository.PersonDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * TFLite person_detect_v0n_320.tflite를 사용한 PersonDetector 구현체.
 *
 * 프레임 탐색 전략: MediaExtractor 순차 순회 (Python cap.read() 동일 원리)
 *   - 계산된 타임스탬프(소수점 오차 → 없는 프레임 참조 가능) 대신
 *   - MediaExtractor.advance()로 컨테이너를 순서대로 걸으며
 *     실제 존재하는 프레임 PTS만 수집 → FFmpeg에 그 PTS 전달
 *   → null 프레임 원천 차단
 */
class PersonDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PersonDetector {

    companion object {
        private const val TAG             = "PersonDetectorImpl"
        private const val MODEL_PATH      = "models/person_detect_v0n_320.tflite"
        private const val MODEL_SIZE      = 320
        private const val CONF_THRESHOLD  = 0.25f
        private const val IOU_THRESHOLD   = 0.45f

        // edge/middle 샘플링 간격 (μs 단위)
        private const val EDGE_WINDOW_US  = 3_000_000L   // 앞뒤 3초
        private const val EDGE_STEP_US    =   200_000L   // edge 구간 200ms 간격
        private const val MIDDLE_STEP_US  = 1_000_000L   // 중간 구간 1초 간격
    }

    private data class FrameCandidate(
        val timestampUs: Long,
        val personCount: Int,
        val confSum: Float
    )

    override suspend fun findBestFrameTime(videoUri: String): Long {
        Log.d(TAG, "▶ findBestFrameTime 시작")

        val uri = Uri.parse(videoUri)
        val retriever = FFmpegMediaMetadataRetriever()
        try {
            if (!setDataSource(retriever, uri)) return 0L

            val durationMs = retriever
                .extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (durationMs == null) {
                Log.e(TAG, "❌ durationMs 추출 실패")
                return 0L
            }
            Log.d(TAG, "   [정보] 길이: ${durationMs}ms")

            // ── Step 1: MediaExtractor로 실제 존재하는 프레임 PTS 수집 ──────────
            // Python cap.read()와 동일: seek 없이 컨테이너를 처음부터 순서대로 순회
            val sampleTimestampsUs = getActualSampleTimestampsUs(uri, durationMs * 1000L)
            if (sampleTimestampsUs.isEmpty()) {
                Log.e(TAG, "❌ 유효한 프레임 타임스탬프 없음")
                return 0L
            }
            Log.d(TAG, "   샘플 수: ${sampleTimestampsUs.size}개")

            // ── Step 2: 수집된 실제 PTS로 FFmpeg 프레임 추출 + person detection ─
            val interpreter = TFLiteInferenceUtils.createInterpreter(context, MODEL_PATH)
            var bestCandidate: FrameCandidate? = null
            var successFrames = 0

            for (timestampUs in sampleTimestampsUs) {
                // MediaExtractor가 반환한 실제 PTS이므로 OPTION_CLOSEST는 항상 성공해야 함
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
                    interpreter         = interpreter,
                    modelSize           = MODEL_SIZE,
                    confidenceThreshold = CONF_THRESHOLD,
                    iouThreshold        = IOU_THRESHOLD
                )
                frame.recycle()

                val personCount = detections.size
                val confSum     = detections.sumOf { it[4].toDouble() }.toFloat()
                Log.d(TAG, "   [${timestampUs / 1000}ms] 사람 ${personCount}명  confSum=${
                    String.format(Locale.US, "%.3f", confSum)}")

                if (personCount == 0) {
                    Log.d(TAG, "   🚀 Early Stop: 사람 0명 → ${timestampUs}μs")
                    interpreter.close()
                    return timestampUs
                }

                val candidate = FrameCandidate(timestampUs, personCount, confSum)
                if (bestCandidate == null || isBetter(candidate, bestCandidate!!)) {
                    bestCandidate = candidate
                }
            }

            Log.d(TAG, "   완료: 성공 프레임 ${successFrames}개")
            interpreter.close()
            return bestCandidate?.timestampUs ?: 0L

        } catch (e: Exception) {
            Log.e(TAG, "❌ findBestFrameTime 예외", e)
            return 0L
        } finally {
            retriever.release()
        }
    }

    /**
     * MediaExtractor로 컨테이너를 순서대로 순회하여
     * edge/middle 전략에 맞는 실제 프레임 PTS(μs) 목록을 반환합니다.
     *
     * Python 동작 원리:
     *   for frame_idx, ts in enumerate(container.frames):   # 순차 순회
     *       if 샘플링 조건 충족:
     *           yield ts
     *
     * MediaExtractor.advance()는 디코딩 없이 패킷 헤더만 읽으므로 매우 빠릅니다.
     */
    private fun getActualSampleTimestampsUs(uri: Uri, durationUs: Long): List<Long> {
        val extractor = MediaExtractor()
        try {
            if (!setupExtractor(extractor, uri)) return emptyList()

            // 비디오 트랙 선택
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

            val result     = mutableListOf<Long>()
            var lastAddedUs = Long.MIN_VALUE

            // 컨테이너를 처음부터 끝까지 순서대로 걷기 (디코딩 없음, 빠름)
            do {
                val ts = extractor.sampleTime
                if (ts < 0) break   // 스트림 끝

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

    private fun isBetter(new: FrameCandidate, current: FrameCandidate): Boolean {
        if (new.personCount != current.personCount) return new.personCount < current.personCount
        if (new.confSum != current.confSum) return new.confSum < current.confSum
        return new.timestampUs < current.timestampUs
    }

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
            val debugDir = File(context.getExternalFilesDir(null), "debug_frames/person")
            if (!debugDir.exists()) debugDir.mkdirs()

            val fileName = "person_frame_${String.format(Locale.US, "%06d", timestampMs)}ms.jpg"
            val file = File(debugDir, fileName)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
            Log.d(TAG, "📸 Saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Save failed: ${e.message}")
        }
    }
}
