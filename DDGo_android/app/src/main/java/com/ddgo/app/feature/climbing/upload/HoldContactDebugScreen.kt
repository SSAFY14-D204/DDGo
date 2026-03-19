package com.ddgo.app.feature.climbing.upload

import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.HoldContact
import com.ddgo.app.domain.usecase.HoldContactConfig
import com.ddgo.app.domain.usecase.HoldContactZone
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.PolygonHoldContact
import com.ddgo.app.domain.usecase.buildHoldContactZones
import com.ddgo.app.domain.usecase.detectHoldContacts
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private val DEBUG_BG = Color(0xFF101010)
private val DEBUG_CARD = Color(0xFF1C1C1C)
private val DEBUG_TEXT = Color(0xFFF4F4F4)
private val DEBUG_SUB_TEXT = Color(0xFFBDBDBD)
private val ZONE_FILL = Color(0x66FF0000)
private val ZONE_STROKE = Color(0xCCFF4D4D)
private val CONTACT_FILL = Color(0x99FFD54F)
private val CONTACT_STROKE = Color(0xFFFFEB3B)
private val CURRENT_MARKER = Color(0xFF4FC3F7)
private const val DEFAULT_DEBUG_VIDEO_ASPECT_RATIO = 9f / 16f

private data class DebugFrameContact(
    val pose: Pose,
    val contacts: List<HoldContact>
)

private enum class HoldDebugMode(val label: String) {
    BBOX(label = "박스"),
    POLYGON(label = "폴리곤")
}

private data class DebugTimelineEntry(
    val frameTimeMs: Long,
    val summary: String
)

private data class DebugVideoContentRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

