package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

private val HAND_LANDMARK_INDICES = (15..22).toList()
private val TORSO_LANDMARK_INDICES = listOf(11, 12, 23, 24)
private val FOOT_LANDMARK_INDICES = (27..32).toList()
private const val LEFT_SHOULDER_INDEX = 11
private const val RIGHT_SHOULDER_INDEX = 12
private const val LEFT_WRIST_INDEX = 15
private const val RIGHT_WRIST_INDEX = 16
private const val LEFT_HIP_INDEX = 23
private const val RIGHT_HIP_INDEX = 24
private const val DEFAULT_MIN_VISIBILITY = 0.5f
private const val DEFAULT_MIN_PRESENCE = 0.5f

class DetectAttemptEndFromPoseUseCase @Inject constructor() {
    operator fun invoke(poses: List<Pose>): ClimbEndDetection? = detectClimbEnd(poses)
}

data class ClimbEndDetection(
    val bestPeakAtMs: Long,
    val descentStartAtMs: Long?,
    val approachStartAtMs: Long?,
    val endAtMs: Long,
    val confidence: Float
)

internal enum class TorsoOrientation {
    Front,
    Back,
    Mixed,
    Unknown
}

internal data class FrameBodyPartHeights(
    val frameTimeMs: Long,
    val handHeight: Float? = null,
    val torsoHeight: Float? = null,
    val footHeight: Float? = null,
    val torsoScale: Float? = null,
    val torsoOrientation: TorsoOrientation = TorsoOrientation.Unknown
)

internal data class DetectionConfig(
    val smoothingWindowFrames: Int = 5,
    val minVisibility: Float = DEFAULT_MIN_VISIBILITY,
    val minPresence: Float = DEFAULT_MIN_PRESENCE,
    val descentHoldMs: Long = 500L,
    val reboundWindowMs: Long = 1_000L,
    val peakScaleWindowMs: Long = 1_000L,
    val approachBaselineWindowMs: Long = 1_000L,
    val approachHoldMs: Long = 500L,
    val approachScaleGrowthRatio: Float = 0.35f,
    val minDescentThreshold: Float = 0.02f,
    val descentScaleRatio: Float = 0.25f,
    val endOffsetMs: Long = 3_000L,
    val fallbackPeakZoneMargin: Float = 0.03f,
    val fallbackHandZoneMargin: Float = 0.03f,
    val fallbackConfidence: Float = 0.45f
)

private data class RawClimbMetric(
    val frameTimeMs: Long,
    val torsoY: Float,
    val torsoScale: Float,
    val wristY: Float?
)

private data class FrameMetric(
    val frameTimeMs: Long,
    val torsoYSmooth: Float,
    val torsoScaleSmooth: Float,
    val wristYSmooth: Float?
)

private data class PeakLikeSelection(
    val peakIndex: Int,
    val globalPeakIndex: Int,
    val approachCutoffIndex: Int?,
    val selectionReason: String
)

internal fun extractBodyPartHeights(
    poses: List<Pose>,
    minVisibility: Float = DEFAULT_MIN_VISIBILITY,
    minPresence: Float = DEFAULT_MIN_PRESENCE
): List<FrameBodyPartHeights> = poses
    .sortedBy { it.frameTimeMs }
    .map { pose ->
        val landmarkMap = buildLandmarkMap(
            landmarks = pose.landmarks,
            minVisibility = minVisibility,
            minPresence = minPresence
        )

        FrameBodyPartHeights(
            frameTimeMs = pose.frameTimeMs,
            handHeight = computeGroupHeight(landmarkMap, HAND_LANDMARK_INDICES),
            torsoHeight = computeGroupHeight(landmarkMap, TORSO_LANDMARK_INDICES),
            footHeight = computeGroupHeight(landmarkMap, FOOT_LANDMARK_INDICES),
            torsoScale = computeTorsoScale(landmarkMap),
            torsoOrientation = computeTorsoOrientation(landmarkMap)
        )
    }

