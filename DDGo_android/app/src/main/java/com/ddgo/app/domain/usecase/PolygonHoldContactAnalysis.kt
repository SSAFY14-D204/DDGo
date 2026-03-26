package com.ddgo.app.domain.usecase

import android.util.Log
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val POLYGON_HOLD_CONTACT_LOG_TAG = "PolygonHoldContactAnalysis"
private const val POLYGON_HOLD_CONTACT_LOG_PREFIX = "[DDGO_HOLD_CONTACT][POLYGON]"

private const val LEFT_ELBOW_INDEX = 13
private const val RIGHT_ELBOW_INDEX = 14
private const val LEFT_WRIST_INDEX = 15
private const val RIGHT_WRIST_INDEX = 16
private const val LEFT_PINKY_INDEX = 17
private const val RIGHT_PINKY_INDEX = 18
private const val LEFT_INDEX_INDEX = 19
private const val RIGHT_INDEX_INDEX = 20
private const val LEFT_THUMB_INDEX = 21
private const val RIGHT_THUMB_INDEX = 22
private const val LEFT_HEEL_INDEX = 29
private const val RIGHT_HEEL_INDEX = 30
private const val LEFT_FOOT_INDEX = 31
private const val RIGHT_FOOT_INDEX = 32

private val POSE_PIXEL_REFERENCE_NAMES = mapOf(
    "left_shoulder" to 11,
    "right_shoulder" to 12,
    "left_elbow" to LEFT_ELBOW_INDEX,
    "right_elbow" to RIGHT_ELBOW_INDEX,
    "left_wrist" to LEFT_WRIST_INDEX,
    "right_wrist" to RIGHT_WRIST_INDEX,
    "left_hand_tip" to LEFT_INDEX_INDEX,
    "right_hand_tip" to RIGHT_INDEX_INDEX,
    "left_hip" to 23,
    "right_hip" to 24,
    "left_knee" to 25,
    "right_knee" to 26,
    "left_ankle" to 27,
    "right_ankle" to 28
)

data class PolygonHoldContactConfig(
    val handDwellMs: Long = 120L,
    val footDwellMs: Long = 120L,
    val handSpeedThresholdPxPerSec: Float = 220f,
    val footSpeedThresholdPxPerSec: Float = 260f,
    val reachRadiusScale: Float = 1.20f,
    val enterMarginScale: Float = 0.20f,
    val exitMarginScale: Float = 0.34f,
    val minEnterMarginPx: Float = 10f,
    val minExitMarginPx: Float = 18f,
    val fallbackFrameWidthPx: Float = 1000f,
    val fallbackFrameHeightPx: Float = 1000f
)

enum class PolygonTrackedLimb(
    val displayName: String,
    val engagedState: String
) {
    LEFT_HAND(displayName = "왼손", engagedState = "GRIP"),
    RIGHT_HAND(displayName = "오른손", engagedState = "GRIP"),
    LEFT_FOOT(displayName = "왼발", engagedState = "STEP"),
    RIGHT_FOOT(displayName = "오른발", engagedState = "STEP");

    val isHand: Boolean
        get() = this == LEFT_HAND || this == RIGHT_HAND
}

data class PolygonHoldContact(
    val hold: HoldNumbered,
    val limb: PolygonTrackedLimb,
    val state: String,
    val insidePolygon: Boolean,
    val distancePx: Float,
    val speedPxPerSec: Float,
    val contactPointNormalized: Hold.Point?
) {
    val holdNo: Int
        get() = hold.holdNo
}

data class PolygonLimbFrameState(
    val limb: PolygonTrackedLimb,
    val state: String,
    val activeHoldNo: Int?,
    val candidateHoldNo: Int?,
    val distancePx: Float?,
    val speedPxPerSec: Float,
    val transition: String?,
    val insidePolygon: Boolean?,
    val contactPointNormalized: Hold.Point?
)

data class PolygonHoldContactFrame(
    val frameTimeMs: Long,
    val limbStates: List<PolygonLimbFrameState>,
    val activeContacts: List<PolygonHoldContact>
)

data class PolygonHoldContactDebugResult(
    val frames: List<PolygonHoldContactFrame>,
    val highestReachedHoldNo: Int,
    val highestReachedFrameTimeMs: Long?,
    val contactedHoldNos: Set<Int>
) {
    val activeContactFrames: List<PolygonHoldContactFrame>
        get() = frames.filter { it.activeContacts.isNotEmpty() }
}

