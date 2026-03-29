package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolygonHoldContactAnalysisTest {

    @Test
    fun `left foot index alone can engage a hold`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.18f,
            top = 0.68f,
            right = 0.22f,
            bottom = 0.72f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(31, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(31, 0.20f, 0.70f)
                )
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        val leftFootState = result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.LEFT_FOOT }
        assertEquals(1, leftFootState.activeHoldNo)
    }

    @Test
    fun `left heel alone can engage a hold when foot index is missing`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.18f,
            top = 0.68f,
            right = 0.22f,
            bottom = 0.72f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(29, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(29, 0.20f, 0.70f)
                )
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        val leftFootState = result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.LEFT_FOOT }
        assertEquals(1, leftFootState.activeHoldNo)
    }

    @Test
    fun `left ankle alone can engage a hold when other foot points are missing`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.18f,
            top = 0.68f,
            right = 0.22f,
            bottom = 0.72f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(27, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(27, 0.20f, 0.70f)
                )
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        val leftFootState = result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.LEFT_FOOT }
        assertEquals(1, leftFootState.activeHoldNo)
    }

    @Test
    fun `left foot index takes priority over heel when they point to different holds`() {
        val preferredHold = numberedHold(
            holdNo = 1,
            role = HoldRole.NORMAL,
            left = 0.18f,
            top = 0.68f,
            right = 0.22f,
            bottom = 0.72f
        )
        val fallbackHold = numberedHold(
            holdNo = 2,
            role = HoldRole.NORMAL,
            left = 0.38f,
            top = 0.68f,
            right = 0.42f,
            bottom = 0.72f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(31, 0.20f, 0.70f),
                    landmark(29, 0.40f, 0.70f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(31, 0.20f, 0.70f),
                    landmark(29, 0.40f, 0.70f)
                )
            ),
            holds = listOf(preferredHold, fallbackHold),
            enableLogging = false
        )

        val leftFootState = result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.LEFT_FOOT }
        assertEquals(1, leftFootState.activeHoldNo)
    }

    @Test
    fun `active hold is maintained when any foot point remains on the hold`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.NORMAL,
            left = 0.18f,
            top = 0.68f,
            right = 0.22f,
            bottom = 0.72f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(31, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(31, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 280L,
                    landmark(31, 0.60f, 0.30f),
                    landmark(29, 0.20f, 0.70f)
                )
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        val leftFootState = result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.LEFT_FOOT }
        assertEquals(1, leftFootState.activeHoldNo)
    }

    @Test
    fun `active hold is released when all foot points leave the hold`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.NORMAL,
            left = 0.18f,
            top = 0.68f,
            right = 0.22f,
            bottom = 0.72f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(31, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(31, 0.20f, 0.70f)
                ),
                poseAt(
                    timeMs = 280L,
                    landmark(31, 0.60f, 0.30f),
                    landmark(29, 0.62f, 0.32f),
                    landmark(27, 0.64f, 0.34f)
                )
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        val leftFootState = result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.LEFT_FOOT }
        assertNull(leftFootState.activeHoldNo)
    }

    @Test
    fun `start helper returns first inside frame when torso is valid`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.48f,
            top = 0.68f,
            right = 0.52f,
            bottom = 0.72f
        )

        assertEquals(
            0L,
            findFirstStartFootInsideTimeMs(
                poses = listOf(
                    poseAt(
                        timeMs = 0L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    ),
                    poseAt(
                        timeMs = 140L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    )
                ),
                holds = listOf(hold),
                startHoldNo = 1,
                analysisStartTimeMs = 0L
            )
        )
    }

    @Test
    fun `start helper ignores inside frame when torso is not valid`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.48f,
            top = 0.68f,
            right = 0.52f,
            bottom = 0.72f
        )

        assertEquals(
            140L,
            findFirstStartFootInsideTimeMs(
                poses = listOf(
                    poseAt(
                        timeMs = 0L,
                        landmark(28, 0.50f, 0.70f)
                    ),
                    poseAt(
                        timeMs = 140L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    )
                ),
                holds = listOf(hold),
                startHoldNo = 1,
                analysisStartTimeMs = 0L
            )
        )
    }

    @Test
    fun `start helper returns first inside frame for any selected hold`() {
        val startHold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.10f,
            top = 0.68f,
            right = 0.14f,
            bottom = 0.72f
        )
        val selectedNormalHold = numberedHold(
            holdNo = 2,
            role = HoldRole.NORMAL,
            left = 0.48f,
            top = 0.68f,
            right = 0.52f,
            bottom = 0.72f
        )

        assertEquals(
            0L,
            findFirstStartFootInsideTimeMs(
                poses = listOf(
                    poseAt(
                        timeMs = 0L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    ),
                    poseAt(
                        timeMs = 140L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    )
                ),
                holds = listOf(startHold, selectedNormalHold),
                startHoldNo = 1,
                analysisStartTimeMs = 0L
            )
        )
    }

    @Test
    fun `start helper ignores inside frames before wall arrival threshold`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.48f,
            top = 0.68f,
            right = 0.52f,
            bottom = 0.72f
        )

        assertEquals(
            140L,
            findFirstStartFootInsideTimeMs(
                poses = listOf(
                    poseAt(
                        timeMs = 0L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    ),
                    poseAt(
                        timeMs = 140L,
                        landmark(28, 0.50f, 0.70f),
                        *torsoLandmarks()
                    )
                ),
                holds = listOf(hold),
                startHoldNo = 1,
                analysisStartTimeMs = 100L
            )
        )
    }

    @Test
    fun `start helper can trigger before active contact engage finishes`() {
        val hold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.48f,
            top = 0.68f,
            right = 0.52f,
            bottom = 0.72f
        )

        val poses = listOf(
            poseAt(
                timeMs = 0L,
                landmark(28, 0.50f, 0.70f),
                *torsoLandmarks()
            ),
            poseAt(
                timeMs = 80L,
                landmark(28, 0.50f, 0.70f),
                *torsoLandmarks()
            )
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poses[0],
                poses[1]
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        assertEquals(
            0L,
            findFirstStartFootInsideTimeMs(
                poses = poses,
                holds = listOf(hold),
                startHoldNo = 1,
                analysisStartTimeMs = 0L
            )
        )
        assertNull(result.frames.last().limbStates.first { it.limb == PolygonTrackedLimb.RIGHT_FOOT }.activeHoldNo)
    }

    @Test
    fun `contacted hold set includes foot contact from heel only input`() {
        val hold = numberedHold(
            holdNo = 3,
            role = HoldRole.NORMAL,
            left = 0.28f,
            top = 0.58f,
            right = 0.32f,
            bottom = 0.62f
        )

        val result = analyzePolygonHoldContacts(
            poses = listOf(
                poseAt(
                    timeMs = 0L,
                    landmark(30, 0.30f, 0.60f)
                ),
                poseAt(
                    timeMs = 140L,
                    landmark(30, 0.30f, 0.60f)
                )
            ),
            holds = listOf(hold),
            enableLogging = false
        )

        assertTrue(result.contactedHoldNos.contains(3))
    }

    private fun poseAt(timeMs: Long, vararg landmarks: PoseLandmark): Pose = Pose(
        frameTimeMs = timeMs,
        landmarks = landmarks.toList()
    )

    private fun landmark(index: Int, x: Float, y: Float): PoseLandmark = PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = 0f
    )

    private fun torsoLandmarks(): Array<PoseLandmark> = arrayOf(
        landmark(11, 0.40f, 0.30f),
        landmark(12, 0.60f, 0.30f),
        landmark(23, 0.44f, 0.55f),
        landmark(24, 0.56f, 0.55f)
    )

    private fun numberedHold(
        holdNo: Int,
        role: HoldRole,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): HoldNumbered = HoldNumbered(
        hold = Hold(
            holdNo = holdNo,
            boundingBox = Hold.BoundingBox(left, top, right, bottom),
            confidence = 0.98f,
            polygon = listOf(
                Hold.Point(left, top),
                Hold.Point(right, top),
                Hold.Point(right, bottom),
                Hold.Point(left, bottom)
            ),
            colorLabel = "green",
            colorScore = 0.98f
        ),
        progress = when (role) {
            HoldRole.START -> 0f
            HoldRole.END -> 1f
            HoldRole.NORMAL -> 0.5f
        },
        axisDistance = 0f,
        role = role
    )
}
