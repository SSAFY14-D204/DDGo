package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.usecase.StallSegmentAnnotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptResultScreenTest {

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
}