fun PolygonHoldContactDebugResult.toAttemptHoldReachResult(
    holds: List<HoldNumbered>,
    analysisStartTimeMs: Long? = null,
    analysisEndTimeMs: Long? = null
): AttemptHoldReachResult {
    val totalHoldCount = holds.size
    val scopedFrames = frames.filter { frame ->
        val afterStart = analysisStartTimeMs == null || frame.frameTimeMs >= analysisStartTimeMs
        val beforeEnd = analysisEndTimeMs == null || frame.frameTimeMs <= analysisEndTimeMs
        afterStart && beforeEnd
    }
    val highestFrame = scopedFrames
        .filter { it.activeContacts.isNotEmpty() }
        .maxByOrNull { frame -> frame.activeContacts.maxOf { it.holdNo } }
    val scopedHighestReachedHoldNo = highestFrame?.activeContacts
        ?.maxOfOrNull(PolygonHoldContact::holdNo)
        ?: 0
    val scopedContactedHoldNos = scopedFrames
        .flatMap { frame -> frame.activeContacts.map(PolygonHoldContact::holdNo) }
        .toSet()
    val highestHold = holds.firstOrNull { it.holdNo == scopedHighestReachedHoldNo }
    val normalizedHighestHoldNo = (highestHold?.holdNo ?: scopedHighestReachedHoldNo)
        .coerceIn(0, totalHoldCount)
    val normalizedContactedHoldNos = scopedContactedHoldNos
        .filter { it in 1..totalHoldCount }
        .toSet()
    val endHoldNo = holds.resolveEndHoldNo()
    val completedWithBothHandsOnEndHold = endHoldNo != null && scopedFrames.any { frame ->
        val engagedHands = frame.activeContacts
            .filter { it.holdNo == endHoldNo }
            .map(PolygonHoldContact::limb)
            .filter(PolygonTrackedLimb::isHand)
            .toSet()
        engagedHands.containsAll(
            setOf(
                PolygonTrackedLimb.LEFT_HAND,
                PolygonTrackedLimb.RIGHT_HAND
            )
        )
    }

    return AttemptHoldReachResult(
        highestReachedHold = highestHold,
        highestReachedHoldNo = normalizedHighestHoldNo,
        highestReachedFrameTimeMs = highestFrame?.frameTimeMs,
        totalHoldCount = totalHoldCount,
        contactedHoldNos = normalizedContactedHoldNos,
        reachedRatio = if (totalHoldCount > 0) {
            normalizedHighestHoldNo.toFloat() / totalHoldCount.toFloat()
        } else {
            0f
        },
        completedWithBothHandsOnEndHold = completedWithBothHandsOnEndHold
    )
}

private data class PointPx(
    val x: Float,
    val y: Float
)

private data class FrameSizePx(
    val width: Float,
    val height: Float
)

private data class PolygonHoldDetection(
    val hold: HoldNumbered,
    val cxPx: Float,
    val cyPx: Float,
    val radiusPx: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val polygonPx: List<PointPx>
)

private data class TrackerLimbState(
    var state: String = "FREE",
    var activeHoldNo: Int? = null,
    var candidateHoldNo: Int? = null,
    var candidateSinceMs: Long? = null,
    var lastPointPx: PointPx? = null,
    var lastTimestampMs: Long? = null,
    var lastTransition: String? = null,
    var lastDistancePx: Float? = null,
    var lastSpeedPxPerSec: Float = 0f
)

private data class LimbComputationResult(
    val state: String,
    val activeHoldNo: Int?,
    val candidateHoldNo: Int?,
    val distancePx: Float?,
    val speedPxPerSec: Float,
    val transition: String?,
    val insidePolygon: Boolean?,
    val contactPointPx: PointPx?
)

