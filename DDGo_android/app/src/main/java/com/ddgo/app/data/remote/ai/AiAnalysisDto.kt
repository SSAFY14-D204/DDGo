package com.ddgo.app.data.remote.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class AiAnalysisRequestDto(
    @SerialName("holds_json") val holdsJson: JsonObject,
    @SerialName("pose3d_sequence_json") val pose3dSequenceJson: JsonObject,
    @SerialName("user_body_json") val userBodyJson: JsonObject,
    @SerialName("top_k_crux") val topKCrux: Int = 3,
    @SerialName("frame_step") val frameStep: Int = 1
)

@Serializable
data class AiAnalysisResponseDto(
    @SerialName("schema_version") val schemaVersion: String = "",
    @SerialName("mode") val mode: String = "",
    @SerialName("video_metadata") val videoMetadata: AiAnalysisVideoMetadataDto? = null,
    @SerialName("timings_s") val timingsSeconds: Map<String, Double> = emptyMap(),
    @SerialName("correction_summary") val correctionSummary: JsonObject? = null,
    @SerialName("hold_state_summary") val holdStateSummary: JsonObject? = null,
    @SerialName("physics_summary") val physicsSummary: JsonObject? = null,
    @SerialName("physics_pipeline_benchmark_timings_s")
    val physicsPipelineBenchmarkTimingsSeconds: JsonObject? = null,
    @SerialName("crux_result") val cruxResult: AiCruxResultDto = AiCruxResultDto(),
    @SerialName("physics_result") val physicsResult: JsonObject? = null
)

@Serializable
data class AiAnalysisVideoMetadataDto(
    @SerialName("frame_width") val frameWidth: Int = 0,
    @SerialName("frame_height") val frameHeight: Int = 0,
    @SerialName("fps") val fps: Float = 0f,
    @SerialName("total_frames") val totalFrames: Int = 0,
    @SerialName("processed_frames") val processedFrames: Int = 0,
    @SerialName("frame_step") val frameStep: Int = 1
)

@Serializable
data class AiCruxResultDto(
    @SerialName("candidate_count") val candidateCount: Int = 0,
    @SerialName("top_candidates") val topCandidates: List<AiCruxCandidateDto> = emptyList(),
    @SerialName("all_candidates") val allCandidates: List<AiCruxCandidateDto> = emptyList()
)

@Serializable
data class AiCruxCandidateDto(
    @SerialName("hold_id") val holdId: Int = 0,
    @SerialName("segment_count") val segmentCount: Int = 0,
    @SerialName("engagement_count") val engagementCount: Int = 0,
    @SerialName("total_active_time_s") val totalActiveTimeSeconds: Double = 0.0,
    @SerialName("longest_continuous_dwell_s") val longestContinuousDwellSeconds: Double = 0.0,
    @SerialName("reason_tags") val reasonTags: List<String> = emptyList(),
    @SerialName("best_segment") val bestSegment: AiCruxSegmentDto? = null,
    @SerialName("fast_crux_score") val fastCruxScore: Double? = null,
    @SerialName("physics_crux_score") val physicsCruxScore: Double? = null
)

@Serializable
data class AiCruxSegmentDto(
    @SerialName("start_frame") val startFrame: Int = 0,
    @SerialName("end_frame") val endFrame: Int = 0,
    @SerialName("start_time_ms") val startTimeMs: Long = 0L,
    @SerialName("end_time_ms") val endTimeMs: Long = 0L,
    @SerialName("duration_s") val durationSeconds: Double = 0.0,
    @SerialName("dominant_limbs") val dominantLimbs: List<String> = emptyList(),
    @SerialName("dominant_modes") val dominantModes: List<String> = emptyList(),
    @SerialName("mean_total_body_load") val meanTotalBodyLoad: Double? = null,
    @SerialName("mean_core_load") val meanCoreLoad: Double? = null,
    @SerialName("mean_negative_margin_cm") val meanNegativeMarginCm: Double? = null,
    @SerialName("mean_load_shift_proxy") val meanLoadShiftProxy: Double? = null,
    @SerialName("mean_confidence_weight") val meanConfidenceWeight: Double? = null,
    @SerialName("ok_fraction") val okFraction: Double? = null,
    @SerialName("segment_crux_score") val segmentCruxScore: Double? = null,
    @SerialName("reason_tags") val reasonTags: List<String> = emptyList()
)

internal fun AiAnalysisResponseDto.toJsonObject(): JsonObject {
    val content = buildMap<String, JsonElement> {
        put("schema_version", kotlinx.serialization.json.JsonPrimitive(schemaVersion))
        put("mode", kotlinx.serialization.json.JsonPrimitive(mode))
        videoMetadata?.let { metadata ->
            put(
                "video_metadata",
                kotlinx.serialization.json.buildJsonObject {
                    put("frame_width", kotlinx.serialization.json.JsonPrimitive(metadata.frameWidth))
                    put("frame_height", kotlinx.serialization.json.JsonPrimitive(metadata.frameHeight))
                    put("fps", kotlinx.serialization.json.JsonPrimitive(metadata.fps))
                    put("total_frames", kotlinx.serialization.json.JsonPrimitive(metadata.totalFrames))
                    put("processed_frames", kotlinx.serialization.json.JsonPrimitive(metadata.processedFrames))
                    put("frame_step", kotlinx.serialization.json.JsonPrimitive(metadata.frameStep))
                }
            )
        }
        put(
            "timings_s",
            kotlinx.serialization.json.buildJsonObject {
                timingsSeconds.forEach { (key, value) ->
                    put(key, kotlinx.serialization.json.JsonPrimitive(value))
                }
            }
        )
        correctionSummary?.let { put("correction_summary", it) }
        holdStateSummary?.let { put("hold_state_summary", it) }
        physicsSummary?.let { put("physics_summary", it) }
        physicsPipelineBenchmarkTimingsSeconds?.let {
            put("physics_pipeline_benchmark_timings_s", it)
        }
        physicsResult?.let { put("physics_result", it) }
    }
    return JsonObject(content)
}
