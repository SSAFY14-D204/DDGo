package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.AttemptPoseOverlayCache
import com.ddgo.app.feature.climbing.upload.COLOR_END
import com.ddgo.app.feature.climbing.upload.COLOR_START
import com.ddgo.app.feature.climbing.upload.CroppedVideoViewport
import com.ddgo.app.feature.climbing.upload.HoldOverviewPreview
import com.ddgo.app.feature.climbing.upload.PoseOverlay
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.PoseScrubberMarker
import com.ddgo.app.feature.climbing.upload.PoseVideoScrubber
import com.ddgo.app.feature.climbing.upload.ScreenRect
import com.ddgo.app.feature.climbing.upload.VideoContentRect
import com.ddgo.app.feature.climbing.upload.calculateVerticalVideoViewportCropSpecFromRawHolds
import com.ddgo.app.feature.climbing.upload.calculateVideoContentRect
import com.ddgo.app.feature.climbing.upload.findNearestOverlayFrameForPlayback
import com.ddgo.app.feature.climbing.upload.holdLabelToComposeColor
import com.ddgo.app.feature.climbing.upload.holdColorToUiColor
import com.ddgo.app.feature.climbing.upload.resolveActiveAnalysisCardIndex
import com.ddgo.app.feature.climbing.upload.resolveDisplayedVideoAspectRatio
import com.ddgo.app.feature.climbing.upload.resolveInitialAttemptPlaybackStartTimeMs
import com.ddgo.app.feature.climbing.upload.toScreenRect
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

internal data class AttemptPreviewHeroState(
    val gymName: String,
    val displayDate: String,
    val difficultyLabel: String,
    val holdColorLabel: String,
    val selectedAttempt: Int,
    val isSuccess: Boolean,
    val analysisModeLabel: String? = null,
    val fallbackLabel: String? = null,
    val previewBitmap: Bitmap?,
    val previewHolds: List<Hold>,
    val numberedHolds: List<HoldNumbered> = emptyList(),
    val selectedAttemptVideoUri: String? = null,
    val analysisPoints: List<AnalysisPoint> = emptyList(),
    val attemptPoseSequence: List<Pose> = emptyList(),
    val overlayCache: AttemptPoseOverlayCache? = null,
    val rawHolds: List<Hold> = emptyList(),
    val wallArrivalTimeMs: Long? = null,
    val personObservationStartTimeMs: Long? = null,
    val usesPoseTimeline: Boolean = false,
    val seekRequestId: Long = 0L,
    val seekRequestTimeMs: Long? = null
)

private val AttemptPreviewOverlayLineColor = Color(0xFF00E5FF).copy(alpha = 0.88f)
private val AttemptPreviewOverlayPointColor = Color.White
private val AttemptPreviewScrubberAccent = Color(0xFF82B1FF)
private val AttemptPreviewHiddenFaceLandmarks = (1..10).toSet()
private val AttemptPreviewTopSafeInset = 24.dp
private val AttemptPreviewBottomSafeInset = 64.dp
private val AttemptPreviewControlHeight = 132.dp

