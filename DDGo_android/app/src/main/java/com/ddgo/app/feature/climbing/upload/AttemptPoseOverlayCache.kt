package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark

internal const val POSE_VALIDITY_WINDOW_RADIUS_MS = 1_000L
internal const val POSE_VALIDITY_MIN_OBSERVED_2D_FRAMES = 3
internal const val ATTEMPT_POSE_TRAIL_WINDOW_MS = 10_000L
internal const val ATTEMPT_POSE_OVERLAY_MAX_SNAP_GAP_MS = 250L
private const val ATTEMPT_POSE_SMOOTH_MEDIAN_WINDOW = 3
private const val ATTEMPT_POSE_SMOOTH_MEAN_WINDOW = 5

internal data class PoseValidityFrame(
    val frameIndex: Int,
    val timestampMs: Long,
    val isObserved2d: Boolean,
    val isObservedAiComplete: Boolean,
    val passesTemporalNoiseFilter: Boolean,
    val isValidForAi: Boolean,
    val isValidForOverlay: Boolean,
    val isValidForEndpoint: Boolean
)

internal data class AttemptPoseOverlayCache(
    val trailWindowMs: Long = ATTEMPT_POSE_TRAIL_WINDOW_MS,
    val frames: List<AttemptPoseOverlayFrame> = emptyList(),
    val frameTimesMs: List<Long> = emptyList(),
    val trackSeriesByKind: Map<OverlayTrackKind, OverlayTrackSeries> = emptyMap()
)

internal data class AttemptPoseOverlayFrame(
    val frameTimeMs: Long,
    val pose: Pose,
    val trackedPoints: Map<OverlayTrackKind, OverlayPoint> = emptyMap()
)

internal data class TrackedPointSample(
    val frameTimeMs: Long,
    val point: OverlayPoint
)

internal data class OverlayTrackSeries(
    val samples: List<TrackedPointSample> = emptyList(),
    val sampleTimesMs: List<Long> = emptyList()
)

internal enum class OverlayTrackKind {
    HIP_CENTER,
    LEFT_HAND_CENTER,
    RIGHT_HAND_CENTER,
    LEFT_FOOT_CENTER,
    RIGHT_FOOT_CENTER
}

internal data class OverlayPoint(
    val x: Float,
    val y: Float
)

internal fun buildPoseValidityFrames(
    sequence: AiPoseSequence,
    windowRadiusMs: Long = POSE_VALIDITY_WINDOW_RADIUS_MS,
    minObserved2dFrames: Int = POSE_VALIDITY_MIN_OBSERVED_2D_FRAMES
): List<PoseValidityFrame> {
    val frames = sequence.frames
    if (frames.isEmpty()) return emptyList()

    val observed2d = BooleanArray(frames.size) { index ->
        frames[index].poseDetected && frames[index].poseLandmarks.size >= REQUIRED_2D_LANDMARK_COUNT
    }
    val observedAiComplete = BooleanArray(frames.size) { index ->
        observed2d[index] && frames[index].poseWorldLandmarks.size >= REQUIRED_3D_LANDMARK_COUNT
    }

    var left = 0
    var rightExclusive = 0
    var observedCountInWindow = 0

    return buildList(frames.size) {
        frames.forEachIndexed { index, frame ->
            val windowStartMs = frame.timestampMs - windowRadiusMs
            val windowEndMs = frame.timestampMs + windowRadiusMs

            while (rightExclusive < frames.size && frames[rightExclusive].timestampMs <= windowEndMs) {
                if (observed2d[rightExclusive]) {
                    observedCountInWindow += 1
                }
                rightExclusive += 1
            }

            while (left < frames.size && frames[left].timestampMs < windowStartMs) {
                if (observed2d[left]) {
                    observedCountInWindow -= 1
                }
                left += 1
            }

            val passesTemporalNoiseFilter = observedCountInWindow >= minObserved2dFrames
            add(
                PoseValidityFrame(
                    frameIndex = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    isObserved2d = observed2d[index],
                    isObservedAiComplete = observedAiComplete[index],
                    passesTemporalNoiseFilter = passesTemporalNoiseFilter,
                    isValidForAi = passesTemporalNoiseFilter && observedAiComplete[index],
                    isValidForOverlay = passesTemporalNoiseFilter && observed2d[index],
                    isValidForEndpoint = passesTemporalNoiseFilter && observed2d[index]
                )
            )
        }
    }
}

