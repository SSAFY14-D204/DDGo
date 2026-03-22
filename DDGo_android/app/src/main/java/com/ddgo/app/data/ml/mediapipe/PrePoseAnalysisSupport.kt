package com.ddgo.app.data.ml.mediapipe

import android.graphics.Bitmap
import android.net.Uri
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PosePixelPoint
import com.ddgo.app.domain.model.PoseWorldPoint
import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult
import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.time.Instant
import java.util.Optional

internal data class PoseCapture(
    val frame: AiPoseFrame,
    val pose: Pose?
)

internal data class PoseSequenceAnalysisResult(
    val sequence: AiPoseSequence,
    val poses: List<Pose>
)

internal fun inferPoseCaptureFromBitmap(
    poseLandmarker: PoseLandmarker,
    frameBitmap: Bitmap,
    frameTimeMs: Long,
    frameIndex: Int,
    referenceWidthPx: Int,
    referenceHeightPx: Int
): PoseCapture {
    val mpImage = BitmapImageBuilder(frameBitmap).build()

    try {
        val result = poseLandmarker.detectForVideo(mpImage, frameTimeMs)
        val landmarks = result.landmarks().firstOrNull().orEmpty()
        val worldLandmarks = result.worldLandmarks().firstOrNull().orEmpty()

        val pose = if (landmarks.isEmpty()) {
            null
        } else {
            landmarks.toPose(
                frameTimeMs = result.timestampMs(),
                frameWidthPx = referenceWidthPx,
                frameHeightPx = referenceHeightPx,
                worldLandmarks = worldLandmarks
            )
        }

        return PoseCapture(
            frame = AiPoseFrame(
                frameIndex = frameIndex,
                timestampMs = result.timestampMs(),
                poseDetected = pose != null,
                poseLandmarks = landmarks.mapIndexed { index, landmark ->
                    landmark.toAiLandmark(
                        index = index,
                        xSelector = { x() },
                        ySelector = { y() },
                        zSelector = { z() },
                        visibilitySelector = { visibility().toNullable() },
                        presenceSelector = { presence().toNullable() }
                    )
                },
                poseWorldLandmarks = worldLandmarks.mapIndexed { index, landmark ->
                    landmark.toAiLandmark(
                        index = index,
                        xSelector = { x() },
                        ySelector = { y() },
                        zSelector = { z() },
                        visibilitySelector = { visibility().toNullable() },
                        presenceSelector = { presence().toNullable() }
                    )
                }
            ),
            pose = pose
        )
    } finally {
        mpImage.close()
    }
}

internal fun buildPoseSequenceAnalysisResult(
    sourceUri: Uri,
    generator: String,
    mimeType: String?,
    frameWidth: Int,
    frameHeight: Int,
    frameRate: Int?,
    analysisFpsLimit: Int,
    rotationDegrees: Int,
    decodedFrameCount: Int,
    processedFrameCount: Int,
    skippedFrameCount: Int,
    aiFrames: List<AiPoseFrame>,
    poses: List<Pose>
): PoseSequenceAnalysisResult {
    return PoseSequenceAnalysisResult(
        sequence = AiPoseSequence(
            source = AiPayloadSource(
                videoUri = sourceUri.toString(),
                generator = generator,
                exportedAtIso = Instant.now().toString(),
                uri = sourceUri.toString(),
                displayName = sourceUri.lastPathSegment,
                mimeType = mimeType,
                path = if (sourceUri.scheme == "file") sourceUri.path else null,
                legacySourceFile = sourceUri.toString()
            ),
            videoMetadata = AiVideoMetadata(
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                fps = frameRate?.toFloat(),
                totalFrames = decodedFrameCount,
                processedFrames = processedFrameCount,
                frameStep = analysisFpsLimit.coerceAtLeast(1),
                rotationDegrees = rotationDegrees,
                mimeType = mimeType,
                analysisFpsLimit = analysisFpsLimit.coerceAtLeast(1),
                decodedFrameCount = decodedFrameCount,
                skippedFrameCount = skippedFrameCount
            ),
            frames = aiFrames
        ),
        poses = poses
    )
}

