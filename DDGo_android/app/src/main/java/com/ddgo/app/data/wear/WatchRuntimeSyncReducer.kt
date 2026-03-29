package com.ddgo.app.data.wear

import com.ddgo.shared.model.HeartRateSnapshot
import com.ddgo.shared.model.WatchSessionStatus

object WatchRuntimeSyncReducer {
    fun applyHeartRate(
        current: WatchRuntimeSyncSnapshot,
        incoming: HeartRateSnapshot,
        connectedAt: Long
    ): ReductionResult {
        if (!shouldApply(current.heartRateSnapshot?.updatedAt, incoming.updatedAt)) {
            return ReductionResult(current, false)
        }

        return ReductionResult(
            snapshot = current.copy(
                heartRateSnapshot = incoming,
                isWatchConnected = true,
                lastConnectionCheckedAt = connectedAt
            ),
            applied = true
        )
    }

    fun applyWatchStatus(
        current: WatchRuntimeSyncSnapshot,
        incoming: WatchSessionStatus,
        connectedAt: Long,
        markAlertReceived: Boolean = false
    ): ReductionResult {
        if (!shouldApply(current.watchSessionStatus?.updatedAt, incoming.updatedAt)) {
            return ReductionResult(current, false)
        }

        return ReductionResult(
            snapshot = current.copy(
                watchSessionStatus = incoming,
                isWatchConnected = true,
                lastConnectionCheckedAt = connectedAt,
                lastAlertReceivedAt = when {
                    markAlertReceived || incoming.alerting -> incoming.updatedAt
                    else -> current.lastAlertReceivedAt
                }
            ),
            applied = true
        )
    }

    fun applyConnection(
        current: WatchRuntimeSyncSnapshot,
        isConnected: Boolean,
        checkedAt: Long
    ): WatchRuntimeSyncSnapshot {
        return current.copy(
            isWatchConnected = isConnected,
            lastConnectionCheckedAt = checkedAt
        )
    }

    private fun shouldApply(currentUpdatedAt: Long?, incomingUpdatedAt: Long): Boolean {
        return currentUpdatedAt == null || incomingUpdatedAt >= currentUpdatedAt
    }

    data class ReductionResult(
        val snapshot: WatchRuntimeSyncSnapshot,
        val applied: Boolean
    )
}
