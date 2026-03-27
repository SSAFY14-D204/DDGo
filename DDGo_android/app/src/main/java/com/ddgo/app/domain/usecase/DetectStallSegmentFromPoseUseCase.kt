package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

class DetectStallSegmentFromPoseUseCase @Inject constructor() {
    operator fun invoke(
        poses: List<Pose>,
        wallArrivalTimeMs: Long?,
        endTimeMs: Long?,
        supportWindowMs: Long = DEFAULT_SUPPORT_WINDOW_MS,
        minSupportCountPerSide: Int = DEFAULT_MIN_SUPPORT_COUNT_PER_SIDE,
        gracePeriodMs: Long = DEFAULT_GRACE_PERIOD_MS,
        endGuardMs: Long = DEFAULT_END_GUARD_MS,
        windowMs: Long = DEFAULT_WINDOW_MS,
        maxGapMs: Long = DEFAULT_MAX_GAP_MS,
        maxHipDisplacementNorm: Double = DEFAULT_MAX_HIP_DISPLACEMENT_NORM,
        minSegmentDurationMs: Long = DEFAULT_MIN_SEGMENT_DURATION_MS
    ): StallSegmentAnnotation? {
        if (poses.isEmpty() || wallArrivalTimeMs == null) return null

        val adjustedAnalysisStartTimeMs = wallArrivalTimeMs + gracePeriodMs
        val analysisEndTimeMs = endTimeMs?.minus(endGuardMs)
        if (analysisEndTimeMs != null && analysisEndTimeMs <= adjustedAnalysisStartTimeMs) return null

        val allValidSamples = poses
            .sortedBy(Pose::frameTimeMs)
            .mapNotNull(::toValidSample)
        if (allValidSamples.isEmpty()) return null

        val allTimestamps = allValidSamples.map(ValidHipSample::frameTimeMs)
        val allSupportFlags = allValidSamples.indices.map { index ->
            countSamplesInWindow(
                timestamps = allTimestamps,
                startMs = allTimestamps[index] - supportWindowMs,
                endMs = allTimestamps[index]
            ) >= minSupportCountPerSide &&
                countSamplesInWindow(
                    timestamps = allTimestamps,
                    startMs = allTimestamps[index],
                    endMs = allTimestamps[index] + supportWindowMs
                ) >= minSupportCountPerSide
        }

        val validSamples = allValidSamples
            .zip(allSupportFlags)
            .filter { (sample, isSupported) ->
                isSupported &&
                    sample.frameTimeMs >= adjustedAnalysisStartTimeMs &&
                    (analysisEndTimeMs == null || sample.frameTimeMs <= analysisEndTimeMs)
            }
            .map { (sample, _) -> sample }
        if (validSamples.isEmpty()) return null
        val timestamps = validSamples.map(ValidHipSample::frameTimeMs)

        val candidateWindows = validSamples.indices.mapNotNull { startIndex ->
            val windowEndIndex = timestamps.findLastIndexWithinWindow(
                startIndex = startIndex,
                endMs = timestamps[startIndex] + windowMs
            )
            if (windowEndIndex <= startIndex) return@mapNotNull null

            val windowSamples = validSamples.subList(startIndex, windowEndIndex + 1)
            val avgTorsoScale = windowSamples
                .map(ValidHipSample::torsoScale)
                .average()
                .takeIf { it > 0.0 }
                ?: return@mapNotNull null
            val hipDistance = windowSamples.first().hipCenter.distanceTo(windowSamples.last().hipCenter)
            val hipDisplacementNorm = hipDistance / avgTorsoScale
            if (hipDisplacementNorm > maxHipDisplacementNorm) return@mapNotNull null

            CandidateWindow(
                startTimeMs = windowSamples.first().frameTimeMs,
                endTimeMs = windowSamples.last().frameTimeMs,
                hipDisplacementNorm = hipDisplacementNorm
            )
        }
        if (candidateWindows.isEmpty()) return null

        val segments = mutableListOf<SegmentCandidate>()
        var currentStartTimeMs = candidateWindows.first().startTimeMs
        var currentEndTimeMs = candidateWindows.first().endTimeMs
        var currentDisplacements = mutableListOf(candidateWindows.first().hipDisplacementNorm)

        candidateWindows.zipWithNext().forEach { (previous, current) ->
            if (current.startTimeMs - previous.startTimeMs <= maxGapMs) {
                currentEndTimeMs = max(currentEndTimeMs, current.endTimeMs)
                currentDisplacements += current.hipDisplacementNorm
            } else {
                buildSegmentCandidate(
                    startTimeMs = currentStartTimeMs,
                    endTimeMs = currentEndTimeMs,
                    displacements = currentDisplacements,
                    minSegmentDurationMs = minSegmentDurationMs
                )?.let(segments::add)
                currentStartTimeMs = current.startTimeMs
                currentEndTimeMs = current.endTimeMs
                currentDisplacements = mutableListOf(current.hipDisplacementNorm)
            }
        }

        buildSegmentCandidate(
            startTimeMs = currentStartTimeMs,
            endTimeMs = currentEndTimeMs,
            displacements = currentDisplacements,
            minSegmentDurationMs = minSegmentDurationMs
        )?.let(segments::add)

        val strongest = segments
            .sortedWith(
                compareByDescending<SegmentCandidate> { it.durationMs }
                    .thenBy { it.averageHipDisplacementNorm }
                    .thenBy { it.startTimeMs }
            )
            .firstOrNull()
            ?: return null

        return StallSegmentAnnotation(
            startTimeMs = strongest.startTimeMs,
            endTimeMs = strongest.endTimeMs,
            durationMs = strongest.durationMs,
            score = (strongest.durationMs / 1_000f) /
                max(strongest.averageHipDisplacementNorm.toFloat(), 0.01f)
        )
    }

