package com.ddgo.app.feature.debug

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PoseWorldPoint
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.VideoContentRect
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoOverlayRenderState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal data class UploadPhysicsDebugFrameMeta(
    val frameIndex: Int,
    val timestampMs: Long,
    val phase: String?,
    val analysisConfidence: String?,
    val supportMode: String?
)

internal data class UploadPhysicsComDebug(
    val x: Double?,
    val y: Double?,
    val z: Double?
) {
    val hasValues: Boolean
        get() = x != null || y != null || z != null
}

internal data class UploadPhysicsSupportDebug(
    val insideSupport: Boolean?,
    val stabilityMarginM: Double?,
    val distanceToSupportM: Double?,
    val confidence: Double?,
    val supportType: String?,
    val supportGeometry: String?,
    val activeHoldIdsByLimb: Map<String, Int>,
    val activeHoldIds: List<Int>,
    val raw: JsonObject?
)

internal data class UploadPhysicsBodyLoadDebug(
    val loads: Map<String, Double>,
    val summaryAvailable: Boolean,
    val raw: JsonObject?
)

internal data class UploadPhysicsTopJointDebug(
    val joint: String,
    val absQfrcInverse: Double?,
    val signedQfrcInverse: Double?
)

internal data class UploadPhysicsJointLoadDebug(
    val loads: Map<String, Double>,
    val topJoints: List<UploadPhysicsTopJointDebug>,
    val raw: JsonObject?
)

internal data class UploadPhysicsForceLimbDebug(
    val forceNormN: Double?,
    val verticalForceN: Double?,
    val normalForceN: Double?,
    val tangentialForceN: Double?,
    val confidenceScore: Double?,
    val raw: JsonObject?
)

internal data class UploadPhysicsContactForceDebug(
    val status: String?,
    val relativeResidual: Double?,
    val limbForces: Map<String, UploadPhysicsForceLimbDebug>,
    val raw: JsonObject?
)

internal data class UploadPhysicsDebugSnapshot(
    val displayedPositionMs: Long,
    val frameDistanceMs: Long,
    val meta: UploadPhysicsDebugFrameMeta,
    val shoulderWidthM: Double?,
    val com: UploadPhysicsComDebug,
    val support: UploadPhysicsSupportDebug,
    val bodyLoad: UploadPhysicsBodyLoadDebug,
    val jointLoad: UploadPhysicsJointLoadDebug,
    val contactForce: UploadPhysicsContactForceDebug,
    val frameRaw: JsonObject
)

internal data class UploadPhysicsComProjectionDebug(
    val overlayCenter: Offset?,
    val deltaPx: Offset?,
    val deltaLeftM: Double?,
    val deltaUpM: Double?,
    val deltaDepthM: Double?,
    val pxPerMeter: Float?,
    val torsoWorldM: List<Double>?,
    val overlayMode: String
)

internal data class UploadPhysicsDebugAnchorSet(
    val torsoAnchor: Offset?,
    val comProjection: UploadPhysicsComProjectionDebug?,
    val supportBadgeAnchor: Offset?,
    val supportCenters: List<Offset>,
    val bodyAnchors: Map<String, Offset?>,
    val jointAnchors: Map<String, Offset?>,
    val contactAnchors: Map<String, Offset?>
)

