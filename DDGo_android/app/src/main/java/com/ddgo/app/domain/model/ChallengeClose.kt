package com.ddgo.app.domain.model

data class ClosedChallenge(
    val challengeId: Long,
    val challengeStatus: String,
    val challengeResult: String,
    val endedAt: String?,
    val averageCenterStabilityRatio: Double?,
    val mostCruxHoldNo: Int?,
    val maxCruxDurationMs: Int?,
    val finalComment: String?
)
