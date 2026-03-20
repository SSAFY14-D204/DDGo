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
    config: HandPeakConfig = HandPeakConfig()
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
    val allowedFlags = validPoints.map { point ->
        isHandPeakCandidateAllowed(points[point.index], config)
    }
    val smoothedValues = smoothSignal(
        values = values,
        medianWindow = config.handPeakMedianWindowFrames,
        meanWindow = config.handPeakMeanWindowFrames
    )
    val globalSupportCount = countSupportingValues(
        values = values,
        targetHeight = globalTop.handHeight,
        bandRadius = config.handPeakBandRadius
    )
    val globalTopAllowed = isHandPeakCandidateAllowed(points[globalTop.index], config)

    if (globalTopAllowed && globalSupportCount >= config.handTopMinSupportCount) {
        val endIndex = findLastSupportedEndIndex(
            timesMs = timesMs,
            values = values,
            allowedFlags = allowedFlags,
            targetHeight = globalTop.handHeight,
            bandRadius = config.handPeakBandRadius,
            minDurationMs = config.handEndMinDurationMs
        )
        return HandPeakAnnotation(
            globalTopTimeMs = globalTop.frameTimeMs,
            globalTopHeight = globalTop.handHeight,
            selectedTopTimeMs = globalTop.frameTimeMs,
            selectedTopHeight = globalTop.handHeight,
            supportCount = globalSupportCount,
            endTimeMs = endIndex?.let(timesMs::get),
            endHeight = endIndex?.let(values::get),
            validTopFound = true
        )
    }

    val candidateIndices = findLocalPeakIndices(smoothedValues)
        .filter { candidateIndex ->
            candidateIndex != globalTopIndex &&
                isHandPeakCandidateAllowed(points[validPoints[candidateIndex].index], config)
        }
        .sortedByDescending { candidateIndex -> smoothedValues[candidateIndex] }

    for (candidateIndex in candidateIndices) {
        val candidateHeight = values[candidateIndex]
        val supportCount = countSupportingValues(
            values = values,
            targetHeight = candidateHeight,
            bandRadius = config.handPeakBandRadius
        )
        if (supportCount < config.handTopMinSupportCount) {
            continue
        }

        val endIndex = findLastSupportedEndIndex(
            timesMs = timesMs,
            values = values,
            allowedFlags = allowedFlags,
            targetHeight = candidateHeight,
            bandRadius = config.handPeakBandRadius,
            minDurationMs = config.handEndMinDurationMs
        )
        return HandPeakAnnotation(
            globalTopTimeMs = globalTop.frameTimeMs,
            globalTopHeight = globalTop.handHeight,
            selectedTopTimeMs = timesMs[candidateIndex],
            selectedTopHeight = candidateHeight,
            supportCount = supportCount,
            endTimeMs = endIndex?.let(timesMs::get),
            endHeight = endIndex?.let(values::get),
            validTopFound = true
        )
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
