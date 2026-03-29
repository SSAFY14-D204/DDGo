package com.ddgo.app.domain.poseanalysis

internal fun smoothOptionalSeries(
    values: List<Double?>,
    medianWindow: Int,
    meanWindow: Int
): List<Double?> {
    val smoothed = MutableList<Double?>(values.size) { null }
    for ((segmentStart, segmentEnd) in groupValidIndices(values)) {
        val segmentValues = values.subList(segmentStart, segmentEnd + 1).filterNotNull()
        val segmentSmoothed = smoothSignal(
            values = segmentValues,
            medianWindow = medianWindow,
            meanWindow = meanWindow
        )
        segmentSmoothed.forEachIndexed { offset, value ->
            smoothed[segmentStart + offset] = value
        }
    }
    return smoothed
}

internal fun groupValidIndices(values: List<Double?>): List<Pair<Int, Int>> {
    val groups = mutableListOf<Pair<Int, Int>>()
    var startIndex: Int? = null

    values.forEachIndexed { index, value ->
        if (value == null) {
            if (startIndex != null) {
                groups += startIndex!! to (index - 1)
                startIndex = null
            }
            return@forEachIndexed
        }
        if (startIndex == null) {
            startIndex = index
        }
    }

    if (startIndex != null) {
        groups += startIndex!! to (values.lastIndex)
    }
    return groups
}

internal fun smoothSignal(
    values: List<Double>,
    medianWindow: Int,
    meanWindow: Int
): List<Double> = rollingMean(
    values = rollingMedian(values, medianWindow),
    window = meanWindow
)

internal fun rollingMedian(values: List<Double>, window: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    val radius = maxOf(0, window / 2)
    return values.indices.map { index ->
        val start = maxOf(0, index - radius)
        val endExclusive = minOf(values.size, index + radius + 1)
        val windowSlice = values.subList(start, endExclusive).sorted()
        val middle = windowSlice.size / 2
        if (windowSlice.size % 2 == 1) {
            windowSlice[middle]
        } else {
            (windowSlice[middle - 1] + windowSlice[middle]) / 2.0
        }
    }
}

internal fun rollingMean(values: List<Double>, window: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    val radius = maxOf(0, window / 2)
    return values.indices.map { index ->
        val start = maxOf(0, index - radius)
        val endExclusive = minOf(values.size, index + radius + 1)
        values.subList(start, endExclusive).average()
    }
}

internal fun findLocalPeakIndices(values: List<Double>): List<Int> {
    return when (values.size) {
        0 -> emptyList()
        1 -> listOf(0)
        2 -> listOf(if (values[0] >= values[1]) 0 else 1)
        else -> {
            val peaks = mutableListOf<Int>()
            for (index in 1 until values.lastIndex) {
                val value = values[index]
                if (value < values[index - 1] || value < values[index + 1]) {
                    continue
                }
                if (value > values[index - 1] || value > values[index + 1]) {
                    peaks += index
                }
            }
            if (peaks.isEmpty()) {
                peaks += values.indices.maxBy { idx -> values[idx] }
            }
            peaks
        }
    }
}

internal fun countSupportingValues(
    values: List<Double>,
    targetHeight: Double,
    bandRadius: Double,
    allowedFlags: List<Boolean>? = null
): Int {
    val effectiveAllowedFlags = allowedFlags ?: List(values.size) { true }
    return values.indices.count { index ->
        effectiveAllowedFlags[index] &&
            kotlin.math.abs(values[index] - targetHeight) <= bandRadius
    }
}

internal fun findLastSupportedEndIndex(
    timesMs: List<Long>,
    values: List<Double>,
    targetHeight: Double,
    bandRadius: Double,
    minDurationMs: Long,
    allowedFlags: List<Boolean>? = null
): Int? {
    val effectiveAllowedFlags = allowedFlags ?: List(values.size) { true }
    val inBandIndices = values.indices.filter { index ->
        effectiveAllowedFlags[index] &&
            kotlin.math.abs(values[index] - targetHeight) <= bandRadius
    }
    if (inBandIndices.isEmpty()) return null

    for ((segmentStart, segmentEnd) in groupConsecutiveIndices(inBandIndices).asReversed()) {
        val segmentDurationMs = timesMs[segmentEnd] - timesMs[segmentStart]
        if (segmentDurationMs >= minDurationMs) {
            return segmentEnd
        }
    }
    return null
}

internal fun groupConsecutiveIndices(indices: List<Int>): List<Pair<Int, Int>> {
    if (indices.isEmpty()) return emptyList()

    val segments = mutableListOf<Pair<Int, Int>>()
    var startIndex = indices.first()
    var endIndex = indices.first()

    indices.zipWithNext().forEach { (previousIndex, currentIndex) ->
        if (currentIndex == previousIndex + 1) {
            endIndex = currentIndex
        } else {
            segments += startIndex to endIndex
            startIndex = currentIndex
            endIndex = currentIndex
        }
    }
    segments += startIndex to endIndex
    return segments
}

internal fun groupTrueSegments(flags: List<Boolean>): List<Pair<Int, Int>> {
    val segments = mutableListOf<Pair<Int, Int>>()
    var startIndex: Int? = null

    flags.forEachIndexed { index, flag ->
        if (flag) {
            if (startIndex == null) {
                startIndex = index
            }
        } else if (startIndex != null) {
            segments += startIndex!! to (index - 1)
            startIndex = null
        }
    }

    if (startIndex != null) {
        segments += startIndex!! to flags.lastIndex
    }
    return segments
}