fun analyzePolygonHoldContacts(
    poses: List<Pose>,
    holds: List<HoldNumbered>,
    config: PolygonHoldContactConfig = PolygonHoldContactConfig(),
    enableLogging: Boolean = true
): PolygonHoldContactDebugResult {
    if (enableLogging) {
        Log.i(
            POLYGON_HOLD_CONTACT_LOG_TAG,
            "$POLYGON_HOLD_CONTACT_LOG_PREFIX 분석 시작: poseCount=${poses.size}, holdCount=${holds.size}"
        )
    }

    if (poses.isEmpty() || holds.isEmpty()) {
        return PolygonHoldContactDebugResult(
            frames = emptyList(),
            highestReachedHoldNo = 0,
            highestReachedFrameTimeMs = null,
            contactedHoldNos = emptySet()
        ).also { result ->
            if (enableLogging) {
                logPolygonSummary(result)
            }
        }
    }

    val frameSize = estimateFrameSizePx(
        poses = poses,
        config = config
    )
    val holdDetections = holds.map { hold ->
        buildPolygonHoldDetection(
            hold = hold,
            frameSize = frameSize
        )
    }
    val trackerStates = PolygonTrackedLimb.entries.associateWith { TrackerLimbState() }.toMutableMap()

    val frames = poses.sortedBy(Pose::frameTimeMs).map { pose ->
        val limbPoints = computeContactPointsPx(
            pose = pose,
            frameSize = frameSize
        )

        val frameStates = PolygonTrackedLimb.entries.map { limb ->
            val result = updateSingleLimb(
                limb = limb,
                state = trackerStates.getValue(limb),
                pointPx = limbPoints[limb],
                timestampMs = pose.frameTimeMs,
                detections = holdDetections,
                config = config
            )

            PolygonLimbFrameState(
                limb = limb,
                state = result.state,
                activeHoldNo = result.activeHoldNo,
                candidateHoldNo = result.candidateHoldNo,
                distancePx = result.distancePx,
                speedPxPerSec = result.speedPxPerSec,
                transition = result.transition,
                insidePolygon = result.insidePolygon,
                contactPointNormalized = result.contactPointPx?.toNormalized(frameSize)
            )
        }

        val activeContacts = frameStates.mapNotNull { limbState ->
            val activeHoldNo = limbState.activeHoldNo ?: return@mapNotNull null
            val hold = holds.firstOrNull { it.holdNo == activeHoldNo } ?: return@mapNotNull null
            PolygonHoldContact(
                hold = hold,
                limb = limbState.limb,
                state = limbState.state,
                insidePolygon = limbState.insidePolygon ?: false,
                distancePx = limbState.distancePx ?: Float.POSITIVE_INFINITY,
                speedPxPerSec = limbState.speedPxPerSec,
                contactPointNormalized = limbState.contactPointNormalized
            )
        }

        if (enableLogging && activeContacts.isNotEmpty()) {
            val contactSummary = activeContacts.joinToString(separator = " / ") { contact ->
                "#${contact.holdNo} ${contact.limb.displayName} state=${contact.state} inside=${contact.insidePolygon}"
            }
            Log.i(
                POLYGON_HOLD_CONTACT_LOG_TAG,
                "$POLYGON_HOLD_CONTACT_LOG_PREFIX frame=${pose.frameTimeMs} active=$contactSummary"
            )
        }

        PolygonHoldContactFrame(
            frameTimeMs = pose.frameTimeMs,
            limbStates = frameStates,
            activeContacts = activeContacts
        )
    }

    val highestFrame = frames
        .filter { it.activeContacts.isNotEmpty() }
        .maxByOrNull { frame -> frame.activeContacts.maxOf { it.holdNo } }
    val highestHoldNo = highestFrame?.activeContacts?.maxOfOrNull(PolygonHoldContact::holdNo) ?: 0
    val result = PolygonHoldContactDebugResult(
        frames = frames,
        highestReachedHoldNo = highestHoldNo,
        highestReachedFrameTimeMs = highestFrame?.frameTimeMs,
        contactedHoldNos = frames
            .flatMap { frame -> frame.activeContacts.map(PolygonHoldContact::holdNo) }
            .toSet()
    )

    if (enableLogging) {
        logPolygonSummary(result)
    }

    return result
}

