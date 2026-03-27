package com.ddgo.app.feature.debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.max
import kotlin.math.roundToInt

private val DebugBackground = Color(0xFF0B0D12)
private val DebugSurface = Color(0xFF171B22)
private val DebugSurfaceBorder = Color(0xFF2A3040)
private val DebugLine = Color(0xFF6EE7F9)
private val DebugPoint = Color.White
private val DebugAccent = Color(0xFF6FA8FF)
private val DebugComColor = Color(0xFF55E6D5)
private val DebugSupportStableColor = Color(0xFF3DDC97)
private val DebugSupportUnstableColor = Color(0xFFFF6B6B)
private val DebugBodyLoadColor = Color(0xFFFFC857)
private val DebugForceColor = Color(0xFFCF8BFF)
private val DebugStatusColor = Color(0xFF9FB5FF)
private val HiddenFaceLandmarks = (1..10).toSet()
private val EmptyVideoContentRect = com.ddgo.app.feature.climbing.upload.VideoContentRect(
    left = 0f,
    top = 0f,
    width = 0f,
    height = 0f
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
                            snapshot = snapshot
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
    snapshot: UploadPhysicsDebugSnapshot?
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

    val supportColor = when (snapshot?.support?.insideSupport) {
        true -> DebugSupportStableColor
        false -> DebugSupportUnstableColor
        null -> DebugSupportUnstableColor.copy(alpha = 0.9f)
    }
    val rightHudX = max(contentRect.left + 12f, contentRect.left + contentRect.width - 150f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (contentRect.width <= 0f || contentRect.height <= 0f || snapshot == null) return@Canvas

        val activeHoldIds = snapshot.support.activeHoldIds.toSet()
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

        if (snapshot.com.hasValues) {
            anchors.torsoAnchor?.let { torsoAnchor ->
                drawCircle(
                    color = DebugComColor,
                    radius = 7.dp.toPx(),
                    center = torsoAnchor
                )
                drawCircle(
                    color = DebugComColor.copy(alpha = 0.22f),
                    radius = 17.dp.toPx(),
                    center = torsoAnchor
                )
            }
        }
    }

    snapshot?.let {
        PhysicsOverlayChip(
            text = buildString {
                append("frame ")
                append(it.meta.frameIndex)
                append("  ")
                append(it.meta.timestampMs)
                append("ms")
                append('\n')
                append(it.meta.phase ?: "phase -")
                append(" / ")
                append(it.meta.analysisConfidence ?: "confidence -")
            },
            color = DebugStatusColor,
            anchor = null,
            fallback = Offset(contentRect.left + 12f, contentRect.top + 12f)
        )

        PhysicsOverlayChip(
            text = buildString {
                append("CoM")
                append('\n')
                append("x ")
                append(formatDebugNumber(it.com.x))
                append("  y ")
                append(formatDebugNumber(it.com.y))
                append("  z ")
                append(formatDebugNumber(it.com.z))
            },
            color = DebugComColor,
            anchor = anchors.torsoAnchor,
            fallback = Offset(contentRect.left + 12f, contentRect.top + 74f)
        )

        PhysicsOverlayChip(
            text = buildString {
                append("support")
                append('\n')
                append(it.support.supportType ?: "-")
                append(" / ")
                append(it.meta.supportMode ?: "-")
                append('\n')
                append("margin ")
                append(formatDebugNumber(it.support.stabilityMarginM))
                append("  dist ")
                append(formatDebugNumber(it.support.distanceToSupportM))
            },
            color = supportColor,
            anchor = anchors.supportBadgeAnchor,
            fallback = Offset(contentRect.left + 12f, contentRect.top + 142f)
        )

        BODY_LOAD_KEYS.forEachIndexed { index, key ->
            val rawValue = it.bodyLoad.loads[key]
            if (rawValue == null) return@forEachIndexed

            PhysicsOverlayChip(
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
        }

        PhysicsOverlayChip(
            text = buildString {
                append("contact")
                append('\n')
                append(it.contactForce.status ?: "-")
                append('\n')
                append("res ")
                append(formatDebugNumber(it.contactForce.relativeResidual))
            },
            color = DebugForceColor,
            anchor = null,
            fallback = Offset(rightHudX, contentRect.top + 292f)
        )

        CONTACT_LIMB_KEYS.forEachIndexed { index, limb ->
            val payload = it.contactForce.limbForces[limb] ?: return@forEachIndexed
            PhysicsOverlayChip(
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
        }
    }
}

@Composable
private fun BoxScope.PhysicsOverlayChip(
    text: String,
    color: Color,
    anchor: Offset?,
    fallback: Offset
) {
    val resolved = anchor?.let {
        Offset(
            x = it.x + 10f,
            y = it.y - 26f
        )
    } ?: fallback

    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset {
                IntOffset(
                    x = resolved.x.roundToInt(),
                    y = resolved.y.roundToInt()
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
