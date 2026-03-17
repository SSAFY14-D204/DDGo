package com.ddgo.app.data.ml.persondetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.ByteArrayOutputStream
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
 * 프레임 탐색 전략 (2단계):
 *
 *  1) Fast Path: 첫/마지막 I-frame을 즉시 확인 → 사람 0명이면 즉시 반환.
 *
 *  2-A) I-frame 충분 (≥ MIN_IFRAMES): OPTION_CLOSEST_SYNC로 각 I-frame 검사.
 *  2-B) I-frame 부족 (< MIN_IFRAMES): MediaCodec 순차 디코딩 → SAMPLE_INTERVAL_US 간격 수집.
 *       하드웨어 가속 디코더 사용, GOP 크기에 무관하게 균일한 샘플링 보장.
 */
class PersonDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PersonDetector {

    companion object {
        private const val TAG               = "PersonDetectorImpl"
        private const val MODEL_PATH        = "models/person_detect_v0n_320.tflite"
        private const val MODEL_SIZE        = 320
        private const val CONF_THRESHOLD    = 0.25f
        private const val IOU_THRESHOLD     = 0.45f

        /** 이 수 이상이면 OPTION_CLOSEST_SYNC, 미만이면 MediaCodec 순차 디코딩 */
        private const val MIN_IFRAMES       = 10
        /** MediaCodec fallback 샘플 간격 (1초) */
        private const val SAMPLE_INTERVAL_US = 1_000_000L

        /** true 로 바꾸면 모든 샘플 프레임을 DCIM/PersonDetect/ 에 저장 */
        private const val DEBUG_SAVE_FRAMES = true
    }

    private data class FrameCandidate(
        val timestampUs: Long,
        val personCount: Int,
        val confSum: Float
    )

    private data class VideoMetadata(
        val firstPtsUs: Long,
        val lastPtsUs: Long,
        val trackDurationUs: Long,
        val iFrameTimestampsUs: List<Long>
    )

