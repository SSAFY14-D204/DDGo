package com.ddgo.app.feature.climbing.upload.ui.shared.organism

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PoseWorldPoint
import com.ddgo.app.feature.climbing.upload.VideoContentRect
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.sqrt

private val AttemptPhysicsComColor = Color(0xFF53A6FF)
private val AttemptPhysicsHeatmapLowColor = Color(0xFF46D6D0)
private val AttemptPhysicsHeatmapMidColor = Color(0xFFFFD166)
private val AttemptPhysicsHeatmapHighColor = Color(0xFFFF6B3D)
private val AttemptPhysicsTorsoFrameColor = AttemptPhysicsHeatmapLowColor.copy(alpha = 0.38f)
private val AttemptPhysicsTorsoFrameSegments = listOf(
    11 to 12,
    11 to 23,
    12 to 24,
    23 to 24
)
private val AttemptPhysicsLimbHeatmapSegments = linkedMapOf(
    "left_arm" to listOf(11 to 13, 13 to 15),
    "right_arm" to listOf(12 to 14, 14 to 16),
    "left_leg" to listOf(23 to 25, 25 to 27, 27 to 31),
    "right_leg" to listOf(24 to 26, 26 to 28, 28 to 32)
)
private val AttemptPhysicsLimbHeatmapKeys = AttemptPhysicsLimbHeatmapSegments.keys.toList()
private const val AttemptPhysicsComLateralScale = 0.7f
private const val AttemptPhysicsComVerticalScale = 0.7f
private const val AttemptPhysicsComMaxOffsetShoulderRatio = 0.32f

@Composable
internal fun BoxScope.AttemptPhysicsLimbHeatmapAndComOverlay(
    renderState: AttemptVideoOverlayRenderState,
    physicsResult: JsonObject?,
    showLimbHeatmap: Boolean = true,
    showCom: Boolean = true
) {
    if (!showLimbHeatmap && !showCom) return

    val pose = renderState.currentOverlayPose ?: return
    val contentRect = renderState.videoContentRect
    if (contentRect.width <= 0f || contentRect.height <= 0f) return

    val snapshot = remember(physicsResult, renderState.displayedPositionMs) {
        buildAttemptPhysicsOverlaySnapshot(
            physicsResult = physicsResult,
            displayedPositionMs = renderState.displayedPositionMs
        )
    } ?: return

    val comProjection = remember(pose, contentRect, snapshot) {
        buildAttemptPhysicsComProjection(
            pose = pose,
            contentRect = contentRect,
            snapshot = snapshot
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (showLimbHeatmap) {
                drawAttemptPhysicsLimbHeatmap(
                    pose = pose,
                    contentRect = contentRect,
                    snapshot = snapshot
                )
            }
            if (showCom) {
                drawAttemptPhysicsCom(
                    snapshot = snapshot,
                    comProjection = comProjection
                )
            }
        }
    }
}

private data class AttemptPhysicsOverlaySnapshot(
    val shoulderWidthM: Double?,
    val com: AttemptPhysicsComData,
    val bodyLoads: Map<String, Double>
)

private data class AttemptPhysicsComData(
    val x: Double?,
    val y: Double?,
    val z: Double?
) {
    val hasValues: Boolean
        get() = x != null || y != null || z != null
}

private data class AttemptPhysicsComProjection(
    val overlayCenter: Offset?,
    val deltaDepthM: Double?,
    val pxPerMeter: Float?
)

private data class AttemptPhysicsScreenBasis(
    val leftShoulder: Offset,
    val rightShoulder: Offset,
    val leftHip: Offset,
    val rightHip: Offset,
    val upAxis: Offset,
    val leftAxis: Offset
)

private data class AttemptPhysicsWorldBasis(
    val torsoCenter: AttemptPhysicsVec3,
    val upAxis: AttemptPhysicsVec3,
    val leftAxis: AttemptPhysicsVec3,
    val forwardAxis: AttemptPhysicsVec3
)

