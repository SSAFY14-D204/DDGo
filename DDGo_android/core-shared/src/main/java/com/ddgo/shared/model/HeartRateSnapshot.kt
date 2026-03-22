package com.ddgo.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class HeartRateSnapshot(
    val heartRate: Int? = null,
    val alerting: Boolean,
    val sensorAvailable: Boolean,
    val measurementStatus: MeasurementStatus,
    val lastMeasuredAt: Long? = null,
    val updatedAt: Long
)