private fun buildPolygonHoldDetection(
    hold: HoldNumbered,
    frameSize: FrameSizePx
): PolygonHoldDetection {
    val polygonPx = hold.hold.polygon.takeIf { it.size >= 3 }?.map { point ->
        PointPx(
            x = point.x * frameSize.width,
            y = point.y * frameSize.height
        )
    } ?: boundingBoxToPolygonPx(
        bbox = hold.hold.boundingBox,
        frameSize = frameSize
    )

    val x1 = polygonPx.minOf(PointPx::x)
    val y1 = polygonPx.minOf(PointPx::y)
    val x2 = polygonPx.maxOf(PointPx::x)
    val y2 = polygonPx.maxOf(PointPx::y)
    val centroid = polygonCentroid(polygonPx)
    val polygonArea = polygonArea(polygonPx)
    val width = max(1f, x2 - x1)
    val height = max(1f, y2 - y1)
    val radiusPx = sqrt(max(polygonArea, 1f) / PI.toFloat()).takeIf { it.isFinite() && it > 1f }
        ?: (0.45f * min(width, height))

    return PolygonHoldDetection(
        hold = hold,
        cxPx = centroid.x,
        cyPx = centroid.y,
        radiusPx = radiusPx,
        x1 = x1,
        y1 = y1,
        x2 = x2,
        y2 = y2,
        polygonPx = polygonPx
    )
}

private fun updateSingleLimb(
    limb: PolygonTrackedLimb,
    state: TrackerLimbState,
    pointPx: PointPx?,
    timestampMs: Long,
    detections: List<PolygonHoldDetection>,
    config: PolygonHoldContactConfig
): LimbComputationResult {
    if (pointPx == null || detections.isEmpty()) {
        state.state = "FREE"
        state.activeHoldNo = null
        state.candidateHoldNo = null
        state.candidateSinceMs = null
        state.lastTransition = "missing"
        state.lastDistancePx = null
        state.lastSpeedPxPerSec = 0f
        state.lastTimestampMs = timestampMs
        state.lastPointPx = pointPx
        return LimbComputationResult(
            state = state.state,
            activeHoldNo = null,
            candidateHoldNo = null,
            distancePx = null,
            speedPxPerSec = 0f,
            transition = state.lastTransition,
            insidePolygon = null,
            contactPointPx = pointPx
        )
    }

    val speedPxPerSec = if (state.lastPointPx != null && state.lastTimestampMs != null) {
        val dtSeconds = max((timestampMs - state.lastTimestampMs!!).toFloat() / 1000f, 1e-6f)
        distance(state.lastPointPx!!, pointPx) / dtSeconds
    } else {
        0f
    }

    state.lastPointPx = pointPx
    state.lastTimestampMs = timestampMs
    state.lastSpeedPxPerSec = speedPxPerSec

    var nearest = findNearestHold(pointPx, detections)
    var hold = nearest.hold
    var insidePolygon = nearest.insidePolygon
    var distancePx = nearest.distancePx
    val dwellMs = if (limb.isHand) config.handDwellMs else config.footDwellMs
    val speedThreshold = if (limb.isHand) {
        config.handSpeedThresholdPxPerSec
    } else {
        config.footSpeedThresholdPxPerSec
    }

    val enterMargin = max(config.minEnterMarginPx, hold.radiusPx * config.enterMarginScale)
    val exitMargin = max(config.minExitMarginPx, hold.radiusPx * config.exitMarginScale)
    val reachRadius = max(enterMargin * 1.5f, hold.radiusPx * config.reachRadiusScale)

    var transition: String? = null

    if (state.activeHoldNo != null) {
        val activeHold = detections.firstOrNull { it.hold.holdNo == state.activeHoldNo }
        if (activeHold != null) {
            val activeProximity = polygonProximity(pointPx, activeHold.polygonPx)
            if (activeProximity.insidePolygon || activeProximity.distancePx <= exitMargin) {
                hold = activeHold
                insidePolygon = activeProximity.insidePolygon
                distancePx = activeProximity.distancePx
            } else {
                transition = "release"
                state.state = "RELEASE"
                state.activeHoldNo = null
                state.candidateHoldNo = null
                state.candidateSinceMs = null
            }
        }
    }

    if (state.activeHoldNo == null) {
        if (insidePolygon || distancePx <= enterMargin) {
            if (state.candidateHoldNo != hold.hold.holdNo) {
                state.candidateHoldNo = hold.hold.holdNo
                state.candidateSinceMs = timestampMs
            }

            val elapsedMs = if (state.candidateSinceMs == null) 0L else timestampMs - state.candidateSinceMs!!
            if (elapsedMs >= dwellMs && speedPxPerSec <= speedThreshold) {
                state.activeHoldNo = hold.hold.holdNo
                state.state = limb.engagedState
                transition = "engage"
            } else {
                state.state = "REACH"
            }
        } else if (distancePx <= reachRadius) {
            state.state = "REACH"
            state.candidateHoldNo = null
            state.candidateSinceMs = null
        } else {
            state.state = "FREE"
            state.candidateHoldNo = null
            state.candidateSinceMs = null
        }
    } else {
        state.state = limb.engagedState
    }

    state.lastTransition = transition
    state.lastDistancePx = distancePx

    return LimbComputationResult(
        state = state.state,
        activeHoldNo = state.activeHoldNo,
        candidateHoldNo = state.candidateHoldNo,
        distancePx = distancePx,
        speedPxPerSec = speedPxPerSec,
        transition = transition,
        insidePolygon = insidePolygon,
        contactPointPx = pointPx
    )
}

