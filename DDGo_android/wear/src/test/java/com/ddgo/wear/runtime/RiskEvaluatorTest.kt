package com.ddgo.wear.runtime

import com.ddgo.shared.config.AlertConfig
import com.ddgo.wear.data.ExerciseRuntimeSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskEvaluatorTest {
    private val evaluator = RiskEvaluator(
        alertConfig = AlertConfig.DEFAULT
    )

    @Test
    fun alertStartsAfterThreeMinutesAboveThreshold() {
        val initial = ExerciseRuntimeSnapshot()
        val first = evaluator.evaluate(initial, heartRate = 145, measuredAt = 0L)
        val sustained = evaluator.evaluate(
            current = initial.copy(
                aboveThresholdStartedAt = first.aboveThresholdStartedAt,
                alerting = first.alerting,
                lastHapticAt = first.lastHapticAt
            ),
            heartRate = 145,
            measuredAt = 180_000L
        )

        assertTrue(sustained.alerting)
        assertTrue(sustained.shouldTriggerHaptic)
    }

    @Test
    fun alertClearsAfterThirtySecondsBelowThreshold() {
        val current = ExerciseRuntimeSnapshot(
            alerting = true,
            aboveThresholdStartedAt = 0L,
            lastAlertTriggeredAt = 180_000L,
            lastHapticAt = 180_000L
        )
        val beforeClear = evaluator.evaluate(current, heartRate = 120, measuredAt = 200_000L)
        val cleared = evaluator.evaluate(
            current = current.copy(
                belowThresholdStartedAt = beforeClear.belowThresholdStartedAt,
                alerting = beforeClear.alerting,
                lastHapticAt = beforeClear.lastHapticAt,
                lastAlertTriggeredAt = beforeClear.lastAlertTriggeredAt
            ),
            heartRate = 120,
            measuredAt = 230_000L
        )

        assertFalse(cleared.alerting)
    }

    @Test
    fun hapticCooldownBlocksRepeatedAlertPulseWithinThirtySeconds() {
        val current = ExerciseRuntimeSnapshot(
            alerting = true,
            aboveThresholdStartedAt = 0L,
            lastAlertTriggeredAt = 180_000L,
            lastHapticAt = 180_000L
        )

        val evaluation = evaluator.evaluate(current, heartRate = 150, measuredAt = 200_000L)

        assertTrue(evaluation.alerting)
        assertFalse(evaluation.shouldTriggerHaptic)
    }
}
