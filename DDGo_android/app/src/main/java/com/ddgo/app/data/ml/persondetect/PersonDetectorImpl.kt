package com.ddgo.app.data.ml.persondetect

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import wseemann.media.FFmpegMediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
 *  1) Fast Path: 영상 첫 번째 실제 PTS를 MediaExtractor로 즉시 읽어
 *     사람이 0명이면 샘플 수집 없이 바로 반환.
 *     클라이밍 영상은 대부분 빈 벽으로 시작 → 거의 항상 여기서 종료.
 *
 *  2) FPS 기반 샘플링: FPS 메타데이터로 프레임 경계 타임스탬프를 직접 계산.
 *     O(샘플 수) — MediaExtractor 전체 순회(O(전체 프레임)) 불필요.
 *     FPS 정보가 없는 경우(VFR 등) 순차 스캔으로 폴백.
 *
 * 샘플링 간격:
 *   EDGE_WINDOW  2 s
 *   EDGE_STEP  500 ms
 *   MIDDLE_STEP  3 s
 *   → 약 27 프레임 샘플
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

        private const val EDGE_WINDOW_US  = 2_000_000L   // 앞뒤 2초
        private const val EDGE_STEP_US    =   500_000L   // edge 500ms
        private const val MIDDLE_STEP_US  = 3_000_000L   // 중간 3초
    }

    private data class FrameCandidate(
        val timestampUs: Long,
        val personCount: Int,
        val confSum: Float
    )

    /** MediaExtractor에서 비디오 트랙의 첫 PTS와 FPS를 한 번에 추출 */
    private data class VideoMetadata(val firstPtsUs: Long, val fps: Float)

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
            val durationUs = durationMs * 1000L
            Log.d(TAG, "   [정보] 길이: ${durationMs}ms")

            val interpreter = TFLiteInferenceUtils.createInterpreter(context, MODEL_PATH)

            // ── Fast Path: 첫 프레임 즉시 확인 + FPS 수집 (MediaExtractor 1회) ──
            val meta = getVideoMetadata(uri)
            val firstPts = meta.firstPtsUs
            if (firstPts >= 0L) {
                val firstFrame = retriever.getFrameAtTime(
                    firstPts, FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                )
                if (firstFrame != null) {
                    val (det, _) = TFLiteInferenceUtils.runInference(
                        bitmap              = firstFrame,
                        interpreter         = interpreter,
                        modelSize           = MODEL_SIZE,
                        confidenceThreshold = CONF_THRESHOLD,
                        iouThreshold        = IOU_THRESHOLD
                    )
                    firstFrame.recycle()
                    if (det.isEmpty()) {
                        Log.d(TAG, "⚡ Fast Path: 첫 프레임 사람 0명 → 즉시 반환 (${firstPts / 1000}ms)")
                        interpreter.close()
                        return firstPts
                    }
                    Log.d(TAG, "   Fast Path miss: 첫 프레임에 사람 ${det.size}명 → 전체 샘플링으로")
                }
            }

            // ── Step 1: FPS 기반 직접 계산 (폴백: MediaExtractor 순차 스캔) ──────
            val sampleTimestampsUs = if (meta.fps > 0f) {
                Log.d(TAG, "   FPS=${String.format(Locale.US, "%.2f", meta.fps)} → 직접 계산")
                calculateSampleTimestampsUs(durationUs, meta.fps)
            } else {
                Log.d(TAG, "   FPS 정보 없음 → MediaExtractor 순차 스캔 폴백")
                getActualSampleTimestampsUs(uri, durationUs)
            }

            if (sampleTimestampsUs.isEmpty()) {
                Log.e(TAG, "❌ 유효한 프레임 타임스탬프 없음")
                interpreter.close()
                return 0L
            }
            Log.d(TAG, "   샘플 수: ${sampleTimestampsUs.size}개")

            // ── Step 2: 프레임 추출 + person detection ────────────────────────────
            var bestCandidate: FrameCandidate? = null
            var successFrames = 0

            for (timestampUs in sampleTimestampsUs) {
                val frame = retriever.getFrameAtTime(
                    timestampUs,
                    FFmpegMediaMetadataRetriever.OPTION_CLOSEST
                )

                if (frame == null) {
                    Log.w(TAG, "   [${timestampUs / 1000}ms] ⚠️ 프레임 null (프레임 경계 불일치?)")
                    continue
                }
                successFrames++

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
     * FPS 기반 타임스탬프 직접 계산 — O(샘플 수).
     *
     * 기존 MediaExtractor 순차 스캔(O(전체 프레임 수))을 대체합니다.
     *
     * 동작 원리:
     *   frameInterval = 1_000_000μs / fps
     *   목표 타임스탬프를 가장 가까운 프레임 경계로 정렬(snapToFrame)하여
     *   OPTION_CLOSEST seek 시 null 반환을 최소화합니다.
     *
     * 예시 (30fps, 60초 영상):
     *   frameInterval = 33_333μs
     *   ts=500_000 → frameIdx=15 → alignedTs=499_995μs (실제 15번째 프레임)
     */
    private fun calculateSampleTimestampsUs(durationUs: Long, fps: Float): List<Long> {
        val frameIntervalUs = (1_000_000.0 / fps).toLong().coerceAtLeast(1L)
        val result = mutableListOf<Long>()

        // targetUs를 가장 가까운 프레임 경계에 정렬
        fun snapToFrame(targetUs: Long): Long {
            val frameIdx = (targetUs + frameIntervalUs / 2) / frameIntervalUs
            return (frameIdx * frameIntervalUs).coerceIn(0L, durationUs)
        }

        fun addIfNew(targetUs: Long) {
            val aligned = snapToFrame(targetUs)
            if (result.isEmpty() || aligned > result.last()) {
                result.add(aligned)
            }
        }

        // 시작 edge: 0 ~ EDGE_WINDOW_US (EDGE_STEP_US 간격)
        var ts = 0L
        while (ts <= EDGE_WINDOW_US.coerceAtMost(durationUs)) {
            addIfNew(ts)
            ts += EDGE_STEP_US
        }

        // 중간: EDGE_WINDOW_US+MIDDLE_STEP_US ~ durationUs-EDGE_WINDOW_US
        if (durationUs > 2 * EDGE_WINDOW_US) {
            ts = EDGE_WINDOW_US + MIDDLE_STEP_US
            val middleEnd = durationUs - EDGE_WINDOW_US
            while (ts <= middleEnd) {
                addIfNew(ts)
                ts += MIDDLE_STEP_US
            }
        }

        // 끝 edge: durationUs-EDGE_WINDOW_US ~ durationUs (EDGE_STEP_US 간격)
        if (durationUs > EDGE_WINDOW_US) {
            ts = maxOf(
                durationUs - EDGE_WINDOW_US,
                (result.lastOrNull() ?: -EDGE_STEP_US) + EDGE_STEP_US
            )
            while (ts <= durationUs) {
                addIfNew(ts)
                ts += EDGE_STEP_US
            }
        }

        Log.d(TAG, "   FPS 기반 계산: ${result.size}개 타임스탬프" +
            " (fps=${String.format(Locale.US, "%.2f", fps)}, frameInterval=${frameIntervalUs}μs)")
        return result
    }

    /**
     * MediaExtractor로 비디오 트랙의 첫 PTS(μs)와 FPS를 한 번에 추출.
     * 기존 getFirstVideoPts()를 대체하며 MediaExtractor 오픈 횟수를 1회로 줄입니다.
     * FPS를 읽을 수 없는 경우 fps=0f 반환.
     */
    private fun getVideoMetadata(uri: Uri): VideoMetadata {
        val extractor = MediaExtractor()
        return try {
            if (!setupExtractor(extractor, uri)) return VideoMetadata(-1L, 0f)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    extractor.selectTrack(i)
                    val firstPts = extractor.sampleTime.takeIf { it >= 0L } ?: -1L
                    val fps = getFpsFromFormat(format)
                    Log.d(TAG, "   [메타] firstPts=${firstPts / 1000}ms," +
                        " fps=${String.format(Locale.US, "%.2f", fps)}")
                    return VideoMetadata(firstPts, fps)
                }
            }
            VideoMetadata(-1L, 0f)
        } catch (e: Exception) {
            Log.w(TAG, "getVideoMetadata 실패: ${e.message}")
            VideoMetadata(-1L, 0f)
        } finally {
            extractor.release()
        }
    }

    /**
     * MediaFormat에서 FPS 추출.
     * KEY_FRAME_RATE는 컨테이너에 따라 Integer 또는 Float일 수 있어 양쪽 모두 시도.
     */
    private fun getFpsFromFormat(format: MediaFormat): Float {
        return try {
            format.getFloat(MediaFormat.KEY_FRAME_RATE)
        } catch (e: Exception) {
            try {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
            } catch (e2: Exception) {
                0f
            }
        }
    }

    /**
     * 폴백: MediaExtractor로 컨테이너를 순서대로 순회하여
     * edge/middle 전략에 맞는 실제 프레임 PTS(μs) 목록을 반환합니다.
     *
     * FPS 메타데이터가 없는 컨테이너(일부 VFR 영상 등)에서만 사용됩니다.
     * MediaExtractor.advance()는 디코딩 없이 패킷 헤더만 읽으므로 빠르지만,
     * 전체 프레임을 순회해야 하는 O(N) 비용이 있습니다.
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

            Log.d(TAG, "   MediaExtractor 폴백: ${result.size}개 실제 PTS 수집")
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
}