private data class AttemptPhysicsVec3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    operator fun plus(other: AttemptPhysicsVec3): AttemptPhysicsVec3 =
        AttemptPhysicsVec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: AttemptPhysicsVec3): AttemptPhysicsVec3 =
        AttemptPhysicsVec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Double): AttemptPhysicsVec3 =
        AttemptPhysicsVec3(x * scale, y * scale, z * scale)

    fun dot(other: AttemptPhysicsVec3): Double = (x * other.x) + (y * other.y) + (z * other.z)

    fun cross(other: AttemptPhysicsVec3): AttemptPhysicsVec3 = AttemptPhysicsVec3(
        x = (y * other.z) - (z * other.y),
        y = (z * other.x) - (x * other.z),
        z = (x * other.y) - (y * other.x)
    )

    fun norm(): Double = sqrt((x * x) + (y * y) + (z * z))

    fun normalizedOrNull(): AttemptPhysicsVec3? {
        val norm = norm()
        if (norm <= 1e-8) return null
        return AttemptPhysicsVec3(x / norm, y / norm, z / norm)
    }

    companion object {
        fun average(vararg values: AttemptPhysicsVec3): AttemptPhysicsVec3 {
            val safeValues = values.toList()
            val size = safeValues.size.toDouble()
            return AttemptPhysicsVec3(
                x = safeValues.sumOf { it.x } / size,
                y = safeValues.sumOf { it.y } / size,
                z = safeValues.sumOf { it.z } / size
            )
        }
    }
}

private fun buildAttemptPhysicsOverlaySnapshot(
    physicsResult: JsonObject?,
    displayedPositionMs: Long
): AttemptPhysicsOverlaySnapshot? {
    val frames = physicsResult
        ?.getArrayOrNull("frames")
        ?.mapNotNull { it.asObjectOrNull() }
        .orEmpty()
    if (frames.isEmpty()) return null

    val nearestFrame = frames.minByOrNull { frame ->
        abs((frame.getLongOrNull("timestamp_ms") ?: Long.MAX_VALUE) - displayedPositionMs)
    } ?: return null

    return AttemptPhysicsOverlaySnapshot(
        shoulderWidthM = physicsResult
            .getObjectOrNull("personalization")
            .getObjectOrNull("applied_metrics_m")
            .getDoubleOrNull("shoulder_width_m"),
        com = nearestFrame.getArrayOrNull("com_position_m").toAttemptPhysicsComData(),
        bodyLoads = extractAttemptPhysicsBodyLoads(
            bodyLoadRaw = nearestFrame.getObjectOrNull("body_loads")
        )
    )
}