internal fun buildUploadPhysicsDebugSnapshot(
    physicsResult: JsonObject?,
    displayedPositionMs: Long
): UploadPhysicsDebugSnapshot? {
    val frames = physicsResult
        ?.getArrayOrNull("frames")
        ?.mapNotNull { it.asObjectOrNull() }
        .orEmpty()
    if (frames.isEmpty()) return null

    val nearestFrame = frames.minByOrNull { frame ->
        abs((frame.getLongOrNull("timestamp_ms") ?: Long.MAX_VALUE) - displayedPositionMs)
    } ?: return null

    val timestampMs = nearestFrame.getLongOrNull("timestamp_ms") ?: 0L
    val supportRaw = nearestFrame.getObjectOrNull("support_stability")
    val activeHoldIdsByLimb = nearestFrame.getActiveHoldIdsByLimb()
    val activeHoldIds = activeHoldIdsByLimb.values
        .distinct()
        .sorted()
    val bodyLoadRaw = nearestFrame.getObjectOrNull("body_loads")
    val jointLoadRaw = nearestFrame.getObjectOrNull("joint_loads")
    val topJointLoadRaw = nearestFrame.getArrayOrNull("top_joint_loads")
    val contactForceRaw = nearestFrame.getObjectOrNull("estimated_contact_forces_n")

    return UploadPhysicsDebugSnapshot(
        displayedPositionMs = displayedPositionMs,
        frameDistanceMs = abs(timestampMs - displayedPositionMs),
        meta = UploadPhysicsDebugFrameMeta(
            frameIndex = nearestFrame.getIntOrNull("frame_index") ?: -1,
            timestampMs = timestampMs,
            phase = nearestFrame.getStringOrNull("phase"),
            analysisConfidence = nearestFrame.getStringOrNull("analysis_confidence"),
            supportMode = nearestFrame.getStringOrNull("support_mode")
        ),
        shoulderWidthM = physicsResult
            .getObjectOrNull("personalization")
            .getObjectOrNull("applied_metrics_m")
            .getDoubleOrNull("shoulder_width_m"),
        com = nearestFrame.getArrayOrNull("com_position_m").toComDebug(),
        support = UploadPhysicsSupportDebug(
            insideSupport = supportRaw.getBooleanOrNull("inside_support"),
            stabilityMarginM = supportRaw.getDoubleOrNull("stability_margin_m"),
            distanceToSupportM = supportRaw.getDoubleOrNull("distance_to_support_m"),
            confidence = supportRaw.getDoubleOrNull("confidence"),
            supportType = supportRaw.getStringOrNull("support_type"),
            supportGeometry = supportRaw.getStringOrNull("support_geometry"),
            activeHoldIdsByLimb = activeHoldIdsByLimb,
            activeHoldIds = activeHoldIds,
            raw = supportRaw
        ),
        bodyLoad = UploadPhysicsBodyLoadDebug(
            loads = bodyLoadRaw?.let(::extractBodyLoads).orEmpty(),
            summaryAvailable = physicsResult.getObjectOrNull("body_load_summary") != null,
            raw = bodyLoadRaw
        ),
        jointLoad = UploadPhysicsJointLoadDebug(
            loads = jointLoadRaw?.let(::extractJointLoads).orEmpty(),
            topJoints = topJointLoadRaw?.let(::extractTopJointLoads).orEmpty(),
            raw = jointLoadRaw
        ),
        contactForce = UploadPhysicsContactForceDebug(
            status = nearestFrame.getStringOrNull("contact_force_status"),
            relativeResidual = nearestFrame.getDoubleOrNull("contact_force_relative_residual"),
            limbForces = contactForceRaw?.let(::extractContactForces).orEmpty(),
            raw = contactForceRaw
        ),
        frameRaw = nearestFrame
    )
}

internal fun buildUploadPhysicsDebugAnchorSet(
    pose: Pose?,
    contentRect: VideoContentRect,
    numberedHolds: List<HoldNumbered>,
    snapshot: UploadPhysicsDebugSnapshot?
): UploadPhysicsDebugAnchorSet {
    val torsoAnchor = pose.averageLandmarkOffset(
        contentRect = contentRect,
        requiredCount = 2,
        landmarkIndices = intArrayOf(11, 12, 23, 24)
    )
    val supportCenters = snapshot
        ?.support
        ?.activeHoldIds
        ?.mapNotNull { holdId ->
            numberedHolds.firstOrNull { it.holdNo == holdId }
                ?.hold
                ?.toScreenCenter(contentRect)
        }
        .orEmpty()
    val supportBadgeAnchor = when {
        supportCenters.isNotEmpty() -> supportCenters.averageOffset()
        else -> torsoAnchor
    }

    val comProjection = buildComProjectionDebug(
        pose = pose,
        contentRect = contentRect,
        torsoAnchor = torsoAnchor,
        snapshot = snapshot
    )

    return UploadPhysicsDebugAnchorSet(
        torsoAnchor = torsoAnchor,
        comProjection = comProjection,
        supportBadgeAnchor = supportBadgeAnchor,
        supportCenters = supportCenters,
        bodyAnchors = linkedMapOf(
            "core" to torsoAnchor,
            "left_arm" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 2,
                landmarkIndices = intArrayOf(11, 13, 15)
            ),
            "right_arm" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 2,
                landmarkIndices = intArrayOf(12, 14, 16)
            ),
            "left_leg" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 2,
                landmarkIndices = intArrayOf(23, 25, 27, 31)
            ),
            "right_leg" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 2,
                landmarkIndices = intArrayOf(24, 26, 28, 32)
            )
        ),
        jointAnchors = linkedMapOf(
            "core" to torsoAnchor,
            "left_shoulder" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(11)
            ),
            "right_shoulder" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(12)
            ),
            "left_elbow" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(13)
            ),
            "right_elbow" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(14)
            ),
            "left_hip" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(23)
            ),
            "right_hip" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(24)
            ),
            "left_knee" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(25)
            ),
            "right_knee" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(26)
            ),
            "left_ankle" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(27)
            ),
            "right_ankle" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(28)
            )
        ),
        contactAnchors = linkedMapOf(
            "left_hand" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(15, 17, 19)
            ),
            "right_hand" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(16, 18, 20)
            ),
            "left_foot" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(27, 29, 31)
            ),
            "right_foot" to pose.averageLandmarkOffset(
                contentRect = contentRect,
                requiredCount = 1,
                landmarkIndices = intArrayOf(28, 30, 32)
            )
        )
    )
}

