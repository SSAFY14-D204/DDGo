package com.ddgo.shared.config

import kotlinx.serialization.Serializable

@Serializable
data class AlertConfig(
    val heartRateThreshold: Int = DEFAULT_HEART_RATE_THRESHOLD,
    val sustainMillis: Long = DEFAULT_SUSTAIN_MILLIS,
    val clearBelowThresholdMillis: Long = DEFAULT_CLEAR_BELOW_THRESHOLD_MILLIS,
    val hapticCooldownMillis: Long = DEFAULT_HAPTIC_COOLDOWN_MILLIS
) {
    companion object {
        const val DEFAULT_HEART_RATE_THRESHOLD = 140
        const val DEFAULT_SUSTAIN_MILLIS = 180_000L
        const val DEFAULT_CLEAR_BELOW_THRESHOLD_MILLIS = 30_000L
        const val DEFAULT_HAPTIC_COOLDOWN_MILLIS = 30_000L

        val DEFAULT = AlertConfig()
    }
}
