package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.ai.AiAnalysisRequestDto
import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.Hold
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal data class PreparedAiAnalysisRequest(
    val request: AiAnalysisRequestDto,
    val frameCount: Int,
    val frameStep: Int
)

internal const val PRIMARY_AI_REQUEST_MAX_FRAME_COUNT = Int.MAX_VALUE
internal const val DEFAULT_AI_REQUEST_MAX_FRAME_COUNT = 90
internal const val RETRY_AI_REQUEST_MAX_FRAME_COUNT = 48

internal object AiAnalysisRequestPayloadBuilder {
    fun buildPreparedRequest(
        context: AiAnalysisRequestContext,
        maxFrameCount: Int
    ): PreparedAiAnalysisRequest {
        val filteredPoseSequence = context.poseSequence.filterValidRequestFrames()
        require(filteredPoseSequence.frames.isNotEmpty()) {
            "No valid pose-detected frames with complete landmarks available for AI analysis."
        }

        val effectiveFrameStep = resolveEffectiveFrameStep(
            totalFrameCount = filteredPoseSequence.frames.size,
            baseFrameStep = context.frameStep,
            maxFrameCount = maxFrameCount
        )
        val sampledPoseSequence = filteredPoseSequence.sampleFrames(effectiveFrameStep)

        return PreparedAiAnalysisRequest(
            request = AiAnalysisRequestDto(
                holdsJson = buildHoldsJson(
                    holds = context.holds,
                    frameWidthPx = context.frameWidthPx,
                    frameHeightPx = context.frameHeightPx
                ),
                pose3dSequenceJson = buildPoseSequenceJson(sampledPoseSequence),
                userBodyJson = buildUserBodyJson(context),
                topKCrux = context.topKCrux,
                frameStep = effectiveFrameStep
            ),
            frameCount = sampledPoseSequence.frames.size,
            frameStep = effectiveFrameStep
        )
    }

    private fun resolveEffectiveFrameStep(
        totalFrameCount: Int,
        baseFrameStep: Int,
        maxFrameCount: Int
    ): Int {
        val normalizedBaseStep = baseFrameStep.coerceAtLeast(1)
        val normalizedMaxFrameCount = maxFrameCount.coerceAtLeast(1)
        if (totalFrameCount <= normalizedMaxFrameCount) {
            return normalizedBaseStep
        }

        val requiredStride = ((totalFrameCount - 1) / normalizedMaxFrameCount) + 1
        val strideMultiplier = ((requiredStride - 1) / normalizedBaseStep) + 1
        return normalizedBaseStep * strideMultiplier
    }