private fun buildComProjectionDebug(
    pose: Pose?,
    contentRect: VideoContentRect,
    torsoAnchor: Offset?,
    snapshot: UploadPhysicsDebugSnapshot?
): UploadPhysicsComProjectionDebug? {
    if (pose == null || snapshot == null || torsoAnchor == null || !snapshot.com.hasValues) {
        return null
    }

    val shoulderWidthM = snapshot.shoulderWidthM
    if (shoulderWidthM == null || shoulderWidthM <= 1e-6) {
        return UploadPhysicsComProjectionDebug(
            overlayCenter = torsoAnchor,
            deltaPx = Offset.Zero,
            deltaLeftM = null,
            deltaUpM = null,
            deltaDepthM = null,
            pxPerMeter = null,
            torsoWorldM = null,
            overlayMode = "torso_hud_fallback"
        )
    }

    val screenPoints = pose.buildComScreenPoints(contentRect) ?: return UploadPhysicsComProjectionDebug(
        overlayCenter = torsoAnchor,
        deltaPx = Offset.Zero,
        deltaLeftM = null,
        deltaUpM = null,
        deltaDepthM = null,
        pxPerMeter = null,
        torsoWorldM = null,
        overlayMode = "screen_basis_missing"
    )

    val torsoWorld = pose.buildApproximateTorsoWorld(shoulderWidthM) ?: return UploadPhysicsComProjectionDebug(
        overlayCenter = torsoAnchor,
        deltaPx = Offset.Zero,
        deltaLeftM = null,
        deltaUpM = null,
        deltaDepthM = null,
        pxPerMeter = null,
        torsoWorldM = null,
        overlayMode = "world_basis_missing"
    )

    val shoulderScreenWidthPx = (screenPoints.leftShoulder - screenPoints.rightShoulder).magnitude()
    if (shoulderScreenWidthPx <= 1f) {
        return UploadPhysicsComProjectionDebug(
            overlayCenter = torsoAnchor,
            deltaPx = Offset.Zero,
            deltaLeftM = null,
            deltaUpM = null,
            deltaDepthM = null,
            pxPerMeter = null,
            torsoWorldM = torsoWorld.torsoCenter.toList(),
            overlayMode = "screen_scale_missing"
        )
    }

    val pxPerMeter = shoulderScreenWidthPx / shoulderWidthM.toFloat()
    val comWorld = DebugVec3(
        x = snapshot.com.x ?: return null,
        y = snapshot.com.y ?: return null,
        z = snapshot.com.z ?: return null
    )
    val deltaWorld = comWorld - torsoWorld.torsoCenter
    val deltaLeftM = deltaWorld.dot(torsoWorld.leftAxis)
    val deltaUpM = deltaWorld.dot(torsoWorld.upAxis)
    val deltaDepthM = deltaWorld.dot(torsoWorld.forwardAxis)

    val rawOffset = (screenPoints.leftAxis * (deltaLeftM.toFloat() * pxPerMeter * COM_LATERAL_SCALE)) +
        (screenPoints.upAxis * (deltaUpM.toFloat() * pxPerMeter * COM_VERTICAL_SCALE))
    val maxComponentPx = maxOf(18f, shoulderScreenWidthPx * COM_MAX_OFFSET_SHOULDER_RATIO)
    val clampedOffset = Offset(
        x = rawOffset.x.coerceIn(-maxComponentPx, maxComponentPx),
        y = rawOffset.y.coerceIn(-maxComponentPx, maxComponentPx)
    )

    return UploadPhysicsComProjectionDebug(
        overlayCenter = torsoAnchor + clampedOffset,
        deltaPx = clampedOffset,
        deltaLeftM = deltaLeftM,
        deltaUpM = deltaUpM,
        deltaDepthM = deltaDepthM,
        pxPerMeter = pxPerMeter,
        torsoWorldM = torsoWorld.torsoCenter.toList(),
        overlayMode = "body_relative_projection"
    )
}

internal fun UploadPhysicsDebugAnchorSet.sortedSupportPolygon(): List<Offset> {
    if (supportCenters.size <= 2) return supportCenters
    val centroid = supportCenters.averageOffset()
    return supportCenters.sortedBy { point ->
        atan2(point.y - centroid.y, point.x - centroid.x)
    }
}

internal fun formatDebugNumber(value: Double?, digits: Int = 3): String {
    return value?.let { "%.${digits}f".format(it) } ?: "-"
}

