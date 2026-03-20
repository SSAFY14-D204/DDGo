package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
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
            personObservationStartTimeMs = 2_000L,
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
                    description = "등반 종료 지점",
                    kind = AnalysisPointKind.CLIMB_END
                ),
                usesPoseDetectorTimeline = true
            )
        )
    }

    @Test
    fun `person observation point seeks directly to anchor time`() {
        assertEquals(
            12_000L,
            resolveAnalysisSeekTimeMs(
                point = AnalysisPoint(
                    index = 1,
                    timeMs = 12_000L,
                    description = "사람이 처음 안정적으로 관찰된 지점",
                    kind = AnalysisPointKind.PERSON_OBSERVATION_START
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

    @Test
    fun `initial playback start snaps to nearest pose timestamp`() {
        assertEquals(
            2_000L,
            resolveInitialAttemptPlaybackStartTimeMs(
                personObservationStartTimeMs = 2_100L,
                poseTimestamps = listOf(0L, 2_000L, 4_000L)
            )
        )
    }
}
