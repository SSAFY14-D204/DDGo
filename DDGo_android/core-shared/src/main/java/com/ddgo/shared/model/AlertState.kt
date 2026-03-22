package com.ddgo.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AlertState(
    val sessionId: String,
    val alerting: Boolean,
    val triggeredAt: Long,
    val reason: AlertReason
)
