package com.ddgo.app.feature.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.calculateExpandedVerticalCropBoundsFromSelectedHoldExtents
import com.ddgo.app.feature.climbing.upload.holdLabelToComposeColor
import com.ddgo.app.feature.climbing.upload.toScreenRect
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoOverlayRenderState
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSection
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSectionState
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val DebugBackground = Color(0xFF0B0D12)
private val DebugSurface = Color(0xFF171B22)
private val DebugSurfaceBorder = Color(0xFF2A3040)
private val DebugLine = Color(0xFF6EE7F9)
private val DebugPoint = Color.White
private val DebugAccent = Color(0xFF6FA8FF)
private val DebugComColor = Color(0xFF53A6FF)
private val DebugSupportStableColor = Color(0xFF3DDC97)
private val DebugSupportUnstableColor = Color(0xFFFF6B6B)
private val DebugBodyLoadColor = Color(0xFFFFC857)
private val DebugHeatmapLowColor = Color(0xFF46D6D0)
private val DebugHeatmapMidColor = Color(0xFFFFD166)
private val DebugHeatmapHighColor = Color(0xFFFF6B3D)
private val DebugForceColor = Color(0xFFCF8BFF)
private val DebugStatusColor = Color(0xFF9FB5FF)
private val HiddenFaceLandmarks = (1..10).toSet()
private val EmptyVideoContentRect = com.ddgo.app.feature.climbing.upload.VideoContentRect(
    left = 0f,
    top = 0f,
    width = 0f,
    height = 0f
)
private val BODY_HEATMAP_SEGMENTS = linkedMapOf(
    "core" to listOf(11 to 12, 11 to 23, 12 to 24, 23 to 24),
    "left_arm" to listOf(11 to 13, 13 to 15),
    "right_arm" to listOf(12 to 14, 14 to 16),
    "left_leg" to listOf(23 to 25, 25 to 27, 27 to 31),
    "right_leg" to listOf(24 to 26, 26 to 28, 28 to 32)
)
private val JOINT_LOAD_POINT_ORDER = listOf(
    "core",
    "left_shoulder",
    "right_shoulder",
    "left_elbow",
    "right_elbow",
    "left_hip",
    "right_hip",
    "left_knee",
    "right_knee",
    "left_ankle",
    "right_ankle"
)
private const val MAX_JOINT_HOTSPOTS = 3

private data class UploadPhysicsOverlayControls(
    val showFrameMeta: Boolean = true,
    val showChips: Boolean = true,
    val showCom: Boolean = true,
    val showSupport: Boolean = true,
    val showBodyHeatmap: Boolean = true,
    val showBodyChips: Boolean = false,
    val showJointLoadPoints: Boolean = true,
    val showJointHotspots: Boolean = false,
    val showContactForce: Boolean = true,
    val visibleBodyLoadKeys: Set<String> = BODY_LOAD_KEYS.toSet(),
    val visibleContactKeys: Set<String> = CONTACT_LIMB_KEYS.toSet()
) {
    fun toggleBodyLoadKey(key: String, enabled: Boolean): UploadPhysicsOverlayControls {
        val updated = visibleBodyLoadKeys.toMutableSet().apply {
            if (enabled) add(key) else remove(key)
        }
        return copy(visibleBodyLoadKeys = updated)
    }

    fun toggleContactKey(key: String, enabled: Boolean): UploadPhysicsOverlayControls {
        val updated = visibleContactKeys.toMutableSet().apply {
            if (enabled) add(key) else remove(key)
        }
        return copy(visibleContactKeys = updated)
    }
}

private data class OverlayChipSpec(
    val key: String,
    val text: String,
    val color: Color,
    val anchor: Offset?,
    val fallback: Offset
)

