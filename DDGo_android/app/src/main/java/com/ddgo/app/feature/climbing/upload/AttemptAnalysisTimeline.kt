package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation

internal const val POSE_END_PREVIEW_LOOKBACK_MS = 3_000L

private const val PERSON_OBSERVATION_DESCRIPTION = "사람이 처음 안정적으로 관찰된 지점"
private const val CLIMB_END_DESCRIPTION = "등반 종료 지점"

internal fun buildAttemptTimelinePoints(
    personObservationStartTimeMs: Long?,
    endTimeMs: Long?
): List<AnalysisPoint> {
    val points = mutableListOf<AnalysisPoint>()

    personObservationStartTimeMs?.let { startTimeMs ->
        points += AnalysisPoint(
            index = 0,
            timeMs = startTimeMs,
            description = PERSON_OBSERVATION_DESCRIPTION,
            kind = AnalysisPointKind.PERSON_OBSERVATION_START
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
    personObservationStartTimeMs = null,
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

internal fun resolveInitialAttemptPlaybackStartTimeMs(
    personObservationStartTimeMs: Long?,
    poseTimestamps: List<Long>
): Long? {
    val anchorMs = personObservationStartTimeMs ?: return null
    if (poseTimestamps.isEmpty()) {
        return anchorMs.coerceAtLeast(0L)
    }
    return poseTimestamps.findNearestTimestamp(anchorMs.coerceAtLeast(0L))
        ?: anchorMs.coerceAtLeast(0L)
}