internal fun AiPoseSequence.filterWithValidity(
    validityFrames: List<PoseValidityFrame>,
    selector: (PoseValidityFrame) -> Boolean
): AiPoseSequence {
    val validFrameIndexes = validityFrames
        .filter(selector)
        .mapTo(hashSetOf()) { it.frameIndex }
    val filteredFrames = frames.filter { frame -> frame.frameIndex in validFrameIndexes }
    return copy(
        videoMetadata = videoMetadata.copy(processedFrames = filteredFrames.size),
        frames = filteredFrames
    )
}

internal fun buildFilteredPoses(
    poses: List<Pose>,
    validityFrames: List<PoseValidityFrame>
): List<Pose> {
    if (poses.isEmpty() || validityFrames.isEmpty()) return emptyList()
    val validTimestamps = validityFrames
        .filter { it.isValidForEndpoint }
        .mapTo(hashSetOf()) { it.timestampMs }
    return poses.filter { pose -> pose.frameTimeMs in validTimestamps }
}

internal fun buildAttemptPoseOverlayCache(
    poses: List<Pose>,
    trailWindowMs: Long = ATTEMPT_POSE_TRAIL_WINDOW_MS
): AttemptPoseOverlayCache {
    if (poses.isEmpty()) {
        return AttemptPoseOverlayCache(trailWindowMs = trailWindowMs)
    }

    val frames = poses
        .sortedBy(Pose::frameTimeMs)
        .map { pose ->
        AttemptPoseOverlayFrame(
            frameTimeMs = pose.frameTimeMs,
            pose = pose,
            trackedPoints = buildTrackedPoints(pose)
        )
    }
    val frameTimesMs = frames.map(AttemptPoseOverlayFrame::frameTimeMs)
    val trackSeriesByKind = buildTrackSeriesByKind(frames)
    return AttemptPoseOverlayCache(
        trailWindowMs = trailWindowMs,
        frames = frames,
        frameTimesMs = frameTimesMs,
        trackSeriesByKind = trackSeriesByKind
    )
}

internal fun buildSmoothedPoses(
    poses: List<Pose>,
    maxGapMs: Long = ATTEMPT_POSE_OVERLAY_MAX_SNAP_GAP_MS
): List<Pose> {
    if (poses.isEmpty()) return emptyList()

    val smoothedLandmarksByPoseIndex = MutableList(poses.size) {
        mutableMapOf<Int, PoseLandmark>()
    }
    val observationsByLandmarkIndex = mutableMapOf<Int, MutableList<IndexedLandmarkObservation>>()

    poses.forEachIndexed { poseIndex, pose ->
        pose.landmarks.forEach { landmark ->
            observationsByLandmarkIndex
                .getOrPut(landmark.index) { mutableListOf() }
                .add(
                    IndexedLandmarkObservation(
                        poseIndex = poseIndex,
                        frameTimeMs = pose.frameTimeMs,
                        landmark = landmark
                    )
                )
        }
    }

    observationsByLandmarkIndex.values.forEach { observations ->
        splitObservationSegments(
            observations = observations,
            maxGapMs = maxGapMs
        ).forEach { segment ->
            val medianSmoothedX = centeredMedian(
                values = segment.map { it.landmark.x },
                windowSize = ATTEMPT_POSE_SMOOTH_MEDIAN_WINDOW
            )
            val medianSmoothedY = centeredMedian(
                values = segment.map { it.landmark.y },
                windowSize = ATTEMPT_POSE_SMOOTH_MEDIAN_WINDOW
            )
            val medianSmoothedZ = centeredMedian(
                values = segment.map { it.landmark.z },
                windowSize = ATTEMPT_POSE_SMOOTH_MEDIAN_WINDOW
            )

            val meanSmoothedX = centeredMean(
                values = medianSmoothedX,
                windowSize = ATTEMPT_POSE_SMOOTH_MEAN_WINDOW
            )
            val meanSmoothedY = centeredMean(
                values = medianSmoothedY,
                windowSize = ATTEMPT_POSE_SMOOTH_MEAN_WINDOW
            )
            val meanSmoothedZ = centeredMean(
                values = medianSmoothedZ,
                windowSize = ATTEMPT_POSE_SMOOTH_MEAN_WINDOW
            )

            segment.forEachIndexed { index, observation ->
                smoothedLandmarksByPoseIndex[observation.poseIndex][observation.landmark.index] =
                    observation.landmark.copy(
                        x = meanSmoothedX[index],
                        y = meanSmoothedY[index],
                        z = meanSmoothedZ[index]
                    )
            }
        }
    }

    return poses.mapIndexed { poseIndex, pose ->
        pose.copy(
            landmarks = pose.landmarks.map { landmark ->
                smoothedLandmarksByPoseIndex[poseIndex][landmark.index] ?: landmark
            }
        )
    }
}

