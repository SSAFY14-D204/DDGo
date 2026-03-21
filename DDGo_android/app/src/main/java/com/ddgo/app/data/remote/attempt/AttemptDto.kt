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
data class AttemptFullResponseDto(
    val attemptId: Long,
    val attemptNo: Int,
    val attemptStatus: String,
    val attemptResult: String? = null,
    val createdAt: String,
    val durationMs: Int? = null,
    val maxHoldNo: Int? = null,
    val videoUrl: String? = null
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
    val cruxHoldNo: Int? = null,
    val cruxDurationMs: Int? = null,
    val dangerEventCount: Int? = null
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