private fun findNearestHold(
    pointPx: PointPx,
    detections: List<PolygonHoldDetection>
): PolygonNearestHold {
    val candidateHolds = candidateHolds(pointPx, detections)
    var best: PolygonNearestHold? = null

    candidateHolds.forEach { hold ->
        val proximity = polygonProximity(pointPx, hold.polygonPx)
        val candidate = PolygonNearestHold(
            hold = hold,
            insidePolygon = proximity.insidePolygon,
            distancePx = proximity.distancePx
        )

        best = when {
            best == null -> candidate
            candidate.insidePolygon && !best!!.insidePolygon -> candidate
            candidate.insidePolygon == best!!.insidePolygon &&
                candidate.distancePx < best!!.distancePx -> candidate
            else -> best
        }
    }

    return best ?: PolygonNearestHold(
        hold = detections.first(),
        insidePolygon = false,
        distancePx = Float.POSITIVE_INFINITY
    )
}

private data class PolygonNearestHold(
    val hold: PolygonHoldDetection,
    val insidePolygon: Boolean,
    val distancePx: Float
)

private fun candidateHolds(
    pointPx: PointPx,
    detections: List<PolygonHoldDetection>
): List<PolygonHoldDetection> {
    val contained = detections.filter { hold ->
        val marginPx = max(18f, hold.radiusPx * 1.20f)
        pointToExpandedBboxDistance(
            point = pointPx,
            x1 = hold.x1,
            y1 = hold.y1,
            x2 = hold.x2,
            y2 = hold.y2,
            marginPx = marginPx
        ) == 0f
    }
    if (contained.isNotEmpty()) return contained

    return detections
        .sortedBy { hold ->
            val marginPx = max(18f, hold.radiusPx * 1.20f)
            pointToExpandedBboxDistance(
                point = pointPx,
                x1 = hold.x1,
                y1 = hold.y1,
                x2 = hold.x2,
                y2 = hold.y2,
                marginPx = marginPx
            )
        }
        .take(6)
}

private fun computeContactPointsPx(
    pose: Pose,
    frameSize: FrameSizePx
): Map<PolygonTrackedLimb, PointPx?> = mapOf(
    PolygonTrackedLimb.LEFT_HAND to inferPalmContactPx(
        wrist = pose.landmarkPx(LEFT_WRIST_INDEX, frameSize),
        elbow = pose.landmarkPx(LEFT_ELBOW_INDEX, frameSize),
        indexTip = pose.landmarkPx(LEFT_INDEX_INDEX, frameSize),
        pinkyTip = pose.landmarkPx(LEFT_PINKY_INDEX, frameSize),
        thumbTip = pose.landmarkPx(LEFT_THUMB_INDEX, frameSize)
    ),
    PolygonTrackedLimb.RIGHT_HAND to inferPalmContactPx(
        wrist = pose.landmarkPx(RIGHT_WRIST_INDEX, frameSize),
        elbow = pose.landmarkPx(RIGHT_ELBOW_INDEX, frameSize),
        indexTip = pose.landmarkPx(RIGHT_INDEX_INDEX, frameSize),
        pinkyTip = pose.landmarkPx(RIGHT_PINKY_INDEX, frameSize),
        thumbTip = pose.landmarkPx(RIGHT_THUMB_INDEX, frameSize)
    ),
    PolygonTrackedLimb.LEFT_FOOT to inferForefootContactPx(
        heel = pose.landmarkPx(LEFT_HEEL_INDEX, frameSize),
        toe = pose.landmarkPx(LEFT_FOOT_INDEX, frameSize)
    ),
    PolygonTrackedLimb.RIGHT_FOOT to inferForefootContactPx(
        heel = pose.landmarkPx(RIGHT_HEEL_INDEX, frameSize),
        toe = pose.landmarkPx(RIGHT_FOOT_INDEX, frameSize)
    )
)

