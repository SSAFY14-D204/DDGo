package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptPosePlaybackTest {

    @Test
    fun `pose가 없으면 null을 반환한다`() {
        assertNull(findNearestPoseForPlayback(emptyList(), positionMs = 500L))
    }

    @Test
    fun `첫 pose 이전 구간에서는 가장 가까운 첫 pose를 반환한다`() {
        val poses = listOf(poseAt(300L), poseAt(600L))

        val result = findNearestPoseForPlayback(poses, positionMs = 299L)

        assertEquals(300L, result?.frameTimeMs)
    }

    @Test
    fun `pose 사이 구간에서는 가장 가까운 pose를 반환한다`() {
        val poseAt300 = poseAt(300L)
        val poseAt600 = poseAt(600L)

        val result = findNearestPoseForPlayback(
            poses = listOf(poseAt300, poseAt600),
            positionMs = 450L
        )

        assertEquals(300L, result?.frameTimeMs)
    }

    @Test
    fun `마지막 pose 이후 구간에서는 마지막 pose를 반환한다`() {
        val poseAt300 = poseAt(300L)
        val poseAt600 = poseAt(600L)

        val result = findNearestPoseForPlayback(
            poses = listOf(poseAt300, poseAt600),
            positionMs = 2_000L
        )

        assertEquals(600L, result?.frameTimeMs)
    }

    @Test
    fun `nearest timestamp는 동률일 때 이전 timestamp를 선택한다`() {
        val timestamps = listOf(100L, 300L)

        val result = timestamps.findNearestTimestamp(200L)

        assertEquals(100L, result)
    }

    @Test
    fun `marker position fraction uses timestamp ratio within duration`() {
        assertEquals(0f, markerPositionFraction(timeMs = -100L, durationMs = 1_000L))
        assertEquals(0.25f, markerPositionFraction(timeMs = 250L, durationMs = 1_000L))
        assertEquals(1f, markerPositionFraction(timeMs = 1_500L, durationMs = 1_000L))
    }

    @Test
    fun `vertical frame mask uses hold bounds to preserve center region`() {
        val mask = calculateVerticalVideoFrameMask(
            holds = listOf(
                holdNumbered(top = 0.30f, bottom = 0.36f),
                holdNumbered(top = 0.62f, bottom = 0.70f)
            ),
            contentRect = VideoContentRect(left = 0f, top = 0f, width = 320f, height = 1_000f),
            videoAspectRatio = 9f / 16f,
            safeInsetPx = 24f
        )

        assertTrue(mask.isVisible)
        assertEquals(276f, mask.topHeightPx, 0.001f)
        assertEquals(276f, mask.bottomHeightPx, 0.001f)
    }

    @Test
    fun `vertical frame mask clamps when hold touches top or bottom`() {
        val mask = calculateVerticalVideoFrameMask(
            holds = listOf(
                holdNumbered(top = 0f, bottom = 0.08f),
                holdNumbered(top = 0.92f, bottom = 1f)
            ),
            contentRect = VideoContentRect(left = 0f, top = 0f, width = 320f, height = 1_000f),
            videoAspectRatio = 9f / 16f,
            safeInsetPx = 24f
        )

        assertEquals(0f, mask.topHeightPx, 0.001f)
        assertEquals(0f, mask.bottomHeightPx, 0.001f)
    }

    @Test
    fun `vertical frame mask is hidden when no holds exist`() {
        val mask = calculateVerticalVideoFrameMask(
            holds = emptyList(),
            contentRect = VideoContentRect(left = 0f, top = 0f, width = 320f, height = 1_000f),
            videoAspectRatio = 9f / 16f,
            safeInsetPx = 24f
        )

        assertFalse(mask.isVisible)
        assertEquals(0f, mask.topHeightPx, 0.001f)
        assertEquals(0f, mask.bottomHeightPx, 0.001f)
    }

    @Test
    fun `vertical frame mask is hidden for landscape video`() {
        val mask = calculateVerticalVideoFrameMask(
            holds = listOf(holdNumbered(top = 0.3f, bottom = 0.4f)),
            contentRect = VideoContentRect(left = 0f, top = 0f, width = 1_000f, height = 560f),
            videoAspectRatio = 16f / 9f,
            safeInsetPx = 24f
        )

        assertFalse(mask.isVisible)
        assertEquals(0f, mask.topHeightPx, 0.001f)
        assertEquals(0f, mask.bottomHeightPx, 0.001f)
    }

    private fun poseAt(frameTimeMs: Long): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = listOf(
            PoseLandmark(index = 11, x = 0.3f, y = 0.4f, z = 0f),
            PoseLandmark(index = 12, x = 0.6f, y = 0.4f, z = 0f)
        )
    )

    private fun holdNumbered(top: Float, bottom: Float): HoldNumbered = HoldNumbered(
        hold = Hold(
            holdNo = 1,
            boundingBox = Hold.BoundingBox(
                left = 0.3f,
                top = top,
                right = 0.6f,
                bottom = bottom
            ),
            confidence = 0.9f
        ),
        progress = 0.5f,
        axisDistance = 0.1f,
        role = HoldRole.NORMAL
    )
}
