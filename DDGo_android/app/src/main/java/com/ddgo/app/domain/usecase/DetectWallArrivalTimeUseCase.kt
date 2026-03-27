package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.poseanalysis.HandPeakConfig
import com.ddgo.app.domain.poseanalysis.FrameBodyPartHeights
import com.ddgo.app.domain.poseanalysis.PoseFrame
import com.ddgo.app.domain.poseanalysis.extractBodyPartHeights
import com.ddgo.app.domain.poseanalysis.smoothOptionalSeries
import javax.inject.Inject

internal data class WallPresenceSegment(
    val id: Int,
    val startTimeMs: Long,
    val endTimeMs: Long
)

internal data class WallArrivalAnalysis(
    val arrivalTimeMs: Long? = null,
    val stableMinTorsoScale: Double? = null,
    val arrivalThreshold: Double? = null,
    val wallSegments: List<WallPresenceSegment> = emptyList(),
    val wallSegmentIdByFrameTimeMs: Map<Long, Int> = emptyMap()
)

class DetectWallArrivalTimeUseCase @Inject constructor() {
    operator fun invoke(
        frames: List<PoseFrame>,
        personObservationStartTimeMs: Long?,
        config: HandPeakConfig = HandPeakConfig(),
        supportWindowMs: Long = DEFAULT_SUPPORT_WINDOW_MS,
        minSupportCountPerSide: Int = DEFAULT_MIN_SUPPORT_COUNT_PER_SIDE,
        stableMinSampleCount: Int = DEFAULT_STABLE_MIN_SAMPLE_COUNT,
        arrivalThresholdMultiplier: Double = DEFAULT_ARRIVAL_THRESHOLD_MULTIPLIER,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
        minDurationMs: Long = DEFAULT_MIN_DURATION_MS
    ): Long? = analyze(
        frames = frames,
        personObservationStartTimeMs = personObservationStartTimeMs,
        config = config,
        supportWindowMs = supportWindowMs,
        minSupportCountPerSide = minSupportCountPerSide,
        stableMinSampleCount = stableMinSampleCount,
        arrivalThresholdMultiplier = arrivalThresholdMultiplier,
        maxGapMs = maxGapMs,
        minDurationMs = minDurationMs
    ).arrivalTimeMs

    internal fun analyze(
        frames: List<PoseFrame>,
        personObservationStartTimeMs: Long?,
        config: HandPeakConfig = HandPeakConfig(),
        supportWindowMs: Long = DEFAULT_SUPPORT_WINDOW_MS,
        minSupportCountPerSide: Int = DEFAULT_MIN_SUPPORT_COUNT_PER_SIDE,
        stableMinSampleCount: Int = DEFAULT_STABLE_MIN_SAMPLE_COUNT,
        arrivalThresholdMultiplier: Double = DEFAULT_ARRIVAL_THRESHOLD_MULTIPLIER,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
        minDurationMs: Long = DEFAULT_MIN_DURATION_MS
    ): WallArrivalAnalysis {
        if (frames.isEmpty()) return WallArrivalAnalysis()

        val filteredPoints = extractBodyPartHeights(frames = frames, config = config)
            .filter { point ->
                personObservationStartTimeMs == null || point.frameTimeMs >= personObservationStartTimeMs
            }
        if (filteredPoints.isEmpty()) return WallArrivalAnalysis()

        return analyzeFilteredPoints(
            filteredPoints = filteredPoints,
            supportWindowMs = supportWindowMs,
            minSupportCountPerSide = minSupportCountPerSide,
            stableMinSampleCount = stableMinSampleCount,
            arrivalThresholdMultiplier = arrivalThresholdMultiplier,
            maxGapMs = maxGapMs,
            minDurationMs = minDurationMs
        )
    }