internal object UploadPhysicsDebugLogger {
    private const val TAG = "UploadPhysicsDebug"

    fun log(
        renderState: AttemptVideoOverlayRenderState?,
        snapshot: UploadPhysicsDebugSnapshot?,
        anchors: UploadPhysicsDebugAnchorSet
    ) {
        val pose = renderState?.currentOverlayPose
        val screenBasis = renderState?.let { pose?.buildComScreenPoints(it.videoContentRect) }
        val worldBasis = if (pose != null && snapshot?.shoulderWidthM != null && snapshot.shoulderWidthM > 1e-6) {
            pose.buildApproximateTorsoWorld(snapshot.shoulderWidthM)
        } else {
            null
        }

        if (renderState == null) {
            Log.i(TAG, "[POSE] renderState=null")
        } else {
            Log.i(
                TAG,
                "[POSE] displayedPositionMs=${renderState.displayedPositionMs} " +
                    "frameTimeMs=${pose?.frameTimeMs} " +
                    "landmarksPx=${pose.formatLandmarksPx()} " +
                    "worldLandmarksSample=${pose.formatWorldLandmarksSample()}"
            )
        }

        Log.i(
            TAG,
            "[QA_SCREEN_BASIS] torsoAnchor=${anchors.torsoAnchor.formatOffset()} " +
                "leftShoulder=${screenBasis?.leftShoulder.formatOffset()} " +
                "rightShoulder=${screenBasis?.rightShoulder.formatOffset()} " +
                "leftHip=${screenBasis?.leftHip.formatOffset()} " +
                "rightHip=${screenBasis?.rightHip.formatOffset()} " +
                "shoulderMid=${screenBasis?.shoulderMid.formatOffset()} " +
                "hipMid=${screenBasis?.hipMid.formatOffset()} " +
                "leftAxis=${screenBasis?.leftAxis.formatOffset()} " +
                "upAxis=${screenBasis?.upAxis.formatOffset()}"
        )

        Log.i(
            TAG,
            "[QA_WORLD_BASIS] scale_m_per_local=${formatDebugNumber(worldBasis?.scaleMPerLocal)} " +
                "offsetWorld=${worldBasis?.offsetWorld.formatDebugVec3()} " +
                "leftShoulderWorld=${worldBasis?.leftShoulderWorld.formatDebugVec3()} " +
                "rightShoulderWorld=${worldBasis?.rightShoulderWorld.formatDebugVec3()} " +
                "leftHipWorld=${worldBasis?.leftHipWorld.formatDebugVec3()} " +
                "rightHipWorld=${worldBasis?.rightHipWorld.formatDebugVec3()} " +
                "torsoCenter=${worldBasis?.torsoCenter.formatDebugVec3()} " +
                "leftAxis=${worldBasis?.leftAxis.formatDebugVec3()} " +
                "upAxis=${worldBasis?.upAxis.formatDebugVec3()} " +
                "forwardAxis=${worldBasis?.forwardAxis.formatDebugVec3()}"
        )

        if (snapshot == null) {
            Log.i(TAG, "[PHYSICS] frame=null")
        } else {
            Log.i(
                TAG,
                "[PHYSICS] frame_index=${snapshot.meta.frameIndex} " +
                    "timestamp_ms=${snapshot.meta.timestampMs} " +
                    "phase=${snapshot.meta.phase} " +
                    "analysis_confidence=${snapshot.meta.analysisConfidence} " +
                    "com_position_m=[${formatDebugNumber(snapshot.com.x)}, ${formatDebugNumber(snapshot.com.y)}, ${formatDebugNumber(snapshot.com.z)}] " +
                    "support_stability=${snapshot.support.raw ?: JsonNull} " +
                    "active_hold_ids=${snapshot.support.activeHoldIdsByLimb} " +
                    "body_loads=${snapshot.bodyLoad.raw ?: JsonNull} " +
                    "top_joint_loads=${snapshot.jointLoad.topJoints} " +
                    "estimated_contact_forces_n=${snapshot.contactForce.raw ?: JsonNull} " +
                    "contact_force_status=${snapshot.contactForce.status}"
            )
        }

        Log.i(
            TAG,
            "[QA_COM_PROJECTION] comWorld=${snapshot?.com.toDebugVec3().formatDebugVec3()} " +
                "deltaWorld=${snapshot?.com.toDebugVec3()?.let { com -> worldBasis?.torsoCenter?.let(com::minus) }.formatDebugVec3()} " +
                "deltaLeftM=${formatDebugNumber(anchors.comProjection?.deltaLeftM)} " +
                "deltaUpM=${formatDebugNumber(anchors.comProjection?.deltaUpM)} " +
                "deltaDepthM=${formatDebugNumber(anchors.comProjection?.deltaDepthM)} " +
                "pxPerMeter=${formatDebugNumber(anchors.comProjection?.pxPerMeter?.toDouble())} " +
                "deltaPx=${anchors.comProjection?.deltaPx.formatOffset()} " +
                "overlayCenter=${anchors.comProjection?.overlayCenter.formatOffset()} " +
                "mode=${anchors.comProjection?.overlayMode}"
        )

        Log.i(
            TAG,
            "[HUD] torso=${anchors.torsoAnchor.formatOffset()} " +
                "com=${anchors.comProjection?.overlayCenter.formatOffset()} " +
                "comMode=${anchors.comProjection?.overlayMode} " +
                "comDeltaPx=${anchors.comProjection?.deltaPx.formatOffset()} " +
                "comDeltaM=[${formatDebugNumber(anchors.comProjection?.deltaLeftM)}, ${formatDebugNumber(anchors.comProjection?.deltaUpM)}, ${formatDebugNumber(anchors.comProjection?.deltaDepthM)}] " +
                "torsoWorld=${anchors.comProjection?.torsoWorldM.formatVec3()} " +
                "supportBadge=${anchors.supportBadgeAnchor.formatOffset()} " +
                "supportCenters=${anchors.supportCenters.joinToString(prefix = "[", postfix = "]") { it.formatOffset() }} " +
                "bodyAnchors=${anchors.bodyAnchors.mapValues { it.value.formatOffset() }} " +
                "jointAnchors=${anchors.jointAnchors.mapValues { it.value.formatOffset() }} " +
                "contactAnchors=${anchors.contactAnchors.mapValues { it.value.formatOffset() }}"
        )
    }
}

