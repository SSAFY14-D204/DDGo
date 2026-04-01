package com.ddgo.app.feature.debug

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ddgo.app.data.mapper.VisionMapper
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.ceil

internal const val PRE_POSE_CAPTURE_INTERVAL_MS = 5_000L

internal data class DebugPoseDetectionResult(
    val frameResult: DebugPoseFrameResult?,
    val updatedCaptureTimeMs: Long
)

internal fun createDebugPoseLandmarker(
    context: Context,
    useGpuAcceleration: Boolean,
    logTag: String
): PoseLandmarker {
    if (useGpuAcceleration) {
        val gpuResult = runCatching { createDebugPoseLandmarker(context, delegate = Delegate.GPU) }
        gpuResult.onFailure {
            Log.w(logTag, "GPU delegate unavailable. Falling back to CPU.", it)
        }
        return gpuResult.getOrElse { createDebugPoseLandmarker(context, delegate = null) }
    }

    return createDebugPoseLandmarker(context, delegate = null)
}

private fun createDebugPoseLandmarker(
    context: Context,
    delegate: Delegate?
): PoseLandmarker {
    val baseOptionsBuilder = BaseOptions.builder()
        .setModelAssetPath(PRE_POSE_MODEL_PATH)
    if (delegate != null) {
        baseOptionsBuilder.setDelegate(delegate)
    }

    val options = PoseLandmarker.PoseLandmarkerOptions.builder()
        .setBaseOptions(baseOptionsBuilder.build())
        .setRunningMode(RunningMode.VIDEO)
        .setNumPoses(1)
        .setMinPoseDetectionConfidence(0.5f)
        .setMinPosePresenceConfidence(0.5f)
        .setMinTrackingConfidence(0.5f)
        .build()

    return PoseLandmarker.createFromOptions(context, options)
}

internal fun detectDebugPoseFrame(
    poseLandmarker: PoseLandmarker,
    frameBitmap: Bitmap,
    frameTimeMs: Long,
    lastCaptureTimeMs: Long,
    imageOptions: ImageProcessingOptions? = null
): DebugPoseDetectionResult {
    val mpImage = BitmapImageBuilder(frameBitmap).build()

    try {
        val result = if (imageOptions != null) {
            poseLandmarker.detectForVideo(mpImage, imageOptions, frameTimeMs)
        } else {
            poseLandmarker.detectForVideo(mpImage, frameTimeMs)
        }
        val landmarks = result.landmarks().firstOrNull().orEmpty()
        val worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty()
        val pose = VisionMapper.toPose(
            frameTimeMs = result.timestampMs(),
            rawLandmarks = landmarks.map { landmark ->
                Triple(landmark.x(), landmark.y(), landmark.z())
            },
            visibilityValues = landmarks.map { landmark -> landmark.visibility().toNullable() },
            presenceValues = landmarks.map { landmark -> landmark.presence().toNullable() }
        )

        val currentTimestampMs = result.timestampMs()
        var updatedCaptureTimeMs = lastCaptureTimeMs
        val shouldCapture = currentTimestampMs >= lastCaptureTimeMs + PRE_POSE_CAPTURE_INTERVAL_MS
        val capturedBitmap = if (shouldCapture) {
            updatedCaptureTimeMs =
                (currentTimestampMs / PRE_POSE_CAPTURE_INTERVAL_MS) * PRE_POSE_CAPTURE_INTERVAL_MS
            Bitmap.createBitmap(frameBitmap)
        } else {
            null
        }

        val frameResult = if (landmarks.isNotEmpty() || capturedBitmap != null) {
            DebugPoseFrameResult(
                pose = pose,
                worldLandmarks = worldLandmarks.mapIndexed { index, landmark ->
                    DebugPoseWorldLandmark(
                        index = index,
                        x = landmark.x(),
                        y = landmark.y(),
                        z = landmark.z(),
                        visibility = landmark.visibility().toNullable(),
                        presence = landmark.presence().toNullable()
                    )
                },
                capturedBitmap = capturedBitmap
            )
        } else {
            null
        }

        return DebugPoseDetectionResult(
            frameResult = frameResult,
            updatedCaptureTimeMs = updatedCaptureTimeMs
        )
    } finally {
        mpImage.close()
    }
}

internal fun resolveOfficialSampleIntervalMs(analysisFpsLimit: Int): Long {
    val normalizedFps = analysisFpsLimit.coerceAtLeast(1)
    return ceil(1_000.0 / normalizedFps.toDouble()).toLong()
}

private fun java.util.Optional<Float>.toNullable(): Float? = if (isPresent) get() else null

private const val PRE_POSE_MODEL_PATH = "models/pose_landmarker_lite.task"
