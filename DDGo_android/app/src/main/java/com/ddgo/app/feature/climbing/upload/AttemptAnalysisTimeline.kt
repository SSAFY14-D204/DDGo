package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.usecase.StallSegmentAnnotation

internal const val POSE_END_PREVIEW_LOOKBACK_MS = 3_000L

private const val WALL_ARRIVAL_DESCRIPTION =
    "\uB4F1\uBC18 \uC900\uBE44 \uC2DC\uC810"
private const val STALL_DESCRIPTION =
    "\uBAB8\uC774 \uC815\uCCB4\uB41C \uAD6C\uAC04"
private const val CLIMB_END_DESCRIPTION =
    "\uB4F1\uBC18 \uC885\uB8CC \uC9C0\uC810"

internal fun buildAttemptTimelinePoints(
    wallArrivalTimeMs: Long?,
    stallSegment: StallSegmentAnnotation?,
    endTimeMs: Long?
): List<AnalysisPoint> {
    val points = mutableListOf<AnalysisPoint>()

    wallArrivalTimeMs?.let { startTimeMs ->
        points += AnalysisPoint(
            index = 0,
            timeMs = startTimeMs,
            description = WALL_ARRIVAL_DESCRIPTION,
            kind = AnalysisPointKind.PERSON_OBSERVATION_START
        )
    }

    stallSegment?.let { segment ->
        points += AnalysisPoint(
            index = 0,
            timeMs = segment.startTimeMs,
            description = STALL_DESCRIPTION,
            kind = AnalysisPointKind.STALL
        )
    }

    endTimeMs?.let { safeEndTimeMs ->
        points += AnalysisPoint(
            index = 0,
            timeMs = safeEndTimeMs,
            description = CLIMB_END_DESCRIPTION,
            kind = AnalysisPointKind.CLIMB_END
        )
    }

    return points
        .sortedBy { point -> point.timeMs }
        .mapIndexed { index, point -> point.copy(index = index + 1) }
}

internal fun HandPeakAnnotation?.toAnalysisPoints(): List<AnalysisPoint> = buildAttemptTimelinePoints(
    wallArrivalTimeMs = null,
    stallSegment = null,
    endTimeMs = this?.endTimeMs
)

internal fun previewStartMs(
    anchorMs: Long,
    lookbackMs: Long = POSE_END_PREVIEW_LOOKBACK_MS
): Long = (anchorMs - lookbackMs).coerceAtLeast(0L)

internal fun resolveAnalysisSeekTimeMs(
    point: AnalysisPoint,
    usesPoseDetectorTimeline: Boolean
): Long = when {
    !usesPoseDetectorTimeline -> point.timeMs.coerceAtLeast(0L)
    point.kind == AnalysisPointKind.CLIMB_END -> previewStartMs(point.timeMs)
    else -> point.timeMs.coerceAtLeast(0L)
}

internal fun resolvePlaybackActiveAnalysisCardIndex(
    points: List<AnalysisPoint>,
    displayedPositionMs: Long
): Int = points.indexOfLast { point -> point.timeMs <= displayedPositionMs }

internal fun shouldKeepTappedAnalysisCardOverride(
    points: List<AnalysisPoint>,
    tappedCardOverrideIdx: Int,
    displayedPositionMs: Long
): Boolean {
    if (tappedCardOverrideIdx !in points.indices) {
        return false
    }

    val nextPointTimeMs = points.getOrNull(tappedCardOverrideIdx + 1)?.timeMs ?: return true
    return displayedPositionMs < nextPointTimeMs
}

internal fun resolveActiveAnalysisCardIndex(
    points: List<AnalysisPoint>,
    displayedPositionMs: Long,
    tappedCardOverrideIdx: Int
): Int {
    val playbackActiveIdx = resolvePlaybackActiveAnalysisCardIndex(
        points = points,
        displayedPositionMs = displayedPositionMs
    )
    return if (
        shouldKeepTappedAnalysisCardOverride(
            points = points,
            tappedCardOverrideIdx = tappedCardOverrideIdx,
            displayedPositionMs = displayedPositionMs
        )
    ) {
        tappedCardOverrideIdx
    } else {
        playbackActiveIdx
    }
}

internal fun resolveInitialAttemptPlaybackStartTimeMs(
    wallArrivalTimeMs: Long?,
    fallbackPersonObservationStartTimeMs: Long?,
    poseTimestamps: List<Long>
): Long? {
    val anchorMs = (wallArrivalTimeMs ?: fallbackPersonObservationStartTimeMs) ?: return null
    if (poseTimestamps.isEmpty()) {
        return anchorMs.coerceAtLeast(0L)
    }
    return poseTimestamps.findNearestTimestamp(anchorMs.coerceAtLeast(0L))
        ?: anchorMs.coerceAtLeast(0L)
}