private fun extractBodyLoads(bodyLoadRaw: JsonObject): Map<String, Double> {
    return buildMap {
        BODY_LOAD_KEYS.forEach { key ->
            put(key, bodyLoadRaw.getDoubleOrNull(key) ?: 0.0)
        }
    }
}

private fun extractJointLoads(jointLoadRaw: JsonObject): Map<String, Double> {
    return buildMap {
        jointLoadRaw.forEach { (jointName, value) ->
            value.asDoubleOrNull()?.let { put(jointName, it) }
        }
    }
}

private fun extractTopJointLoads(topJointLoadRaw: JsonArray): List<UploadPhysicsTopJointDebug> {
    return topJointLoadRaw.mapNotNull { element ->
        val payload = element.asObjectOrNull() ?: return@mapNotNull null
        val joint = payload.getStringOrNull("joint") ?: return@mapNotNull null
        UploadPhysicsTopJointDebug(
            joint = joint,
            absQfrcInverse = payload.getDoubleOrNull("abs_qfrc_inverse"),
            signedQfrcInverse = payload.getDoubleOrNull("signed_qfrc_inverse")
        )
    }
}

private fun extractContactForces(contactForceRaw: JsonObject): Map<String, UploadPhysicsForceLimbDebug> {
    return buildMap {
        CONTACT_LIMB_KEYS.forEach { limbKey ->
            val payload = contactForceRaw.getObjectOrNull(limbKey)
            if (payload != null) {
                put(
                    limbKey,
                    UploadPhysicsForceLimbDebug(
                        forceNormN = payload.getDoubleOrNull("force_norm_n"),
                        verticalForceN = payload.getDoubleOrNull("vertical_force_n"),
                        normalForceN = payload.getDoubleOrNull("normal_force_n"),
                        tangentialForceN = payload.getDoubleOrNull("tangential_force_n"),
                        confidenceScore = payload.getDoubleOrNull("confidence_score"),
                        raw = payload
                    )
                )
            }
        }
    }
}

private fun JsonObject.getActiveHoldIdsByLimb(): Map<String, Int> {
    val activeHoldIds = getObjectOrNull("active_hold_ids") ?: return emptyMap()
    return buildMap {
        activeHoldIds.forEach { (limb, value) ->
            value.asIntOrNull()?.let { put(limb, it) }
        }
    }
}

private fun JsonArray?.toComDebug(): UploadPhysicsComDebug {
    return UploadPhysicsComDebug(
        x = this?.getOrNull(0).asDoubleOrNull(),
        y = this?.getOrNull(1).asDoubleOrNull(),
        z = this?.getOrNull(2).asDoubleOrNull()
    )
}

private fun Pose?.averageLandmarkOffset(
    contentRect: VideoContentRect,
    requiredCount: Int,
    landmarkIndices: IntArray
): Offset? {
    if (this == null || contentRect.width <= 0f || contentRect.height <= 0f) return null
    val byIndex = landmarks.associateBy(PoseLandmark::index)
    val points = landmarkIndices.toList().mapNotNull { index ->
        byIndex[index]?.toScreenOffset(contentRect)
    }
    if (points.size < requiredCount) return null
    return points.averageOffset()
}