    override suspend fun findBestFrameTime(videoUri: String): Long {
        Log.d(TAG, "▶ findBestFrameTime 시작")

        val uri = Uri.parse(videoUri)
        val retriever = MediaMetadataRetriever()
        try {
            if (!setDataSource(retriever, uri)) return 0L

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: run {
                    Log.e(TAG, "❌ durationMs 추출 실패"); return 0L
                }
            val durationUs = durationMs * 1000L
            Log.d(TAG, "   [정보] 길이: ${durationMs}ms")

            val interpreter = TFLiteInferenceUtils.createInterpreter(context, MODEL_PATH)
            val meta = getVideoMetadata(uri, durationUs)

            // ── Fast Path: 첫 프레임 ────────────────────────────────────────────
            val firstPts = meta.firstPtsUs
            if (firstPts >= 0L) {
                val firstFrame = retriever.getFrameAtTime(
                    firstPts, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (firstFrame != null) {
                    val (det, _) = TFLiteInferenceUtils.runInference(
                        bitmap = firstFrame, interpreter = interpreter,
                        modelSize = MODEL_SIZE, confidenceThreshold = CONF_THRESHOLD,
                        iouThreshold = IOU_THRESHOLD
                    )
                    saveDebugFrame(firstFrame, "first", firstPts / 1000, det.size)
                    firstFrame.recycle()
                    Log.d(TAG, "   [First] ${firstPts / 1000}ms → 사람 ${det.size}명")
                    if (det.isEmpty()) {
                        Log.d(TAG, "⚡ Fast Path hit: 첫 프레임")
                        interpreter.close(); return firstPts
                    }
                }
            }

            // ── Fast Path: 마지막 프레임 ─────────────────────────────────────────
            val lastPts = meta.lastPtsUs
            val lastFrame = retriever.getFrameAtTime(
                lastPts, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            if (lastFrame != null) {
                val (det, _) = TFLiteInferenceUtils.runInference(
                    bitmap = lastFrame, interpreter = interpreter,
                    modelSize = MODEL_SIZE, confidenceThreshold = CONF_THRESHOLD,
                    iouThreshold = IOU_THRESHOLD
                )
                saveDebugFrame(lastFrame, "last", lastPts / 1000, det.size)
                lastFrame.recycle()
                Log.d(TAG, "   [Last] ${lastPts / 1000}ms → 사람 ${det.size}명")
                if (det.isEmpty()) {
                    Log.d(TAG, "⚡ Fast Path hit: 마지막 프레임")
                    interpreter.close(); return lastPts
                }
            }

            // ── Step 2: 전체 샘플링 ──────────────────────────────────────────────
            val iFrames = meta.iFrameTimestampsUs
            Log.d(TAG, "   I-frame ${iFrames.size}개 → ${if (iFrames.size >= MIN_IFRAMES) "OPTION_CLOSEST_SYNC" else "MediaCodec 순차 디코딩"}")

            val sampledFrames: List<Pair<Long, Bitmap>> = if (iFrames.size >= MIN_IFRAMES) {
                // 2-A: 각 I-frame을 OPTION_CLOSEST_SYNC로 가져오기
                iFrames.mapNotNull { ts ->
                    val bmp = retriever.getFrameAtTime(ts, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (bmp != null) Pair(ts, bmp) else null
                }
            } else {
                // 2-B: MediaCodec 순차 디코딩 (GOP 크기에 무관)
                decodeAndSampleWithMediaCodec(uri, durationUs, SAMPLE_INTERVAL_US)
            }

            if (sampledFrames.isEmpty()) {
                Log.e(TAG, "❌ 샘플 프레임 없음")
                interpreter.close(); return 0L
            }
            Log.d(TAG, "   샘플 수: ${sampledFrames.size}개")

            var bestCandidate: FrameCandidate? = null

            for ((timestampUs, frame) in sampledFrames) {
                val (detections, _) = TFLiteInferenceUtils.runInference(
                    bitmap = frame, interpreter = interpreter,
                    modelSize = MODEL_SIZE, confidenceThreshold = CONF_THRESHOLD,
                    iouThreshold = IOU_THRESHOLD
                )
                val personCount = detections.size
                val confSum = detections.sumOf { it[4].toDouble() }.toFloat()
                saveDebugFrame(frame, "sample", timestampUs / 1000, personCount)
                frame.recycle()

                Log.d(TAG, "   [${timestampUs / 1000}ms] 사람 ${personCount}명  confSum=${
                    String.format(Locale.US, "%.3f", confSum)}")

                if (personCount == 0) {
                    Log.d(TAG, "   🚀 Early Stop → ${timestampUs / 1000}ms")
                    interpreter.close()
                    return timestampUs
                }
                val candidate = FrameCandidate(timestampUs, personCount, confSum)
                if (bestCandidate == null || isBetter(candidate, bestCandidate!!)) {
                    bestCandidate = candidate
                }
            }

            interpreter.close()
            return bestCandidate?.timestampUs ?: 0L

        } catch (e: Exception) {
            Log.e(TAG, "❌ findBestFrameTime 예외", e)
            return 0L
        } finally {
            retriever.close()
        }
    }

    // ── VideoMetadata 수집 ────────────────────────────────────────────────────

    private fun getVideoMetadata(uri: Uri, durationUs: Long): VideoMetadata {
        val extractor = MediaExtractor()
        return try {
            if (!setupExtractor(extractor, uri)) {
                return VideoMetadata(-1L, durationUs - 1L, durationUs, emptyList())
            }

            var videoTrack      = -1
            var firstPts        = -1L
            var trackDurationUs = -1L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime   = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    extractor.selectTrack(i)
                    firstPts        = extractor.sampleTime.takeIf { it >= 0L } ?: -1L
                    trackDurationUs = try { format.getLong(MediaFormat.KEY_DURATION) } catch (e: Exception) { -1L }
                    videoTrack      = i
                    break
                }
            }

            if (videoTrack == -1) {
                return VideoMetadata(-1L, durationUs - 1L, durationUs, emptyList())
            }

            val clampTo = if (trackDurationUs > 0L) trackDurationUs else durationUs

            // 실제 마지막 PTS
            extractor.seekTo(clampTo, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var lastPts = extractor.sampleTime.takeIf { it >= 0L } ?: (clampTo - 1L)
            while (extractor.advance()) {
                val ts = extractor.sampleTime
                if (ts < 0) break
                lastPts = ts
            }
            lastPts = lastPts.coerceAtMost(clampTo - 1L)

            // I-frame 수집 (SAMPLE_FLAG_SYNC 직접 확인)
            val iFrames = mutableListOf<Long>()
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_NEXT_SYNC)
            while (true) {
                val ts = extractor.sampleTime
                if (ts < 0) break
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    iFrames.add(ts)
                }
                if (!extractor.advance()) break
            }

            val gopAvgMs = if (iFrames.size > 1)
                (iFrames.last() - iFrames.first()) / (iFrames.size - 1) / 1000 else 0L
            Log.d(TAG, "   [메타] firstPts=${firstPts / 1000}ms, lastPts=${lastPts / 1000}ms" +
                ", trackDuration=${clampTo / 1000}ms, I-frames=${iFrames.size}개 (GOPavg≈${gopAvgMs}ms)")

            VideoMetadata(firstPts, lastPts, clampTo, iFrames)
        } catch (e: Exception) {
            Log.w(TAG, "getVideoMetadata 실패: ${e.message}")
            VideoMetadata(-1L, durationUs - 1L, durationUs, emptyList())
        } finally {
            extractor.release()
        }
    }

    // ── MediaCodec 순차 디코딩 (I-frame 부족 시 fallback) ────────────────────

