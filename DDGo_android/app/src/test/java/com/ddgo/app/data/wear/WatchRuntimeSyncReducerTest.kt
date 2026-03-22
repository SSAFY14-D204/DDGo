package com.ddgo.app.data.wear

import com.ddgo.shared.model.HeartRateSnapshot
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchSessionStatus
import com.ddgo.shared.model.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRuntimeSyncReducerTest {
    @Test
    fun olderHeartRateSnapshotIsIgnored() {
        val current = WatchRuntimeSyncSnapshot(
            heartRateSnapshot = HeartRateSnapshot(
                heartRate = 150,
                alerting = true,
                sensorAvailable = true,
                measurementStatus = MeasurementStatus.MEASURING,
                lastMeasuredAt = 9_000L,
                updatedAt = 10_000L
            )
        )

        val result = WatchRuntimeSyncReducer.applyHeartRate(
            current = current,
            incoming = HeartRateSnapshot(
                heartRate = 132,
                alerting = false,
                sensorAvailable = true,
                measurementStatus = MeasurementStatus.MEASURING,
                lastMeasuredAt = 8_000L,
                updatedAt = 9_500L
            ),
            connectedAt = 12_000L
        )

        assertFalse(result.applied)
        assertEquals(150, result.snapshot.heartRateSnapshot?.heartRate)
    }

    @Test
    fun newerWatchStatusUpdatesAlertTimestamp() {
        val current = WatchRuntimeSyncSnapshot(
            watchSessionStatus = WatchSessionStatus(
                sessionId = "session-1",
                watchState = WatchState.RECORDING,
                serviceActive = true,
                alerting = false,
                sensorAvailable = true,
                updatedAt = 10_000L
            )
        )

        val result = WatchRuntimeSyncReducer.applyWatchStatus(
            current = current,
            incoming = WatchSessionStatus(
                sessionId = "session-1",
                watchState = WatchState.ALERTING,
                serviceActive = true,
                alerting = true,
                sensorAvailable = true,
                updatedAt = 12_000L
            ),
            connectedAt = 12_500L,
            markAlertReceived = true
        )

        assertTrue(result.applied)
        assertEquals(WatchState.ALERTING, result.snapshot.watchSessionStatus?.watchState)
        assertEquals(12_000L, result.snapshot.lastAlertReceivedAt)
        assertTrue(result.snapshot.isWatchConnected)
    }

    @Test
    fun connectionStateCanFlipOffline() {
        val current = WatchRuntimeSyncSnapshot(
            isWatchConnected = true,
            lastConnectionCheckedAt = 5_000L
        )

        val updated = WatchRuntimeSyncReducer.applyConnection(
            current = current,
            isConnected = false,
            checkedAt = 8_000L
        )

        assertFalse(updated.isWatchConnected)
        assertEquals(8_000L, updated.lastConnectionCheckedAt)
    }
}
