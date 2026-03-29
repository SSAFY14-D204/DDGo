package com.ddgo.app.domain.model

import kotlinx.serialization.json.JsonObject

data class AiAnalysisResult(
    val mode: AiAnalysisMode,
    val requestedMode: AiAnalysisMode = mode,
    val schemaVersion: String,
    val videoMetadata: AiAnalysisVideoMetadata?,
    val timingsSeconds: Map<String, Double>,
    val correctionSummary: JsonObject?,
    val cruxResult: AiCruxResult,
    val holdStateSummary: JsonObject? = null,
    val physicsSummary: JsonObject? = null,
    val physicsPipelineBenchmarkTimingsSeconds: JsonObject? = null,
    val physicsResult: JsonObject? = null,
    val fallbackReason: AiAnalysisFallbackReason? = null,
    val rawResponse: JsonObject
)

enum class AiAnalysisFallbackReason {
    MISSING_WEIGHT,
    PHYSICS_REQUEST_FAILED
}

data class AiAnalysisVideoMetadata(
    val frameWidth: Int,
    val frameHeight: Int,
    val fps: Float? = null,
    val totalFrames: Int,
    val processedFrames: Int,
    val frameStep: Int
)

data class AiCruxResult(
    val candidateCount: Int,
    val topCandidates: List<AiCruxCandidate>,
    val allCandidates: List<AiCruxCandidate>
)

data class AiCruxCandidate(
    val holdId: Int,
    val segmentCount: Int,
    val engagementCount: Int,
    val totalActiveTimeSeconds: Double,
    val longestContinuousDwellSeconds: Double,
    val reasonTags: List<String>,
    val bestSegment: AiCruxSegment?,
    val fastCruxScore: Double? = null,
    val physicsCruxScore: Double? = null
)

data class AiCruxSegment(
    val startFrame: Int,
    val endFrame: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationSeconds: Double,
    val dominantLimbs: List<String>,
    val dominantModes: List<String>,
    val meanTotalBodyLoad: Double? = null,
    val meanCoreLoad: Double? = null,
    val meanNegativeMarginCm: Double? = null,
    val meanLoadShiftProxy: Double? = null,
    val meanConfidenceWeight: Double? = null,
    val okFraction: Double? = null,
    val segmentCruxScore: Double? = null,
    val reasonTags: List<String> = emptyList()
)
