package com.ddgo.shared.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertConfigTest {
    @Test
    fun defaultValuesMatchWatchSpecification() {
        val config = AlertConfig.DEFAULT

        assertEquals(140, config.heartRateThreshold)
        assertEquals(180_000L, config.sustainMillis)
        assertEquals(30_000L, config.clearBelowThresholdMillis)
        assertEquals(30_000L, config.hapticCooldownMillis)
    }
}