private fun inferPalmContactPx(
    wrist: PointPx?,
    elbow: PointPx?,
    indexTip: PointPx?,
    pinkyTip: PointPx?,
    thumbTip: PointPx?
): PointPx? {
    if (wrist == null || elbow == null || indexTip == null || pinkyTip == null || thumbTip == null) {
        return null
    }

    val fingertipCentroid = PointPx(
        x = (indexTip.x + pinkyTip.x + thumbTip.x) / 3f,
        y = (indexTip.y + pinkyTip.y + thumbTip.y) / 3f
    )
    val fingertipDirection = normalize(PointPx(fingertipCentroid.x - wrist.x, fingertipCentroid.y - wrist.y))
    val forearmDirection = normalize(PointPx(wrist.x - elbow.x, wrist.y - elbow.y))
    var blendedDirection = normalize(
        PointPx(
            x = 0.55f * fingertipDirection.x + 0.45f * forearmDirection.x,
            y = 0.55f * fingertipDirection.y + 0.45f * forearmDirection.y
        )
    )

    if (vectorLength(blendedDirection) < 1e-6f) {
        blendedDirection = if (vectorLength(forearmDirection) >= 1e-6f) {
            forearmDirection
        } else {
            fingertipDirection
        }
    }

    val forearmLength = distance(wrist, elbow)
    val fingertipSpan = distance(fingertipCentroid, wrist)
    val offset = max(0.28f * forearmLength, 0.65f * fingertipSpan).coerceIn(8f, 60f)

    return PointPx(
        x = wrist.x + blendedDirection.x * offset,
        y = wrist.y + blendedDirection.y * offset
    )
}

private fun inferForefootContactPx(
    heel: PointPx?,
    toe: PointPx?
): PointPx? {
    if (heel == null || toe == null) return null
    return PointPx(
        x = heel.x + 0.80f * (toe.x - heel.x),
        y = heel.y + 0.80f * (toe.y - heel.y)
    )
}

private fun Pose.landmarkPx(
    index: Int,
    frameSize: FrameSizePx
): PointPx? = landmarks.firstOrNull { it.index == index }?.let { landmark ->
    PointPx(
        x = landmark.x * frameSize.width,
        y = landmark.y * frameSize.height
    )
}

private fun estimateFrameSizePx(
    poses: List<Pose>,
    config: PolygonHoldContactConfig
): FrameSizePx {
    val widthEstimates = mutableListOf<Float>()
    val heightEstimates = mutableListOf<Float>()

    poses.take(5).forEach { pose ->
        pose.landmarksPx.forEach { (name, pointPx) ->
            val index = POSE_PIXEL_REFERENCE_NAMES[name] ?: return@forEach
            val landmark = pose.landmarks.firstOrNull { it.index == index } ?: return@forEach
            if (landmark.x.isFinite() && landmark.x > 1e-3f) {
                widthEstimates += pointPx.x / landmark.x
            }
            if (landmark.y.isFinite() && landmark.y > 1e-3f) {
                heightEstimates += pointPx.y / landmark.y
            }
        }
    }

    return FrameSizePx(
        width = widthEstimates.averageOr(config.fallbackFrameWidthPx),
        height = heightEstimates.averageOr(config.fallbackFrameHeightPx)
    )
}

private fun List<Float>.averageOr(fallback: Float): Float {
    val finiteValues = filter { it.isFinite() && it > 1f }
    return if (finiteValues.isEmpty()) fallback else finiteValues.average().toFloat()
}

private fun polygonArea(points: List<PointPx>): Float {
    if (points.size < 3) return 0f
    var acc = 0f
    points.indices.forEach { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        acc += current.x * next.y - next.x * current.y
    }
    return abs(acc) * 0.5f
}

private fun polygonCentroid(points: List<PointPx>): PointPx {
    if (points.size < 3) return averagePoint(points)

    var factorSum = 0f
    var cx = 0f
    var cy = 0f
    points.indices.forEach { index ->
        val current = points[index]
        val next = points[(index + 1) % points.size]
        val factor = current.x * next.y - next.x * current.y
        factorSum += factor
        cx += (current.x + next.x) * factor
        cy += (current.y + next.y) * factor
    }

    val signedArea = factorSum * 0.5f
    if (abs(signedArea) < 1e-6f) return averagePoint(points)

    return PointPx(
        x = cx / (6f * signedArea),
        y = cy / (6f * signedArea)
    )
}

