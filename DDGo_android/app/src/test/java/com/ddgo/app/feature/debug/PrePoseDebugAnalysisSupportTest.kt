package com.ddgo.app.feature.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class PrePoseDebugAnalysisSupportTest {

    @Test
    fun `resolveOfficialSampleIntervalMs maps supported fps to expected sampling interval`() {
        assertEquals(100L, resolveOfficialSampleIntervalMs(10))
        assertEquals(50L, resolveOfficialSampleIntervalMs(20))
        assertEquals(34L, resolveOfficialSampleIntervalMs(30))
    }
}
