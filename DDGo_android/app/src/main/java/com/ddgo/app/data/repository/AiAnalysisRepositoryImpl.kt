package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.ai.AiAnalysisApi
import com.ddgo.app.data.remote.ai.AiAnalysisRequestDto
import com.ddgo.app.data.remote.ai.AiAnalysisResponseDto
import com.ddgo.app.data.remote.ai.toJsonObject
import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AiCruxCandidate
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiCruxSegment
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.repository.AiAnalysisRepository
import com.ddgo.app.domain.model.Hold
import javax.inject.Inject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class AiAnalysisRepositoryImpl @Inject constructor(
    private val aiAnalysisApi: AiAnalysisApi
) : AiAnalysisRepository {

    override suspend fun analyze(context: AiAnalysisRequestContext): Result<AiAnalysisResult> {
        return runCatching {
            val request = AiAnalysisRequestDto(
                holdsJson = buildHoldsJson(
                    holds = context.holds,
                    frameWidthPx = context.frameWidthPx,
                    frameHeightPx = context.frameHeightPx
                ),
                pose3dSequenceJson = buildPoseSequenceJson(context),
                userBodyJson = buildUserBodyJson(context),
                topKCrux = context.topKCrux,
                frameStep = context.frameStep
            )

            val response = when (context.mode) {
                AiAnalysisMode.FAST -> aiAnalysisApi.analyzeFast(request)
                AiAnalysisMode.PHYSICS -> aiAnalysisApi.analyzePhysics(request)
            }

            response.toDomain(mode = context.mode)
        }
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
        context: AiAnalysisRequestContext
    ): JsonObject = buildJsonObject {
        put(
            "source",
            buildJsonObject {
                put("video_uri", JsonPrimitive(context.poseSequence.source.videoUri))
                put("generator", JsonPrimitive(context.poseSequence.source.generator))
                put("exported_at", JsonPrimitive(context.poseSequence.source.exportedAtIso))
            }
        )
        put(
            "video_metadata",
            buildJsonObject {
                put("frame_width", JsonPrimitive(context.poseSequence.videoMetadata.frameWidth))
                put("frame_height", JsonPrimitive(context.poseSequence.videoMetadata.frameHeight))
                context.poseSequence.videoMetadata.fps?.let { fps ->
                    put("fps", JsonPrimitive(fps))
                }
                put("total_frames", JsonPrimitive(context.poseSequence.videoMetadata.totalFrames))
                put("processed_frames", JsonPrimitive(context.poseSequence.videoMetadata.processedFrames))
                put("analysis_fps_limit", JsonPrimitive(context.poseSequence.videoMetadata.analysisFpsLimit))
            }
        )
        put(
            "frames",
            JsonArray(
                context.poseSequence.frames.map { frame ->
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

    private fun AiAnalysisResponseDto.toDomain(
        mode: AiAnalysisMode
    ): AiAnalysisResult {
        return AiAnalysisResult(
            mode = mode,
            schemaVersion = schemaVersion,
            videoMetadata = videoMetadata?.let { metadata ->
                AiAnalysisVideoMetadata(
                    frameWidth = metadata.frameWidth,
                    frameHeight = metadata.frameHeight,
                    fps = metadata.fps,
                    totalFrames = metadata.totalFrames,
                    processedFrames = metadata.processedFrames,
                    frameStep = metadata.frameStep
                )
            },
            timingsSeconds = timingsSeconds,
            correctionSummary = correctionSummary,
            cruxResult = AiCruxResult(
                candidateCount = cruxResult.candidateCount,
                topCandidates = cruxResult.topCandidates.map { it.toDomain() },
                allCandidates = cruxResult.allCandidates.map { it.toDomain() }
            ),
            holdStateSummary = holdStateSummary,
            physicsSummary = physicsSummary,
            physicsPipelineBenchmarkTimingsSeconds = physicsPipelineBenchmarkTimingsSeconds,
            physicsResult = physicsResult,
            rawResponse = toJsonObject()
        )
    }

    private fun com.ddgo.app.data.remote.ai.AiCruxCandidateDto.toDomain(): AiCruxCandidate {
        return AiCruxCandidate(
            holdId = holdId,
            segmentCount = segmentCount,
            engagementCount = engagementCount,
            totalActiveTimeSeconds = totalActiveTimeSeconds,
            longestContinuousDwellSeconds = longestContinuousDwellSeconds,
            reasonTags = reasonTags,
            bestSegment = bestSegment?.toDomain(),
            fastCruxScore = fastCruxScore,
            physicsCruxScore = physicsCruxScore
        )
    }

    private fun com.ddgo.app.data.remote.ai.AiCruxSegmentDto.toDomain(): AiCruxSegment {
        return AiCruxSegment(
            startFrame = startFrame,
            endFrame = endFrame,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationSeconds = durationSeconds,
            dominantLimbs = dominantLimbs,
            dominantModes = dominantModes,
            meanTotalBodyLoad = meanTotalBodyLoad,
            meanCoreLoad = meanCoreLoad,
            meanNegativeMarginCm = meanNegativeMarginCm,
            meanLoadShiftProxy = meanLoadShiftProxy,
            meanConfidenceWeight = meanConfidenceWeight,
            okFraction = okFraction,
            segmentCruxScore = segmentCruxScore,
            reasonTags = reasonTags
        )
    }
}
