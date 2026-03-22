package com.ddgo.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class WatchState {
    IDLE,
    RECORDING,
    ALERTING,
    SENSOR_UNAVAILABLE,
    PERMISSION_BLOCKED,
    SESSION_RECOVERING
}