internal fun detectClimbEnd(
    poses: List<Pose>,
    config: DetectionConfig = DetectionConfig()
): ClimbEndDetection? {
    val metrics = extractClimbMetrics(poses, config)
    if (metrics.isEmpty()) return null

    val firstValidFrameTimeMs = metrics.first().frameTimeMs
    val bestPeakIndex = findGlobalPeakIndex(metrics, config)
    val bestPeak = metrics[bestPeakIndex]
    val descentThreshold = computeDescentThreshold(metrics, bestPeakIndex, config)

    var descentCandidateStartAtMs: Long? = null
    var descentStartAtMs: Long? = null
    var approachCandidateStartAtMs: Long? = null
    var approachCandidateBaselineScale: Float? = null
    var approachStartAtMs: Long? = null

    for (index in (bestPeakIndex + 1) until metrics.size) {
        val metric = metrics[index]
        val isDescendingFromPeak = metric.torsoYSmooth >= bestPeak.torsoYSmooth + descentThreshold
        val descentTrackingOpen = descentCandidateStartAtMs != null || descentStartAtMs != null

        if (descentTrackingOpen) {
            val baselineScale = computeLookbackScaleMedian(
                metrics = metrics,
                currentIndex = index,
                windowMs = config.approachBaselineWindowMs
            )
            if (baselineScale != null) {
                val referenceScale = approachCandidateBaselineScale ?: baselineScale
                val approachThreshold = referenceScale * (1f + config.approachScaleGrowthRatio)
                if (metric.torsoScaleSmooth >= approachThreshold) {
                    if (approachCandidateStartAtMs == null) {
                        approachCandidateStartAtMs = metric.frameTimeMs
                        approachCandidateBaselineScale = baselineScale
                    }
                } else {
                    approachCandidateStartAtMs = null
                    approachCandidateBaselineScale = null
                }

                if (
                    approachCandidateStartAtMs != null &&
                    descentCandidateStartAtMs != null &&
                    descentStartAtMs == null &&
                    metric.frameTimeMs - descentCandidateStartAtMs >= config.descentHoldMs
                ) {
                    descentStartAtMs = descentCandidateStartAtMs
                }

                if (
                    approachCandidateStartAtMs != null &&
                    metric.frameTimeMs - approachCandidateStartAtMs >= config.approachHoldMs
                ) {
                    approachStartAtMs = approachCandidateStartAtMs
                    if (descentStartAtMs != null) {
                        return buildDetection(
                            bestPeakAtMs = bestPeak.frameTimeMs,
                            descentStartAtMs = descentStartAtMs,
                            approachStartAtMs = approachStartAtMs,
                            firstValidFrameTimeMs = firstValidFrameTimeMs,
                            finalObservedTimeMs = approachStartAtMs,
                            config = config
                        )
                    }
                    break
                }
            }
        } else {
            approachCandidateStartAtMs = null
            approachCandidateBaselineScale = null
        }

        if (
            descentStartAtMs != null &&
            metric.frameTimeMs - descentStartAtMs >= config.reboundWindowMs
        ) {
            return buildDetection(
                bestPeakAtMs = bestPeak.frameTimeMs,
                descentStartAtMs = descentStartAtMs,
                approachStartAtMs = approachStartAtMs,
                firstValidFrameTimeMs = firstValidFrameTimeMs,
                finalObservedTimeMs = metric.frameTimeMs,
                config = config
            )
        }

        if (approachCandidateStartAtMs != null) continue

        if (isDescendingFromPeak) {
            if (descentCandidateStartAtMs == null) {
                descentCandidateStartAtMs = metric.frameTimeMs
            }
            if (
                descentStartAtMs == null &&
                metric.frameTimeMs - descentCandidateStartAtMs >= config.descentHoldMs
            ) {
                descentStartAtMs = descentCandidateStartAtMs
            }
        } else if (descentStartAtMs == null) {
            descentCandidateStartAtMs = null
        } else if (metric.frameTimeMs - descentStartAtMs < config.reboundWindowMs) {
            descentCandidateStartAtMs = null
            descentStartAtMs = null
            approachCandidateStartAtMs = null
            approachCandidateBaselineScale = null
        }
    }

    return buildFallbackDetection(
        metrics = metrics,
        firstValidFrameTimeMs = firstValidFrameTimeMs,
        config = config
    )
}