private fun buildAttemptPhysicsComProjection(
    pose: Pose,
    contentRect: VideoContentRect,
    snapshot: AttemptPhysicsOverlaySnapshot
): AttemptPhysicsComProjection? {
    val torsoAnchor = pose.averageLandmarkOffset(
        contentRect = contentRect,
        requiredCount = 2,
        landmarkIndices = intArrayOf(11, 12, 23, 24)
    ) ?: return null

    if (!snapshot.com.hasValues) {
        return AttemptPhysicsComProjection(
            overlayCenter = null,
            deltaDepthM = null,
            pxPerMeter = null
        )
    }

    val shoulderWidthM = snapshot.shoulderWidthM
    if (shoulderWidthM == null || shoulderWidthM <= 1e-6) {
        return AttemptPhysicsComProjection(
            overlayCenter = torsoAnchor,
            deltaDepthM = null,
            pxPerMeter = null
        )
    }

    val screenBasis = pose.buildAttemptPhysicsScreenBasis(contentRect) ?: return null
    val worldBasis = pose.buildAttemptPhysicsWorldBasis(shoulderWidthM) ?: return null
    val shoulderScreenWidthPx = (screenBasis.leftShoulder - screenBasis.rightShoulder).magnitude()
    if (shoulderScreenWidthPx <= 1f) return null

    val pxPerMeter = shoulderScreenWidthPx / shoulderWidthM.toFloat()
    val comWorld = AttemptPhysicsVec3(
        x = snapshot.com.x ?: return null,
        y = snapshot.com.y ?: return null,
        z = snapshot.com.z ?: return null
    )
    val deltaWorld = comWorld - worldBasis.torsoCenter
    val deltaLeftM = deltaWorld.dot(worldBasis.leftAxis)
    val deltaUpM = deltaWorld.dot(worldBasis.upAxis)
    val deltaDepthM = deltaWorld.dot(worldBasis.forwardAxis)

    val rawOffset = (screenBasis.leftAxis * (deltaLeftM.toFloat() * pxPerMeter * AttemptPhysicsComLateralScale)) +
        (screenBasis.upAxis * (deltaUpM.toFloat() * pxPerMeter * AttemptPhysicsComVerticalScale))
    val maxComponentPx = maxOf(
        18f,
        shoulderScreenWidthPx * AttemptPhysicsComMaxOffsetShoulderRatio
    )
    val clampedOffset = Offset(
        x = rawOffset.x.coerceIn(-maxComponentPx, maxComponentPx),
        y = rawOffset.y.coerceIn(-maxComponentPx, maxComponentPx)
    )

    return AttemptPhysicsComProjection(
        overlayCenter = torsoAnchor + clampedOffset,
        deltaDepthM = deltaDepthM,
        pxPerMeter = pxPerMeter
    )
}

