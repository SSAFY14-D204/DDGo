package com.ddgo.app.data.repository

import android.util.Log
import com.ddgo.app.data.remote.ai.AiAnalysisApi
import com.ddgo.app.data.remote.ai.AiAnalysisRequestDto
import com.ddgo.app.data.remote.ai.AiAnalysisResponseDto
import com.ddgo.app.data.remote.ai.toJsonObject
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AiCruxCandidate
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiCruxSegment
import com.ddgo.app.domain.repository.AiAnalysisRepository
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

class AiAnalysisRepositoryImpl @Inject constructor(
    private val aiAnalysisApi: AiAnalysisApi
) : AiAnalysisRepository {

    override suspend fun analyze(context: AiAnalysisRequestContext): Result<AiAnalysisResult> {
        return runCatching {
            val primaryRequest = AiAnalysisRequestPayloadBuilder.buildPreparedRequest(
                context = context,
                maxFrameCount = DEFAULT_AI_REQUEST_MAX_FRAME_COUNT
            )
            val response = try {
                executeRequest(context.mode, primaryRequest.request)
            } catch (throwable: Throwable) {
                if (!throwable.isRequestEntityTooLarge()) {
                    throw throwable
                }

                val retryRequest = AiAnalysisRequestPayloadBuilder.buildPreparedRequest(
                    context = context,
                    maxFrameCount = RETRY_AI_REQUEST_MAX_FRAME_COUNT
                )
                if (retryRequest.frameCount >= primaryRequest.frameCount) {
                    throw throwable
                }

                Log.w(
                    TAG,
                    "AI request hit 413. Retrying with fewer frames: " +
                        "mode=${context.mode}, originalFrames=${context.poseSequence.frames.size}, " +
                        "primaryFrames=${primaryRequest.frameCount}, retryFrames=${retryRequest.frameCount}, " +
                        "primaryStep=${primaryRequest.frameStep}, retryStep=${retryRequest.frameStep}"
                )
                executeRequest(context.mode, retryRequest.request)
            }

            response.toDomain(mode = context.mode)
        }
    }

    private suspend fun executeRequest(
        mode: AiAnalysisMode,
        request: AiAnalysisRequestDto
    ): AiAnalysisResponseDto {
        return when (mode) {
            AiAnalysisMode.FAST -> aiAnalysisApi.analyzeFast(request)
            AiAnalysisMode.PHYSICS -> aiAnalysisApi.analyzePhysics(request)
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

    private fun Throwable.isRequestEntityTooLarge(): Boolean =
        (this as? HttpException)?.code() == REQUEST_ENTITY_TOO_LARGE

    companion object {
        private const val TAG = "AiAnalysisRepository"
        private const val REQUEST_ENTITY_TOO_LARGE = 413
    }
}