@Composable
internal fun AttemptPreviewHero(
    state: AttemptPreviewHeroState,
    onShareClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var showHoldOverviewDialog by remember(state.previewBitmap, state.previewHolds) { mutableStateOf(false) }
    val difficultyChipTone = remember(state.difficultyLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.difficultyLabel))
    }
    val holdChipTone = remember(state.holdColorLabel) {
        buildHeaderChipTone(holdColorToUiColor(state.holdColorLabel))
    }
    val statusChipTone = remember(state.isSuccess) {
        buildHeaderChipTone(
            if (state.isSuccess) Color(0xFF39C66D) else Color(0xFFFF5E63)
        )
    }
    val poseTimestamps = remember(state.attemptPoseSequence) {
        state.attemptPoseSequence
            .map(Pose::frameTimeMs)
            .distinct()
            .sorted()
    }
    val exoPlayer = remember(context, state.selectedAttemptVideoUri) {
        state.selectedAttemptVideoUri?.let { videoUri ->
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
                prepare()
            }
        }
    }
    var playerVideoSize by remember(state.selectedAttemptVideoUri) { mutableStateOf(VideoSize.UNKNOWN) }
    var fullVideoLayerSize by remember(state.selectedAttemptVideoUri) { mutableStateOf(IntSize.Zero) }
    var currentPositionMs by remember(state.selectedAttemptVideoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(state.selectedAttemptVideoUri) { mutableLongStateOf(0L) }
    var isScrubbing by remember(state.selectedAttemptVideoUri) { mutableStateOf(false) }
    var scrubPositionMs by remember(state.selectedAttemptVideoUri) { mutableLongStateOf(0L) }
    var hasInitialAutoSeekApplied by remember(state.selectedAttemptVideoUri) { mutableStateOf(false) }
    var wasPlayingBeforeScrub by remember(state.selectedAttemptVideoUri) { mutableStateOf(false) }

    val displayedPositionMs = if (isScrubbing) scrubPositionMs else currentPositionMs
    val canScrub = durationMs > 0L && poseTimestamps.isNotEmpty()
    val videoAspectRatio = remember(playerVideoSize) {
        resolveDisplayedVideoAspectRatio(playerVideoSize)
    }
    val videoContentRect = remember(fullVideoLayerSize, playerVideoSize) {
        calculateVideoContentRect(
            containerSize = fullVideoLayerSize,
            videoSize = playerVideoSize
        )
    }
    val viewportCropSpec = remember(
        state.rawHolds,
        fullVideoLayerSize,
        videoAspectRatio,
        density
    ) {
        if (fullVideoLayerSize.width <= 0 || fullVideoLayerSize.height <= 0) {
            com.ddgo.app.feature.climbing.upload.uncroppedVideoViewportCropSpec(videoAspectRatio)
        } else {
            calculateVerticalVideoViewportCropSpecFromRawHolds(
                holds = state.rawHolds,
                videoAspectRatio = videoAspectRatio,
                fullVideoHeightPx = fullVideoLayerSize.height.toFloat(),
                topSafeInsetPx = with(density) { AttemptPreviewTopSafeInset.toPx() },
                bottomSafeInsetPx = with(density) { AttemptPreviewBottomSafeInset.toPx() }
            )
        }
    }
    val topCropOffsetPx = remember(viewportCropSpec, fullVideoLayerSize) {
        if (viewportCropSpec.isActive) {
            fullVideoLayerSize.height * viewportCropSpec.topCropFraction
        } else {
            0f
        }
    }
    val currentOverlayFrame = remember(state.overlayCache, displayedPositionMs) {
        state.overlayCache?.let { overlayCache ->
            findNearestOverlayFrameForPlayback(
                cache = overlayCache,
                positionMs = displayedPositionMs
            )
        }
    }
    val activeTimelineIndex by remember(state.analysisPoints, displayedPositionMs) {
        derivedStateOf {
            resolveActiveAnalysisCardIndex(
                points = state.analysisPoints,
                displayedPositionMs = displayedPositionMs,
                tappedCardOverrideIdx = -1
            )
        }
    }
    val scrubberMarkers = remember(state.analysisPoints, activeTimelineIndex) {
        state.analysisPoints.mapIndexed { index, point ->
            PoseScrubberMarker(
                index = point.index,
                timeMs = point.timeMs,
                isSelected = index == activeTimelineIndex
            )
        }
    }

    LaunchedEffect(state.selectedAttemptVideoUri) {
        currentPositionMs = 0L
        durationMs = 0L
        scrubPositionMs = 0L
        isScrubbing = false
        hasInitialAutoSeekApplied = false

        state.selectedAttemptVideoUri?.let { videoUri ->
            exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            playerVideoSize = exoPlayer?.videoSize ?: VideoSize.UNKNOWN
        }
    }

    LaunchedEffect(exoPlayer, state.seekRequestId, state.seekRequestTimeMs) {
        val safePlayer = exoPlayer ?: return@LaunchedEffect
        val seekTimeMs = state.seekRequestTimeMs ?: return@LaunchedEffect
        safePlayer.seekTo(seekTimeMs.coerceAtLeast(0L))
        currentPositionMs = seekTimeMs.coerceAtLeast(0L)
        scrubPositionMs = seekTimeMs.coerceAtLeast(0L)
        safePlayer.play()
    }

    LaunchedEffect(
        state.selectedAttemptVideoUri,
        state.wallArrivalTimeMs,
        state.personObservationStartTimeMs,
        poseTimestamps,
        hasInitialAutoSeekApplied
    ) {
        val safePlayer = exoPlayer ?: return@LaunchedEffect
        if (hasInitialAutoSeekApplied) return@LaunchedEffect

        val initialStartMs = resolveInitialAttemptPlaybackStartTimeMs(
            wallArrivalTimeMs = state.wallArrivalTimeMs,
            fallbackPersonObservationStartTimeMs = state.personObservationStartTimeMs,
            poseTimestamps = poseTimestamps
        ) ?: return@LaunchedEffect

        safePlayer.seekTo(initialStartMs)
        currentPositionMs = initialStartMs
        scrubPositionMs = initialStartMs
        hasInitialAutoSeekApplied = true
    }

    LaunchedEffect(exoPlayer, state.selectedAttemptVideoUri, isScrubbing) {
        val safePlayer = exoPlayer ?: return@LaunchedEffect
        while (isActive) {
            durationMs = safePlayer.duration.coerceAtLeast(0L)
            if (!isScrubbing) {
                currentPositionMs = safePlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(33L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoSize = videoSize
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                durationMs = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L
                if (!isScrubbing) {
                    currentPositionMs = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                }
            }
        }

        exoPlayer?.addListener(listener)
        onDispose {
            exoPlayer?.removeListener(listener)
            exoPlayer?.release()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = state.gymName.ifBlank { "클라이밍장 미지정" },
                    color = AnalysisText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = state.displayDate,
                    color = AnalysisMuted,
                    fontSize = 13.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderChip(
                        text = "${state.selectedAttempt}차 ${if (state.isSuccess) "성공" else "실패"}",
                        background = statusChipTone.background,
                        contentColor = statusChipTone.content,
                        borderColor = statusChipTone.border
                    )
                    if (state.difficultyLabel.isNotBlank()) {
                        HeaderChip(
                            text = "난이도 ${state.difficultyLabel}",
                            background = difficultyChipTone.background,
                            contentColor = difficultyChipTone.content,
                            borderColor = difficultyChipTone.border
                        )
                    }
                    if (state.holdColorLabel.isNotBlank()) {
                        HeaderChip(
                            text = "홀드 ${state.holdColorLabel}",
                            background = holdChipTone.background,
                            contentColor = holdChipTone.content,
                            borderColor = holdChipTone.border
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(width = 116.dp, height = 96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        enabled = state.previewBitmap != null || state.previewHolds.isNotEmpty()
                    ) { showHoldOverviewDialog = true }
            ) {
                HoldOverviewPreview(
                    bitmap = state.previewBitmap,
                    holds = state.previewHolds,
                    modifier = Modifier.fillMaxSize(),
                    showZoomBadge = true
                )
            }
        }

        if (showHoldOverviewDialog) {
            Dialog(onDismissRequest = { showHoldOverviewDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF17181C))
                        .padding(16.dp)
                ) {
                    HoldOverviewPreview(
                        bitmap = state.previewBitmap,
                        holds = state.previewHolds,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF101114))
        ) {
            if (exoPlayer != null) {
                CroppedVideoViewport(
                    cropSpec = viewportCropSpec,
                    fullVideoAspectRatio = videoAspectRatio,
                    topCropPx = topCropOffsetPx,
                    modifier = Modifier.fillMaxWidth(),
                    onFullVideoSizeChanged = { fullVideoLayerSize = it },
                    transformedLayer = {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { playerView ->
                                playerView.player = exoPlayer
                            }
                        )

                        currentOverlayFrame?.let { overlayFrame ->
                            PoseOverlay(
                                pose = overlayFrame.pose,
                                contentRect = videoContentRect,
                                modifier = Modifier.fillMaxSize(),
                                lineColor = AttemptPreviewOverlayLineColor,
                                pointColor = AttemptPreviewOverlayPointColor,
                                hiddenLandmarkIndices = AttemptPreviewHiddenFaceLandmarks
                            )
                        }

                        if (state.numberedHolds.isNotEmpty()) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawHoldNumbers(
                                    holds = state.numberedHolds,
                                    contentRect = videoContentRect
                                )
                            }
                        }
                    },
                    overlayLayer = {

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                }
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(AttemptPreviewControlHeight)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.22f to Color(0xFF101114).copy(alpha = 0.08f),
                                            0.55f to Color(0xFF101114).copy(alpha = 0.46f),
                                            0.82f to Color(0xFF101114).copy(alpha = 0.84f),
                                            1.0f to Color(0xFF101114)
                                        )
                                    )
                                )
                        ) {
                            PoseVideoScrubber(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp),
                                currentPositionMs = displayedPositionMs,
                                durationMs = durationMs,
                                enabled = canScrub,
                                markers = scrubberMarkers,
                                colors = PoseScrubberColors(
                                    trackColor = Color.White.copy(alpha = if (canScrub) 0.18f else 0.08f),
                                    progressColor = AttemptPreviewScrubberAccent,
                                    thumbColor = Color.White,
                                    textColor = Color.White.copy(alpha = 0.82f)
                                ),
                                trackAnchoredToBottom = true,
                                onTapSeek = { requestedTimeMs ->
                                    if (poseTimestamps.isEmpty()) return@PoseVideoScrubber
                                    val snappedTimeMs = poseTimestamps.minByOrNull { timestamp ->
                                        abs(timestamp - requestedTimeMs.coerceIn(0L, durationMs))
                                    } ?: return@PoseVideoScrubber
                                    currentPositionMs = snappedTimeMs
                                    scrubPositionMs = snappedTimeMs
                                    exoPlayer.seekTo(snappedTimeMs)
                                    exoPlayer.play()
                                },
                                onScrubStart = {
                                    if (!canScrub) return@PoseVideoScrubber
                                    wasPlayingBeforeScrub = exoPlayer.isPlaying
                                    scrubPositionMs = poseTimestamps.minByOrNull { timestamp ->
                                        abs(timestamp - displayedPositionMs)
                                    } ?: displayedPositionMs
                                    isScrubbing = true
                                    exoPlayer.pause()
                                },
                                onScrubMove = { requestedTimeMs ->
                                    if (!canScrub) return@PoseVideoScrubber
                                    val snappedTimeMs = poseTimestamps.minByOrNull { timestamp ->
                                        abs(timestamp - requestedTimeMs.coerceIn(0L, durationMs))
                                    } ?: return@PoseVideoScrubber
                                    scrubPositionMs = snappedTimeMs
                                    currentPositionMs = snappedTimeMs
                                },
                                onScrubStop = {
                                    if (!isScrubbing) return@PoseVideoScrubber
                                    val finalTimeMs = poseTimestamps.minByOrNull { timestamp ->
                                        abs(timestamp - scrubPositionMs)
                                    } ?: scrubPositionMs
                                    exoPlayer.seekTo(finalTimeMs)
                                    currentPositionMs = finalTimeMs
                                    scrubPositionMs = finalTimeMs
                                    isScrubbing = false
                                    if (wasPlayingBeforeScrub) {
                                        exoPlayer.play()
                                    }
                                }
                            )
                        }

                        if (onShareClick != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 72.dp)
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF101114).copy(alpha = 0.82f))
                                    .clickable(onClick = onShareClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "시도 분석 결과 공유",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            } else {
                HoldOverviewPreview(
                    bitmap = state.previewBitmap,
                    holds = state.previewHolds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(242.dp)
                )
            }

        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

