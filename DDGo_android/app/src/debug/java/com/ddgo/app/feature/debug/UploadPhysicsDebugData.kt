package com.ddgo.app.feature.debug

import android.util.Log
import androidx.compose.ui.geometry.Offset
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
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
    val com: UploadPhysicsComDebug,
    val support: UploadPhysicsSupportDebug,
    val bodyLoad: UploadPhysicsBodyLoadDebug,
    val contactForce: UploadPhysicsContactForceDebug,
    val frameRaw: JsonObject
)

internal data class UploadPhysicsDebugAnchorSet(
    val torsoAnchor: Offset?,
    val supportBadgeAnchor: Offset?,
    val supportCenters: List<Offset>,
    val bodyAnchors: Map<String, Offset?>,
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

    return UploadPhysicsDebugAnchorSet(
        torsoAnchor = torsoAnchor,
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
        if (renderState == null) {
            Log.i(TAG, "[POSE] renderState=null")
        } else {
            val pose = renderState.currentOverlayPose
            Log.i(
                TAG,
                "[POSE] displayedPositionMs=${renderState.displayedPositionMs} " +
                    "frameTimeMs=${pose?.frameTimeMs} " +
                    "landmarksPx=${pose.formatLandmarksPx()} " +
                    "worldLandmarksSample=${pose.formatWorldLandmarksSample()}"
            )
        }

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
                    "estimated_contact_forces_n=${snapshot.contactForce.raw ?: JsonNull} " +
                    "contact_force_status=${snapshot.contactForce.status}"
            )
        }

        Log.i(
            TAG,
            "[HUD] torso=${anchors.torsoAnchor.formatOffset()} " +
                "supportBadge=${anchors.supportBadgeAnchor.formatOffset()} " +
                "supportCenters=${anchors.supportCenters.joinToString(prefix = "[", postfix = "]") { it.formatOffset() }} " +
                "bodyAnchors=${anchors.bodyAnchors.mapValues { it.value.formatOffset() }} " +
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
