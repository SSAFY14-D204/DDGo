package com.ddgo.app.domain.model

data class AttemptCompletionPayload(
    val attemptResult: String? = null,
    val durationMs: Int? = null,
    val maxHoldNo: Int? = null,
    val centerStabilityRatio: Double? = null,
    val cruxHoldNo: Int? = null,
    val cruxDurationMs: Int? = null,
    val dangerEventCount: Int? = null,
    val failureReason: String? = null,
    val riskAlert: String? = null,
    val nextMission: String? = null
)
