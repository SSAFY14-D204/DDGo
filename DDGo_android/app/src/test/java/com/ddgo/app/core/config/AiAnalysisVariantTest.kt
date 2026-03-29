package com.ddgo.app.core.config

import com.ddgo.app.domain.model.AiAnalysisMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AiAnalysisVariantTest {

    @Test
    fun `v1 keeps original defaults`() {
        val variant = AiAnalysisVariant.V1

        assertEquals(AiRequestTransport.PLAIN, variant.requestTransport)
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

        assertEquals(AiRequestTransport.GZIP, variant.requestTransport)
        assertEquals("api/v2/mujoco-complete/analyze/fast", variant.fastPath)
        assertEquals("api/v2/mujoco-complete/analyze/physics", variant.physicsPath)
        assertEquals(30, variant.uploadPrePoseAnalysisFps)
        assertEquals(Int.MAX_VALUE, variant.primaryRequestMaxFrameCount)
        assertEquals(48, variant.retryRequestMaxFrameCount)
        assertEquals(30, variant.defaultVideoPoseAnalysisFps)
        assertEquals("api/v2/mujoco-complete/analyze/physics", variant.analyzePath(AiAnalysisMode.PHYSICS))
    }

    @Test
    fun `v2 gzip 10fps is the current default policy`() {
        val variant = AiAnalysisVariant.V2_GZIP_10FPS

        assertEquals(AiRequestTransport.GZIP, variant.requestTransport)
        assertEquals("api/v2/mujoco-complete/analyze/fast", variant.fastPath)
        assertEquals("api/v2/mujoco-complete/analyze/physics", variant.physicsPath)
        assertEquals(10, variant.uploadPrePoseAnalysisFps)
        assertEquals(Int.MAX_VALUE, variant.primaryRequestMaxFrameCount)
        assertEquals(48, variant.retryRequestMaxFrameCount)
        assertEquals(10, variant.defaultVideoPoseAnalysisFps)
        assertEquals(AiAnalysisVariant.V2_GZIP_10FPS, AiAnalysisVariantConfig.current)
    }
}