    private fun buildSegmentCandidate(
        startTimeMs: Long,
        endTimeMs: Long,
        displacements: List<Double>,
        minSegmentDurationMs: Long
    ): SegmentCandidate? {
        val durationMs = endTimeMs - startTimeMs
        if (durationMs < minSegmentDurationMs) return null
        return SegmentCandidate(
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            durationMs = durationMs,
            averageHipDisplacementNorm = displacements.average()
        )
    }

    private fun toValidSample(pose: Pose): ValidHipSample? {
        val leftHip = pose.landmarks.findLandmark(23) ?: return null
        val rightHip = pose.landmarks.findLandmark(24) ?: return null
        val leftShoulder = pose.landmarks.findLandmark(11) ?: return null
        val rightShoulder = pose.landmarks.findLandmark(12) ?: return null

        val hipCenter = Point2D(
            x = (leftHip.x + rightHip.x) / 2.0,
            y = (leftHip.y + rightHip.y) / 2.0
        )
        val shoulderCenter = Point2D(
            x = (leftShoulder.x + rightShoulder.x) / 2.0,
            y = (leftShoulder.y + rightShoulder.y) / 2.0
        )
        val torsoScale = abs(hipCenter.y - shoulderCenter.y)
        if (torsoScale <= 0.0) return null

        return ValidHipSample(
            frameTimeMs = pose.frameTimeMs,
            hipCenter = hipCenter,
            torsoScale = torsoScale
        )
    }

    private fun List<PoseLandmark>.findLandmark(index: Int): PoseLandmark? = firstOrNull { landmark ->
        landmark.index == index
    }

    private fun countSamplesInWindow(
        timestamps: List<Long>,
        startMs: Long,
        endMs: Long
    ): Int = timestamps.count { timestampMs -> timestampMs in startMs..endMs }

    private fun List<Long>.findLastIndexWithinWindow(
        startIndex: Int,
        endMs: Long
    ): Int {
        var left = startIndex
        var right = lastIndex
        var answer = startIndex
        while (left <= right) {
            val middle = (left + right) ushr 1
            if (this[middle] <= endMs) {
                answer = middle
                left = middle + 1
            } else {
                right = middle - 1
            }
        }
        return answer
    }

    private data class Point2D(
        val x: Double,
        val y: Double
    ) {
        fun distanceTo(other: Point2D): Double = hypot(x - other.x, y - other.y)
    }

    private data class ValidHipSample(
        val frameTimeMs: Long,
        val hipCenter: Point2D,
        val torsoScale: Double
    )

    private data class CandidateWindow(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val hipDisplacementNorm: Double
    )

    private data class SegmentCandidate(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val durationMs: Long,
        val averageHipDisplacementNorm: Double
    )

    companion object {
        const val DEFAULT_SUPPORT_WINDOW_MS = 1_000L
        const val DEFAULT_MIN_SUPPORT_COUNT_PER_SIDE = 2
        const val DEFAULT_GRACE_PERIOD_MS = 1_000L
        const val DEFAULT_END_GUARD_MS = 500L
        const val DEFAULT_WINDOW_MS = 1_000L
        const val DEFAULT_MAX_GAP_MS = 250L
        const val DEFAULT_MAX_HIP_DISPLACEMENT_NORM = 0.12
        const val DEFAULT_MIN_SEGMENT_DURATION_MS = 1_000L
    }
}