    private fun buildHoldsJson(
        holds: List<Hold>,
        frameWidthPx: Int,
        frameHeightPx: Int
    ): JsonObject = buildJsonObject {
        put(
            "source",
            buildJsonObject {
                put("origin", JsonPrimitive("android_hold_selection"))
                put("frame_width_px", JsonPrimitive(frameWidthPx))
                put("frame_height_px", JsonPrimitive(frameHeightPx))
            }
        )
        put(
            "holds",
            buildJsonArray {
                holds.forEachIndexed { index, hold ->
                    add(
                        buildJsonObject {
                            put("hold_id", JsonPrimitive(hold.holdNo.takeIf { it > 0 } ?: (index + 1)))
                            put("confidence", JsonPrimitive(hold.confidence))
                            put(
                                "bbox_px",
                                buildJsonObject {
                                    put("x1", JsonPrimitive((hold.boundingBox.left * frameWidthPx).coerceIn(0f, frameWidthPx.toFloat())))
                                    put("y1", JsonPrimitive((hold.boundingBox.top * frameHeightPx).coerceIn(0f, frameHeightPx.toFloat())))
                                    put("x2", JsonPrimitive((hold.boundingBox.right * frameWidthPx).coerceIn(0f, frameWidthPx.toFloat())))
                                    put("y2", JsonPrimitive((hold.boundingBox.bottom * frameHeightPx).coerceIn(0f, frameHeightPx.toFloat())))
                                }
                            )
                            put(
                                "polygon_px",
                                buildJsonArray {
                                    hold.polygon.forEach { point ->
                                        add(
                                            buildJsonObject {
                                                put("x", JsonPrimitive((point.x * frameWidthPx).coerceIn(0f, frameWidthPx.toFloat())))
                                                put("y", JsonPrimitive((point.y * frameHeightPx).coerceIn(0f, frameHeightPx.toFloat())))
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }
        )
    }

    private fun buildPoseSequenceJson(
        poseSequence: AiPoseSequence
    ): JsonObject = buildJsonObject {
        put(
            "source",
            buildJsonObject {
                put("video_uri", JsonPrimitive(poseSequence.source.videoUri))
                put("generator", JsonPrimitive(poseSequence.source.generator))
                put("exported_at", JsonPrimitive(poseSequence.source.exportedAtIso))
            }
        )
        put(
            "video_metadata",
            buildJsonObject {
                put("frame_width", JsonPrimitive(poseSequence.videoMetadata.frameWidth))
                put("frame_height", JsonPrimitive(poseSequence.videoMetadata.frameHeight))
                poseSequence.videoMetadata.fps?.let { fps ->
                    put("fps", JsonPrimitive(fps))
                }
                put("total_frames", JsonPrimitive(poseSequence.videoMetadata.totalFrames))
                put("processed_frames", JsonPrimitive(poseSequence.videoMetadata.processedFrames))
                put("analysis_fps_limit", JsonPrimitive(poseSequence.videoMetadata.analysisFpsLimit))
            }
        )
        put(
            "frames",
            JsonArray(
                poseSequence.frames.map { frame ->
                    buildJsonObject {
                        put("frame_index", JsonPrimitive(frame.frameIndex))
                        put("timestamp_ms", JsonPrimitive(frame.timestampMs))
                        put("pose_detected", JsonPrimitive(frame.poseDetected))
                        put("pose_landmarks", buildLandmarksJson(frame.poseLandmarks))
                        put("pose_world_landmarks", buildLandmarksJson(frame.poseWorldLandmarks))
                    }
                }
            )
        )
    }

    private fun buildLandmarksJson(
        landmarks: List<AiLandmark3D>
    ): JsonArray = buildJsonArray {
        landmarks.forEach { landmark ->
            add(
                buildJsonObject {
                    put("index", JsonPrimitive(landmark.index))
                    put("x", JsonPrimitive(landmark.x))
                    put("y", JsonPrimitive(landmark.y))
                    put("z", JsonPrimitive(landmark.z))
                    landmark.visibility?.let { put("visibility", JsonPrimitive(it)) }
                    landmark.presence?.let { put("presence", JsonPrimitive(it)) }
                }
            )
        }
    }

    private fun buildUserBodyJson(
        context: AiAnalysisRequestContext
    ): JsonObject {
        val heightCm = context.heightCm.takeIf { it > 0f }
        val wingspanCm = context.wingspanCm?.takeIf { it > 0f } ?: heightCm
        val resolvedHeightCm = heightCm ?: wingspanCm
            ?: throw IllegalArgumentException("Height or wingspan is required for AI analysis.")
        val resolvedWingspanCm = wingspanCm ?: resolvedHeightCm

        val heightM = resolvedHeightCm / 100f
        val wingspanM = resolvedWingspanCm / 100f
        val armScale = wingspanM / 1.75f
        val legScale = heightM / 1.75f
        val upperArm = 0.3118f * armScale
        val forearm = 0.3118f * armScale
        val thigh = 0.4001f * legScale
        val shin = 0.39f * legScale
        val shoulderWidth = 0.34f * armScale

        return buildJsonObject {
            put(
                "user_profile",
                buildJsonObject {
                    put("height_m", JsonPrimitive(heightM))
                    put("weight_kg", JsonPrimitive((context.weightKg ?: 0f).coerceAtLeast(0f)))
                    put("wingspan_m", JsonPrimitive(wingspanM))
                }
            )
            put(
                "calibration_compat",
                buildJsonObject {
                    put("upper_arm_m", JsonPrimitive(upperArm))
                    put("forearm_m", JsonPrimitive(forearm))
                    put("thigh_m", JsonPrimitive(thigh))
                    put("shin_m", JsonPrimitive(shin))
                    put("shoulder_width_m", JsonPrimitive(shoulderWidth))
                    put("wingspan_m", JsonPrimitive(wingspanM))
                    put("left_upper_arm_m", JsonPrimitive(upperArm))
                    put("right_upper_arm_m", JsonPrimitive(upperArm))
                    put("left_forearm_m", JsonPrimitive(forearm))
                    put("right_forearm_m", JsonPrimitive(forearm))
                    put("left_thigh_m", JsonPrimitive(thigh))
                    put("right_thigh_m", JsonPrimitive(thigh))
                    put("left_shin_m", JsonPrimitive(shin))
                    put("right_shin_m", JsonPrimitive(shin))
                }
            )
        }
    }

    private fun AiPoseSequence.sampleFrames(frameStep: Int): AiPoseSequence {
        val normalizedStep = frameStep.coerceAtLeast(1)
        val sampledFrames = if (normalizedStep == 1) {
            frames
        } else {
            frames.filter { frame -> frame.frameIndex % normalizedStep == 0 }
                .ifEmpty { frames.firstOrNull()?.let(::listOf).orEmpty() }
        }

        return copy(
            videoMetadata = videoMetadata.copy(processedFrames = sampledFrames.size),
            frames = sampledFrames
        )
    }

    private fun AiPoseSequence.filterValidRequestFrames(): AiPoseSequence {
        val validFrames = frames.filter { frame ->
            frame.poseDetected &&
                frame.poseLandmarks.size >= MIN_REQUIRED_LANDMARK_COUNT &&
                frame.poseWorldLandmarks.size >= MIN_REQUIRED_LANDMARK_COUNT
        }

        return copy(
            videoMetadata = videoMetadata.copy(processedFrames = validFrames.size),
            frames = validFrames
        )
    }

    private const val MIN_REQUIRED_LANDMARK_COUNT = 33
}
