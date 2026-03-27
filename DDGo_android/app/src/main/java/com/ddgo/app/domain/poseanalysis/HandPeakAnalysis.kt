package com.ddgo.app.domain.poseanalysis

fun extractBodyPartHeights(
    frames: List<PoseFrame>,
    config: HandPeakConfig = HandPeakConfig()
): List<FrameBodyPartHeights> {
    val sortedFrames = frames.sortedBy { frame -> frame.frameTimeMs }
    val points = sortedFrames.map { frame ->
        buildBodyPartHeightsPoint(frame, config)
    }
    return annotateFacingCamera(annotateSmoothMetrics(points, config), config)
}

fun analyzeHandPeakAndEnd(
    points: List<FrameBodyPartHeights>,
    config: HandPeakConfig = HandPeakConfig(),
    wallSegmentIdByFrameTimeMs: Map<Long, Int> = emptyMap()
): HandPeakAnnotation? {
    val validPoints = points.mapIndexedNotNull { index, point ->
        point.handHeight?.let { handHeight ->
            HandPoint(index = index, frameTimeMs = point.frameTimeMs, handHeight = handHeight)
        }
    }
    if (validPoints.isEmpty()) return null

    val globalTopIndex = validPoints.indices.maxBy { index -> validPoints[index].handHeight }
    val globalTop = validPoints[globalTopIndex]
    val timesMs = validPoints.map { point -> point.frameTimeMs }
    val values = validPoints.map { point -> point.handHeight }
    val usesWallScope = wallSegmentIdByFrameTimeMs.isNotEmpty()
    val allowedFlags = validPoints.map { point ->
        isHandPeakCandidateAllowed(points[point.index], config)
    }
    val wallSegmentIds = validPoints.map { point ->
        if (usesWallScope) {
            wallSegmentIdByFrameTimeMs[point.frameTimeMs]
        } else {
            DEFAULT_WALL_SEGMENT_ID
        }
    }
    val smoothedValues = smoothSignal(
        values = values,
        medianWindow = config.handPeakMedianWindowFrames,
        meanWindow = config.handPeakMeanWindowFrames
    )
    resolveCandidateAnnotation(
        candidateIndex = globalTopIndex,
        globalTop = globalTop,
        values = values,
        timesMs = timesMs,
        allowedFlags = allowedFlags,
        wallSegmentIds = wallSegmentIds,
        usesWallScope = usesWallScope,
        config = config
    )?.let { return it }

    val candidateIndices = findLocalPeakIndices(smoothedValues)
        .filter { candidateIndex ->
            candidateIndex != globalTopIndex &&
                allowedFlags[candidateIndex] &&
                (!usesWallScope || wallSegmentIds[candidateIndex] != null)
        }
        .sortedByDescending { candidateIndex -> smoothedValues[candidateIndex] }

    for (candidateIndex in candidateIndices) {
        resolveCandidateAnnotation(
            candidateIndex = candidateIndex,
            globalTop = globalTop,
            values = values,
            timesMs = timesMs,
            allowedFlags = allowedFlags,
            wallSegmentIds = wallSegmentIds,
            usesWallScope = usesWallScope,
            config = config
        )?.let { return it }
    }

    return HandPeakAnnotation(
        globalTopTimeMs = globalTop.frameTimeMs,
        globalTopHeight = globalTop.handHeight,
        selectedTopTimeMs = null,
        selectedTopHeight = null,
        supportCount = 0,
        endTimeMs = null,
        endHeight = null,
        validTopFound = false
    )
}

internal fun annotateSmoothMetrics(
    points: List<FrameBodyPartHeights>,
    config: HandPeakConfig
): List<FrameBodyPartHeights> {
    val handSmooth = smoothOptionalSeries(
        values = points.map { point -> point.handHeight },
        medianWindow = config.handPeakMedianWindowFrames,
        meanWindow = config.handPeakMeanWindowFrames
    )
    val torsoSmooth = smoothOptionalSeries(
        values = points.map { point -> point.torsoHeight },
        medianWindow = config.handPeakMedianWindowFrames,
        meanWindow = config.handPeakMeanWindowFrames
    )
    val footSmooth = smoothOptionalSeries(
        values = points.map { point -> point.footHeight },
        medianWindow = config.handPeakMedianWindowFrames,
        meanWindow = config.handPeakMeanWindowFrames
    )

    return points.mapIndexed { index, point ->
        point.copy(
            handHeightSmooth = handSmooth[index],
            torsoHeightSmooth = torsoSmooth[index],
            footHeightSmooth = footSmooth[index]
        )
    }
}

internal fun annotateFacingCamera(
    points: List<FrameBodyPartHeights>,
    config: HandPeakConfig
): List<FrameBodyPartHeights> {
    val candidateFlags = points.map { point -> point.torsoOrientation == TorsoOrientation.FRONT }
    if (candidateFlags.none { it }) {
        return points.map { point -> point.copy(facingCamera = false) }
    }

    val finalFlags = MutableList(points.size) { false }
    for ((segmentStart, segmentEnd) in groupTrueSegments(candidateFlags)) {
        val durationMs = points[segmentEnd].frameTimeMs - points[segmentStart].frameTimeMs
        if (durationMs < config.facingMinDurationMs) {
            continue
        }
        for (index in segmentStart..segmentEnd) {
            finalFlags[index] = true
        }
    }

    return points.mapIndexed { index, point ->
        point.copy(facingCamera = finalFlags[index])
    }
}

internal fun isHandPeakCandidateAllowed(
    point: FrameBodyPartHeights,
    config: HandPeakConfig
): Boolean {
    if (!point.facingCamera) return true
    val footHeight = point.footHeight ?: return true
    return footHeight >= config.handPeakLowFootHeightThreshold
}

private data class HandPoint(
    val index: Int,
    val frameTimeMs: Long,
    val handHeight: Double
)

private const val DEFAULT_WALL_SEGMENT_ID = 0

private fun resolveCandidateAnnotation(
    candidateIndex: Int,
    globalTop: HandPoint,
    values: List<Double>,
    timesMs: List<Long>,
    allowedFlags: List<Boolean>,
    wallSegmentIds: List<Int?>,
    usesWallScope: Boolean,
    config: HandPeakConfig
): HandPeakAnnotation? {
    if (!allowedFlags[candidateIndex]) return null

    val candidateSegmentId = wallSegmentIds[candidateIndex]
    if (usesWallScope && candidateSegmentId == null) return null

    val segmentAllowedFlags = values.indices.map { index ->
        allowedFlags[index] &&
            (!usesWallScope || wallSegmentIds[index] == candidateSegmentId)
    }
    val candidateHeight = values[candidateIndex]
    val supportCount = countSupportingValues(
        values = values,
        targetHeight = candidateHeight,
        bandRadius = config.handPeakBandRadius,
        allowedFlags = segmentAllowedFlags
    )
    if (supportCount < config.handTopMinSupportCount) return null

    val endIndex = findLastSupportedEndIndex(
        timesMs = timesMs,
        values = values,
        allowedFlags = segmentAllowedFlags,
        targetHeight = candidateHeight,
        bandRadius = config.handPeakBandRadius,
        minDurationMs = config.handEndMinDurationMs
    ) ?: return null

    return HandPeakAnnotation(
        globalTopTimeMs = globalTop.frameTimeMs,
        globalTopHeight = globalTop.handHeight,
        selectedTopTimeMs = timesMs[candidateIndex],
        selectedTopHeight = candidateHeight,
        supportCount = supportCount,
        endTimeMs = timesMs[endIndex],
        endHeight = values[endIndex],
        validTopFound = true
    )
}
