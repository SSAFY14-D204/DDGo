package com.ddgo.app.domain.model

data class AttemptCompletionPayload(
    val attemptResult: String? = null,
    val durationMs: Int? = null,
    val maxHoldNo: Int? = null,
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
    val nextMission: String? = null,
    val insightData: AttemptInsightPayload? = null
)

data class AttemptInsightPayload(
    val videoDurationMs: Int? = null,
    val stabilityFocusFraction: Double? = null,
    val stabilityTimeline: List<AttemptStabilityPointPayload> = emptyList(),
    val heartRateSeries: List<AttemptHeartRateSamplePayload> = emptyList()
)

data class AttemptStabilityPointPayload(
    val timestampMs: Long,
    val stabilityScore: Double
)

data class AttemptHeartRateSamplePayload(
    val timestampMs: Long,
    val bpm: Int
)
