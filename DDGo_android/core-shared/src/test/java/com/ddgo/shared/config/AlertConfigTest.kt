package com.ddgo.shared.config

import com.ddgo.shared.model.RecordingState
import com.ddgo.shared.model.RecordingStateConflictResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun newerRecordingStateWins() {
        val current = RecordingState(sessionId = "session-a", isRecording = true, updatedAt = 100L)
        val incoming = RecordingState(sessionId = "session-b", isRecording = false, updatedAt = 101L)

        assertTrue(RecordingStateConflictResolver.shouldApply(current, incoming))
    }

    @Test
    fun duplicateOrOlderRecordingStateIsIgnored() {
        val current = RecordingState(sessionId = "session-a", isRecording = true, updatedAt = 200L)

        assertFalse(RecordingStateConflictResolver.shouldApply(current, current.copy()))
        assertFalse(
            RecordingStateConflictResolver.shouldApply(
                current = current,
                incoming = current.copy(isRecording = false, updatedAt = 199L)
            )
        )
    }
}
