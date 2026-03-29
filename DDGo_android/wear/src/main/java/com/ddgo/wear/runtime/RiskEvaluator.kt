package com.ddgo.wear.runtime

import com.ddgo.shared.config.AlertConfig
import com.ddgo.wear.data.ExerciseRuntimeSnapshot

class RiskEvaluator(
    private val alertConfig: AlertConfig = AlertConfig.DEFAULT
) {
    fun evaluate(
        current: ExerciseRuntimeSnapshot,
        heartRate: Int?,
        measuredAt: Long?
    ): RiskEvaluationResult {
        if (heartRate == null || measuredAt == null) {
            return RiskEvaluationResult(
                alerting = current.alerting,
                aboveThresholdStartedAt = current.aboveThresholdStartedAt,
                belowThresholdStartedAt = current.belowThresholdStartedAt,
                lastAlertTriggeredAt = current.lastAlertTriggeredAt,
                lastHapticAt = current.lastHapticAt,
                shouldTriggerHaptic = false
            )
        }

        val threshold = alertConfig.heartRateThreshold
        val sustainMillis = alertConfig.sustainMillis
        val clearMillis = alertConfig.clearBelowThresholdMillis
        val cooldownMillis = alertConfig.hapticCooldownMillis

        return if (heartRate >= threshold) {
            val aboveStartedAt = current.aboveThresholdStartedAt ?: measuredAt
            val sustained = measuredAt - aboveStartedAt >= sustainMillis
            val shouldBeAlerting = current.alerting || sustained
            val shouldTriggerHaptic = shouldBeAlerting &&
                (!current.alerting || current.lastHapticAt == null || measuredAt - current.lastHapticAt >= cooldownMillis)

            RiskEvaluationResult(
                alerting = shouldBeAlerting,
                aboveThresholdStartedAt = aboveStartedAt,
                belowThresholdStartedAt = null,
                lastAlertTriggeredAt = if (shouldBeAlerting && !current.alerting) {
                    measuredAt
                } else {
                    current.lastAlertTriggeredAt
                },
                lastHapticAt = if (shouldTriggerHaptic) measuredAt else current.lastHapticAt,
                shouldTriggerHaptic = shouldTriggerHaptic
            )
        } else {
            val belowStartedAt = current.belowThresholdStartedAt ?: measuredAt
            val shouldClear = current.alerting && measuredAt - belowStartedAt >= clearMillis
            RiskEvaluationResult(
                alerting = if (shouldClear) false else current.alerting,
                aboveThresholdStartedAt = null,
                belowThresholdStartedAt = if (current.alerting || current.belowThresholdStartedAt != null) {
                    belowStartedAt
                } else {
                    null
                },
                lastAlertTriggeredAt = current.lastAlertTriggeredAt,
                lastHapticAt = current.lastHapticAt,
                shouldTriggerHaptic = false
            )
        }
    }
}

data class RiskEvaluationResult(
    val alerting: Boolean,
    val aboveThresholdStartedAt: Long?,
    val belowThresholdStartedAt: Long?,
    val lastAlertTriggeredAt: Long?,
    val lastHapticAt: Long?,
    val shouldTriggerHaptic: Boolean
)
