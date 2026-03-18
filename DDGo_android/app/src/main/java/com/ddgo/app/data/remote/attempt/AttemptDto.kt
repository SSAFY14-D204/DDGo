package com.ddgo.app.data.remote.attempt

import kotlinx.serialization.Serializable

/** 시도 시작 응답 DTO입니다. */
@Serializable
data class AttemptStartResponseDto(
    val attemptId: Long,
    val attemptNo: Int
)

/** presigned URL 발급 요청 DTO입니다. */
@Serializable
data class GenerateVideoUrlRequestDto(
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long
)

/** presigned URL 발급 응답 DTO입니다. */
@Serializable
data class GenerateVideoUrlResponseDto(
    val videoUrl: String,
    val objectKey: String
)

/**
 * 시도 종료 요청 DTO입니다.
 *
 * 규칙:
 * - 백엔드가 요구하는 중첩 구조(baseData / metricsData / feedbacksData)를 그대로 맞춥니다.
 * - 현재 프론트에서는 최소한의 종료 처리만 먼저 수행하므로, baseData만 비워서 전달할 수 있습니다.
 */
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
