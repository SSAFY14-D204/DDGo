package com.ddgo.app.core.config

import com.ddgo.app.domain.model.AiAnalysisMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAnalysisVariantTest {

    @Test
    fun `v1 keeps original defaults`() {
        val variant = AiAnalysisVariant.V1

        assertFalse(variant.useGzipRequest)
        assertEquals("api/v1/mujoco-complete/analyze/fast", variant.fastPath)
        assertEquals("api/v1/mujoco-complete/analyze/physics", variant.physicsPath)
        assertEquals(10, variant.uploadPrePoseAnalysisFps)
        assertEquals(90, variant.primaryRequestMaxFrameCount)
        assertEquals(48, variant.retryRequestMaxFrameCount)
        assertEquals(10, variant.defaultVideoPoseAnalysisFps)
        assertEquals("api/v1/mujoco-complete/analyze/fast", variant.analyzePath(AiAnalysisMode.FAST))
    }

    @Test
    fun `v2 preserves gzip and expanded frame settings`() {
        val variant = AiAnalysisVariant.V2

        assertTrue(variant.useGzipRequest)
        assertEquals("api/v2/mujoco-complete/analyze/fast", variant.fastPath)
        assertEquals("api/v2/mujoco-complete/analyze/physics", variant.physicsPath)
        assertEquals(30, variant.uploadPrePoseAnalysisFps)
        assertEquals(Int.MAX_VALUE, variant.primaryRequestMaxFrameCount)
        assertEquals(48, variant.retryRequestMaxFrameCount)
        assertEquals(30, variant.defaultVideoPoseAnalysisFps)
        assertEquals("api/v2/mujoco-complete/analyze/physics", variant.analyzePath(AiAnalysisMode.PHYSICS))
    }
}
