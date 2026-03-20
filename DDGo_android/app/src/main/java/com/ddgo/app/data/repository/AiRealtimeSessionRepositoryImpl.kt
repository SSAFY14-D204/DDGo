package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.ai.AiAnalysisResponseDto
import com.ddgo.app.data.remote.ai.AiAnalysisVideoMetadataDto
import com.ddgo.app.data.remote.ai.AiRealtimeLandmark3DDto
import com.ddgo.app.data.remote.ai.AiRealtimePoseChunkRequestDto
import com.ddgo.app.data.remote.ai.AiRealtimePoseFrameDto
import com.ddgo.app.data.remote.ai.AiRealtimeSessionAckResponseDto
import com.ddgo.app.data.remote.ai.AiRealtimeSessionApi
import com.ddgo.app.data.remote.ai.AiRealtimeSessionContextRequestDto
import com.ddgo.app.data.remote.ai.AiRealtimeSessionStartRequestDto
import com.ddgo.app.data.remote.ai.toJsonObject
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AiCruxCandidate
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiCruxSegment
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiUserBodyProfile
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.AiRealtimeSessionAck
import com.ddgo.app.domain.repository.AiRealtimeSessionContextRequest
import com.ddgo.app.domain.repository.AiRealtimeSessionHandle
import com.ddgo.app.domain.repository.AiRealtimeSessionRepository
import com.ddgo.app.domain.repository.AiRealtimeSessionStartRequest
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class AiRealtimeSessionRepositoryImpl @Inject constructor(
    private val aiRealtimeSessionApi: AiRealtimeSessionApi
) : AiRealtimeSessionRepository {

    override suspend fun startSession(
        request: AiRealtimeSessionStartRequest
    ): Result<AiRealtimeSessionHandle> = runCatching {
        val effectiveMode = resolveEffectiveMode(request)
        val response = aiRealtimeSessionApi.startSession(
            AiRealtimeSessionStartRequestDto(
                mode = effectiveMode.apiValue,
                userBodyJson = buildUserBodyJson(request.userBodyProfile),
                videoMetadata = request.videoMetadata.toDto(),
                topKCrux = request.topKCrux.coerceAtLeast(1),
                frameStep = request.frameStep.coerceAtLeast(1)
            )
        )
        val sessionId = response.sessionId.ifBlank {
            throw IllegalStateException("Realtime session start did not return a session id.")
        }
        AiRealtimeSessionHandle(
            sessionId = sessionId,
            requestedMode = request.mode,
            effectiveMode = effectiveMode
        )
    }

    override suspend fun appendPoseFrames(
        session: AiRealtimeSessionHandle,
        frames: List<AiPoseFrame>
    ): Result<AiRealtimeSessionAck> = runCatching {
        val filteredFrames = frames.filter { it.isRealtimeEligible() }
        if (filteredFrames.isEmpty()) {
            return@runCatching AiRealtimeSessionAck(
                sessionId = session.sessionId,
                acceptedFrames = 0,
                lastFrameIndex = -1,
                status = "skipped",
                message = "No valid pose frames to append."
            )
        }

        val response = aiRealtimeSessionApi.appendPoseChunks(
            sessionId = session.sessionId,
            request = AiRealtimePoseChunkRequestDto(
                frames = filteredFrames.map { frame ->
                    AiRealtimePoseFrameDto(
                        frameIndex = frame.frameIndex,
                        timestampMs = frame.timestampMs,
                        poseDetected = true,
                        poseLandmarks = frame.poseLandmarks.map { it.toDto() },
                        poseWorldLandmarks = frame.poseWorldLandmarks.map { it.toDto() }
                    )
                }
            )
        )
        response.toDomainAck()
    }

    override suspend fun attachContext(
        session: AiRealtimeSessionHandle,
        request: AiRealtimeSessionContextRequest
    ): Result<AiRealtimeSessionAck> = runCatching {
        if (request.holds.isEmpty()) {
            return@runCatching AiRealtimeSessionAck(
                sessionId = session.sessionId,
                acceptedFrames = 0,
                lastFrameIndex = -1,
                status = "skipped",
                message = "No holds to attach."
            )
        }

        val response = aiRealtimeSessionApi.attachContext(
            sessionId = session.sessionId,
            request = AiRealtimeSessionContextRequestDto(
                holdsJson = buildHoldsJson(
                    holds = request.holds,
                    frameWidthPx = request.videoMetadata.frameWidth,
                    frameHeightPx = request.videoMetadata.frameHeight
                )
            )
        )
        response.toDomainAck()
    }

    override suspend fun finalizeSession(session: AiRealtimeSessionHandle): Result<AiAnalysisResult> {
        return runCatching {
            aiRealtimeSessionApi.finalizeSession(session.sessionId).toDomain(session.effectiveMode)
        }
    }

    override suspend fun abortSession(
        session: AiRealtimeSessionHandle
    ): Result<AiRealtimeSessionAck> = runCatching {
        aiRealtimeSessionApi.abortSession(session.sessionId).toDomainAck()
    }

    private fun resolveEffectiveMode(request: AiRealtimeSessionStartRequest): AiAnalysisMode {
        if (request.mode != AiAnalysisMode.PHYSICS) return request.mode
        val weightKg = request.userBodyProfile.userProfile.weightKg?.takeIf { it > 0f }
        return if (weightKg == null) AiAnalysisMode.FAST else request.mode
    }

    private fun buildUserBodyJson(profile: AiUserBodyProfile): JsonObject {
        val heightCm = profile.userProfile.heightCm?.takeIf { it > 0f }
            ?: profile.userProfile.wingspanCm?.takeIf { it > 0f }
            ?: throw IllegalArgumentException("Height or wingspan is required for realtime AI analysis.")
        val wingspanCm = profile.userProfile.wingspanCm?.takeIf { it > 0f } ?: heightCm
        val weightKg = profile.userProfile.weightKg?.takeIf { it > 0f } ?: 0f

        val heightM = heightCm / 100f
        val wingspanM = wingspanCm / 100f
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
                    put("weight_kg", JsonPrimitive(weightKg))
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

    private fun AiRealtimeSessionAckResponseDto.toDomainAck(): AiRealtimeSessionAck {
        return AiRealtimeSessionAck(
            sessionId = sessionId,
            acceptedFrames = acceptedFrames,
            lastFrameIndex = lastFrameIndex,
            status = status,
            message = message
        )
    }

    private fun AiAnalysisResponseDto.toDomain(mode: AiAnalysisMode): AiAnalysisResult {
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

    private fun AiPoseFrame.isRealtimeEligible(): Boolean {
        return poseDetected &&
            poseLandmarks.size >= MIN_REQUIRED_LANDMARK_COUNT &&
            poseWorldLandmarks.size >= MIN_REQUIRED_LANDMARK_COUNT
    }

    private fun AiLandmark3D.toDto(): AiRealtimeLandmark3DDto {
        return AiRealtimeLandmark3DDto(
            index = index,
            x = x,
            y = y,
            z = z,
            visibility = visibility,
            presence = presence
        )
    }

    private fun AiVideoMetadata.toDto(): AiAnalysisVideoMetadataDto {
        return AiAnalysisVideoMetadataDto(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            fps = fps ?: 0f,
            totalFrames = totalFrames,
            processedFrames = processedFrames,
            frameStep = frameStep
        )
    }

    companion object {
        private const val MIN_REQUIRED_LANDMARK_COUNT = 33
    }
}