internal fun findNearestOverlayFrameForPlayback(
    cache: AttemptPoseOverlayCache,
    positionMs: Long,
    maxSnapGapMs: Long = ATTEMPT_POSE_OVERLAY_MAX_SNAP_GAP_MS
): AttemptPoseOverlayFrame? {
    if (cache.frames.isEmpty() || positionMs < 0L) return null

    val nearestIndex = cache.frameTimesMs.findNearestIndex(positionMs) ?: return null
    val nearest = cache.frames[nearestIndex]

    return if (kotlin.math.abs(nearest.frameTimeMs - positionMs) <= maxSnapGapMs) {
        nearest
    } else {
        null
    }
}

internal fun buildOverlayTrackTrailSegments(
    cache: AttemptPoseOverlayCache,
    anchorTimeMs: Long,
    kind: OverlayTrackKind,
    trailWindowMs: Long = ATTEMPT_POSE_TRAIL_WINDOW_MS,
    maxGapMs: Long = ATTEMPT_POSE_OVERLAY_MAX_SNAP_GAP_MS
): List<List<OverlayPoint>> {
    val trackSeries = cache.trackSeriesByKind[kind] ?: return emptyList()
    val samples = trackSeries.samples
    if (samples.isEmpty()) return emptyList()

    val startTimeMs = (anchorTimeMs - trailWindowMs).coerceAtLeast(0L)
    val startIndex = trackSeries.sampleTimesMs.lowerBound(startTimeMs)
    val endExclusiveIndex = trackSeries.sampleTimesMs.upperBound(anchorTimeMs)
    if (startIndex >= endExclusiveIndex) return emptyList()

    val segments = mutableListOf<MutableList<OverlayPoint>>()
    var previousTimeMs: Long? = null

    samples.subList(startIndex, endExclusiveIndex).forEach { sample ->
        if (segments.isEmpty() || previousTimeMs == null || sample.frameTimeMs - previousTimeMs!! > maxGapMs) {
            segments += mutableListOf(sample.point)
        } else {
            segments.last() += sample.point
        }
        previousTimeMs = sample.frameTimeMs
    }

    return segments.filter { it.size >= 2 }.map { it.toList() }
}

private fun buildTrackedPoints(pose: Pose): Map<OverlayTrackKind, OverlayPoint> {
    val landmarksByIndex = pose.landmarks.associateBy(PoseLandmark::index)
    return buildMap {
        averagePoint(
            landmarksByIndex = landmarksByIndex,
            indices = HIP_CENTER_INDICES
        )?.let { point ->
            put(OverlayTrackKind.HIP_CENTER, point)
        }
        averagePoint(
            landmarksByIndex = landmarksByIndex,
            indices = LEFT_HAND_CENTER_INDICES
        )?.let { point ->
            put(OverlayTrackKind.LEFT_HAND_CENTER, point)
        }
        averagePoint(
            landmarksByIndex = landmarksByIndex,
            indices = RIGHT_HAND_CENTER_INDICES
        )?.let { point ->
            put(OverlayTrackKind.RIGHT_HAND_CENTER, point)
        }
        averagePoint(
            landmarksByIndex = landmarksByIndex,
            indices = LEFT_FOOT_CENTER_INDICES
        )?.let { point ->
            put(OverlayTrackKind.LEFT_FOOT_CENTER, point)
        }
        averagePoint(
            landmarksByIndex = landmarksByIndex,
            indices = RIGHT_FOOT_CENTER_INDICES
        )?.let { point ->
            put(OverlayTrackKind.RIGHT_FOOT_CENTER, point)
        }
    }
}