internal fun PoseSequenceAnalysisResult.toPrePoseVideoAnalysisResult(): PrePoseVideoAnalysisResult {
    return PrePoseVideoAnalysisResult(
        aiPoseSequence = sequence,
        poses = poses,
        processedFrames = sequence.frames.map { frame ->
            ProcessedPoseDetectionFrame(
                timestampMs = frame.timestampMs,
                poseDetected = frame.poseDetected
            )
        }
    )
}

internal fun emptyPrePoseAnalysisResult(
    videoUri: String,
    analysisFpsLimit: Int,
    generator: String
): PoseSequenceAnalysisResult {
    return PoseSequenceAnalysisResult(
        sequence = AiPoseSequence(
            source = AiPayloadSource(
                videoUri = videoUri,
                generator = generator,
                exportedAtIso = Instant.now().toString(),
                uri = videoUri,
                legacySourceFile = videoUri
            ),
            videoMetadata = AiVideoMetadata(
                frameWidth = 0,
                frameHeight = 0,
                fps = null,
                totalFrames = 0,
                processedFrames = 0,
                frameStep = analysisFpsLimit.coerceAtLeast(1),
                rotationDegrees = 0,
                mimeType = null,
                analysisFpsLimit = analysisFpsLimit.coerceAtLeast(1),
                decodedFrameCount = 0,
                skippedFrameCount = 0
            ),
            frames = emptyList()
        ),
        poses = emptyList()
    )
}

private fun List<NormalizedLandmark>.toPose(
    frameTimeMs: Long,
    frameWidthPx: Int,
    frameHeightPx: Int,
    worldLandmarks: List<Landmark>
): Pose = Pose(
    frameTimeMs = frameTimeMs,
    landmarks = mapIndexed { index, landmark ->
        PoseLandmark(
            index = index,
            x = landmark.x(),
            y = landmark.y(),
            z = landmark.z(),
            visibility = landmark.visibility().toNullable(),
            presence = landmark.presence().toNullable()
        )
    },
    landmarksPx = toNamedPixelMap(
        frameWidthPx = frameWidthPx,
        frameHeightPx = frameHeightPx
    ),
    worldLandmarksSample = worldLandmarks.toNamedWorldMap()
)

private fun List<NormalizedLandmark>.toNamedPixelMap(
    frameWidthPx: Int,
    frameHeightPx: Int
): Map<String, PosePixelPoint> {
    if (isEmpty()) return emptyMap()

    val points = linkedMapOf<String, PosePixelPoint>()
    POSE_DTO_LANDMARK_NAMES_BY_INDEX.forEach { (index, landmarkName) ->
        getOrNull(index)?.let { landmark ->
            points[landmarkName] = PosePixelPoint(
                x = landmark.x() * frameWidthPx.toFloat(),
                y = landmark.y() * frameHeightPx.toFloat()
            )
        }
    }
    return points
}

private fun List<Landmark>.toNamedWorldMap(): Map<String, PoseWorldPoint> {
    if (isEmpty()) return emptyMap()

    val points = linkedMapOf<String, PoseWorldPoint>()
    POSE_DTO_LANDMARK_NAMES_BY_INDEX.forEach { (index, landmarkName) ->
        getOrNull(index)?.let { landmark ->
            points[landmarkName] = PoseWorldPoint(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z()
            )
        }
    }
    return points
}

private fun Optional<Float>.toNullable(): Float? = if (isPresent) get() else null

private inline fun <T> T.toAiLandmark(
    index: Int,
    xSelector: T.() -> Float,
    ySelector: T.() -> Float,
    zSelector: T.() -> Float,
    visibilitySelector: T.() -> Float? = { null },
    presenceSelector: T.() -> Float? = { null }
): AiLandmark3D {
    return AiLandmark3D(
        index = index,
        x = xSelector(),
        y = ySelector(),
        z = zSelector(),
        visibility = visibilitySelector(),
        presence = presenceSelector()
    )
}

private val POSE_DTO_LANDMARK_NAMES_BY_INDEX = linkedMapOf(
    11 to "left_shoulder",
    12 to "right_shoulder",
    13 to "left_elbow",
    14 to "right_elbow",
    15 to "left_wrist",
    16 to "right_wrist",
    19 to "left_hand_tip",
    20 to "right_hand_tip",
    23 to "left_hip",
    24 to "right_hip",
    25 to "left_knee",
    26 to "right_knee",
    27 to "left_ankle",
    28 to "right_ankle"
)