    private fun analyzeFilteredPoints(
        filteredPoints: List<FrameBodyPartHeights>,
        supportWindowMs: Long,
        minSupportCountPerSide: Int,
        stableMinSampleCount: Int,
        arrivalThresholdMultiplier: Double,
        maxGapMs: Long,
        minDurationMs: Long
    ): WallArrivalAnalysis {
        if (filteredPoints.isEmpty()) return WallArrivalAnalysis()

        val timesMs = filteredPoints.map { point -> point.frameTimeMs }
        val torsoScales = filteredPoints.map { point -> point.torsoScale }
        val torsoScaleSmoothed = smoothOptionalSeries(
            values = torsoScales,
            medianWindow = TORSO_SCALE_MEDIAN_WINDOW,
            meanWindow = TORSO_SCALE_MEAN_WINDOW
        )
        val baseValidFlags = torsoScaleSmoothed.map { value -> value != null }
        if (baseValidFlags.none { flag -> flag }) return WallArrivalAnalysis()

        val validTorsoScaleFlags = filteredPoints.indices.map { index ->
            torsoScaleSmoothed[index] != null &&
                filteredPoints[index].torsoUpright &&
                countValidFramesInWindow(
                    timesMs = timesMs,
                    validFlags = baseValidFlags,
                    startMs = timesMs[index] - supportWindowMs,
                    endMs = timesMs[index]
                ) >= minSupportCountPerSide &&
                countValidFramesInWindow(
                    timesMs = timesMs,
                    validFlags = baseValidFlags,
                    startMs = timesMs[index],
                    endMs = timesMs[index] + supportWindowMs
                ) >= minSupportCountPerSide
        }

        val stableMinSamples = filteredPoints.indices
            .mapNotNull { index ->
                torsoScaleSmoothed[index]?.takeIf { validTorsoScaleFlags[index] }
            }
            .sorted()
            .take(stableMinSampleCount)
        if (stableMinSamples.isEmpty()) return WallArrivalAnalysis()

        val stableMinTorsoScale = median(stableMinSamples)
        val arrivalThreshold = stableMinTorsoScale * arrivalThresholdMultiplier
        val candidateIndices = filteredPoints.indices.filter { index ->
            validTorsoScaleFlags[index] &&
                torsoScaleSmoothed[index] != null &&
                torsoScaleSmoothed[index]!! <= arrivalThreshold
        }
        val wallSegments = mutableListOf<WallPresenceSegment>()
        val wallSegmentIdByFrameTimeMs = mutableMapOf<Long, Int>()

        fun appendSegment(segmentCandidateIndices: List<Int>) {
            if (segmentCandidateIndices.isEmpty()) return

            val startIndex = segmentCandidateIndices.first()
            val endIndex = segmentCandidateIndices.last()
            val durationMs = timesMs[endIndex] - timesMs[startIndex]
            if (durationMs < minDurationMs) return

            val segmentId = wallSegments.size
            wallSegments += WallPresenceSegment(
                id = segmentId,
                startTimeMs = timesMs[startIndex],
                endTimeMs = timesMs[endIndex]
            )
            segmentCandidateIndices.forEach { candidateIndex ->
                wallSegmentIdByFrameTimeMs[timesMs[candidateIndex]] = segmentId
            }
        }

        if (candidateIndices.isNotEmpty()) {
            val currentSegmentCandidateIndices = mutableListOf(candidateIndices.first())
            candidateIndices.zipWithNext().forEach { (previousIndex, currentIndex) ->
                if (timesMs[currentIndex] - timesMs[previousIndex] <= maxGapMs) {
                    currentSegmentCandidateIndices += currentIndex
                } else {
                    appendSegment(currentSegmentCandidateIndices.toList())
                    currentSegmentCandidateIndices.clear()
                    currentSegmentCandidateIndices += currentIndex
                }
            }
            appendSegment(currentSegmentCandidateIndices.toList())
        }

        return WallArrivalAnalysis(
            arrivalTimeMs = wallSegments.firstOrNull()?.startTimeMs,
            stableMinTorsoScale = stableMinTorsoScale,
            arrivalThreshold = arrivalThreshold,
            wallSegments = wallSegments,
            wallSegmentIdByFrameTimeMs = wallSegmentIdByFrameTimeMs
        )
    }

    private fun countValidFramesInWindow(
        timesMs: List<Long>,
        validFlags: List<Boolean>,
        startMs: Long,
        endMs: Long
    ): Int = timesMs.indices.count { index ->
        validFlags[index] && timesMs[index] in startMs..endMs
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middleIndex = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middleIndex]
        } else {
            (sorted[middleIndex - 1] + sorted[middleIndex]) / 2.0
        }
    }

    companion object {
        const val DEFAULT_SUPPORT_WINDOW_MS = 1_000L
        const val DEFAULT_MIN_SUPPORT_COUNT_PER_SIDE = 2
        const val DEFAULT_STABLE_MIN_SAMPLE_COUNT = 5
        const val DEFAULT_ARRIVAL_THRESHOLD_MULTIPLIER = 1.3
        const val DEFAULT_MAX_GAP_MS = 250L
        const val DEFAULT_MIN_DURATION_MS = 500L

        private const val TORSO_SCALE_MEDIAN_WINDOW = 3
        private const val TORSO_SCALE_MEAN_WINDOW = 5
    }
}