private fun buildTrackSeriesByKind(
    frames: List<AttemptPoseOverlayFrame>
): Map<OverlayTrackKind, OverlayTrackSeries> = OverlayTrackKind.entries.associateWith { kind ->
    val samples = frames.mapNotNull { frame ->
        frame.trackedPoints[kind]?.let { point ->
            TrackedPointSample(
                frameTimeMs = frame.frameTimeMs,
                point = point
            )
        }
    }
    OverlayTrackSeries(
        samples = samples,
        sampleTimesMs = samples.map(TrackedPointSample::frameTimeMs)
    )
}

private data class IndexedLandmarkObservation(
    val poseIndex: Int,
    val frameTimeMs: Long,
    val landmark: PoseLandmark
)

private fun splitObservationSegments(
    observations: List<IndexedLandmarkObservation>,
    maxGapMs: Long
): List<List<IndexedLandmarkObservation>> {
    if (observations.isEmpty()) return emptyList()

    val segments = mutableListOf<MutableList<IndexedLandmarkObservation>>()
    observations.forEach { observation ->
        val previous = segments.lastOrNull()?.lastOrNull()
        val shouldStartNewSegment = previous == null ||
            observation.poseIndex != previous.poseIndex + 1 ||
            observation.frameTimeMs - previous.frameTimeMs > maxGapMs

        if (shouldStartNewSegment) {
            segments += mutableListOf(observation)
        } else {
            segments.last() += observation
        }
    }
    return segments.map { it.toList() }
}

private fun centeredMedian(
    values: List<Float>,
    windowSize: Int
): List<Float> = centeredWindowMap(values, windowSize) { window ->
    median(window)
}

private fun centeredMean(
    values: List<Float>,
    windowSize: Int
): List<Float> = centeredWindowMap(values, windowSize) { window ->
    window.average().toFloat()
}

private fun centeredWindowMap(
    values: List<Float>,
    windowSize: Int,
    transform: (List<Float>) -> Float
): List<Float> {
    if (values.isEmpty()) return emptyList()
    if (windowSize <= 1) return values

    val halfWindow = windowSize / 2
    return values.indices.map { index ->
        val startIndex = (index - halfWindow).coerceAtLeast(0)
        val endIndex = (index + halfWindow).coerceAtMost(values.lastIndex)
        transform(values.subList(startIndex, endIndex + 1))
    }
}

private fun median(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2f
    }
}

private fun List<Long>.findNearestIndex(targetMs: Long): Int? {
    if (isEmpty()) return null

    val index = binarySearch(targetMs)
    if (index >= 0) return index

    val insertionPoint = -(index + 1)
    val beforeIndex = insertionPoint - 1
    val afterIndex = insertionPoint

    return when {
        beforeIndex < 0 -> afterIndex.takeIf { it in indices }
        afterIndex > lastIndex -> beforeIndex
        targetMs - this[beforeIndex] <= this[afterIndex] - targetMs -> beforeIndex
        else -> afterIndex
    }
}

private fun List<Long>.lowerBound(targetMs: Long): Int {
    var left = 0
    var right = size
    while (left < right) {
        val mid = (left + right) ushr 1
        if (this[mid] < targetMs) {
            left = mid + 1
        } else {
            right = mid
        }
    }
    return left
}

private fun List<Long>.upperBound(targetMs: Long): Int {
    var left = 0
    var right = size
    while (left < right) {
        val mid = (left + right) ushr 1
        if (this[mid] <= targetMs) {
            left = mid + 1
        } else {
            right = mid
        }
    }
    return left
}

private fun averagePoint(
    landmarksByIndex: Map<Int, PoseLandmark>,
    indices: List<Int>
): OverlayPoint? {
    val landmarks = indices.mapNotNull(landmarksByIndex::get)
    if (landmarks.isEmpty()) return null
    return OverlayPoint(
        x = landmarks.map(PoseLandmark::x).average().toFloat(),
        y = landmarks.map(PoseLandmark::y).average().toFloat()
    )
}

private const val REQUIRED_2D_LANDMARK_COUNT = 33
private const val REQUIRED_3D_LANDMARK_COUNT = 33

private val HIP_CENTER_INDICES = listOf(23, 24)
private val LEFT_HAND_CENTER_INDICES = listOf(15, 17, 19, 21)
private val RIGHT_HAND_CENTER_INDICES = listOf(16, 18, 20, 22)
private val LEFT_FOOT_CENTER_INDICES = listOf(27, 29, 31)
private val RIGHT_FOOT_CENTER_INDICES = listOf(28, 30, 32)
