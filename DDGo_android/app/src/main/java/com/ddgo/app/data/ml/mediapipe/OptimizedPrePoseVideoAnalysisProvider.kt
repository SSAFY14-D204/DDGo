package com.ddgo.app.data.ml.mediapipe

import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult
import com.ddgo.app.domain.repository.PrePoseVideoAnalysisProvider
import com.ddgo.app.feature.climbing.upload.UploadAiTraceLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptimizedPrePoseVideoAnalysisProvider @Inject constructor(
    private val optimizedAnalyzer: UploadOptimizedPrePoseVideoAnalyzer,
    private val sequentialAnalyzer: SequentialPoseVideoAnalyzer
) : PrePoseVideoAnalysisProvider {

    override suspend fun analyze(
        videoUri: String,
        analysisFpsLimit: Int
    ): PrePoseVideoAnalysisResult {
        if (!ENABLE_OPTIMIZED_UPLOAD_PREPOSE) {
            return runSequentialFallback(
                videoUri = videoUri,
                analysisFpsLimit = analysisFpsLimit
            )
        }

        val optimizedStartedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_BEGIN",
            playbackUri = videoUri,
            details = mapOf(
                "analysisFpsLimit" to analysisFpsLimit,
                "maxInferenceDimensionPx" to UploadOptimizedPrePoseVideoAnalyzer.MAX_INFERENCE_DIMENSION_PX
            )
        )

        return runCatching {
            optimizedAnalyzer.analyze(
                videoUri = videoUri,
                analysisFpsLimit = analysisFpsLimit
            )
        }.onSuccess { result ->
            UploadAiTraceLogger.log(
                event = "UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_SUCCESS",
                playbackUri = videoUri,
                elapsedMs = UploadAiTraceLogger.elapsedSince(optimizedStartedAt),
                status = "success",
                details = mapOf(
                    "poseCount" to result.poses.size,
                    "processedFrameCount" to result.processedFrames.size
                )
            )
        }.getOrElse { error ->
            UploadAiTraceLogger.log(
                event = "UPLOAD_PREPOSE_PROVIDER_OPTIMIZED_FAILED",
                playbackUri = videoUri,
                elapsedMs = UploadAiTraceLogger.elapsedSince(optimizedStartedAt),
                status = "failed",
                details = mapOf(
                    "message" to (error.message ?: error::class.simpleName)
                )
            )

            runSequentialFallback(
                videoUri = videoUri,
                analysisFpsLimit = analysisFpsLimit
            )
        }
    }

    private suspend fun runSequentialFallback(
        videoUri: String,
        analysisFpsLimit: Int
    ): PrePoseVideoAnalysisResult {
        val fallbackStartedAt = UploadAiTraceLogger.now()
        UploadAiTraceLogger.log(
            event = "UPLOAD_PREPOSE_PROVIDER_SEQUENTIAL_FALLBACK_BEGIN",
            playbackUri = videoUri,
            details = mapOf("analysisFpsLimit" to analysisFpsLimit)
        )

        return sequentialAnalyzer.analyze(
            videoUri = videoUri,
            analysisFpsLimit = analysisFpsLimit
        ).also { result ->
            UploadAiTraceLogger.log(
                event = "UPLOAD_PREPOSE_PROVIDER_SEQUENTIAL_FALLBACK_SUCCESS",
                playbackUri = videoUri,
                elapsedMs = UploadAiTraceLogger.elapsedSince(fallbackStartedAt),
                status = "success",
                details = mapOf(
                    "poseCount" to result.poses.size,
                    "processedFrameCount" to result.processedFrames.size
                )
            )
        }
    }

    companion object {
        private const val ENABLE_OPTIMIZED_UPLOAD_PREPOSE = true
    }
}
