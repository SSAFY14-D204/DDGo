package com.ddgo.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class WatchSessionStatus(
    val sessionId: String,
    val watchState: WatchState,
    val serviceActive: Boolean,
    val alerting: Boolean,
    val sensorAvailable: Boolean,
    val updatedAt: Long
)
