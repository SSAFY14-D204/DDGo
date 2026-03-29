package com.ddgo.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class MeasurementStatus {
    MEASURING,
    UNAVAILABLE,
    PERMISSION_BLOCKED,
    RECOVERING
}
