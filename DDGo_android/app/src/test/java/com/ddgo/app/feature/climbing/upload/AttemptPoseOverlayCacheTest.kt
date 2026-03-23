package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptPoseOverlayCacheTest {

    @Test
    fun `temporal validity filter removes isolated two dimensional detections`() {
        val validityFrames = buildPoseValidityFrames(
            sequence = aiPoseSequence(
                aiFrame(0L, hasWorldLandmarks = true),
                aiFrame(1_000L, hasWorldLandmarks = true),
                aiFrame(3_000L, poseDetected = false, hasWorldLandmarks = false)
            )
        )

        assertEquals(3, validityFrames.size)
        assertFalse(validityFrames[0].passesTemporalNoiseFilter)
        assertFalse(validityFrames[1].passesTemporalNoiseFilter)
        assertFalse(validityFrames[0].isValidForOverlay)
        assertFalse(validityFrames[1].isValidForAi)
    }

    @Test
    fun `overlay and endpoint keep 2d-supported frame without full world landmarks`() {
        val validityFrames = buildPoseValidityFrames(
            sequence = aiPoseSequence(
                aiFrame(0L, hasWorldLandmarks = true),
                aiFrame(500L, hasWorldLandmarks = false),
                aiFrame(1_000L, hasWorldLandmarks = true)
            )
        )

        assertTrue(validityFrames[1].passesTemporalNoiseFilter)
        assertTrue(validityFrames[1].isValidForOverlay)
        assertTrue(validityFrames[1].isValidForEndpoint)
        assertFalse(validityFrames[1].isValidForAi)
    }

    @Test
    fun `smoothed poses apply centered smoothing and keep anchor confidence metadata`() {
        val rawPoses = listOf(
            poseWithLandmark(0L, 23, x = 0.10f, y = 0.20f, z = 0.30f, visibility = 0.51f, presence = 0.61f),
            poseWithLandmark(100L, 23, x = 0.20f, y = 0.30f, z = 0.40f, visibility = 0.52f, presence = 0.62f),
            poseWithLandmark(200L, 23, x = 0.90f, y = 0.95f, z = 1.00f, visibility = 0.53f, presence = 0.63f),
            poseWithLandmark(300L, 23, x = 0.30f, y = 0.40f, z = 0.50f, visibility = 0.54f, presence = 0.64f),
            poseWithLandmark(400L, 23, x = 0.40f, y = 0.50f, z = 0.60f, visibility = 0.55f, presence = 0.65f)
        )

        val smoothedLandmark = buildSmoothedPoses(rawPoses)[2].landmarks.single()

        assertTrue(smoothedLandmark.x < 0.90f)
        assertTrue(smoothedLandmark.y < 0.95f)
        assertTrue(smoothedLandmark.z < 1.00f)
        assertEquals(0.53f, smoothedLandmark.visibility ?: 0f, 0f)
        assertEquals(0.63f, smoothedLandmark.presence ?: 0f, 0f)
    }

    @Test
    fun `smoothed poses do not cross missing landmark gaps`() {
        val rawPoses = listOf(
            poseWithLandmark(0L, 23, x = 0.20f, y = 0.30f, z = 0.40f),
            Pose(frameTimeMs = 100L, landmarks = emptyList()),
            poseWithLandmark(200L, 23, x = 0.80f, y = 0.90f, z = 1.00f)
        )

        val smoothedPoses = buildSmoothedPoses(rawPoses)

        assertEquals(0.20f, smoothedPoses[0].landmarks.single().x, 0f)
        assertTrue(smoothedPoses[1].landmarks.isEmpty())
        assertEquals(0.80f, smoothedPoses[2].landmarks.single().x, 0f)
    }

    @Test
    fun `filtered ai sequence keeps only AI-complete valid frames`() {
        val rawSequence = aiPoseSequence(
            aiFrame(0L, hasWorldLandmarks = true),
            aiFrame(500L, hasWorldLandmarks = false),
            aiFrame(1_000L, hasWorldLandmarks = true)
        )
        val validityFrames = buildPoseValidityFrames(rawSequence)

        val filteredSequence = rawSequence.filterWithValidity(
            validityFrames = validityFrames,
            selector = PoseValidityFrame::isValidForAi
        )

        assertEquals(listOf(0L, 1_000L), filteredSequence.frames.map(AiPoseFrame::timestampMs))
    }

    @Test
    fun `overlay trail segments split when filtered frame gap is too large`() {
        val overlayCache = buildAttemptPoseOverlayCache(
            poses = listOf(
                poseWithHipCenter(0L, 0.20f, 0.30f),
                poseWithHipCenter(100L, 0.22f, 0.31f),
                poseWithHipCenter(200L, 0.24f, 0.32f),
                poseWithHipCenter(600L, 0.36f, 0.46f),
                poseWithHipCenter(700L, 0.38f, 0.48f)
            )
        )

        assertEquals(10_000L, overlayCache.trailWindowMs)
        assertEquals(listOf(0L, 100L, 200L, 600L, 700L), overlayCache.frameTimesMs)
        assertEquals(5, overlayCache.trackSeriesByKind[OverlayTrackKind.HIP_CENTER]?.samples?.size)
        assertEquals(listOf(0L, 100L, 200L, 600L, 700L), overlayCache.trackSeriesByKind[OverlayTrackKind.HIP_CENTER]?.sampleTimesMs)

        val segments = buildOverlayTrackTrailSegments(
            cache = overlayCache,
            anchorTimeMs = 700L,
            kind = OverlayTrackKind.HIP_CENTER
        )

        assertEquals(2, segments.size)
        assertEquals(3, segments[0].size)
        assertEquals(2, segments[1].size)
    }

    @Test
    fun `nearest overlay frame hides overlay when snap gap is too large`() {
        val overlayCache = buildAttemptPoseOverlayCache(
            poses = listOf(
                poseWithHipCenter(0L, 0.20f, 0.30f),
                poseWithHipCenter(100L, 0.22f, 0.31f)
            )
        )

        assertNull(
            findNearestOverlayFrameForPlayback(
                cache = overlayCache,
                positionMs = 500L
            )
        )

        assertNotNull(
            findNearestOverlayFrameForPlayback(
                cache = overlayCache,
                positionMs = 120L
            )
        )
    }

    @Test
    fun `nearest overlay frame uses sorted timestamps from cache`() {
        val overlayCache = buildAttemptPoseOverlayCache(
            poses = listOf(
                poseWithHipCenter(300L, 0.30f, 0.40f),
                poseWithHipCenter(100L, 0.20f, 0.30f),
                poseWithHipCenter(200L, 0.25f, 0.35f)
            )
        )

        val nearest = findNearestOverlayFrameForPlayback(
            cache = overlayCache,
            positionMs = 210L
        )

        assertEquals(listOf(100L, 200L, 300L), overlayCache.frameTimesMs)
        assertEquals(200L, nearest?.frameTimeMs)
    }

    private fun aiPoseSequence(vararg frames: AiPoseFrame): AiPoseSequence = AiPoseSequence(
        source = AiPayloadSource(
            uri = "file:///attempt.mp4",
            videoUri = "file:///attempt.mp4",
            generator = "test",
            exportedAtIso = "2026-03-23T00:00:00Z"
        ),
        videoMetadata = AiVideoMetadata(
            frameWidth = 1080,
            frameHeight = 1920,
            totalFrames = frames.size,
            processedFrames = frames.size,
            analysisFpsLimit = 10
        ),
        frames = frames.toList()
    )

    private fun aiFrame(
        timestampMs: Long,
        poseDetected: Boolean = true,
        hasWorldLandmarks: Boolean
    ): AiPoseFrame = AiPoseFrame(
        frameIndex = (timestampMs / 100L).toInt(),
        timestampMs = timestampMs,
        poseDetected = poseDetected,
        poseLandmarks = if (poseDetected) sampleAiLandmarks(offset = 0f) else emptyList(),
        poseWorldLandmarks = if (poseDetected && hasWorldLandmarks) {
            sampleAiLandmarks(offset = 1f)
        } else {
            emptyList()
        }
    )

    private fun sampleAiLandmarks(offset: Float): List<AiLandmark3D> = List(33) { index ->
        AiLandmark3D(
            index = index,
            x = offset + (index * 0.01f),
            y = offset + (index * 0.02f),
            z = offset + (index * 0.03f),
            visibility = 0.9f,
            presence = 0.8f
        )
    }

    private fun poseWithHipCenter(frameTimeMs: Long, centerX: Float, centerY: Float): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = listOf(
            PoseLandmark(index = 23, x = centerX - 0.02f, y = centerY, z = 0f),
            PoseLandmark(index = 24, x = centerX + 0.02f, y = centerY, z = 0f)
        )
    )

    private fun poseWithLandmark(
        frameTimeMs: Long,
        index: Int,
        x: Float,
        y: Float,
        z: Float,
        visibility: Float? = null,
        presence: Float? = null
    ): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = listOf(
            PoseLandmark(
                index = index,
                x = x,
                y = y,
                z = z,
                visibility = visibility,
                presence = presence
            )
        )
    )
}
