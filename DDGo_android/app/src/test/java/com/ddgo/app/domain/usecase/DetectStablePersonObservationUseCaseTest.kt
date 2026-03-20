package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectStablePersonObservationUseCaseTest {

    private val useCase = DetectStablePersonObservationUseCase()

    @Test
    fun `returns first timestamp of first qualifying five frame streak`() {
        val result = useCase(
            processedFrames = listOf(
                frame(100L, true),
                frame(200L, true),
                frame(300L, true),
                frame(400L, true),
                frame(500L, true)
            )
        )

        assertEquals(100L, result)
    }

    @Test
    fun `false detection resets streak`() {
        val result = useCase(
            processedFrames = listOf(
                frame(100L, true),
                frame(200L, true),
                frame(300L, false),
                frame(400L, true),
                frame(500L, true),
                frame(600L, true),
                frame(700L, true),
                frame(800L, true)
            )
        )

        assertEquals(400L, result)
    }

    @Test
    fun `returns null when fewer than five consecutive detections exist`() {
        val result = useCase(
            processedFrames = listOf(
                frame(100L, true),
                frame(200L, true),
                frame(300L, true),
                frame(400L, true)
            )
        )

        assertNull(result)
    }

    @Test
    fun `keeps first frame of first qualifying streak when detections continue`() {
        val result = useCase(
            processedFrames = listOf(
                frame(100L, true),
                frame(200L, true),
                frame(300L, true),
                frame(400L, true),
                frame(500L, true),
                frame(600L, true),
                frame(700L, true)
            )
        )

        assertEquals(100L, result)
    }

    private fun frame(timestampMs: Long, poseDetected: Boolean) = ProcessedPoseDetectionFrame(
        timestampMs = timestampMs,
        poseDetected = poseDetected
    )
}