private fun averagePoint(points: List<PointPx>): PointPx {
    if (points.isEmpty()) return PointPx(0f, 0f)
    return PointPx(
        x = points.map(PointPx::x).average().toFloat(),
        y = points.map(PointPx::y).average().toFloat()
    )
}

private data class PolygonProximity(
    val insidePolygon: Boolean,
    val distancePx: Float
)

private fun polygonProximity(
    point: PointPx,
    polygon: List<PointPx>
): PolygonProximity {
    val inside = pointInPolygon(point, polygon)
    var minDistance = Float.POSITIVE_INFINITY

    polygon.indices.forEach { index ->
        val start = polygon[index]
        val end = polygon[(index + 1) % polygon.size]
        minDistance = min(minDistance, distancePointToSegment(point, start, end))
    }

    if (!minDistance.isFinite()) {
        minDistance = 0f
    }

    return PolygonProximity(
        insidePolygon = inside,
        distancePx = if (inside) 0f else minDistance
    )
}

private fun pointInPolygon(
    point: PointPx,
    polygon: List<PointPx>
): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        val intersects = ((current.y > point.y) != (previous.y > point.y)) &&
            (point.x < (previous.x - current.x) * (point.y - current.y) /
            ((previous.y - current.y).takeIf { abs(it) > 1e-6f } ?: 1e-6f) + current.x)

        if (intersects) {
            inside = !inside
        }
        previous = current
    }
    return inside
}

private fun distancePointToSegment(
    point: PointPx,
    start: PointPx,
    end: PointPx
): Float {
    val ab = PointPx(end.x - start.x, end.y - start.y)
    val denom = ab.x * ab.x + ab.y * ab.y
    if (denom <= 1e-6f) {
        return distance(point, start)
    }

    val t = (((point.x - start.x) * ab.x + (point.y - start.y) * ab.y) / denom)
        .coerceIn(0f, 1f)
    val projection = PointPx(
        x = start.x + t * ab.x,
        y = start.y + t * ab.y
    )
    return distance(point, projection)
}

private fun pointToExpandedBboxDistance(
    point: PointPx,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    marginPx: Float
): Float {
    val ex1 = x1 - marginPx
    val ey1 = y1 - marginPx
    val ex2 = x2 + marginPx
    val ey2 = y2 + marginPx
    val dx = max(ex1 - point.x, max(0f, point.x - ex2))
    val dy = max(ey1 - point.y, max(0f, point.y - ey2))
    if (dx <= 0f && dy <= 0f) return 0f
    return hypot(dx, dy)
}

private fun boundingBoxToPolygonPx(
    bbox: Hold.BoundingBox,
    frameSize: FrameSizePx
): List<PointPx> = listOf(
    PointPx(bbox.left * frameSize.width, bbox.top * frameSize.height),
    PointPx(bbox.right * frameSize.width, bbox.top * frameSize.height),
    PointPx(bbox.right * frameSize.width, bbox.bottom * frameSize.height),
    PointPx(bbox.left * frameSize.width, bbox.bottom * frameSize.height)
)

private fun normalize(point: PointPx): PointPx {
    val length = vectorLength(point)
    if (length < 1e-6f) return PointPx(0f, 0f)
    return PointPx(point.x / length, point.y / length)
}

private fun vectorLength(point: PointPx): Float = hypot(point.x, point.y)

private fun distance(start: PointPx, end: PointPx): Float = hypot(end.x - start.x, end.y - start.y)

private fun PointPx.toNormalized(frameSize: FrameSizePx): Hold.Point = Hold.Point(
    x = (x / frameSize.width).coerceIn(0f, 1f),
    y = (y / frameSize.height).coerceIn(0f, 1f)
)

private fun logPolygonSummary(result: PolygonHoldContactDebugResult) {
    Log.i(
        POLYGON_HOLD_CONTACT_LOG_TAG,
        "$POLYGON_HOLD_CONTACT_LOG_PREFIX 분석 요약: highestHoldNo=${result.highestReachedHoldNo}, " +
            "contacted=${result.contactedHoldNos}, activeFrameCount=${result.activeContactFrames.size}"
    )
}
