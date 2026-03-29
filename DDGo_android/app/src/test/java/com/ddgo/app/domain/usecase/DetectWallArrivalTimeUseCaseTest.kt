package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.poseanalysis.Landmark
import com.ddgo.app.domain.poseanalysis.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectWallArrivalTimeUseCaseTest {

    private val useCase = DetectWallArrivalTimeUseCase()

    @Test
    fun `stable min uses smallest five values median instead of single minimum`() {
        val result = useCase(
            frames = listOf(
                torsoFrame(1_000L, 0.50),
                torsoFrame(1_100L, 0.49),
                torsoFrame(1_200L, 0.48),
                torsoFrame(1_300L, 0.31),
                torsoFrame(1_400L, 0.30),
                torsoFrame(1_500L, 0.10),
                torsoFrame(1_600L, 0.30),
                torsoFrame(1_700L, 0.31),
                torsoFrame(1_800L, 0.30),
                torsoFrame(1_900L, 0.30),
                torsoFrame(2_000L, 0.31)
            ),
            personObservationStartTimeMs = 1_000L
        )

        assertEquals(1_300L, result)
    }

    @Test
    fun `returns null when supported near wall segment does not last long enough`() {
        val result = useCase(
            frames = listOf(
                torsoFrame(1_000L, 1.00),
                torsoFrame(1_100L, 0.95),
                torsoFrame(1_200L, 0.90),
                torsoFrame(1_300L, 0.10),
                torsoFrame(1_400L, 0.10),
                torsoFrame(1_500L, 0.10),
                torsoFrame(1_600L, 0.10),
                torsoFrame(1_700L, 1.00),
                torsoFrame(1_800L, 0.95),
                torsoFrame(1_900L, 0.90),
                torsoFrame(2_000L, 0.85)
            ),
            personObservationStartTimeMs = 1_000L
        )

        assertNull(result)
    }

    @Test
    fun `frames before person observation start are ignored`() {
        val result = useCase(
            frames = listOf(
                torsoFrame(0L, 0.28),
                torsoFrame(100L, 0.28),
                torsoFrame(200L, 0.28),
                torsoFrame(300L, 0.28),
                torsoFrame(400L, 0.28),
                torsoFrame(500L, 0.28),
                torsoFrame(1_000L, 0.70),
                torsoFrame(1_100L, 0.69),
                torsoFrame(1_200L, 0.68),
                torsoFrame(1_300L, 0.67),
                torsoFrame(1_400L, 0.66),
                torsoFrame(1_500L, 0.65),
                torsoFrame(1_600L, 0.64),
                torsoFrame(1_700L, 0.30),
                torsoFrame(1_800L, 0.30),
                torsoFrame(1_900L, 0.30),
                torsoFrame(2_000L, 0.30),
                torsoFrame(2_100L, 0.30),
                torsoFrame(2_200L, 0.30),
                torsoFrame(2_300L, 0.30),
                torsoFrame(2_400L, 0.30),
                torsoFrame(2_500L, 0.30),
                torsoFrame(2_600L, 0.30)
            ),
            personObservationStartTimeMs = 1_000L
        )

        assertEquals(1_800L, result)
    }

    @Test
    fun `returns null when frames do not have two supporting timestamps on both sides`() {
        val result = useCase(
            frames = listOf(
                torsoFrame(1_000L, 0.50),
                torsoFrame(1_050L, 0.30),
                torsoFrame(2_500L, 0.30)
            ),
            personObservationStartTimeMs = 1_000L
        )

        assertNull(result)
    }

    @Test
    fun `lying frames are excluded from stable min and arrival candidates`() {
        val result = useCase(
            frames = listOf(
                lyingTorsoFrame(1_000L, 0.10),
                lyingTorsoFrame(1_100L, 0.10),
                lyingTorsoFrame(1_200L, 0.10),
                lyingTorsoFrame(1_300L, 0.10),
                lyingTorsoFrame(1_400L, 0.10),
                torsoFrame(1_500L, 0.80),
                torsoFrame(1_600L, 0.75),
                torsoFrame(1_700L, 0.70),
                torsoFrame(1_800L, 0.32),
                torsoFrame(1_900L, 0.31),
                torsoFrame(2_000L, 0.30),
                torsoFrame(2_100L, 0.30),
                torsoFrame(2_200L, 0.31),
                torsoFrame(2_300L, 0.30),
                torsoFrame(2_400L, 0.30),
                torsoFrame(2_500L, 0.30),
                torsoFrame(2_600L, 0.30),
                torsoFrame(2_700L, 0.31),
                torsoFrame(2_800L, 0.30)
            ),
            personObservationStartTimeMs = 1_000L
        )

        assertNotNull(result)
        assertTrue(result!! >= 1_800L)
    }

    @Test
    fun `returns null when all small torso scales are lying poses`() {
        val result = useCase(
            frames = listOf(
                lyingTorsoFrame(1_000L, 0.10),
                lyingTorsoFrame(1_100L, 0.10),
                lyingTorsoFrame(1_200L, 0.10),
                lyingTorsoFrame(1_300L, 0.10),
                lyingTorsoFrame(1_400L, 0.10),
                lyingTorsoFrame(1_500L, 0.10),
                lyingTorsoFrame(1_600L, 0.10)
            ),
            personObservationStartTimeMs = 1_000L
        )

        assertNull(result)
    }

    private fun torsoFrame(frameTimeMs: Long, torsoScale: Double): PoseFrame {
        val shoulderY = 0.5 - (torsoScale / 2.0)
        val hipY = 0.5 + (torsoScale / 2.0)
        return PoseFrame(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                landmark(index = 11, x = 0.60, y = shoulderY),
                landmark(index = 12, x = 0.40, y = shoulderY),
                landmark(index = 23, x = 0.58, y = hipY),
                landmark(index = 24, x = 0.42, y = hipY)
            )
        )
    }

    private fun lyingTorsoFrame(frameTimeMs: Long, torsoScale: Double): PoseFrame {
        val centerX = 0.5
        val centerY = 0.5
        val shoulderX = centerX - (torsoScale / 2.0)
        val hipX = centerX + (torsoScale / 2.0)
        return PoseFrame(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                landmark(index = 11, x = shoulderX, y = centerY - 0.01),
                landmark(index = 12, x = shoulderX, y = centerY + 0.01),
                landmark(index = 23, x = hipX, y = centerY - 0.01),
                landmark(index = 24, x = hipX, y = centerY + 0.01)
            )
        )
    }

    private fun landmark(index: Int, x: Double, y: Double): Landmark = Landmark(
        index = index,
        x = x,
        y = y,
        visibility = 0.99,
        presence = 0.99
    )
}