private fun Pose.buildComScreenPoints(contentRect: VideoContentRect): DebugScreenBasis? {
    val byIndex = landmarks.associateBy(PoseLandmark::index)
    val leftShoulder = byIndex[11]?.toScreenOffset(contentRect) ?: return null
    val rightShoulder = byIndex[12]?.toScreenOffset(contentRect) ?: return null
    val leftHip = byIndex[23]?.toScreenOffset(contentRect) ?: return null
    val rightHip = byIndex[24]?.toScreenOffset(contentRect) ?: return null
    val shoulderMid = listOf(leftShoulder, rightShoulder).averageOffset()
    val hipMid = listOf(leftHip, rightHip).averageOffset()
    val upAxis = (shoulderMid - hipMid).normalizedOrNull() ?: return null
    val leftAxis = (leftShoulder - rightShoulder).normalizedOrNull() ?: return null
    return DebugScreenBasis(
        leftShoulder = leftShoulder,
        rightShoulder = rightShoulder,
        leftHip = leftHip,
        rightHip = rightHip,
        shoulderMid = shoulderMid,
        hipMid = hipMid,
        upAxis = upAxis,
        leftAxis = leftAxis
    )
}

private fun Pose.buildApproximateTorsoWorld(shoulderWidthM: Double): DebugWorldBasis? {
    val leftShoulderMp = worldLandmarksSample["left_shoulder"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null
    val rightShoulderMp = worldLandmarksSample["right_shoulder"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null
    val leftHipMp = worldLandmarksSample["left_hip"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null
    val rightHipMp = worldLandmarksSample["right_hip"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null

    val localShoulderWidth = (leftShoulderMp - rightShoulderMp).norm()
    if (localShoulderWidth <= 1e-6) return null

    val scale = shoulderWidthM / localShoulderWidth
    val pelvisLocal = (leftHipMp + rightHipMp) * 0.5
    val offsetWorld = DebugVec3(0.0, 0.0, 1.05) - (pelvisLocal * scale)

    val leftShoulderWorld = (leftShoulderMp * scale) + offsetWorld
    val rightShoulderWorld = (rightShoulderMp * scale) + offsetWorld
    val leftHipWorld = (leftHipMp * scale) + offsetWorld
    val rightHipWorld = (rightHipMp * scale) + offsetWorld

    val shoulderMid = (leftShoulderWorld + rightShoulderWorld) * 0.5
    val hipMid = (leftHipWorld + rightHipWorld) * 0.5
    val torsoCenter = DebugVec3.average(
        leftShoulderWorld,
        rightShoulderWorld,
        leftHipWorld,
        rightHipWorld
    )
    val upAxis = (shoulderMid - hipMid).normalizedOrNull() ?: return null
    var leftAxis = (
        (leftShoulderWorld - rightShoulderWorld) +
            (leftHipWorld - rightHipWorld)
        ).normalizedOrNull() ?: return null
    val forwardAxis = leftAxis.cross(upAxis).normalizedOrNull() ?: DebugVec3(1.0, 0.0, 0.0)
    leftAxis = upAxis.cross(forwardAxis).normalizedOrNull() ?: leftAxis

    return DebugWorldBasis(
        scaleMPerLocal = scale,
        offsetWorld = offsetWorld,
        leftShoulderWorld = leftShoulderWorld,
        rightShoulderWorld = rightShoulderWorld,
        leftHipWorld = leftHipWorld,
        rightHipWorld = rightHipWorld,
        torsoCenter = torsoCenter,
        upAxis = upAxis,
        leftAxis = leftAxis,
        forwardAxis = forwardAxis
    )
}

private fun Hold.toScreenCenter(contentRect: VideoContentRect): Offset {
    val centerX = (boundingBox.left + boundingBox.right) / 2f
    val centerY = (boundingBox.top + boundingBox.bottom) / 2f
    return Offset(
        x = contentRect.left + (centerX * contentRect.width),
        y = contentRect.top + (centerY * contentRect.height)
    )
}

private fun PoseLandmark.toScreenOffset(contentRect: VideoContentRect): Offset {
    return Offset(
        x = contentRect.left + (x.coerceIn(0f, 1f) * contentRect.width),
        y = contentRect.top + (y.coerceIn(0f, 1f) * contentRect.height)
    )
}

private fun List<Offset>.averageOffset(): Offset {
    if (isEmpty()) return Offset.Zero
    val avgX = sumOf { it.x.toDouble() } / size.toDouble()
    val avgY = sumOf { it.y.toDouble() } / size.toDouble()
    return Offset(avgX.toFloat(), avgY.toFloat())
}

private fun Pose?.formatLandmarksPx(): String {
    val safePose = this ?: return "null"
    if (safePose.landmarksPx.isEmpty()) return "{}"
    return safePose.landmarksPx.entries.joinToString(
        prefix = "{",
        postfix = "}"
    ) { (key, point) ->
        "$key=(${formatDebugNumber(point.x.toDouble(), 1)}, ${formatDebugNumber(point.y.toDouble(), 1)})"
    }
}

private fun Pose?.formatWorldLandmarksSample(): String {
    val safePose = this ?: return "null"
    if (safePose.worldLandmarksSample.isEmpty()) return "{}"
    return safePose.worldLandmarksSample.entries.joinToString(
        prefix = "{",
        postfix = "}"
    ) { (key, point) ->
        "$key=(${formatDebugNumber(point.x.toDouble())}, ${formatDebugNumber(point.y.toDouble())}, ${formatDebugNumber(point.z.toDouble())})"
    }
}

private fun Offset?.formatOffset(): String {
    return this?.let { "(${formatDebugNumber(it.x.toDouble(), 1)}, ${formatDebugNumber(it.y.toDouble(), 1)})" }
        ?: "null"
}

private fun DebugVec3?.formatDebugVec3(): String {
    return this?.let { "(${formatDebugNumber(it.x)}, ${formatDebugNumber(it.y)}, ${formatDebugNumber(it.z)})" }
        ?: "null"
}

private fun List<Double>?.formatVec3(): String {
    if (this == null || size < 3) return "null"
    return "(${formatDebugNumber(this[0])}, ${formatDebugNumber(this[1])}, ${formatDebugNumber(this[2])})"
}

private fun UploadPhysicsComDebug?.toDebugVec3(): DebugVec3? {
    val safeCom = this ?: return null
    val x = safeCom.x ?: return null
    val y = safeCom.y ?: return null
    val z = safeCom.z ?: return null
    return DebugVec3(x = x, y = y, z = z)
}

private fun JsonObject?.getObjectOrNull(key: String): JsonObject? = this?.get(key).asObjectOrNull()

private fun JsonObject?.getArrayOrNull(key: String): JsonArray? = this?.get(key).asArrayOrNull()

private fun JsonObject?.getStringOrNull(key: String): String? = this?.get(key).asStringOrNull()

private fun JsonObject?.getBooleanOrNull(key: String): Boolean? = this?.get(key).asBooleanOrNull()

private fun JsonObject?.getDoubleOrNull(key: String): Double? = this?.get(key).asDoubleOrNull()

private fun JsonObject?.getIntOrNull(key: String): Int? = this?.get(key).asIntOrNull()

private fun JsonObject?.getLongOrNull(key: String): Long? = this?.get(key).asLongOrNull()

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asStringOrNull(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content
}

private fun JsonElement?.asBooleanOrNull(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toBooleanStrictOrNull()
}

private fun JsonElement?.asIntOrNull(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toIntOrNull()
}

private fun JsonElement?.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toLongOrNull()
}

private fun JsonElement?.asDoubleOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toDoubleOrNull()
}

internal val BODY_LOAD_KEYS = listOf("core", "left_arm", "right_arm", "left_leg", "right_leg")
internal val CONTACT_LIMB_KEYS = listOf("left_hand", "right_hand", "left_foot", "right_foot")
internal val BODY_GROUP_JOINT_PREFIXES = linkedMapOf(
    "core" to listOf("abdomen_", "neck_"),
    "left_arm" to listOf("shoulder_shrug_left", "shoulder1_left", "shoulder2_left", "shoulder3_left", "elbow_left"),
    "right_arm" to listOf("shoulder_shrug_right", "shoulder1_right", "shoulder2_right", "shoulder3_right", "elbow_right"),
    "left_leg" to listOf("hip_x_left", "hip_z_left", "hip_y_left", "knee_left", "ankle_y_left", "ankle_x_left"),
    "right_leg" to listOf("hip_x_right", "hip_z_right", "hip_y_right", "knee_right", "ankle_y_right", "ankle_x_right")
)

private const val COM_LATERAL_SCALE = 0.7f
private const val COM_VERTICAL_SCALE = 0.7f
private const val COM_MAX_OFFSET_SHOULDER_RATIO = 0.32f

internal fun resolveBodyGroupForJoint(jointName: String): String? {
    return BODY_GROUP_JOINT_PREFIXES.entries.firstOrNull { (_, prefixes) ->
        prefixes.any { prefix -> jointName == prefix || jointName.startsWith(prefix) }
    }?.key
}

internal fun resolveJointAnchorKey(jointName: String): String? {
    return when {
        jointName.startsWith("abdomen_") || jointName.startsWith("neck_") -> "core"
        jointName.startsWith("shoulder") && jointName.endsWith("_left") -> "left_shoulder"
        jointName.startsWith("shoulder") && jointName.endsWith("_right") -> "right_shoulder"
        jointName == "elbow_left" -> "left_elbow"
        jointName == "elbow_right" -> "right_elbow"
        jointName.startsWith("hip_") && jointName.endsWith("_left") -> "left_hip"
        jointName.startsWith("hip_") && jointName.endsWith("_right") -> "right_hip"
        jointName == "knee_left" -> "left_knee"
        jointName == "knee_right" -> "right_knee"
        jointName.startsWith("ankle_") && jointName.endsWith("_left") -> "left_ankle"
        jointName.startsWith("ankle_") && jointName.endsWith("_right") -> "right_ankle"
        else -> null
    }
}

internal fun buildJointAnchorLoadMap(
    jointLoads: Map<String, Double>,
    visibleGroups: Set<String>
): Map<String, Double> {
    val aggregated = linkedMapOf<String, Double>()
    jointLoads.forEach { (jointName, rawLoad) ->
        val bodyGroup = resolveBodyGroupForJoint(jointName) ?: return@forEach
        if (!visibleGroups.contains(bodyGroup)) return@forEach
        val anchorKey = resolveJointAnchorKey(jointName) ?: return@forEach
        aggregated[anchorKey] = (aggregated[anchorKey] ?: 0.0) + abs(rawLoad)
    }
    return aggregated
}

private data class DebugScreenBasis(
    val leftShoulder: Offset,
    val rightShoulder: Offset,
    val leftHip: Offset,
    val rightHip: Offset,
    val shoulderMid: Offset,
    val hipMid: Offset,
    val upAxis: Offset,
    val leftAxis: Offset
)

private data class DebugWorldBasis(
    val scaleMPerLocal: Double,
    val offsetWorld: DebugVec3,
    val leftShoulderWorld: DebugVec3,
    val rightShoulderWorld: DebugVec3,
    val leftHipWorld: DebugVec3,
    val rightHipWorld: DebugVec3,
    val torsoCenter: DebugVec3,
    val upAxis: DebugVec3,
    val leftAxis: DebugVec3,
    val forwardAxis: DebugVec3
)

private data class DebugVec3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: DebugVec3): DebugVec3 = DebugVec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: DebugVec3): DebugVec3 = DebugVec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Double): DebugVec3 = DebugVec3(x * scale, y * scale, z * scale)
    fun dot(other: DebugVec3): Double = (x * other.x) + (y * other.y) + (z * other.z)
    fun cross(other: DebugVec3): DebugVec3 = DebugVec3(
        x = (y * other.z) - (z * other.y),
        y = (z * other.x) - (x * other.z),
        z = (x * other.y) - (y * other.x)
    )
    fun norm(): Double = sqrt((x * x) + (y * y) + (z * z))
    fun normalizedOrNull(): DebugVec3? {
        val norm = norm()
        if (norm <= 1e-8) return null
        return DebugVec3(x / norm, y / norm, z / norm)
    }
    fun toList(): List<Double> = listOf(x, y, z)

    companion object {
        fun average(vararg values: DebugVec3): DebugVec3 {
            val safeValues = values.toList()
            val size = safeValues.size.toDouble()
            return DebugVec3(
                x = safeValues.sumOf { it.x } / size,
                y = safeValues.sumOf { it.y } / size,
                z = safeValues.sumOf { it.z } / size
            )
        }
    }
}