private fun extractClimbMetrics(
    poses: List<Pose>,
    config: DetectionConfig
): List<FrameMetric> {
    val rawMetrics = poses
        .sortedBy { it.frameTimeMs }
        .mapNotNull { pose ->
            buildClimbMetric(
                pose = pose,
                minVisibility = config.minVisibility,
                minPresence = config.minPresence
            )
        }
    if (rawMetrics.isEmpty()) return emptyList()

    val window = max(1, config.smoothingWindowFrames)
    val halfWindow = window / 2

    return rawMetrics.mapIndexed { index, rawMetric ->
        val start = max(0, index - halfWindow)
        val end = minOf(rawMetrics.size, index + halfWindow + 1)
        val windowSlice = rawMetrics.subList(start, end)
        val wristValues = windowSlice.mapNotNull { it.wristY }

        FrameMetric(
            frameTimeMs = rawMetric.frameTimeMs,
            torsoYSmooth = windowSlice.map { it.torsoY }.average().toFloat(),
            torsoScaleSmooth = windowSlice.map { it.torsoScale }.average().toFloat(),
            wristYSmooth = wristValues.takeIf { it.isNotEmpty() }?.average()?.toFloat()
        )
    }
}

private fun buildClimbMetric(
    pose: Pose,
    minVisibility: Float,
    minPresence: Float
): RawClimbMetric? {
    val landmarkMap = buildLandmarkMap(
        landmarks = pose.landmarks,
        minVisibility = minVisibility,
        minPresence = minPresence
    )
    if (!TORSO_LANDMARK_INDICES.all(landmarkMap::containsKey)) return null

    val shoulderMidY =
        (landmarkMap.getValue(LEFT_SHOULDER_INDEX).y + landmarkMap.getValue(RIGHT_SHOULDER_INDEX).y) / 2f
    val hipMidY =
        (landmarkMap.getValue(LEFT_HIP_INDEX).y + landmarkMap.getValue(RIGHT_HIP_INDEX).y) / 2f
    val wristY =
        if (landmarkMap.containsKey(LEFT_WRIST_INDEX) && landmarkMap.containsKey(RIGHT_WRIST_INDEX)) {
            (landmarkMap.getValue(LEFT_WRIST_INDEX).y + landmarkMap.getValue(RIGHT_WRIST_INDEX).y) / 2f
        } else {
            null
        }

    return RawClimbMetric(
        frameTimeMs = pose.frameTimeMs,
        torsoY = (shoulderMidY + hipMidY) / 2f,
        torsoScale = abs(hipMidY - shoulderMidY),
        wristY = wristY
    )
}

private fun buildLandmarkMap(
    landmarks: List<PoseLandmark>,
    minVisibility: Float,
    minPresence: Float
): Map<Int, PoseLandmark> = landmarks
    .asSequence()
    .filter { landmark -> isLandmarkConfident(landmark, minVisibility, minPresence) }
    .associateBy { it.index }

private fun isLandmarkConfident(
    landmark: PoseLandmark,
    minVisibility: Float,
    minPresence: Float
): Boolean {
    if (landmark.visibility != null && landmark.visibility < minVisibility) return false
    if (landmark.presence != null && landmark.presence < minPresence) return false
    return true
}

private fun computeGroupHeight(
    landmarkMap: Map<Int, PoseLandmark>,
    indices: List<Int>
): Float? {
    val yValues = indices.mapNotNull { index -> landmarkMap[index]?.y }
    if (yValues.isEmpty()) return null
    return 1f - yValues.average().toFloat()
}

private fun computeTorsoScale(landmarkMap: Map<Int, PoseLandmark>): Float? {
    if (!TORSO_LANDMARK_INDICES.all(landmarkMap::containsKey)) return null

    val shoulderMidY =
        (landmarkMap.getValue(LEFT_SHOULDER_INDEX).y + landmarkMap.getValue(RIGHT_SHOULDER_INDEX).y) / 2f
    val hipMidY =
        (landmarkMap.getValue(LEFT_HIP_INDEX).y + landmarkMap.getValue(RIGHT_HIP_INDEX).y) / 2f
    return abs(hipMidY - shoulderMidY)
}