    /**
     * MediaCodec 하드웨어 디코더로 영상을 순차 디코딩, intervalUs 간격으로 프레임 수집.
     * GOP 크기에 무관하게 균일한 샘플링 보장. seek 없음 → 시작부터 순서대로 디코딩.
     */
    private fun decodeAndSampleWithMediaCodec(
        uri: Uri,
        durationUs: Long,
        intervalUs: Long
    ): List<Pair<Long, Bitmap>> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var imageReader: ImageReader? = null
        val results = mutableListOf<Pair<Long, Bitmap>>()

        try {
            if (!setupExtractor(extractor, uri)) return emptyList()

            var videoTrackIdx = -1
            var videoFormat: MediaFormat? = null
            var width = 0; var height = 0; var mime = ""

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val m = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (m.startsWith("video/")) {
                    videoTrackIdx = i; videoFormat = fmt
                    width = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    height = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    mime = m; break
                }
            }
            if (videoTrackIdx == -1 || videoFormat == null) return emptyList()
            extractor.selectTrack(videoTrackIdx)

            imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(videoFormat, imageReader.surface, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone  = false
            var outputDone = false
            var nextSampleUs = 0L
            val TIMEOUT_US = 10_000L

            while (!outputDone) {
                // Input
                if (!inputDone) {
                    val inputIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIdx >= 0) {
                        val buf  = codec.getInputBuffer(inputIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Output
                val outputIdx = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputIdx >= 0) {
                    val pts           = bufferInfo.presentationTimeUs
                    val shouldCapture = pts >= nextSampleUs && bufferInfo.size > 0
                    codec.releaseOutputBuffer(outputIdx, shouldCapture)

                    if (shouldCapture) {
                        // ImageReader에서 이미지 획득 (최대 100ms 대기)
                        var image: Image? = null
                        val deadline = System.currentTimeMillis() + 100
                        while (image == null && System.currentTimeMillis() < deadline) {
                            image = imageReader.acquireLatestImage()
                            if (image == null) Thread.sleep(5)
                        }
                        if (image != null) {
                            val bitmap = imageToScaledBitmap(image)
                            image.close()
                            if (bitmap != null) {
                                results.add(Pair(pts, bitmap))
                                Log.d(TAG, "   [MediaCodec] ${pts / 1000}ms 수집")
                            }
                        } else {
                            Log.w(TAG, "   [MediaCodec] ${pts / 1000}ms 이미지 획득 타임아웃")
                        }
                        nextSampleUs += intervalUs
                    }

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec decode 실패: ${e.message}", e)
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            imageReader?.close()
            extractor.release()
        }

        Log.d(TAG, "   [MediaCodec] 완료: ${results.size}개 수집")
        return results
    }

    /**
     * YUV_420_888 Image → Bitmap 변환.
     * stride/pixelStride를 정확히 처리하여 색상 오류 방지.
     */
    private fun imageToScaledBitmap(image: Image): Bitmap? {
        return try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val w            = image.width
            val h            = image.height
            val yRowStride   = yPlane.rowStride
            val uvRowStride  = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            val nv21 = ByteArray(w * h + 2 * (w / 2) * (h / 2))
            val yBuf = yPlane.buffer
            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer

            // Y plane (stride 고려)
            for (row in 0 until h) {
                yBuf.position(row * yRowStride)
                yBuf.get(nv21, row * w, w)
            }
            // VU interleaved → NV21
            var uvIdx = w * h
            for (row in 0 until h / 2) {
                for (col in 0 until w / 2) {
                    val bufIdx = row * uvRowStride + col * uvPixelStride
                    nv21[uvIdx++] = vBuf.get(bufIdx)
                    nv21[uvIdx++] = uBuf.get(bufIdx)
                }
            }

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, w, h, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, w, h), 85, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        } catch (e: Exception) {
            Log.w(TAG, "imageToScaledBitmap 실패: ${e.message}")
            null
        }
    }

    // ── 디버그 ──────────────────────────────────────────────────────────────

    private fun saveDebugFrame(bitmap: Bitmap, label: String, timeMs: Long, personCount: Int) {
        if (!DEBUG_SAVE_FRAMES) return
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                "PersonDetect"
            ).also { it.mkdirs() }
            val file = File(dir, "${label}_${timeMs}ms_${personCount}p.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            Log.d(TAG, "   [DBG] 저장: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "   [DBG] 저장 실패: ${e.message}")
        }
    }

    // ── 유틸 ────────────────────────────────────────────────────────────────

    private fun isBetter(new: FrameCandidate, current: FrameCandidate): Boolean {
        if (new.personCount != current.personCount) return new.personCount < current.personCount
        if (new.confSum != current.confSum) return new.confSum < current.confSum
        return new.timestampUs < current.timestampUs
    }

    private fun setDataSource(retriever: MediaMetadataRetriever, uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") retriever.setDataSource(uri.path ?: return false)
            else retriever.setDataSource(context, uri)
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ setDataSource 실패: ${e.message}"); false
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
            Log.e(TAG, "❌ MediaExtractor setDataSource 실패: ${e.message}"); false
        }
    }
}
