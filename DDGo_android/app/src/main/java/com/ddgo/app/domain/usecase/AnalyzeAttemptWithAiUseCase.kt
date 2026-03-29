package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.AiAnalysisRepository
import com.ddgo.app.domain.repository.AiPoseSequenceProvider
import javax.inject.Inject

class AnalyzeAttemptWithAiUseCase @Inject constructor(
    private val aiPoseSequenceProvider: AiPoseSequenceProvider,
    private val aiAnalysisRepository: AiAnalysisRepository
) {
    suspend operator fun invoke(
        mode: AiAnalysisMode,
        videoUri: String,
        holds: List<Hold>,
        frameWidthPx: Int,
        frameHeightPx: Int,
        heightCm: Float,
        weightKg: Float?,
        wingspanCm: Float?,
        analysisFpsLimit: Int = 10,
        cachedPoseSequence: AiPoseSequence? = null,
        topKCrux: Int = 3,
        frameStep: Int = 1
    ): Result<AiAnalysisResult> {
        if (videoUri.isBlank()) {
            return Result.failure(IllegalArgumentException("Video URI is required for AI analysis."))
        }
        if (holds.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one hold is required for AI analysis."))
        }
        if (frameWidthPx <= 0 || frameHeightPx <= 0) {
            return Result.failure(IllegalArgumentException("Frame dimensions are invalid for AI analysis."))
        }
        if (heightCm <= 0f) {
            return Result.failure(IllegalArgumentException("Body profile height is required for AI analysis."))
        }
        val poseSequence = cachedPoseSequence ?: runCatching {
            aiPoseSequenceProvider.analyzePoseSequence(
                videoUri = videoUri,
                analysisFpsLimit = analysisFpsLimit
            )
        }.getOrElse { throwable ->
            return Result.failure(throwable)
        }

        val requestContext = AiAnalysisRequestContext(
            mode = mode,
            holds = holds,
            poseSequence = poseSequence,
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx,
            heightCm = heightCm,
            weightKg = weightKg,
            wingspanCm = wingspanCm,
            topKCrux = topKCrux,
            frameStep = frameStep
        )

        if (mode == AiAnalysisMode.PHYSICS && (weightKg == null || weightKg <= 0f)) {
            return aiAnalysisRepository.analyze(requestContext.copy(mode = AiAnalysisMode.FAST))
                .map { result ->
                    result.copy(
                        requestedMode = AiAnalysisMode.PHYSICS,
                        fallbackReason = AiAnalysisFallbackReason.MISSING_WEIGHT
                    )
                }
        }

        val primaryResult = aiAnalysisRepository.analyze(requestContext)
        if (mode != AiAnalysisMode.PHYSICS || primaryResult.isSuccess) {
            return primaryResult
        }

        return aiAnalysisRepository.analyze(requestContext.copy(mode = AiAnalysisMode.FAST))
            .map { result ->
                result.copy(
                    requestedMode = AiAnalysisMode.PHYSICS,
                    fallbackReason = AiAnalysisFallbackReason.PHYSICS_REQUEST_FAILED
                )
            }
    }
}