private fun computeTorsoOrientation(
    landmarkMap: Map<Int, PoseLandmark>
): TorsoOrientation {
    if (!TORSO_LANDMARK_INDICES.all(landmarkMap::containsKey)) {
        return TorsoOrientation.Unknown
    }

    val leftShoulderX = landmarkMap.getValue(LEFT_SHOULDER_INDEX).x
    val rightShoulderX = landmarkMap.getValue(RIGHT_SHOULDER_INDEX).x
    val leftHipX = landmarkMap.getValue(LEFT_HIP_INDEX).x
    val rightHipX = landmarkMap.getValue(RIGHT_HIP_INDEX).x

    val shouldersFront = leftShoulderX > rightShoulderX
    val hipsFront = leftHipX > rightHipX
    val shouldersBack = leftShoulderX < rightShoulderX
    val hipsBack = leftHipX < rightHipX

    return when {
        shouldersFront && hipsFront -> TorsoOrientation.Front
        shouldersBack && hipsBack -> TorsoOrientation.Back
        else -> TorsoOrientation.Mixed
    }
}

private fun computeDescentThreshold(
    metrics: List<FrameMetric>,
    bestPeakIndex: Int,
    config: DetectionConfig
): Float {
    val peakTimeMs = metrics[bestPeakIndex].frameTimeMs
    val nearbyScales = metrics
        .filter { metric -> abs(metric.frameTimeMs - peakTimeMs) <= config.peakScaleWindowMs }
        .map { it.torsoScaleSmooth }
    val peakWindowScaleMedian = nearbyScales.medianOrNull() ?: metrics[bestPeakIndex].torsoScaleSmooth

    return max(
        config.minDescentThreshold.toDouble(),
        (peakWindowScaleMedian * config.descentScaleRatio).toDouble()
    ).toFloat()
}

private fun findGlobalPeakIndex(
    metrics: List<FrameMetric>,
    config: DetectionConfig
): Int {
    val approachCutoffIndex = findApproachCutoffIndex(metrics, config)
    val peakSearchMetrics = metrics.take(approachCutoffIndex ?: metrics.size).ifEmpty { metrics }
    return peakSearchMetrics.indices.minByOrNull { index ->
        peakSearchMetrics[index].torsoYSmooth
    } ?: 0
}

private fun findFallbackPeakIndex(
    metrics: List<FrameMetric>,
    config: DetectionConfig
): Int? = findPeakLikeIndex(metrics, config)?.peakIndex

private fun findPeakLikeIndex(
    metrics: List<FrameMetric>,
    config: DetectionConfig
): PeakLikeSelection? {
    if (metrics.isEmpty()) return null

    val approachCutoffIndex = findApproachCutoffIndex(metrics, config)
    val peakSearchMetrics = metrics.take(approachCutoffIndex ?: metrics.size).ifEmpty { metrics }
    val globalPeakIndex = peakSearchMetrics.indices.minByOrNull { index ->
        peakSearchMetrics[index].torsoYSmooth
    } ?: return null
    val globalPeakY = peakSearchMetrics[globalPeakIndex].torsoYSmooth

    val topZoneIndices = peakSearchMetrics.indices.filter { index ->
        peakSearchMetrics[index].torsoYSmooth <= globalPeakY + config.fallbackPeakZoneMargin
    }
    if (topZoneIndices.isEmpty()) {
        return PeakLikeSelection(
            peakIndex = globalPeakIndex,
            globalPeakIndex = globalPeakIndex,
            approachCutoffIndex = approachCutoffIndex,
            selectionReason = "global_peak"
        )
    }

    val topZoneWristValues = topZoneIndices.mapNotNull { index ->
        peakSearchMetrics[index].wristYSmooth
    }
    if (topZoneWristValues.isNotEmpty()) {
        val bestWristY = topZoneWristValues.minOrNull() ?: topZoneWristValues.first()
        val handAlignedIndices = topZoneIndices.filter { index ->
            val wristY = peakSearchMetrics[index].wristYSmooth ?: return@filter false
            wristY <= bestWristY + config.fallbackHandZoneMargin
        }
        if (handAlignedIndices.isNotEmpty()) {
            return PeakLikeSelection(
                peakIndex = handAlignedIndices.last(),
                globalPeakIndex = globalPeakIndex,
                approachCutoffIndex = approachCutoffIndex,
                selectionReason = "hand_aligned_top_zone"
            )
        }
    }

    return PeakLikeSelection(
        peakIndex = topZoneIndices.last(),
        globalPeakIndex = globalPeakIndex,
        approachCutoffIndex = approachCutoffIndex,
        selectionReason = "last_top_zone"
    )
}