@Composable
fun HoldContactDebugScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val currentAttemptIndex = viewModel.currentAttemptIndex
    val playbackAttemptUris = viewModel.playbackAttemptUris
    val currentVideoUri = playbackAttemptUris.getOrNull(currentAttemptIndex)
    val numberedHolds = viewModel.numberedHolds
    val analyzedPoses = viewModel.currentAttemptAnalyzedPoses
    val polygonDebugResult = viewModel.currentAttemptPolygonHoldContactDebugResult
    var debugMode by remember { mutableStateOf(HoldDebugMode.POLYGON) }

    val holdContactConfig = remember { HoldContactConfig() }
    val contactZones = remember(numberedHolds, holdContactConfig) {
        buildHoldContactZones(
            holds = numberedHolds,
            config = holdContactConfig
        )
    }
    val analyzedFrameContacts = remember(analyzedPoses, numberedHolds, holdContactConfig) {
        analyzedPoses.map { pose ->
            DebugFrameContact(
                pose = pose,
                contacts = detectHoldContacts(
                    landmarks = pose.landmarks,
                    holds = numberedHolds,
                    config = holdContactConfig,
                    enableLogging = false
                )
            )
        }
    }
    val contactFrames = remember(analyzedFrameContacts) {
        analyzedFrameContacts.filter { it.contacts.isNotEmpty() }
    }
    val polygonActiveFrames = remember(polygonDebugResult) {
        polygonDebugResult?.activeContactFrames.orEmpty()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    var playerVideoSize by remember(currentVideoUri) { mutableStateOf(VideoSize.UNKNOWN) }
    var videoContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val videoAspectRatio = remember(playerVideoSize) {
        debugResolveDisplayedVideoAspectRatio(playerVideoSize)
    }
    val videoContentRect = remember(videoContainerSize, playerVideoSize) {
        debugCalculateVideoContentRect(
            containerSize = videoContainerSize,
            videoSize = playerVideoSize
        )
    }

    LaunchedEffect(currentVideoUri) {
        if (currentVideoUri == null) return@LaunchedEffect

        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(currentVideoUri)))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = false
        playerVideoSize = exoPlayer.videoSize
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoSize = videoSize
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }

    LaunchedEffect(exoPlayer, currentVideoUri, contactFrames, polygonActiveFrames) {
        while (isActive) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = when {
                exoPlayer.duration > 0L -> exoPlayer.duration
                polygonActiveFrames.isNotEmpty() -> polygonActiveFrames.last().frameTimeMs.coerceAtLeast(1L)
                contactFrames.isNotEmpty() -> contactFrames.last().pose.frameTimeMs.coerceAtLeast(1L)
                else -> 1L
            }
            delay(120L)
        }
    }

    var selectedBboxFrameTimeMs by remember(currentAttemptIndex, currentVideoUri, contactFrames) {
        mutableStateOf<Long?>(contactFrames.firstOrNull()?.pose?.frameTimeMs)
    }
    var selectedPolygonFrameTimeMs by remember(currentAttemptIndex, currentVideoUri, polygonActiveFrames) {
        mutableStateOf<Long?>(polygonActiveFrames.firstOrNull()?.frameTimeMs)
    }

    val currentFrame = remember(analyzedFrameContacts, currentPositionMs) {
        analyzedFrameContacts.minByOrNull { frame ->
            abs(frame.pose.frameTimeMs - currentPositionMs)
        }
    }
    val selectedBboxFrame = remember(contactFrames, selectedBboxFrameTimeMs) {
        contactFrames.firstOrNull { frame ->
            frame.pose.frameTimeMs == selectedBboxFrameTimeMs
        }
    }
    val polygonCurrentFrame = remember(polygonDebugResult, currentPositionMs) {
        polygonDebugResult?.frames?.minByOrNull { frame ->
            abs(frame.frameTimeMs - currentPositionMs)
        }
    }
    val selectedPolygonFrame = remember(polygonActiveFrames, selectedPolygonFrameTimeMs) {
        polygonActiveFrames.firstOrNull { frame ->
            frame.frameTimeMs == selectedPolygonFrameTimeMs
        }
    }
    val currentContacts = if (debugMode == HoldDebugMode.BBOX) {
        currentFrame?.contacts.orEmpty()
    } else {
        polygonCurrentFrame?.activeContacts.orEmpty()
    }
    val selectedFrameTimeMs = if (debugMode == HoldDebugMode.BBOX) {
        selectedBboxFrame?.pose?.frameTimeMs
    } else {
        selectedPolygonFrame?.frameTimeMs
    }
    val currentContactFrameCountLabel = if (debugMode == HoldDebugMode.BBOX) {
        "${contactFrames.size} / ${analyzedFrameContacts.size}"
    } else {
        "${polygonActiveFrames.size} / ${polygonDebugResult?.frames?.size ?: 0}"
    }
    val timelineEntries = if (debugMode == HoldDebugMode.BBOX) {
        contactFrames.map { frame ->
            DebugTimelineEntry(
                frameTimeMs = frame.pose.frameTimeMs,
                summary = formatBboxContactSummary(frame.contacts)
            )
        }
    } else {
        polygonActiveFrames.map { frame ->
            DebugTimelineEntry(
                frameTimeMs = frame.frameTimeMs,
                summary = formatPolygonContactSummary(frame.activeContacts)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DEBUG_BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = DEBUG_TEXT
                )
            ) {
                Text("뒤로")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "홀드탐지디버깅",
                    color = DEBUG_TEXT,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "빨강: 인식 범위 또는 폴리곤 / 노랑: 현재 재생 시점 접촉 홀드",
                    color = DEBUG_SUB_TEXT,
                    fontSize = 12.sp
                )
            }
        }

        if (playbackAttemptUris.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                playbackAttemptUris.indices.forEach { index ->
                    val isSelected = index == currentAttemptIndex
                    OutlinedButton(
                        onClick = { viewModel.selectAttempt(index) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Color(0xFF263238) else DEBUG_CARD,
                            contentColor = DEBUG_TEXT
                        )
                    ) {
                        Text("${index + 1}차 시도")
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HoldDebugMode.entries.forEach { mode ->
                val isSelected = debugMode == mode
                OutlinedButton(
                    onClick = { debugMode = mode },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) Color(0xFF37474F) else DEBUG_CARD,
                        contentColor = DEBUG_TEXT
                    )
                ) {
                    Text("${mode.label} 기준")
                }
            }
        }

        if (currentVideoUri == null || numberedHolds.isEmpty() || analyzedPoses.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DEBUG_CARD
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "디버깅 데이터가 아직 준비되지 않았습니다.",
                        color = DEBUG_TEXT,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "분석을 한 번 끝까지 돌린 뒤 다시 들어오면, 시도 결과 영상 위에 홀드 접촉 범위와 접촉 프레임 타임라인이 표시됩니다.",
                        color = DEBUG_SUB_TEXT,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "현재 상태: video=${currentVideoUri != null}, holds=${numberedHolds.size}, poses=${analyzedPoses.size}",
                        color = DEBUG_SUB_TEXT,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "polygonFrames=${polygonDebugResult?.frames?.size ?: 0}",
                        color = DEBUG_SUB_TEXT,
                        fontSize = 12.sp
                    )
                }
            }
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DEBUG_CARD
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(videoAspectRatio)
                        .background(Color.Black)
                        .onSizeChanged { videoContainerSize = it }
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                player = exoPlayer
                            }
                        },
                        update = { playerView ->
                            playerView.player = exoPlayer
                        }
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        when (debugMode) {
                            HoldDebugMode.BBOX -> drawHoldDebugOverlay(
                                contentRect = videoContentRect,
                                zones = contactZones,
                                contacts = currentContacts.filterIsInstance<HoldContact>()
                            )

                            HoldDebugMode.POLYGON -> drawPolygonHoldDebugOverlay(
                                contentRect = videoContentRect,
                                holds = numberedHolds,
                                contacts = currentContacts.filterIsInstance<PolygonHoldContact>()
                            )
                        }
                    }
                }

                DebugInfoRow(
                    label = "현재 재생 위치",
                    value = currentPositionMs.toDebugTimeString()
                )
                DebugInfoRow(
                    label = "선택 프레임",
                    value = selectedFrameTimeMs?.toDebugTimeString() ?: "없음"
                )
                DebugInfoRow(
                    label = "현재 접촉 홀드",
                    value = when (debugMode) {
                        HoldDebugMode.BBOX -> formatBboxContactSummary(
                            currentContacts.filterIsInstance<HoldContact>()
                        )
                        HoldDebugMode.POLYGON -> formatPolygonContactSummary(
                            currentContacts.filterIsInstance<PolygonHoldContact>()
                        )
                    }.ifBlank { "없음" }
                )
                DebugInfoRow(
                    label = "접촉 프레임 수",
                    value = currentContactFrameCountLabel
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DEBUG_CARD
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${debugMode.label} 접촉 프레임 타임라인",
                    color = DEBUG_TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                ContactTimelineBar(
                    entries = timelineEntries,
                    durationMs = durationMs,
                    currentPositionMs = currentPositionMs,
                    selectedFrameTimeMs = selectedFrameTimeMs,
                    onSelectFrame = { entry ->
                        if (debugMode == HoldDebugMode.BBOX) {
                            selectedBboxFrameTimeMs = entry.frameTimeMs
                        } else {
                            selectedPolygonFrameTimeMs = entry.frameTimeMs
                        }
                        exoPlayer.seekTo((entry.frameTimeMs - 300L).coerceAtLeast(0L))
                        exoPlayer.play()
                    }
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timelineEntries.forEach { entry ->
                        val isSelected = entry.frameTimeMs == selectedFrameTimeMs
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (debugMode == HoldDebugMode.BBOX) {
                                        selectedBboxFrameTimeMs = entry.frameTimeMs
                                    } else {
                                        selectedPolygonFrameTimeMs = entry.frameTimeMs
                                    }
                                    exoPlayer.seekTo((entry.frameTimeMs - 300L).coerceAtLeast(0L))
                                    exoPlayer.play()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF263238) else Color(0xFF242424),
                            tonalElevation = if (isSelected) 4.dp else 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = entry.frameTimeMs.toDebugTimeString(),
                                    color = DEBUG_TEXT,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = entry.summary,
                                    color = DEBUG_SUB_TEXT,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(92.dp),
            color = DEBUG_SUB_TEXT,
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = DEBUG_TEXT,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ContactTimelineBar(
    entries: List<DebugTimelineEntry>,
    durationMs: Long,
    currentPositionMs: Long,
    selectedFrameTimeMs: Long?,
    onSelectFrame: (DebugTimelineEntry) -> Unit
) {
    val timelineWidth = remember { mutableStateOf(0f) }
    val safeDurationMs = maxOf(
        durationMs,
        entries.lastOrNull()?.frameTimeMs ?: 1L
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, Color(0xFF2E2E2E), RoundedCornerShape(10.dp))
            .background(Color(0xFF161616), RoundedCornerShape(10.dp))
            .onSizeChanged { timelineWidth.value = it.width.toFloat() }
            .pointerInput(entries, safeDurationMs, timelineWidth.value) {
                detectTapGestures { tapOffset ->
                    if (entries.isEmpty() || timelineWidth.value <= 0f) return@detectTapGestures

                    val width = (timelineWidth.value - 24f).coerceAtLeast(1f)
                    val startX = 6f
                    val nearestFrame = entries.minByOrNull { entry ->
                        val fraction = entry.frameTimeMs.toFloat() / safeDurationMs.toFloat()
                        val markerX = startX + (width * fraction.coerceIn(0f, 1f))
                        abs(markerX - tapOffset.x)
                    } ?: return@detectTapGestures

                    onSelectFrame(nearestFrame)
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val startX = 6f
            val endX = size.width - 6f
            val width = (endX - startX).coerceAtLeast(1f)

            drawLine(
                color = Color(0xFF4A4A4A),
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            val currentFraction = currentPositionMs.toFloat() / safeDurationMs.toFloat()
            val currentX = startX + (width * currentFraction.coerceIn(0f, 1f))
            drawLine(
                color = CURRENT_MARKER,
                start = Offset(currentX, 8f),
                end = Offset(currentX, size.height - 8f),
                strokeWidth = 3f
            )

            entries.forEach { entry ->
                val fraction = entry.frameTimeMs.toFloat() / safeDurationMs.toFloat()
                val markerX = startX + (width * fraction.coerceIn(0f, 1f))
                val isSelected = entry.frameTimeMs == selectedFrameTimeMs

                drawCircle(
                    color = if (isSelected) CONTACT_STROKE else ZONE_STROKE,
                    radius = if (isSelected) 11f else 8f,
                    center = Offset(markerX, centerY),
                    style = Fill
                )

                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = if (isSelected) 13f else 10f,
                    center = Offset(markerX, centerY),
                    style = Stroke(width = 2f)
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "00:00.000",
            color = DEBUG_SUB_TEXT,
            fontSize = 11.sp
        )
        Text(
            text = safeDurationMs.toDebugTimeString(),
            color = DEBUG_SUB_TEXT,
            fontSize = 11.sp
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHoldDebugOverlay(
    contentRect: DebugVideoContentRect,
    zones: List<HoldContactZone>,
    contacts: List<HoldContact>
) {
    val drawArea = if (contentRect.width > 0f && contentRect.height > 0f) {
        contentRect
    } else {
        DebugVideoContentRect(
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height
        )
    }

    zones.forEach { zone ->
        drawBoundingBox(
            bbox = zone.expandedBoundingBox,
            contentRect = drawArea,
            fillColor = ZONE_FILL,
            strokeColor = ZONE_STROKE,
            label = zone.hold.holdNo.toString()
        )
    }

    contacts.forEach { contact ->
        drawBoundingBox(
            bbox = contact.hold.hold.boundingBox,
            contentRect = drawArea,
            fillColor = CONTACT_FILL,
            strokeColor = CONTACT_STROKE,
            label = contact.holdNo.toString()
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygonHoldDebugOverlay(
    contentRect: DebugVideoContentRect,
    holds: List<HoldNumbered>,
    contacts: List<PolygonHoldContact>
) {
    val drawArea = if (contentRect.width > 0f && contentRect.height > 0f) {
        contentRect
    } else {
        DebugVideoContentRect(
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height
        )
    }

    holds.forEach { hold ->
        drawPolygonShape(
            polygon = hold.hold.toOverlayPolygon(),
            contentRect = drawArea,
            fillColor = ZONE_FILL,
            strokeColor = ZONE_STROKE,
            label = hold.holdNo.toString()
        )
    }

    contacts.forEach { contact ->
        drawPolygonShape(
            polygon = contact.hold.hold.toOverlayPolygon(),
            contentRect = drawArea,
            fillColor = CONTACT_FILL,
            strokeColor = CONTACT_STROKE,
            label = contact.holdNo.toString()
        )
        contact.contactPointNormalized?.let { point ->
            drawCircle(
                color = CONTACT_STROKE,
                radius = 9f,
                center = Offset(
                    x = drawArea.left + point.x.coerceIn(0f, 1f) * drawArea.width,
                    y = drawArea.top + point.y.coerceIn(0f, 1f) * drawArea.height
                )
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoundingBox(
    bbox: Hold.BoundingBox,
    contentRect: DebugVideoContentRect,
    fillColor: Color,
    strokeColor: Color,
    label: String
) {
    val left = contentRect.left + (bbox.left.coerceIn(0f, 1f) * contentRect.width)
    val top = contentRect.top + (bbox.top.coerceIn(0f, 1f) * contentRect.height)
    val right = contentRect.left + (bbox.right.coerceIn(0f, 1f) * contentRect.width)
    val bottom = contentRect.top + (bbox.bottom.coerceIn(0f, 1f) * contentRect.height)
    val width = (right - left).coerceAtLeast(1f)
    val height = (bottom - top).coerceAtLeast(1f)

    drawRect(
        color = fillColor,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(width, height)
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(left, top),
        size = androidx.compose.ui.geometry.Size(width, height),
        style = Stroke(width = 3f)
    )

    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 30f
        isAntiAlias = true
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
    }

    drawContext.canvas.nativeCanvas.drawText(
        label,
        left + 8f,
        top + 32f,
        textPaint
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygonShape(
    polygon: List<Hold.Point>,
    contentRect: DebugVideoContentRect,
    fillColor: Color,
    strokeColor: Color,
    label: String
) {
    if (polygon.isEmpty()) return

    val path = Path()
    val first = polygon.first()
    val firstOffset = Offset(
        x = contentRect.left + first.x.coerceIn(0f, 1f) * contentRect.width,
        y = contentRect.top + first.y.coerceIn(0f, 1f) * contentRect.height
    )
    path.moveTo(firstOffset.x, firstOffset.y)

    polygon.drop(1).forEach { point ->
        path.lineTo(
            x = contentRect.left + point.x.coerceIn(0f, 1f) * contentRect.width,
            y = contentRect.top + point.y.coerceIn(0f, 1f) * contentRect.height
        )
    }
    path.close()

    drawPath(path = path, color = fillColor, style = Fill)
    drawPath(path = path, color = strokeColor, style = Stroke(width = 3f))

    val centroid = polygon.let { points ->
        Hold.Point(
            x = points.map(Hold.Point::x).average().toFloat(),
            y = points.map(Hold.Point::y).average().toFloat()
        )
    }
    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 30f
        isAntiAlias = true
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
    }

    drawContext.canvas.nativeCanvas.drawText(
        label,
        contentRect.left + centroid.x.coerceIn(0f, 1f) * contentRect.width,
        contentRect.top + centroid.y.coerceIn(0f, 1f) * contentRect.height,
        textPaint
    )
}

private fun Hold.toOverlayPolygon(): List<Hold.Point> {
    if (polygon.size >= 3) return polygon
    return listOf(
        Hold.Point(boundingBox.left, boundingBox.top),
        Hold.Point(boundingBox.right, boundingBox.top),
        Hold.Point(boundingBox.right, boundingBox.bottom),
        Hold.Point(boundingBox.left, boundingBox.bottom)
    )
}

private fun formatBboxContactSummary(contacts: List<HoldContact>): String =
    contacts.joinToString(separator = " / ") { contact ->
        val handLabel = when {
            contact.handSides.size > 1 -> "양손"
            contact.handSides.firstOrNull()?.name == "LEFT" -> "왼손"
            contact.handSides.firstOrNull()?.name == "RIGHT" -> "오른손"
            else -> "손"
        }
        "#${contact.holdNo} $handLabel"
    }.ifBlank { "없음" }

private fun formatPolygonContactSummary(contacts: List<PolygonHoldContact>): String =
    contacts.joinToString(separator = " / ") { contact ->
        "#${contact.holdNo} ${contact.limb.displayName} ${contact.state}"
    }.ifBlank { "없음" }

private fun Long.toDebugTimeString(): String {
    val minutes = this / 60_000L
    val seconds = (this / 1_000L) % 60L
    val millis = this % 1_000L
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}

private fun debugResolveDisplayedVideoAspectRatio(videoSize: VideoSize): Float {
    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return DEFAULT_DEBUG_VIDEO_ASPECT_RATIO
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight

    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return DEFAULT_DEBUG_VIDEO_ASPECT_RATIO
    }

    return displayedWidth / displayedHeight
}

private fun debugCalculateVideoContentRect(
    containerSize: IntSize,
    videoSize: VideoSize
): DebugVideoContentRect {
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return DebugVideoContentRect(0f, 0f, 0f, 0f)
    }

    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return DebugVideoContentRect(0f, 0f, containerWidth, containerHeight)
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return DebugVideoContentRect(0f, 0f, containerWidth, containerHeight)
    }

    val videoAspectRatio = displayedWidth / displayedHeight
    val containerAspectRatio = containerWidth / containerHeight

    return if (containerAspectRatio > videoAspectRatio) {
        val fittedHeight = containerHeight
        val fittedWidth = fittedHeight * videoAspectRatio
        DebugVideoContentRect(
            left = (containerWidth - fittedWidth) / 2f,
            top = 0f,
            width = fittedWidth,
            height = fittedHeight
        )
    } else {
        val fittedWidth = containerWidth
        val fittedHeight = fittedWidth / videoAspectRatio
        DebugVideoContentRect(
            left = 0f,
            top = (containerHeight - fittedHeight) / 2f,
            width = fittedWidth,
            height = fittedHeight
        )
    }
}