private data class HeaderChipTone(
    val background: Color,
    val content: Color,
    val border: Color
)

private fun buildHeaderChipTone(baseColor: Color): HeaderChipTone {
    val contentColor = when {
        baseColor.luminance() < 0.12f -> lerp(baseColor, Color.White, 0.72f)
        baseColor.luminance() < 0.22f -> lerp(baseColor, Color.White, 0.52f)
        baseColor.luminance() > 0.84f -> lerp(baseColor, Color.Black, 0.28f)
        else -> baseColor
    }

    return HeaderChipTone(
        background = baseColor.copy(alpha = 0.32f),
        content = contentColor,
        border = baseColor.copy(alpha = 0.62f)
    )
}

private fun DrawScope.drawHoldNumbers(
    holds: List<HoldNumbered>,
    contentRect: VideoContentRect
) {
    val drawArea = resolveHoldDrawArea(
        contentRect = contentRect,
        fallbackSize = IntSize(size.width.toInt(), size.height.toInt())
    )

    calculateHoldNumberScreenRects(
        holds = holds,
        drawArea = drawArea
    ).forEach { (numbered, rect) ->
        val center = Offset(
            x = (rect.l + rect.r) / 2f,
            y = (rect.t + rect.b) / 2f
        )
        val badgeRadius = minOf(rect.r - rect.l, rect.b - rect.t)
            .times(0.22f)
            .coerceIn(12.dp.toPx(), 20.dp.toPx())
        val fillColor = when {
            numbered.isStart -> COLOR_START
            numbered.isEnd -> COLOR_END
            else -> holdLabelToComposeColor(numbered.hold.colorLabel)
        }.copy(alpha = 0.92f)
        val textColor = if (fillColor.luminance() < 0.45f) Color.White else Color.Black

        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = badgeRadius + 3.dp.toPx(),
            center = center
        )
        drawCircle(
            color = fillColor,
            radius = badgeRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = badgeRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = textColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = (badgeRadius * 1.05f).coerceAtLeast(12.sp.toPx())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val baseline = center.y - ((paint.descent() + paint.ascent()) / 2f)
            canvas.nativeCanvas.drawText(
                numbered.holdNo.toString(),
                center.x,
                baseline,
                paint
            )
        }
    }
}

private fun resolveHoldDrawArea(
    contentRect: VideoContentRect,
    fallbackSize: IntSize
): VideoContentRect {
    if (contentRect.width > 0f && contentRect.height > 0f) {
        return contentRect
    }

    return VideoContentRect(
        left = 0f,
        top = 0f,
        width = fallbackSize.width.toFloat(),
        height = fallbackSize.height.toFloat()
    )
}

private fun calculateHoldNumberScreenRects(
    holds: List<HoldNumbered>,
    drawArea: VideoContentRect
): List<Pair<HoldNumbered, ScreenRect>> {
    return holds.map { numbered ->
        numbered to numbered.hold.toScreenRect(
            offX = drawArea.left,
            offY = drawArea.top,
            scaledW = drawArea.width,
            scaledH = drawArea.height
        )
    }
}
