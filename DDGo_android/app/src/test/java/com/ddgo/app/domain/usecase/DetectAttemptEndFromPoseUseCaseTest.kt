package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectAttemptEndFromPoseUseCaseTest {

    @Test
    fun `extract body part heights computes averages scale and front orientation`() {
        val result = extractBodyPartHeights(
            poses = listOf(
                Pose(
                    frameTimeMs = 100L,
                    landmarks = listOf(
                        poseLandmark(index = 11, x = 0.70f, y = 0.30f),
                        poseLandmark(index = 12, x = 0.40f, y = 0.30f),
                        poseLandmark(index = 15, x = 0.68f, y = 0.20f),
                        poseLandmark(index = 16, x = 0.38f, y = 0.30f),
                        poseLandmark(index = 17, x = 0.69f, y = 0.25f),
                        poseLandmark(index = 18, x = 0.39f, y = 0.35f),
                        poseLandmark(index = 19, x = 0.70f, y = 0.20f),
                        poseLandmark(index = 20, x = 0.40f, y = 0.30f),
                        poseLandmark(index = 21, x = 0.71f, y = 0.25f),
                        poseLandmark(index = 22, x = 0.41f, y = 0.35f),
                        poseLandmark(index = 23, x = 0.68f, y = 0.50f),
                        poseLandmark(index = 24, x = 0.42f, y = 0.50f),
                        poseLandmark(index = 27, x = 0.62f, y = 0.80f),
                        poseLandmark(index = 28, x = 0.38f, y = 0.82f),
                        poseLandmark(index = 29, x = 0.63f, y = 0.78f),
                        poseLandmark(index = 30, x = 0.37f, y = 0.84f),
                        poseLandmark(index = 31, x = 0.64f, y = 0.79f),
                        poseLandmark(index = 32, x = 0.36f, y = 0.81f)
                    )
                )
            )
        ).single()

        assertEquals(0.725f, result.handHeight ?: 0f, 0.0001f)
        assertEquals(0.60f, result.torsoHeight ?: 0f, 0.0001f)
        assertEquals(0.19333333f, result.footHeight ?: 0f, 0.0001f)
        assertEquals(0.20f, result.torsoScale ?: 0f, 0.0001f)
        assertEquals(TorsoOrientation.Front, result.torsoOrientation)
    }

    @Test
    fun `extract body part heights ignores low confidence landmarks`() {
        val result = extractBodyPartHeights(
            poses = listOf(
                Pose(
                    frameTimeMs = 0L,
                    landmarks = listOf(
                        poseLandmark(index = 15, x = 0.6f, y = 0.20f, visibility = 0.20f),
                        poseLandmark(index = 16, x = 0.4f, y = 0.30f),
                        poseLandmark(index = 11, x = 0.65f, y = 0.35f, presence = 0.20f),
                        poseLandmark(index = 12, x = 0.35f, y = 0.35f),
                        poseLandmark(index = 23, x = 0.64f, y = 0.55f),
                        poseLandmark(index = 24, x = 0.36f, y = 0.55f)
                    )
                )
            )
        ).single()

        assertEquals(0.70f, result.handHeight ?: 0f, 0.0001f)
        assertEquals(0.51666665f, result.torsoHeight ?: 0f, 0.0001f)
        assertNull(result.torsoScale)
        assertEquals(TorsoOrientation.Unknown, result.torsoOrientation)
    }

    @Test
    fun `detect climb end returns end timestamp from sustained descent`() {
        val detection = detectClimbEnd(
            poses = listOf(
                torsoPoseAt(0L, torsoY = 0.90f, wristY = 0.82f),
                torsoPoseAt(1_000L, torsoY = 0.80f, wristY = 0.72f),
                torsoPoseAt(2_000L, torsoY = 0.68f, wristY = 0.58f),
                torsoPoseAt(3_000L, torsoY = 0.54f, wristY = 0.44f),
                torsoPoseAt(4_000L, torsoY = 0.40f, wristY = 0.30f),
                torsoPoseAt(4_500L, torsoY = 0.48f, wristY = 0.36f),
                torsoPoseAt(5_000L, torsoY = 0.50f, wristY = 0.38f),
                torsoPoseAt(5_500L, torsoY = 0.53f, wristY = 0.40f)
            ),
            config = DetectionConfig(smoothingWindowFrames = 1)
        )

        assertNotNull(detection)
        assertEquals(4_000L, detection?.bestPeakAtMs)
        assertEquals(4_500L, detection?.descentStartAtMs)
        assertEquals(1_000L, detection?.endAtMs)
        assertTrue((detection?.confidence ?: 0f) > 0.45f)
    }

    @Test
    fun `detect climb end falls back to peak when no descent is confirmed`() {
        val detection = detectClimbEnd(
            poses = listOf(
                torsoPoseAt(0L, torsoY = 0.92f, wristY = 0.84f),
                torsoPoseAt(1_000L, torsoY = 0.80f, wristY = 0.72f),
                torsoPoseAt(2_000L, torsoY = 0.65f, wristY = 0.58f),
                torsoPoseAt(3_000L, torsoY = 0.48f, wristY = 0.40f),
                torsoPoseAt(4_000L, torsoY = 0.40f, wristY = 0.32f)
            ),
            config = DetectionConfig(smoothingWindowFrames = 1)
        )

        assertNotNull(detection)
        assertEquals(4_000L, detection?.bestPeakAtMs)
        assertNull(detection?.descentStartAtMs)
        assertEquals(1_000L, detection?.endAtMs)
        assertEquals(0.45f, detection?.confidence ?: 0f, 0.0001f)
    }

    @Test
    fun `detect climb end clamps timestamp to first valid frame`() {
        val detection = detectClimbEnd(
            poses = listOf(
                torsoPoseAt(1_000L, torsoY = 0.72f, wristY = 0.60f),
                torsoPoseAt(2_000L, torsoY = 0.40f, wristY = 0.30f),
                torsoPoseAt(2_500L, torsoY = 0.48f, wristY = 0.36f),
                torsoPoseAt(3_000L, torsoY = 0.52f, wristY = 0.40f),
                torsoPoseAt(3_500L, torsoY = 0.55f, wristY = 0.42f)
            ),
            config = DetectionConfig(smoothingWindowFrames = 1)
        )

        assertNotNull(detection)
        assertEquals(1_000L, detection?.endAtMs)
    }

    @Test
    fun `detect climb end returns null when torso landmarks are missing`() {
        val detection = DetectAttemptEndFromPoseUseCase()(
            poses = listOf(
                Pose(
                    frameTimeMs = 0L,
                    landmarks = listOf(
                        poseLandmark(index = 15, x = 0.6f, y = 0.2f),
                        poseLandmark(index = 16, x = 0.4f, y = 0.2f)
                    )
                )
            )
        )

        assertNull(detection)
    }

    private fun torsoPoseAt(frameTimeMs: Long, torsoY: Float, wristY: Float): Pose {
        val shoulderY = torsoY - 0.10f
        val hipY = torsoY + 0.10f
        return Pose(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                poseLandmark(index = 11, x = 0.65f, y = shoulderY),
                poseLandmark(index = 12, x = 0.35f, y = shoulderY),
                poseLandmark(index = 23, x = 0.64f, y = hipY),
                poseLandmark(index = 24, x = 0.36f, y = hipY),
                poseLandmark(index = 15, x = 0.62f, y = wristY),
                poseLandmark(index = 16, x = 0.38f, y = wristY)
            )
        )
    }

    private fun poseLandmark(
        index: Int,
        x: Float,
        y: Float,
        visibility: Float = 0.99f,
        presence: Float = 0.99f
    ): PoseLandmark = PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = 0f,
        visibility = visibility,
        presence = presence
    )
}