private data class OverlayChipRender(
    val key: String,
    val text: String,
    val color: Color,
    val position: Offset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UploadPhysicsOverlayDebugScreen(
    uploadViewModel: UploadViewModel?,
    onNavigateBack: () -> Unit
) {
    val currentAttemptIndex = uploadViewModel?.currentAttemptIndex ?: 0
    val numberedHolds = uploadViewModel?.currentAttemptDisplayHolds.orEmpty()
    val rawHolds = uploadViewModel?.allRawHolds.orEmpty()
    val currentVideoUri = uploadViewModel
        ?.playbackAttemptUris
        ?.getOrNull(currentAttemptIndex)
    val currentAttemptAiAnalysisResult = uploadViewModel?.currentAttemptAiAnalysisResult
    val physicsResult = currentAttemptAiAnalysisResult?.physicsResult
    val overlayCache = uploadViewModel?.currentAttemptOverlayCache
    val currentAttemptPoses = uploadViewModel?.currentAttemptPoseSequence.orEmpty()
    val cropBounds = remember(numberedHolds) {
        calculateExpandedVerticalCropBoundsFromSelectedHoldExtents(numberedHolds)
    }

    var displayedPositionMs by remember(currentVideoUri) { mutableLongStateOf(0L) }
    var latestRenderState by remember(currentVideoUri) {
        mutableStateOf<AttemptVideoOverlayRenderState?>(null)
    }
    var overlayControls by remember(currentVideoUri) {
        mutableStateOf(UploadPhysicsOverlayControls())
    }

    LaunchedEffect(currentVideoUri) {
        displayedPositionMs = 0L
        latestRenderState = null
    }

    val snapshot = remember(physicsResult, displayedPositionMs) {
        buildUploadPhysicsDebugSnapshot(
            physicsResult = physicsResult,
            displayedPositionMs = displayedPositionMs
        )
    }
    val latestAnchors = remember(
        latestRenderState?.currentOverlayPose,
        latestRenderState?.videoContentRect,
        numberedHolds,
        snapshot
    ) {
        buildUploadPhysicsDebugAnchorSet(
            pose = latestRenderState?.currentOverlayPose,
            contentRect = latestRenderState?.videoContentRect ?: EmptyVideoContentRect,
            numberedHolds = numberedHolds,
            snapshot = snapshot
        )
    }

    Scaffold(
        containerColor = DebugBackground,
        topBar = {
            TopAppBar(
                title = { Text("Upload Physics Overlay", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DebugBackground)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "Current Attempt",
                body = buildString {
                    append("attempt=")
                    append(currentAttemptIndex + 1)
                    append("  ")
                    append("mode=")
                    append(currentAttemptAiAnalysisResult?.mode ?: "unknown")
                    append("  ")
                    append("physics=")
                    append(if (physicsResult != null) "ready" else "missing")
                }
            )

            if (uploadViewModel == null) {
                StatusCard(
                    title = "No Upload Session",
                    body = "MainGraph upload session is not on the back stack. Start from the main app upload flow and reopen this page."
                )
                return@Column
            }

            if (physicsResult == null) {
                StatusCard(
                    title = "PHYSICS Result Missing",
                    body = "Current attempt does not have physics_result. Switch DEV AI mode to PHYSICS, finish batch AI, and reopen this page."
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        UploadPhysicsDebugLogger.log(
                            renderState = latestRenderState,
                            snapshot = snapshot,
                            anchors = latestAnchors
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Log Current Frame")
                }

                StatusChip(
                    text = snapshot?.let {
                        "frame=${it.meta.frameIndex} t=${it.meta.timestampMs}ms"
                    } ?: "frame=none"
                )
            }

            UploadPhysicsOverlayControlsCard(
                controls = overlayControls,
                onControlsChanged = { overlayControls = it }
            )

            if (currentVideoUri.isNullOrBlank()) {
                StatusCard(
                    title = "No Video",
                    body = "The current attempt has no playback video URI. Raw physics values below still use the nearest frame."
                )
            } else {
                AttemptVideoSection(
                    state = AttemptVideoSectionState(
                        videoUri = currentVideoUri,
                        numberedHolds = numberedHolds,
                        rawHolds = rawHolds,
                        viewportCropBounds = cropBounds,
                        attemptPoseSequence = currentAttemptPoses,
                        overlayCache = overlayCache
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    lineColor = DebugLine,
                    pointColor = DebugPoint,
                    scrubberColors = PoseScrubberColors(
                        trackColor = Color.White.copy(alpha = 0.16f),
                        progressColor = DebugAccent,
                        thumbColor = Color.White,
                        textColor = Color.White.copy(alpha = 0.88f)
                    ),
                    controlSurfaceColor = DebugBackground,
                    hiddenLandmarkIndices = HiddenFaceLandmarks,
                    topSafeInset = 24.dp,
                    bottomSafeInset = 64.dp,
                    controlAreaHeight = 132.dp,
                    onDisplayedPositionChanged = { displayedPositionMs = it },
                    overlayContent = { renderState ->
                        SideEffect {
                            latestRenderState = renderState
                        }
                        UploadPhysicsVideoOverlay(
                            renderState = renderState,
                            numberedHolds = numberedHolds,
                            snapshot = snapshot,
                            controls = overlayControls
                        )
                    }
                )
            }

            if (overlayCache == null) {
                StatusCard(
                    title = "Pose Overlay Fallback",
                    body = "currentAttemptOverlayCache is empty, so skeleton/body anchors may fall back to HUD positions. Physics frame parsing still works."
                )
            }

            UploadPhysicsRawPanel(snapshot = snapshot)
        }
    }
}

@Composable
private fun BoxScope.UploadPhysicsVideoOverlay(
    renderState: AttemptVideoOverlayRenderState,
    numberedHolds: List<HoldNumbered>,
    snapshot: UploadPhysicsDebugSnapshot?,
    controls: UploadPhysicsOverlayControls
) {
    val contentRect = renderState.videoContentRect
    val anchors = remember(
        renderState.currentOverlayPose,
        renderState.videoContentRect,
        numberedHolds,
        snapshot
    ) {
        buildUploadPhysicsDebugAnchorSet(
            pose = renderState.currentOverlayPose,
            contentRect = renderState.videoContentRect,
            numberedHolds = numberedHolds,
            snapshot = snapshot
        )
    }
    val overlayChips = remember(
        contentRect,
        snapshot,
        anchors,
        controls
    ) {
        snapshot?.let {
            buildOverlayChips(
                snapshot = it,
                anchors = anchors,
                contentRect = contentRect,
                controls = controls
            )
        }.orEmpty()
    }
    val jointAnchorLoads = remember(snapshot?.jointLoad?.loads, controls.visibleBodyLoadKeys) {
        snapshot?.jointLoad?.loads?.let { loads ->
            buildJointAnchorLoadMap(
                jointLoads = loads,
                visibleGroups = controls.visibleBodyLoadKeys
            )
        }.orEmpty()
    }

    val supportColor = when (snapshot?.support?.insideSupport) {
        true -> DebugSupportStableColor
        false -> DebugSupportUnstableColor
        null -> DebugSupportUnstableColor.copy(alpha = 0.9f)
    }
    val rightHudX = max(contentRect.left + 12f, contentRect.left + contentRect.width - 150f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(5f)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (contentRect.width <= 0f || contentRect.height <= 0f || snapshot == null) return@Canvas

            val activeHoldIds = snapshot.support.activeHoldIds.toSet()
            val comAnchor = anchors.comProjection?.overlayCenter ?: anchors.torsoAnchor
            val comDepthPx = (
                abs(anchors.comProjection?.deltaDepthM?.toFloat() ?: 0f) *
                    (anchors.comProjection?.pxPerMeter ?: 0f) *
                    0.12f
                ).coerceIn(0f, 18.dp.toPx())
            if (controls.showSupport) {
                numberedHolds
                    .filter { it.holdNo in activeHoldIds }
                    .forEach { numbered ->
                        drawActiveHold(
                            hold = numbered.hold,
                            contentRect = contentRect,
                            emphasisColor = supportColor,
                            outlineColor = holdLabelToComposeColor(numbered.hold.colorLabel)
                        )
                    }

                val supportPoints = anchors.sortedSupportPolygon()
                when (supportPoints.size) {
                    0 -> Unit
                    1 -> {
                        drawCircle(
                            color = supportColor.copy(alpha = 0.2f),
                            radius = 16.dp.toPx(),
                            center = supportPoints.first()
                        )
                        drawCircle(
                            color = supportColor,
                            radius = 7.dp.toPx(),
                            center = supportPoints.first()
                        )
                    }
                    2 -> {
                        drawLine(
                            color = supportColor,
                            start = supportPoints[0],
                            end = supportPoints[1],
                            strokeWidth = 4.dp.toPx()
                        )
                    }
                    else -> {
                        val path = supportPoints.toClosedPath()
                        drawPath(
                            path = path,
                            color = supportColor.copy(alpha = 0.16f)
                        )
                        drawPath(
                            path = path,
                            color = supportColor,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }

            renderState.currentOverlayPose?.let { pose ->
                if (controls.showBodyHeatmap) {
                    drawBodyHeatmap(
                        pose = pose,
                        contentRect = contentRect,
                        snapshot = snapshot,
                        visibleGroups = controls.visibleBodyLoadKeys
                    )
                }
                if (controls.showJointHotspots) {
                    drawJointHotspots(
                        snapshot = snapshot,
                        anchors = anchors,
                        visibleGroups = controls.visibleBodyLoadKeys
                    )
                }
            }

            if (controls.showCom && snapshot.com.hasValues) {
                comAnchor?.let { center ->
                    drawCircle(
                        color = DebugComColor.copy(alpha = 0.18f),
                        radius = 18.dp.toPx() + comDepthPx,
                        center = center
                    )
                    drawCircle(
                        color = DebugComColor,
                        radius = 7.dp.toPx(),
                        center = center
                    )
                    drawCircle(
                        color = DebugComColor.copy(alpha = 0.9f),
                        radius = 11.dp.toPx() + (comDepthPx * 0.35f),
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        overlayChips.forEach { chip ->
            PhysicsOverlayChip(
                text = chip.text,
                color = chip.color,
                position = chip.position
            )
        }

        if (controls.showJointLoadPoints) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(6f)
            ) {
                if (contentRect.width <= 0f || contentRect.height <= 0f || snapshot == null) return@Canvas
                drawJointLoadPoints(
                    anchors = anchors,
                    jointAnchorLoads = jointAnchorLoads
                )
            }
        }
    }
}

@Composable
private fun BoxScope.PhysicsOverlayChip(
    text: String,
    color: Color,
    position: Offset
) {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                IntOffset(
                    x = position.x.roundToInt(),
                    y = position.y.roundToInt()
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.92f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UploadPhysicsOverlayControlsCard(
    controls: UploadPhysicsOverlayControls,
    onControlsChanged: (UploadPhysicsOverlayControls) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DebugSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Overlay Controls",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayCheckbox(
                    label = "Frame",
                    checked = controls.showFrameMeta,
                    onCheckedChange = { onControlsChanged(controls.copy(showFrameMeta = it)) }
                )
                OverlayCheckbox(
                    label = "Chips",
                    checked = controls.showChips,
                    onCheckedChange = { onControlsChanged(controls.copy(showChips = it)) }
                )
                OverlayCheckbox(
                    label = "CoM",
                    checked = controls.showCom,
                    onCheckedChange = { onControlsChanged(controls.copy(showCom = it)) }
                )
                OverlayCheckbox(
                    label = "Support",
                    checked = controls.showSupport,
                    onCheckedChange = { onControlsChanged(controls.copy(showSupport = it)) }
                )
                OverlayCheckbox(
                    label = "Body Heatmap",
                    checked = controls.showBodyHeatmap,
                    onCheckedChange = { onControlsChanged(controls.copy(showBodyHeatmap = it)) }
                )
                OverlayCheckbox(
                    label = "Body Chips",
                    checked = controls.showBodyChips,
                    onCheckedChange = { onControlsChanged(controls.copy(showBodyChips = it)) }
                )
                OverlayCheckbox(
                    label = "Joint Load Points",
                    checked = controls.showJointLoadPoints,
                    onCheckedChange = { onControlsChanged(controls.copy(showJointLoadPoints = it)) }
                )
                OverlayCheckbox(
                    label = "Joint Hotspot",
                    checked = controls.showJointHotspots,
                    onCheckedChange = { onControlsChanged(controls.copy(showJointHotspots = it)) }
                )
                OverlayCheckbox(
                    label = "Contact",
                    checked = controls.showContactForce,
                    onCheckedChange = { onControlsChanged(controls.copy(showContactForce = it)) }
                )
            }

            if (controls.showBodyHeatmap || controls.showBodyChips || controls.showJointLoadPoints || controls.showJointHotspots) {
                Text(
                    text = "Body Overlay Detail",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BODY_LOAD_KEYS.forEach { key ->
                        OverlayCheckbox(
                            label = key,
                            checked = controls.visibleBodyLoadKeys.contains(key),
                            onCheckedChange = { checked ->
                                onControlsChanged(controls.toggleBodyLoadKey(key, checked))
                            }
                        )
                    }
                }
            }

            if (controls.showContactForce) {
                Text(
                    text = "Contact Detail",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CONTACT_LIMB_KEYS.forEach { limb ->
                        OverlayCheckbox(
                            label = shortLimbLabel(limb),
                            checked = controls.visibleContactKeys.contains(limb),
                            onCheckedChange = { checked ->
                                onControlsChanged(controls.toggleContactKey(limb, checked))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun UploadPhysicsRawPanel(
    snapshot: UploadPhysicsDebugSnapshot?
) {
    val frameLines = snapshot?.let {
        listOf(
            "displayedPositionMs=${it.displayedPositionMs}",
            "frameDistanceMs=${it.frameDistanceMs}",
            "frame_index=${it.meta.frameIndex}",
            "timestamp_ms=${it.meta.timestampMs}",
            "phase=${it.meta.phase ?: "-"}",
            "analysis_confidence=${it.meta.analysisConfidence ?: "-"}",
            "support_mode=${it.meta.supportMode ?: "-"}"
        )
    } ?: listOf("No frame available for the current position.")

    val comLines = snapshot?.let {
        listOf(
            "x=${formatDebugNumber(it.com.x)}",
            "y=${formatDebugNumber(it.com.y)}",
            "z=${formatDebugNumber(it.com.z)}"
        )
    } ?: listOf("No frame available for the current position.")

    val supportLines = snapshot?.let {
        buildList {
            add("inside_support=${it.support.insideSupport}")
            add("support_type=${it.support.supportType ?: "-"}")
            add("support_geometry=${it.support.supportGeometry ?: "-"}")
            add("stability_margin_m=${formatDebugNumber(it.support.stabilityMarginM)}")
            add("distance_to_support_m=${formatDebugNumber(it.support.distanceToSupportM)}")
            add("confidence=${formatDebugNumber(it.support.confidence)}")
            add("active_hold_ids=${it.support.activeHoldIdsByLimb}")
        }
    } ?: listOf("No frame available for the current position.")

    val bodyLoadLines = snapshot?.let {
        buildList {
            add("summary_available=${it.bodyLoad.summaryAvailable}")
            BODY_LOAD_KEYS.forEach { key ->
                add("$key=${formatDebugNumber(it.bodyLoad.loads[key])}")
            }
            add("joint_load_count=${it.jointLoad.loads.size}")
            if (it.jointLoad.topJoints.isEmpty()) {
                add("top_joint_loads=none")
            } else {
                add("top_joint_loads:")
                it.jointLoad.topJoints.take(3).forEach { topJoint ->
                    add(
                        "${topJoint.joint} abs=${formatDebugNumber(topJoint.absQfrcInverse)} " +
                            "signed=${formatDebugNumber(topJoint.signedQfrcInverse)}"
                    )
                }
            }
        }
    } ?: listOf("No frame available for the current position.")

    val contactForceLines = snapshot?.let {
        buildList {
            add("contact_force_status=${it.contactForce.status ?: "-"}")
            add("contact_force_relative_residual=${formatDebugNumber(it.contactForce.relativeResidual)}")
            CONTACT_LIMB_KEYS.forEach { limb ->
                val payload = it.contactForce.limbForces[limb]
                if (payload == null) {
                    add("$limb=inactive")
                } else {
                    add(
                        "$limb force_norm_n=${formatDebugNumber(payload.forceNormN, 1)} " +
                            "vertical_force_n=${formatDebugNumber(payload.verticalForceN, 1)} " +
                            "confidence_score=${formatDebugNumber(payload.confidenceScore, 2)}"
                    )
                }
            }
        }
    } ?: listOf("No frame available for the current position.")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DebugSectionCard(title = "Frame", lines = frameLines)
        DebugSectionCard(title = "CoM", lines = comLines)
        DebugSectionCard(title = "Support", lines = supportLines)
        DebugSectionCard(title = "Body Load", lines = bodyLoadLines)
        DebugSectionCard(title = "Contact Force", lines = contactForceLines)
    }
}

@Composable
private fun DebugSectionCard(
    title: String,
    lines: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DebugSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SelectionContainer {
                Text(
                    text = lines.joinToString(separator = "\n"),
                    color = Color.White.copy(alpha = 0.92f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DebugSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DebugSurface)
            .border(
                width = 1.dp,
                color = DebugSurfaceBorder,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawJointLoadPoints(
    anchors: UploadPhysicsDebugAnchorSet,
    jointAnchorLoads: Map<String, Double>
) {
    if (jointAnchorLoads.isEmpty()) return

    val visibleLoads = JOINT_LOAD_POINT_ORDER
        .mapNotNull { key -> jointAnchorLoads[key]?.takeIf { it > 0.0 } }
    val frameMaxLoad = visibleLoads.maxOrNull() ?: return

    JOINT_LOAD_POINT_ORDER.forEachIndexed { index, anchorKey ->
        val rawLoad = jointAnchorLoads[anchorKey] ?: return@forEachIndexed
        if (rawLoad <= 0.0) return@forEachIndexed
        val anchor = anchors.jointAnchors[anchorKey] ?: return@forEachIndexed
        val intensity = (rawLoad / frameMaxLoad).coerceIn(0.10, 1.0).toFloat()
        val fillColor = heatmapColorForIntensity(intensity)
        val center = anchor + hotspotJitterOffset(index)
        val outerRadius = 9.dp.toPx() + (intensity * 8.dp.toPx())
        val innerRadius = 4.dp.toPx() + (intensity * 4.dp.toPx())

        drawCircle(
            color = fillColor.copy(alpha = 0.20f + (0.16f * intensity)),
            radius = outerRadius,
            center = center
        )
        drawCircle(
            color = fillColor.copy(alpha = 0.96f),
            radius = innerRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 1.6.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBodyHeatmap(
    pose: com.ddgo.app.domain.model.Pose,
    contentRect: com.ddgo.app.feature.climbing.upload.VideoContentRect,
    snapshot: UploadPhysicsDebugSnapshot,
    visibleGroups: Set<String>
) {
    val landmarkOffsets = pose.buildLandmarkOffsetMap(contentRect)
    if (landmarkOffsets.isEmpty()) return

    val selectedLoads = BODY_LOAD_KEYS
        .filter { visibleGroups.contains(it) }
        .mapNotNull { key -> snapshot.bodyLoad.loads[key] }
        .filter { it > 0.0 }
    val frameMaxLoad = selectedLoads.maxOrNull() ?: return

    BODY_HEATMAP_SEGMENTS.forEach { (groupKey, segments) ->
        if (!visibleGroups.contains(groupKey)) return@forEach
        val rawLoad = snapshot.bodyLoad.loads[groupKey] ?: return@forEach
        val intensity = (rawLoad / frameMaxLoad).coerceIn(0.08, 1.0).toFloat()
        val lineColor = heatmapColorForIntensity(intensity)
        val strokeWidth = bodyHeatmapStrokeWidth(intensity)

        segments.forEach { (startIndex, endIndex) ->
            val start = landmarkOffsets[startIndex] ?: return@forEach
            val end = landmarkOffsets[endIndex] ?: return@forEach
            drawLine(
                color = lineColor.copy(alpha = 0.88f),
                start = start,
                end = end,
                strokeWidth = strokeWidth
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawJointHotspots(
    snapshot: UploadPhysicsDebugSnapshot,
    anchors: UploadPhysicsDebugAnchorSet,
    visibleGroups: Set<String>
) {
    val topJoints = snapshot.jointLoad.topJoints
        .filter { topJoint ->
            resolveBodyGroupForJoint(topJoint.joint)?.let(visibleGroups::contains) == true
        }
        .take(MAX_JOINT_HOTSPOTS)
    if (topJoints.isEmpty()) return

    val maxLoad = topJoints.maxOfOrNull { it.absQfrcInverse ?: 0.0 }?.takeIf { it > 1e-6 } ?: return

    topJoints.forEachIndexed { index, topJoint ->
        val anchorKey = resolveJointAnchorKey(topJoint.joint) ?: return@forEachIndexed
        val anchor = anchors.jointAnchors[anchorKey] ?: return@forEachIndexed
        val intensity = ((topJoint.absQfrcInverse ?: 0.0) / maxLoad).coerceIn(0.12, 1.0).toFloat()
        val center = anchor + hotspotJitterOffset(index)
        val fillColor = heatmapColorForIntensity(intensity)
        val outerRadius = 11.dp.toPx() + (intensity * 10.dp.toPx())
        val innerRadius = 4.dp.toPx() + (intensity * 5.dp.toPx())

        drawCircle(
            color = fillColor.copy(alpha = 0.18f + (0.18f * intensity)),
            radius = outerRadius,
            center = center
        )
        drawCircle(
            color = fillColor.copy(alpha = 0.95f),
            radius = innerRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = outerRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

private fun com.ddgo.app.domain.model.Pose.buildLandmarkOffsetMap(
    contentRect: com.ddgo.app.feature.climbing.upload.VideoContentRect
): Map<Int, Offset> {
    if (contentRect.width <= 0f || contentRect.height <= 0f) return emptyMap()
    return landmarks.associate { landmark ->
        landmark.index to Offset(
            x = contentRect.left + (landmark.x.coerceIn(0f, 1f) * contentRect.width),
            y = contentRect.top + (landmark.y.coerceIn(0f, 1f) * contentRect.height)
        )
    }
}

private fun heatmapColorForIntensity(intensity: Float): Color {
    val clamped = intensity.coerceIn(0f, 1f)
    return if (clamped < 0.5f) {
        lerp(DebugHeatmapLowColor, DebugHeatmapMidColor, clamped / 0.5f)
    } else {
        lerp(DebugHeatmapMidColor, DebugHeatmapHighColor, (clamped - 0.5f) / 0.5f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.bodyHeatmapStrokeWidth(intensity: Float): Float {
    return 4.dp.toPx() + (intensity.coerceIn(0f, 1f) * 7.dp.toPx())
}

private fun hotspotJitterOffset(index: Int): Offset {
    return when (index % 3) {
        1 -> Offset(12f, -8f)
        2 -> Offset(-12f, 8f)
        else -> Offset.Zero
    }
}

private fun buildOverlayChips(
    snapshot: UploadPhysicsDebugSnapshot,
    anchors: UploadPhysicsDebugAnchorSet,
    contentRect: com.ddgo.app.feature.climbing.upload.VideoContentRect,
    controls: UploadPhysicsOverlayControls
): List<OverlayChipRender> {
    if (contentRect.width <= 0f || contentRect.height <= 0f) return emptyList()

    val supportColor = when (snapshot.support.insideSupport) {
        true -> DebugSupportStableColor
        false -> DebugSupportUnstableColor
        null -> DebugSupportUnstableColor.copy(alpha = 0.9f)
    }
    val rightHudX = max(contentRect.left + 12f, contentRect.left + contentRect.width - 150f)
    val chipSpecs = buildList {
        if (controls.showChips && controls.showFrameMeta) {
            add(
                OverlayChipSpec(
                    key = "frame",
                    text = buildString {
                        append("frame ")
                        append(snapshot.meta.frameIndex)
                        append("  ")
                        append(snapshot.meta.timestampMs)
                        append("ms")
                        append('\n')
                        append(snapshot.meta.phase ?: "phase -")
                        append(" / ")
                        append(snapshot.meta.analysisConfidence ?: "confidence -")
                    },
                    color = DebugStatusColor,
                    anchor = null,
                    fallback = Offset(contentRect.left + 12f, contentRect.top + 12f)
                )
            )
        }

        if (controls.showChips && controls.showCom && snapshot.com.hasValues) {
            add(
                OverlayChipSpec(
                    key = "com",
                    text = buildString {
                        append("CoM")
                        append('\n')
                        append("x ")
                        append(formatDebugNumber(snapshot.com.x))
                        append("  y ")
                        append(formatDebugNumber(snapshot.com.y))
                        append("  z ")
                        append(formatDebugNumber(snapshot.com.z))
                        anchors.comProjection?.deltaDepthM?.let { depth ->
                            append('\n')
                            append("d ")
                            append(formatDebugNumber(depth))
                            append("  ")
                            append(anchors.comProjection.overlayMode)
                        }
                    },
                    color = DebugComColor,
                    anchor = anchors.comProjection?.overlayCenter ?: anchors.torsoAnchor,
                    fallback = Offset(contentRect.left + 12f, contentRect.top + 74f)
                )
            )
        }

        if (controls.showChips && controls.showSupport) {
            add(
                OverlayChipSpec(
                    key = "support",
                    text = buildString {
                        append("support")
                        append('\n')
                        append(snapshot.support.supportType ?: "-")
                        append(" / ")
                        append(snapshot.meta.supportMode ?: "-")
                        append('\n')
                        append("margin ")
                        append(formatDebugNumber(snapshot.support.stabilityMarginM))
                        append("  dist ")
                        append(formatDebugNumber(snapshot.support.distanceToSupportM))
                    },
                    color = supportColor,
                    anchor = anchors.supportBadgeAnchor,
                    fallback = Offset(contentRect.left + 12f, contentRect.top + 142f)
                )
            )
        }

        if (controls.showChips && controls.showBodyChips) {
            BODY_LOAD_KEYS.forEachIndexed { index, key ->
                if (!controls.visibleBodyLoadKeys.contains(key)) return@forEachIndexed
                val rawValue = snapshot.bodyLoad.loads[key] ?: return@forEachIndexed
                add(
                    OverlayChipSpec(
                        key = "body_$key",
                        text = buildString {
                            append(key)
                            append('\n')
                            append(formatDebugNumber(rawValue))
                        },
                        color = DebugBodyLoadColor,
                        anchor = anchors.bodyAnchors[key],
                        fallback = Offset(
                            x = rightHudX,
                            y = contentRect.top + 12f + (index * 54f)
                        )
                    )
                )
            }
        }

        if (controls.showChips && controls.showContactForce) {
            add(
                OverlayChipSpec(
                    key = "contact_summary",
                    text = buildString {
                        append("contact")
                        append('\n')
                        append(snapshot.contactForce.status ?: "-")
                        append('\n')
                        append("res ")
                        append(formatDebugNumber(snapshot.contactForce.relativeResidual))
                    },
                    color = DebugForceColor,
                    anchor = null,
                    fallback = Offset(rightHudX, contentRect.top + 292f)
                )
            )

            CONTACT_LIMB_KEYS.forEachIndexed { index, limb ->
                if (!controls.visibleContactKeys.contains(limb)) return@forEachIndexed
                val payload = snapshot.contactForce.limbForces[limb] ?: return@forEachIndexed
                add(
                    OverlayChipSpec(
                        key = "contact_$limb",
                        text = buildString {
                            append(shortLimbLabel(limb))
                            append('\n')
                            append("F ")
                            append(formatDebugNumber(payload.forceNormN, 1))
                            append("N")
                            append('\n')
                            append("V ")
                            append(formatDebugNumber(payload.verticalForceN, 1))
                            append("  C ")
                            append(formatDebugNumber(payload.confidenceScore, 2))
                        },
                        color = DebugForceColor,
                        anchor = anchors.contactAnchors[limb],
                        fallback = Offset(
                            x = rightHudX,
                            y = contentRect.top + 356f + (index * 64f)
                        )
                    )
                )
            }
        }
    }

    return layoutOverlayChips(chipSpecs, contentRect)
}

private fun layoutOverlayChips(
    chipSpecs: List<OverlayChipSpec>,
    contentRect: com.ddgo.app.feature.climbing.upload.VideoContentRect
): List<OverlayChipRender> {
    if (chipSpecs.isEmpty()) return emptyList()
    val bounds = Rect(
        left = contentRect.left,
        top = contentRect.top,
        right = contentRect.left + contentRect.width,
        bottom = contentRect.top + contentRect.height
    )
    val usedRects = mutableListOf<Rect>()

    return chipSpecs.map { spec ->
        val estimatedSize = estimateChipSize(spec.text)
        val preferredTopLeft = spec.anchor?.let {
            Offset(
                x = it.x + 10f,
                y = it.y - 26f
            )
        }
        val candidates = buildList {
            preferredTopLeft?.let { add(it) }
            preferredTopLeft?.let { add(Offset(it.x + 92f, it.y)) }
            preferredTopLeft?.let { add(Offset(it.x - 92f, it.y)) }
            preferredTopLeft?.let { add(Offset(it.x, it.y + 68f)) }
            preferredTopLeft?.let { add(Offset(it.x, it.y - 68f)) }
            add(spec.fallback)
            add(Offset(spec.fallback.x, spec.fallback.y + 64f))
            add(Offset(spec.fallback.x, spec.fallback.y - 64f))
        }
        val resolvedTopLeft = candidates
            .map { clampChipTopLeft(it, estimatedSize, bounds) }
            .firstOrNull { candidate ->
                val rect = Rect(candidate, estimatedSize)
                usedRects.none { used -> used.overlapsWithMargin(rect, 8f) }
            }
            ?: clampChipTopLeft(spec.fallback, estimatedSize, bounds)
        usedRects += Rect(resolvedTopLeft, estimatedSize)
        OverlayChipRender(
            key = spec.key,
            text = spec.text,
            color = spec.color,
            position = resolvedTopLeft
        )
    }
}

private fun estimateChipSize(text: String): Size {
    val lines = text.lines().ifEmpty { listOf(text) }
    val maxChars = lines.maxOfOrNull { it.length } ?: 0
    val width = (maxChars * 7.1f + 34f).coerceIn(96f, 220f)
    val height = (lines.size * 16f + 20f).coerceAtLeast(40f)
    return Size(width = width, height = height)
}

private fun clampChipTopLeft(
    raw: Offset,
    size: Size,
    bounds: Rect
): Offset {
    val margin = 8f
    return Offset(
        x = raw.x.coerceIn(bounds.left + margin, bounds.right - size.width - margin),
        y = raw.y.coerceIn(bounds.top + margin, bounds.bottom - size.height - margin)
    )
}

private fun Rect.overlapsWithMargin(other: Rect, margin: Float): Boolean {
    val expanded = Rect(
        left = left - margin,
        top = top - margin,
        right = right + margin,
        bottom = bottom + margin
    )
    return expanded.overlaps(other)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawActiveHold(
    hold: Hold,
    contentRect: com.ddgo.app.feature.climbing.upload.VideoContentRect,
    emphasisColor: Color,
    outlineColor: Color
) {
    val polygon = hold.toScreenPolygon(contentRect)
    if (polygon.size >= 3) {
        drawPath(
            path = polygon.toClosedPath(),
            color = emphasisColor.copy(alpha = 0.18f)
        )
        drawPath(
            path = polygon.toClosedPath(),
            color = outlineColor.copy(alpha = 0.92f),
            style = Stroke(width = 3.dp.toPx())
        )
        return
    }

    val rect = hold.toScreenRect(
        offX = contentRect.left,
        offY = contentRect.top,
        scaledW = contentRect.width,
        scaledH = contentRect.height
    )
    drawRect(
        color = emphasisColor.copy(alpha = 0.18f),
        topLeft = Offset(rect.l, rect.t),
        size = androidx.compose.ui.geometry.Size(
            width = rect.r - rect.l,
            height = rect.b - rect.t
        )
    )
    drawRect(
        color = outlineColor.copy(alpha = 0.92f),
        topLeft = Offset(rect.l, rect.t),
        size = androidx.compose.ui.geometry.Size(
            width = rect.r - rect.l,
            height = rect.b - rect.t
        ),
        style = Stroke(width = 3.dp.toPx())
    )
}

private fun Hold.toScreenPolygon(
    contentRect: com.ddgo.app.feature.climbing.upload.VideoContentRect
): List<Offset> {
    return polygon.map { point ->
        Offset(
            x = contentRect.left + (point.x * contentRect.width),
            y = contentRect.top + (point.y * contentRect.height)
        )
    }
}

private fun List<Offset>.toClosedPath(): Path {
    return Path().apply {
        if (isEmpty()) return@apply
        moveTo(first().x, first().y)
        drop(1).forEach { point ->
            lineTo(point.x, point.y)
        }
        close()
    }
}

private fun shortLimbLabel(limb: String): String {
    return when (limb) {
        "left_hand" -> "LH"
        "right_hand" -> "RH"
        "left_foot" -> "LF"
        "right_foot" -> "RF"
        else -> limb
    }
}
