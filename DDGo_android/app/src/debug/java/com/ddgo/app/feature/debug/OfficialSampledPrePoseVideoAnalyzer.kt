package com.ddgo.app.feature.debug

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OfficialSampledPrePoseVideoAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastCaptureTimeMs: Long = -PRE_POSE_CAPTURE_INTERVAL_MS

    suspend operator fun invoke(
        videoUri: String,
        analysisFpsLimit: Int = DEFAULT_ANALYSIS_FPS_LIMIT,
        useGpuAcceleration: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Result<List<DebugPoseFrameResult>> = withContext(Dispatchers.IO) {
        runCatching {
            lastCaptureTimeMs = -PRE_POSE_CAPTURE_INTERVAL_MS
            analyzeInternal(
                uri = Uri.parse(videoUri),
                analysisFpsLimit = analysisFpsLimit,
                useGpuAcceleration = useGpuAcceleration,
                onProgress = onProgress
            )
        }
    }

    private fun analyzeInternal(
        uri: Uri,
        analysisFpsLimit: Int,
        useGpuAcceleration: Boolean,
        onProgress: (Float) -> Unit
    ): List<DebugPoseFrameResult> {
        val poseLandmarker = createDebugPoseLandmarker(
            context = context,
            useGpuAcceleration = useGpuAcceleration,
            logTag = TAG
        )
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val intervalMs = resolveOfficialSampleIntervalMs(analysisFpsLimit)

            Log.d(
                TAG,
                "Official sampled analysis started: durationMs=$durationMs, " +
                    "analysisFpsLimit=$analysisFpsLimit, intervalMs=$intervalMs"
            )

            val poses = ArrayList<DebugPoseFrameResult>()
            var sampledFrameCount = 0
            var detectedFrameCount = 0
            var timestampMs = 0L

            while (timestampMs <= durationMs) {
                if (durationMs > 0L) {
                    onProgress((timestampMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f))
                }

                val frameBitmap = retriever.getFrameAtTime(
                    timestampMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (frameBitmap == null) {
                    Log.w(TAG, "getFrameAtTime returned null at ${timestampMs}ms")
                    timestampMs += intervalMs
                    continue
                }

                sampledFrameCount++
                val argbBitmap = if (frameBitmap.config == Bitmap.Config.ARGB_8888) {
                    frameBitmap
                } else {
                    frameBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        ?: throw IllegalStateException("Failed to copy frame bitmap to ARGB_8888.")
                }

                try {
                    val detection = detectDebugPoseFrame(
                        poseLandmarker = poseLandmarker,
                        frameBitmap = argbBitmap,
                        frameTimeMs = timestampMs,
                        lastCaptureTimeMs = lastCaptureTimeMs
                    )
                    lastCaptureTimeMs = detection.updatedCaptureTimeMs
                    detection.frameResult?.let {
                        poses.add(it)
                        detectedFrameCount++
                    }
                } finally {
                    if (argbBitmap !== frameBitmap) {
                        argbBitmap.recycle()
                    }
                    frameBitmap.recycle()
                }

                timestampMs += intervalMs
            }

            onProgress(1f)
            Log.d(
                TAG,
                "Official sampled analysis complete: sampled=$sampledFrameCount, detected=$detectedFrameCount, intervalMs=$intervalMs"
            )
            return poses
        } finally {
            runCatching { retriever.release() }
            poseLandmarker.close()
        }
    }

    companion object {
        private const val TAG = "OfficialSampledPrePose"
        private const val DEFAULT_ANALYSIS_FPS_LIMIT = 30
    }
}
