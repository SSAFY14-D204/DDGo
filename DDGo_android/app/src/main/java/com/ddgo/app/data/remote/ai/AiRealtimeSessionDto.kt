package com.ddgo.app.data.remote.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AiRealtimeSessionStartRequestDto(
    @SerialName("mode") val mode: String,
    @SerialName("user_body_json") val userBodyJson: JsonObject,
    @SerialName("video_metadata") val videoMetadata: AiAnalysisVideoMetadataDto,
    @SerialName("top_k_crux") val topKCrux: Int = 3,
    @SerialName("frame_step") val frameStep: Int = 1
)

@Serializable
data class AiRealtimePoseChunkRequestDto(
    @SerialName("frames") val frames: List<AiRealtimePoseFrameDto> = emptyList()
)

@Serializable
data class AiRealtimeSessionContextRequestDto(
    @SerialName("holds_json") val holdsJson: JsonObject
)

@Serializable
data class AiRealtimeSessionAckResponseDto(
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("accepted_frame_count") val acceptedFrames: Int = 0,
    @SerialName("last_frame_index") val lastFrameIndex: Int = -1,
    @SerialName("status") val status: String = "",
    @SerialName("message") val message: String? = null,
    @SerialName("mode") val mode: String? = null
)

@Serializable
data class AiRealtimePoseFrameDto(
    @SerialName("frame_index") val frameIndex: Int,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("pose_detected") val poseDetected: Boolean,
    @SerialName("pose_landmarks") val poseLandmarks: List<AiRealtimeLandmark3DDto>,
    @SerialName("pose_world_landmarks") val poseWorldLandmarks: List<AiRealtimeLandmark3DDto>
)

@Serializable
data class AiRealtimeLandmark3DDto(
    @SerialName("index") val index: Int,
    @SerialName("x") val x: Float,
    @SerialName("y") val y: Float,
    @SerialName("z") val z: Float,
    @SerialName("visibility") val visibility: Float? = null,
    @SerialName("presence") val presence: Float? = null
)