private fun DrawScope.drawAttemptPhysicsLimbHeatmap(
    pose: Pose,
    contentRect: VideoContentRect,
    snapshot: AttemptPhysicsOverlaySnapshot
) {
    val landmarkOffsets = pose.buildLandmarkOffsetMap(contentRect)
    if (landmarkOffsets.isEmpty()) return
    val strokeWidth = bodyHeatmapStrokeWidth()

    AttemptPhysicsTorsoFrameSegments.forEach { (startIndex, endIndex) ->
        val start = landmarkOffsets[startIndex] ?: return@forEach
        val end = landmarkOffsets[endIndex] ?: return@forEach
        drawLine(
            color = AttemptPhysicsTorsoFrameColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }

    val frameMaxLoad = AttemptPhysicsLimbHeatmapKeys
        .mapNotNull { key -> snapshot.bodyLoads[key] }
        .filter { it > 0.0 }
        .maxOrNull()
        ?: return

    AttemptPhysicsLimbHeatmapSegments.forEach { (groupKey, segments) ->
        val rawLoad = snapshot.bodyLoads[groupKey] ?: return@forEach
        if (rawLoad <= 0.0) return@forEach
        val intensity = (rawLoad / frameMaxLoad).coerceIn(0.08, 1.0).toFloat()
        val lineColor = attemptPhysicsHeatmapColorForIntensity(intensity)

        segments.forEach { (startIndex, endIndex) ->
            val start = landmarkOffsets[startIndex] ?: return@forEach
            val end = landmarkOffsets[endIndex] ?: return@forEach
            drawLine(
                color = lineColor.copy(alpha = 0.88f),
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawAttemptPhysicsCom(
    snapshot: AttemptPhysicsOverlaySnapshot,
    comProjection: AttemptPhysicsComProjection?
) {
    if (!snapshot.com.hasValues) return
    val center = comProjection?.overlayCenter ?: return
    val comDepthPx = (
        abs(comProjection.deltaDepthM?.toFloat() ?: 0f) *
            (comProjection.pxPerMeter ?: 0f) *
            0.12f
        ).coerceIn(0f, 18.dp.toPx())

    drawCircle(
        color = AttemptPhysicsComColor.copy(alpha = 0.18f),
        radius = 18.dp.toPx() + comDepthPx,
        center = center
    )
    drawCircle(
        color = AttemptPhysicsComColor,
        radius = 7.dp.toPx(),
        center = center
    )
    drawCircle(
        color = AttemptPhysicsComColor.copy(alpha = 0.9f),
        radius = 11.dp.toPx() + (comDepthPx * 0.35f),
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun extractAttemptPhysicsBodyLoads(bodyLoadRaw: JsonObject?): Map<String, Double> {
    return buildMap {
        AttemptPhysicsLimbHeatmapKeys.forEach { key ->
            put(key, bodyLoadRaw.getDoubleOrNull(key) ?: 0.0)
        }
    }
}

private fun JsonArray?.toAttemptPhysicsComData(): AttemptPhysicsComData {
    return AttemptPhysicsComData(
        x = this?.getOrNull(0).asDoubleOrNull(),
        y = this?.getOrNull(1).asDoubleOrNull(),
        z = this?.getOrNull(2).asDoubleOrNull()
    )
}

private fun Pose.averageLandmarkOffset(
    contentRect: VideoContentRect,
    requiredCount: Int,
    landmarkIndices: IntArray
): Offset? {
    if (contentRect.width <= 0f || contentRect.height <= 0f) return null
    val byIndex = landmarks.associateBy(PoseLandmark::index)
    val points = landmarkIndices.toList().mapNotNull { index ->
        byIndex[index]?.toScreenOffset(contentRect)
    }
    if (points.size < requiredCount) return null
    return points.averageOffset()
}

private fun Pose.buildAttemptPhysicsScreenBasis(contentRect: VideoContentRect): AttemptPhysicsScreenBasis? {
    val byIndex = landmarks.associateBy(PoseLandmark::index)
    val leftShoulder = byIndex[11]?.toScreenOffset(contentRect) ?: return null
    val rightShoulder = byIndex[12]?.toScreenOffset(contentRect) ?: return null
    val leftHip = byIndex[23]?.toScreenOffset(contentRect) ?: return null
    val rightHip = byIndex[24]?.toScreenOffset(contentRect) ?: return null
    val shoulderMid = listOf(leftShoulder, rightShoulder).averageOffset()
    val hipMid = listOf(leftHip, rightHip).averageOffset()
    val upAxis = (shoulderMid - hipMid).normalizedOrNull() ?: return null
    val leftAxis = (leftShoulder - rightShoulder).normalizedOrNull() ?: return null
    return AttemptPhysicsScreenBasis(
        leftShoulder = leftShoulder,
        rightShoulder = rightShoulder,
        leftHip = leftHip,
        rightHip = rightHip,
        upAxis = upAxis,
        leftAxis = leftAxis
    )
}

private fun Pose.buildAttemptPhysicsWorldBasis(shoulderWidthM: Double): AttemptPhysicsWorldBasis? {
    val leftShoulderLocal = worldLandmarksSample["left_shoulder"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null
    val rightShoulderLocal = worldLandmarksSample["right_shoulder"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null
    val leftHipLocal = worldLandmarksSample["left_hip"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null
    val rightHipLocal = worldLandmarksSample["right_hip"]?.toMujocoLocal(worldLandmarksSample)
        ?: return null

    val localShoulderWidth = (leftShoulderLocal - rightShoulderLocal).norm()
    if (localShoulderWidth <= 1e-6) return null

    val scale = shoulderWidthM / localShoulderWidth
    val pelvisLocal = (leftHipLocal + rightHipLocal) * 0.5
    val offsetWorld = AttemptPhysicsVec3(0.0, 0.0, 1.05) - (pelvisLocal * scale)

    val leftShoulderWorld = (leftShoulderLocal * scale) + offsetWorld
    val rightShoulderWorld = (rightShoulderLocal * scale) + offsetWorld
    val leftHipWorld = (leftHipLocal * scale) + offsetWorld
    val rightHipWorld = (rightHipLocal * scale) + offsetWorld

    val shoulderMid = (leftShoulderWorld + rightShoulderWorld) * 0.5
    val hipMid = (leftHipWorld + rightHipWorld) * 0.5
    val torsoCenter = AttemptPhysicsVec3.average(
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
    val forwardAxis = leftAxis.cross(upAxis).normalizedOrNull() ?: AttemptPhysicsVec3(1.0, 0.0, 0.0)
    leftAxis = upAxis.cross(forwardAxis).normalizedOrNull() ?: leftAxis

    return AttemptPhysicsWorldBasis(
        torsoCenter = torsoCenter,
        upAxis = upAxis,
        leftAxis = leftAxis,
        forwardAxis = forwardAxis
    )
}

private fun Pose.buildLandmarkOffsetMap(contentRect: VideoContentRect): Map<Int, Offset> {
    if (contentRect.width <= 0f || contentRect.height <= 0f) return emptyMap()
    return landmarks.associate { landmark ->
        landmark.index to landmark.toScreenOffset(contentRect)
    }
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

private fun Offset.magnitude(): Float = sqrt((x * x) + (y * y))

private fun Offset.normalizedOrNull(): Offset? {
    val magnitude = magnitude()
    if (magnitude <= 1e-4f) return null
    return Offset(x / magnitude, y / magnitude)
}

private fun PoseWorldPoint.toMujocoLocal(worldSample: Map<String, PoseWorldPoint>): AttemptPhysicsVec3 {
    val verticalSign = inferAttemptPhysicsVerticalSign(worldSample)
    return AttemptPhysicsVec3(
        x = -z.toDouble(),
        y = -x.toDouble(),
        z = verticalSign * y.toDouble()
    )
}

private fun inferAttemptPhysicsVerticalSign(worldSample: Map<String, PoseWorldPoint>): Double {
    val leftShoulder = worldSample["left_shoulder"]
    val rightShoulder = worldSample["right_shoulder"]
    val leftHip = worldSample["left_hip"]
    val rightHip = worldSample["right_hip"]
    if (leftShoulder == null || rightShoulder == null || leftHip == null || rightHip == null) return 1.0

    val shoulderMidY = 0.5 * (leftShoulder.y + rightShoulder.y)
    val hipMidY = 0.5 * (leftHip.y + rightHip.y)
    val torsoDeltaY = shoulderMidY - hipMidY
    return if (abs(torsoDeltaY) < 1e-5f || torsoDeltaY >= 0f) {
        1.0
    } else {
        -1.0
    }
}

private fun attemptPhysicsHeatmapColorForIntensity(intensity: Float): Color {
    val clamped = intensity.coerceIn(0f, 1f)
    return if (clamped < 0.5f) {
        lerp(AttemptPhysicsHeatmapLowColor, AttemptPhysicsHeatmapMidColor, clamped / 0.5f)
    } else {
        lerp(AttemptPhysicsHeatmapMidColor, AttemptPhysicsHeatmapHighColor, (clamped - 0.5f) / 0.5f)
    }
}

private fun DrawScope.bodyHeatmapStrokeWidth(): Float {
    // AttemptPreviewHero uses PoseOverlay(pointRadiusScale = 0.75f),
    // so the visible white joint point radius is 3dp and its diameter is 6dp.
    // Match the limb heatmap stroke width to that white point diameter.
    return 6.dp.toPx()
}

private fun JsonObject?.getObjectOrNull(key: String): JsonObject? = this?.get(key).asObjectOrNull()

private fun JsonObject?.getArrayOrNull(key: String): JsonArray? = this?.get(key).asArrayOrNull()

private fun JsonObject?.getDoubleOrNull(key: String): Double? = this?.get(key).asDoubleOrNull()

private fun JsonObject?.getLongOrNull(key: String): Long? = this?.get(key).asLongOrNull()

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asLongOrNull(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toLongOrNull()
}

private fun JsonElement?.asDoubleOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.content.toDoubleOrNull()
}