private fun Offset.magnitude(): Float = sqrt((x * x) + (y * y))

private fun Offset.normalizedOrNull(): Offset? {
    val magnitude = magnitude()
    if (magnitude <= 1e-4f) return null
    return Offset(x / magnitude, y / magnitude)
}

private fun PoseWorldPoint.toMujocoLocal(worldSample: Map<String, PoseWorldPoint>): DebugVec3 {
    val verticalSign = inferVerticalSign(worldSample)
    return DebugVec3(
        x = -z.toDouble(),
        y = -x.toDouble(),
        z = verticalSign * y.toDouble()
    )
}

private fun inferVerticalSign(worldSample: Map<String, PoseWorldPoint>): Double {
    val leftShoulder = worldSample["left_shoulder"]
    val rightShoulder = worldSample["right_shoulder"]
    val leftHip = worldSample["left_hip"]
    val rightHip = worldSample["right_hip"]
    if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null) return 1.0

    val shoulderMidY = 0.5 * (leftShoulder.y + rightShoulder.y)
    val hipMidY = 0.5 * (leftHip.y + rightHip.y)
    val torsoDeltaY = shoulderMidY - hipMidY
    return if (abs(torsoDeltaY) < 1e-5f) {
        1.0
    } else if (torsoDeltaY >= 0f) {
        1.0
    } else {
        -1.0
    }
}
