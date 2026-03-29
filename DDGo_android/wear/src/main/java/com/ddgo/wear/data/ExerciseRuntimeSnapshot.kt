package com.ddgo.wear.data

import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchState
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseRuntimeSnapshot(
    val sessionId: String? = null,
    val watchState: WatchState = WatchState.IDLE,
    val serviceActive: Boolean = false,
    val alerting: Boolean = false,
    val sensorAvailable: Boolean = false,
    val measurementStatus: MeasurementStatus = MeasurementStatus.UNAVAILABLE,
    val latestHeartRate: Int? = null,
    val lastMeasuredAt: Long? = null,
    val aboveThresholdStartedAt: Long? = null,
    val belowThresholdStartedAt: Long? = null,
    val lastAlertTriggeredAt: Long? = null,
    val lastHapticAt: Long? = null,
    val updatedAt: Long = 0L,
    val lastReason: String? = null
)
