package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectStallSegmentFromPoseUseCaseTest {

    private val useCase = DetectStallSegmentFromPoseUseCase()

    @Test
    fun `returns strongest stall segment when hip barely moves for more than one second`() {
        val result = useCase(
            poses = buildList {
                for (timeMs in 1_000L..3_200L step 100L) {
                    add(stallPoseAt(timeMs, 0.40f, 0.60f))
                }
                for (timeMs in 3_300L..5_000L step 100L) {
                    val progress = ((timeMs - 3_300L) / 100L).toFloat()
                    add(
                        stallPoseAt(
                            frameTimeMs = timeMs,
                            hipCenterX = 0.45f + (progress * 0.05f),
                            hipCenterY = 0.65f + (progress * 0.05f)
                        )
                    )
                }
            },
            wallArrivalTimeMs = 1_000L,
            endTimeMs = 5_000L
        )

        assertEquals(2_000L, result?.startTimeMs)
        assertTrue((result?.durationMs ?: 0L) >= 1_000L)
    }

    @Test
    fun `isolated noise frames are filtered by support window requirements`() {
        val result = useCase(
            poses = listOf(
                stallPoseAt(1_000L, 0.40f, 0.60f),
                stallPoseAt(1_100L, 0.40f, 0.60f),
                stallPoseAt(2_600L, 0.40f, 0.60f)
            ),
            wallArrivalTimeMs = 1_000L,
            endTimeMs = 4_000L
        )

        assertNull(result)
    }

    @Test
    fun `wall arrival grace period prevents immediate post arrival pause from becoming stall`() {
        val result = useCase(
            poses = (1_000L..2_000L step 100L).map { timeMs ->
                stallPoseAt(timeMs, 0.40f, 0.60f)
            },
            wallArrivalTimeMs = 1_000L,
            endTimeMs = 2_600L
        )

        assertNull(result)
    }

    @Test
    fun `frames near end point are excluded by end guard`() {
        val result = useCase(
            poses = (1_000L..2_800L step 100L).map { timeMs ->
                stallPoseAt(timeMs, 0.40f, 0.60f)
            },
            wallArrivalTimeMs = 1_000L,
            endTimeMs = 2_400L
        )

        assertNull(result)
    }

    @Test
    fun `returns null when wall arrival time is missing`() {
        val result = useCase(
            poses = (1_000L..3_000L step 100L).map { timeMs ->
                stallPoseAt(timeMs, 0.40f, 0.60f)
            },
            wallArrivalTimeMs = null,
            endTimeMs = 4_000L
        )

        assertNull(result)
    }

    private fun stallPoseAt(frameTimeMs: Long, hipCenterX: Float, hipCenterY: Float): Pose {
        val torsoScale = 0.30f
        val shoulderCenterY = hipCenterY - torsoScale
        return Pose(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                PoseLandmark(index = 11, x = hipCenterX - 0.05f, y = shoulderCenterY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 12, x = hipCenterX + 0.05f, y = shoulderCenterY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 23, x = hipCenterX - 0.03f, y = hipCenterY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 24, x = hipCenterX + 0.03f, y = hipCenterY, z = 0f, visibility = 0.99f, presence = 0.99f)
            )
        )
    }
}
