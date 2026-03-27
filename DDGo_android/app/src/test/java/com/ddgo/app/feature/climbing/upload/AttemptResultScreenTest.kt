package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.StallSegmentAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptResultScreenTest {

    @Test
    fun `portrait video crop spec returns expected fractions`() {
        val cropSpec = calculateVerticalVideoViewportCropSpec(
            holds = listOf(
                holdNumbered(top = 0.20f, bottom = 0.35f),
                holdNumbered(top = 0.45f, bottom = 0.80f)
            ),
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 50f
        )

        assertTrue(cropSpec.isActive)
        assertEquals(0.15f, cropSpec.topCropFraction, 0.0001f)
        assertEquals(0.15f, cropSpec.bottomCropFraction, 0.0001f)
        assertEquals(0.70f, cropSpec.visibleHeightFraction, 0.0001f)
        assertEquals((9f / 16f) / 0.70f, cropSpec.viewportAspectRatio, 0.0001f)
    }

    @Test
    fun `crop spec stays inactive when holds are missing`() {
        val cropSpec = calculateVerticalVideoViewportCropSpec(
            holds = emptyList(),
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 50f
        )

        assertFalse(cropSpec.isActive)
        assertEquals(1f, cropSpec.visibleHeightFraction, 0f)
        assertEquals(9f / 16f, cropSpec.viewportAspectRatio, 0f)
    }

    @Test
    fun `crop spec stays inactive for landscape video`() {
        val cropSpec = calculateVerticalVideoViewportCropSpec(
            holds = listOf(holdNumbered(top = 0.10f, bottom = 0.90f)),
            videoAspectRatio = 16f / 9f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 50f
        )

        assertFalse(cropSpec.isActive)
        assertEquals(1f, cropSpec.visibleHeightFraction, 0f)
        assertEquals(16f / 9f, cropSpec.viewportAspectRatio, 0f)
    }

    @Test
    fun `larger safe inset expands crop boundaries predictably`() {
        val smallInset = calculateVerticalVideoViewportCropSpec(
            holds = listOf(
                holdNumbered(top = 0.25f, bottom = 0.35f),
                holdNumbered(top = 0.55f, bottom = 0.70f)
            ),
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 20f
        )
        val largeInset = calculateVerticalVideoViewportCropSpec(
            holds = listOf(
                holdNumbered(top = 0.25f, bottom = 0.35f),
                holdNumbered(top = 0.55f, bottom = 0.70f)
            ),
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 80f
        )

        assertTrue(largeInset.topCropFraction < smallInset.topCropFraction)
        assertTrue(largeInset.bottomCropFraction < smallInset.bottomCropFraction)
        assertTrue(largeInset.visibleHeightFraction > smallInset.visibleHeightFraction)
    }

    @Test
    fun `viewport aspect ratio matches visible height fraction`() {
        val cropSpec = calculateVerticalVideoViewportCropSpec(
            holds = listOf(
                holdNumbered(top = 0.10f, bottom = 0.22f),
                holdNumbered(top = 0.50f, bottom = 0.68f)
            ),
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 800f,
            safeInsetPx = 40f
        )

        assertEquals(
            (9f / 16f) / cropSpec.visibleHeightFraction,
            cropSpec.viewportAspectRatio,
            0.0001f
        )
    }

    @Test
    fun `raw bounds crop spec uses all detected hold extremes`() {
        val cropSpec = calculateVerticalVideoViewportCropSpecFromBounds(
            topFraction = 0.10f,
            bottomFraction = 0.90f,
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 24f
        )

        assertTrue(cropSpec.isActive)
        assertEquals(0.076f, cropSpec.topCropFraction, 0.0001f)
        assertEquals(0.076f, cropSpec.bottomCropFraction, 0.0001f)
        assertEquals(0.848f, cropSpec.visibleHeightFraction, 0.0001f)
        assertEquals((9f / 16f) / 0.848f, cropSpec.viewportAspectRatio, 0.0001f)
    }

    @Test
    fun `raw bounds crop spec falls back when bounds are invalid`() {
        val cropSpec = calculateVerticalVideoViewportCropSpecFromBounds(
            topFraction = 0.65f,
            bottomFraction = 0.60f,
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 24f
        )

        assertFalse(cropSpec.isActive)
        assertEquals(1f, cropSpec.visibleHeightFraction, 0f)
        assertEquals(9f / 16f, cropSpec.viewportAspectRatio, 0f)
    }

    @Test
    fun `raw bounds crop spec stays inactive for landscape video`() {
        val cropSpec = calculateVerticalVideoViewportCropSpecFromBounds(
            topFraction = 0.10f,
            bottomFraction = 0.90f,
            videoAspectRatio = 16f / 9f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 24f
        )

        assertFalse(cropSpec.isActive)
        assertEquals(1f, cropSpec.visibleHeightFraction, 0f)
        assertEquals(16f / 9f, cropSpec.viewportAspectRatio, 0f)
    }

    @Test
    fun `raw bounds crop spec allows larger bottom inset`() {
        val symmetricInset = calculateVerticalVideoViewportCropSpecFromBounds(
            topFraction = 0.20f,
            bottomFraction = 0.70f,
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            safeInsetPx = 24f
        )
        val largerBottomInset = calculateVerticalVideoViewportCropSpecFromBounds(
            topFraction = 0.20f,
            bottomFraction = 0.70f,
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            topSafeInsetPx = 24f,
            bottomSafeInsetPx = 56f
        )

        assertEquals(symmetricInset.topCropFraction, largerBottomInset.topCropFraction, 0.0001f)
        assertTrue(largerBottomInset.bottomCropFraction < symmetricInset.bottomCropFraction)
        assertTrue(largerBottomInset.visibleHeightFraction > symmetricInset.visibleHeightFraction)
    }

    @Test
    fun `raw bounds crop spec keeps fractions clamped when bottom inset is large`() {
        val cropSpec = calculateVerticalVideoViewportCropSpecFromBounds(
            topFraction = 0.25f,
            bottomFraction = 0.98f,
            videoAspectRatio = 9f / 16f,
            fullVideoHeightPx = 1_000f,
            topSafeInsetPx = 24f,
            bottomSafeInsetPx = 80f
        )

        assertTrue(cropSpec.isActive)
        assertTrue(cropSpec.topCropFraction in 0f..1f)
        assertTrue(cropSpec.bottomCropFraction in 0f..1f)
        assertTrue(cropSpec.visibleHeightFraction in 0f..1f)
        assertEquals(0f, cropSpec.bottomCropFraction, 0.0001f)
    }

    @Test
    fun `raw vertical crop bounds use average of top and bottom five detections`() {
        val rawBounds = calculateRawVerticalCropBounds(
            listOf(
                rawHold(top = 0.05f, bottom = 0.95f),
                rawHold(top = 0.10f, bottom = 0.92f),
                rawHold(top = 0.15f, bottom = 0.89f),
                rawHold(top = 0.20f, bottom = 0.86f),
                rawHold(top = 0.25f, bottom = 0.83f),
                rawHold(top = 0.30f, bottom = 0.80f),
                rawHold(top = 0.35f, bottom = 0.77f)
            )
        )

        assertTrue(rawBounds != null)
        assertEquals(0.15f, rawBounds!!.topFraction, 0.0001f)
        assertEquals(0.89f, rawBounds.bottomFraction, 0.0001f)
    }

    @Test
    fun `hybrid crop bounds prefer tighter selected hold window over raw averages`() {
        val resolvedBounds = resolveHybridVerticalCropBounds(
            rawBounds = RawVerticalCropBounds(
                topFraction = 0.18f,
                bottomFraction = 0.84f
            ),
            selectedHolds = listOf(
                holdNumbered(top = 0.52f, bottom = 0.58f),
                holdNumbered(top = 0.60f, bottom = 0.74f)
            )
        )

        assertTrue(resolvedBounds != null)
        assertEquals(0.52f, resolvedBounds!!.topFraction, 0.0001f)
        assertEquals(0.74f, resolvedBounds.bottomFraction, 0.0001f)
    }

    @Test
    fun `cropped viewport placement uses the intended middle segment`() {
        val placement = calculateCroppedVideoViewportPlacement(
            fullVideoWidthPx = 10,
            fullVideoHeightPx = 10,
            cropSpec = VideoViewportCropSpec(
                topCropFraction = 0.4f,
                bottomCropFraction = 0.2f,
                visibleHeightFraction = 0.4f,
                viewportAspectRatio = 1f,
                isActive = true
            ),
            topCropPx = 4f
        )

        assertEquals(10, placement.fullVideoHeightPx)
        assertEquals(4, placement.viewportHeightPx)
        assertEquals(-4, placement.transformedLayerOffsetYPx)
    }

    @Test
    fun `cropped viewport placement keeps full height when crop is inactive`() {
        val placement = calculateCroppedVideoViewportPlacement(
            fullVideoWidthPx = 10,
            fullVideoHeightPx = 10,
            cropSpec = uncroppedVideoViewportCropSpec(videoAspectRatio = 9f / 16f),
            topCropPx = 0f
        )

        assertEquals(10, placement.fullVideoHeightPx)
        assertEquals(10, placement.viewportHeightPx)
        assertEquals(0, placement.transformedLayerOffsetYPx)
    }

    @Test
    fun `analysis points use raw hand end timestamp`() {
        val annotation = HandPeakAnnotation(
            globalTopTimeMs = 8_000L,
            globalTopHeight = 0.71,
            selectedTopTimeMs = 7_500L,
            selectedTopHeight = 0.69,
            supportCount = 12,
            endTimeMs = 5_000L,
            endHeight = 0.67,
            validTopFound = true
        )

        val point = annotation.toAnalysisPoints().single()

        assertEquals(5_000L, point.timeMs)
        assertEquals(AnalysisPointKind.CLIMB_END, point.kind)
    }

    @Test
    fun `analysis points stay empty when raw hand endpoint is missing`() {
        val annotation = HandPeakAnnotation(
            globalTopTimeMs = 8_000L,
            globalTopHeight = 0.71,
            selectedTopTimeMs = 7_500L,
            selectedTopHeight = 0.69,
            supportCount = 12,
            endTimeMs = null,
            endHeight = null,
            validTopFound = true
        )

        assertTrue(annotation.toAnalysisPoints().isEmpty())
    }

    @Test
    fun `buildAttemptTimelinePoints sorts timestamps and reindexes markers`() {
        val points = buildAttemptTimelinePoints(
            wallArrivalTimeMs = 2_000L,
            stallSegment = null,
            endTimeMs = 10_000L
        )

        assertEquals(2, points.size)
        assertEquals(1, points[0].index)
        assertEquals(2_000L, points[0].timeMs)
        assertEquals(AnalysisPointKind.PERSON_OBSERVATION_START, points[0].kind)
        assertEquals(2, points[1].index)
        assertEquals(10_000L, points[1].timeMs)
        assertEquals(AnalysisPointKind.CLIMB_END, points[1].kind)
    }

    @Test
    fun `buildAttemptTimelinePoints inserts stall point between wall arrival and end`() {
        val points = buildAttemptTimelinePoints(
            wallArrivalTimeMs = 2_000L,
            stallSegment = StallSegmentAnnotation(
                startTimeMs = 5_000L,
                endTimeMs = 6_500L,
                durationMs = 1_500L,
                score = 4.2f
            ),
            endTimeMs = 10_000L
        )

        assertEquals(listOf(2_000L, 5_000L, 10_000L), points.map { it.timeMs })
        assertEquals(listOf(1, 2, 3), points.map { it.index })
        assertEquals(AnalysisPointKind.PERSON_OBSERVATION_START, points[0].kind)
        assertEquals(AnalysisPointKind.STALL, points[1].kind)
        assertEquals(AnalysisPointKind.CLIMB_END, points[2].kind)
    }

    @Test
    fun `preview start subtracts lookback from anchor time`() {
        assertEquals(9_000L, previewStartMs(anchorMs = 12_000L))
    }

    @Test
    fun `preview start clamps to zero when anchor is smaller than lookback`() {
        assertEquals(0L, previewStartMs(anchorMs = 2_000L))
    }

    @Test
    fun `climb end point seeks from preview start`() {
        assertEquals(
            9_000L,
            resolveAnalysisSeekTimeMs(
                point = AnalysisPoint(
                    index = 1,
                    timeMs = 12_000L,
                    description = "end",
                    kind = AnalysisPointKind.CLIMB_END
                ),
                usesPoseDetectorTimeline = true
            )
        )
    }

    @Test
    fun `start point seeks directly to anchor time`() {
        assertEquals(
            12_000L,
            resolveAnalysisSeekTimeMs(
                point = AnalysisPoint(
                    index = 1,
                    timeMs = 12_000L,
                    description = "start",
                    kind = AnalysisPointKind.PERSON_OBSERVATION_START
                ),
                usesPoseDetectorTimeline = true
            )
        )
    }

    @Test
    fun `stall point seeks directly to anchor time`() {
        assertEquals(
            12_000L,
            resolveAnalysisSeekTimeMs(
                point = AnalysisPoint(
                    index = 1,
                    timeMs = 12_000L,
                    description = "stall",
                    kind = AnalysisPointKind.STALL
                ),
                usesPoseDetectorTimeline = true
            )
        )
    }

    @Test
    fun `non detector timeline seeks to anchor time`() {
        assertEquals(
            12_000L,
            resolveAnalysisSeekTimeMs(
                point = AnalysisPoint(
                    index = 1,
                    timeMs = 12_000L,
                    description = "generic"
                ),
                usesPoseDetectorTimeline = false
            )
        )
    }

    fun `playback active card stays on previous point until next timestamp`() {
        val points = listOf(
            AnalysisPoint(index = 1, timeMs = 21_000L, description = "1"),
            AnalysisPoint(index = 2, timeMs = 48_000L, description = "2")
        )

        assertEquals(
            0,
            resolvePlaybackActiveAnalysisCardIndex(
                points = points,
                displayedPositionMs = 30_000L
            )
        )
    }

    @Test
    fun `last tapped card stays active even when preview seek lands earlier`() {
        val points = listOf(
            AnalysisPoint(
                index = 1,
                timeMs = 12_000L,
                description = "start",
                kind = AnalysisPointKind.PERSON_OBSERVATION_START
            ),
            AnalysisPoint(
                index = 2,
                timeMs = 15_000L,
                description = "end",
                kind = AnalysisPointKind.CLIMB_END
            )
        )

        assertEquals(
            1,
            resolveActiveAnalysisCardIndex(
                points = points,
                displayedPositionMs = previewStartMs(15_000L),
                tappedCardOverrideIdx = 1
            )
        )
    }

    @Test
    fun `tapped override clears after crossing next timestamp`() {
        val points = listOf(
            AnalysisPoint(index = 1, timeMs = 21_000L, description = "1"),
            AnalysisPoint(index = 2, timeMs = 48_000L, description = "2"),
            AnalysisPoint(index = 3, timeMs = 66_000L, description = "3")
        )

        assertEquals(
            2,
            resolveActiveAnalysisCardIndex(
                points = points,
                displayedPositionMs = 66_000L,
                tappedCardOverrideIdx = 1
            )
        )
    }

    @Test
    fun `cleared override falls back to playback active card`() {
        val points = listOf(
            AnalysisPoint(index = 1, timeMs = 21_000L, description = "1"),
            AnalysisPoint(index = 2, timeMs = 48_000L, description = "2")
        )

        assertEquals(
            1,
            resolveActiveAnalysisCardIndex(
                points = points,
                displayedPositionMs = 52_000L,
                tappedCardOverrideIdx = -1
            )
        )
    }

    @Test
    fun `initial playback start prefers wall arrival timestamp`() {
        assertEquals(
            2_000L,
            resolveInitialAttemptPlaybackStartTimeMs(
                wallArrivalTimeMs = 2_100L,
                fallbackPersonObservationStartTimeMs = 1_100L,
                poseTimestamps = listOf(0L, 2_000L, 4_000L)
            )
        )
    }

    @Test
    fun `initial playback start falls back to person observation timestamp`() {
        assertEquals(
            2_000L,
            resolveInitialAttemptPlaybackStartTimeMs(
                wallArrivalTimeMs = null,
                fallbackPersonObservationStartTimeMs = 2_100L,
                poseTimestamps = listOf(0L, 2_000L, 4_000L)
            )
        )
    }

    private fun holdNumbered(
        top: Float,
        bottom: Float
    ): HoldNumbered = HoldNumbered(
        hold = Hold(
            holdNo = 1,
            boundingBox = Hold.BoundingBox(
                left = 0.20f,
                top = top,
                right = 0.80f,
                bottom = bottom
            ),
            confidence = 1f
        ),
        progress = 0f,
        axisDistance = 0f,
        role = HoldRole.NORMAL
    )

    private fun rawHold(
        top: Float,
        bottom: Float
    ): Hold = Hold(
        holdNo = 0,
        boundingBox = Hold.BoundingBox(
            left = 0.10f,
            top = top,
            right = 0.20f,
            bottom = bottom
        ),
        confidence = 1f
    )
}
