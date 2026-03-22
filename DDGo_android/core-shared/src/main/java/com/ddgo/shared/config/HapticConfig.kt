package com.ddgo.shared.config

object HapticConfig {
    val START_PATTERN_MILLIS: LongArray = longArrayOf(0L, 150L)
    val STOP_PATTERN_MILLIS: LongArray = longArrayOf(0L, 250L, 150L, 250L)
    val ALERT_PATTERN_MILLIS: LongArray = longArrayOf(0L, 300L, 200L, 300L)
}