private fun findApproachCutoffIndex(
    metrics: List<FrameMetric>,
    config: DetectionConfig
): Int? {
    var approachCandidateStartIndex: Int? = null
    var approachCandidateBaselineScale: Float? = null

    for (index in 1 until metrics.size) {
        val baselineScale = computeLookbackScaleMedian(
            metrics = metrics,
            currentIndex = index,
            windowMs = config.approachBaselineWindowMs
        ) ?: continue

        val referenceScale = approachCandidateBaselineScale ?: baselineScale
        val approachThreshold = referenceScale * (1f + config.approachScaleGrowthRatio)
        if (metrics[index].torsoScaleSmooth >= approachThreshold) {
            if (approachCandidateStartIndex == null) {
                approachCandidateStartIndex = index
                approachCandidateBaselineScale = baselineScale
            }
        } else {
            approachCandidateStartIndex = null
            approachCandidateBaselineScale = null
        }

        if (
            approachCandidateStartIndex != null &&
            metrics[index].frameTimeMs - metrics[approachCandidateStartIndex].frameTimeMs >= config.approachHoldMs
        ) {
            return approachCandidateStartIndex
        }
    }

    return null
}

private fun computeLookbackScaleMedian(
    metrics: List<FrameMetric>,
    currentIndex: Int,
    windowMs: Long
): Float? {
    val currentTimeMs = metrics[currentIndex].frameTimeMs
    val lookbackScales = metrics
        .take(currentIndex)
        .filter { metric -> currentTimeMs - metric.frameTimeMs <= windowMs }
        .map { it.torsoScaleSmooth }
    return lookbackScales.medianOrNull()
}

private fun buildDetection(
    bestPeakAtMs: Long,
    descentStartAtMs: Long,
    approachStartAtMs: Long?,
    firstValidFrameTimeMs: Long,
    finalObservedTimeMs: Long,
    config: DetectionConfig
): ClimbEndDetection {
    val endAtMs = max(firstValidFrameTimeMs, bestPeakAtMs - config.endOffsetMs)
    val descentObservedMs = max(0L, finalObservedTimeMs - descentStartAtMs)

    var confidence = 0.55f
    confidence += minOf(
        0.20f,
        (descentObservedMs.toFloat() / max(1L, config.reboundWindowMs).toFloat()) * 0.20f
    )
    confidence += if (approachStartAtMs == null) 0.10f else 0.05f
    confidence += if (bestPeakAtMs - firstValidFrameTimeMs >= config.endOffsetMs) 0.10f else 0.05f

    return ClimbEndDetection(
        bestPeakAtMs = bestPeakAtMs,
        descentStartAtMs = descentStartAtMs,
        approachStartAtMs = approachStartAtMs,
        endAtMs = endAtMs,
        confidence = confidence.coerceAtMost(0.99f).roundTo2()
    )
}

private fun buildFallbackDetection(
    metrics: List<FrameMetric>,
    firstValidFrameTimeMs: Long,
    config: DetectionConfig
): ClimbEndDetection? {
    val fallbackPeakIndex = findFallbackPeakIndex(metrics, config) ?: return null
    val bestPeakAtMs = metrics[fallbackPeakIndex].frameTimeMs
    val endAtMs = max(firstValidFrameTimeMs, bestPeakAtMs - config.endOffsetMs)

    return ClimbEndDetection(
        bestPeakAtMs = bestPeakAtMs,
        descentStartAtMs = null,
        approachStartAtMs = null,
        endAtMs = endAtMs,
        confidence = config.fallbackConfidence.roundTo2()
    )
}

private fun List<Float>.medianOrNull(): Float? {
    if (isEmpty()) return null
    val sorted = sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle]) / 2f
    }
}

private fun Float.roundTo2(): Float = round(this * 100f) / 100f
