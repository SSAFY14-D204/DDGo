package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun poseAt(frameTimeMs: Long): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = listOf(
            PoseLandmark(index = 11, x = 0.3f, y = 0.4f, z = 0f),
            PoseLandmark(index = 12, x = 0.6f, y = 0.4f, z = 0f)
        )
    )
}
