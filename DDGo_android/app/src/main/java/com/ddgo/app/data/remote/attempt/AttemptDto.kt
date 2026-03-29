package com.ddgo.app.data.remote.attempt

import kotlinx.serialization.Serializable

@Serializable
data class AttemptStartResponseDto(
    val attemptId: Long,
    val attemptNo: Int
)

@Serializable
data class AttemptDetailResponseDto(
    val attemptId: Long,
    val attemptNo: Int,
    val attemptStatus: String,
    val attemptResult: String? = null,
    val createdAt: String,
    val durationMs: Int? = null,
    val maxHoldNo: Int? = null
)

@Serializable
data class AttemptListResponseDto(
    val challengeId: Long,
    val attempts: List<AttemptDetailResponseDto> = emptyList()
)

@Serializable
data class GenerateVideoUrlRequestDto(
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long
)

@Serializable
data class GenerateVideoUrlResponseDto(
    val videoUrl: String,
    val objectKey: String
)

@Serializable
data class VideoUploadCompleteRequestDto(
    val etag: String? = null
)

@Serializable
data class VideoUploadCompleteResponseDto(
    val attemptId: Long,
    val uploaded: Boolean? = null,
    val isUploaded: Boolean? = null,
    val attemptStatus: String,
    val uploadedAt: String? = null
) {
    fun isUploadConfirmed(): Boolean = uploaded ?: isUploaded ?: false
}

@Serializable
data class AttemptFullResponseDto(
    val attemptId: Long,
    val attemptNo: Int,
    val attemptStatus: String,
    val attemptResult: String? = null,
    val createdAt: String,
    val durationMs: Int? = null,
    val maxHoldNo: Int? = null,
    val videoUrl: String? = null,
    val metricsData: AttemptMetricsResponseDto? = null,
    val feedbacksData: AttemptFeedbacksResponseDto? = null,
    val centerStabilityRatio: Double? = null,
    val stabilityRecoveryScore: Int? = null,
    val stableContactRatio: Double? = null,
    val lowerBodyDriveScore: Int? = null,
    val overallMovementScore: Int? = null,
    val cruxHoldNo: Int? = null,
    val cruxDurationMs: Int? = null,
    val dangerEventCount: Int? = null,
    val loadFocusLabel: String? = null,
    val failureReason: String? = null,
    val riskAlert: String? = null,
    val nextMission: String? = null
)

@Serializable
data class AttemptMetricsResponseDto(
    val centerStabilityRatio: Double? = null,
    val stabilityRecoveryScore: Int? = null,
    val stableContactRatio: Double? = null,
    val lowerBodyDriveScore: Int? = null,
    val overallMovementScore: Int? = null,
    val cruxHoldNo: Int? = null,
    val cruxDurationMs: Int? = null,
    val dangerEventCount: Int? = null,
    val loadFocusLabel: String? = null
)

@Serializable
data class AttemptFeedbacksResponseDto(
    val failureReason: String? = null,
    val riskAlert: String? = null,
    val nextMission: String? = null
)

@Serializable
data class AttemptEndBaseDataDto(
    val attemptResult: String? = null,
    val durationMs: Int? = null,
    val maxHoldNo: Int? = null
)

@Serializable
data class AttemptEndMetricsDataDto(
    val centerStabilityRatio: Double? = null,
    val stabilityRecoveryScore: Int? = null,
    val stableContactRatio: Double? = null,
    val lowerBodyDriveScore: Int? = null,
    val overallMovementScore: Int? = null,
    val cruxHoldNo: Int? = null,
    val cruxDurationMs: Int? = null,
    val dangerEventCount: Int? = null,
    val loadFocusLabel: String? = null
)

@Serializable
data class AttemptEndFeedbacksDataDto(
    val failureReason: String? = null,
    val riskAlert: String? = null,
    val nextMission: String? = null
)

@Serializable
data class AttemptEndRequestDto(
    val baseData: AttemptEndBaseDataDto? = null,
    val metricsData: AttemptEndMetricsDataDto? = null,
    val feedbacksData: AttemptEndFeedbacksDataDto? = null
)
