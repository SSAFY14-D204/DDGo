package com.ddgo.app.feature.climbing.upload.ui.shared.organism

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.BuildConfig
import com.ddgo.app.R
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PosePixelPoint
import com.ddgo.app.domain.model.PoseWorldPoint
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.AttemptPoseOverlayCache
import com.ddgo.app.feature.climbing.upload.AttemptPoseOverlayFrame
import com.ddgo.app.feature.climbing.upload.CroppedVideoViewport
import com.ddgo.app.feature.climbing.upload.RawVerticalCropBounds
import com.ddgo.app.feature.climbing.upload.PoseOverlay
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.PoseScrubberMarker
import com.ddgo.app.feature.climbing.upload.PoseVideoScrubber
import com.ddgo.app.feature.climbing.upload.ScreenRect
import com.ddgo.app.feature.climbing.upload.VideoViewportCropSpec
import com.ddgo.app.feature.climbing.upload.VideoContentRect
import com.ddgo.app.feature.climbing.upload.calculateVerticalVideoViewportCropSpecFromBounds
import com.ddgo.app.feature.climbing.upload.calculateVerticalVideoViewportCropSpecFromRawHolds
import com.ddgo.app.feature.climbing.upload.calculateVideoContentRect
import com.ddgo.app.feature.climbing.upload.findNearestOverlayFrameForPlayback
import com.ddgo.app.feature.climbing.upload.findNearestTimestamp
import com.ddgo.app.feature.climbing.upload.holdLabelToComposeColor
import com.ddgo.app.feature.climbing.upload.resolveDisplayedVideoAspectRatio
import com.ddgo.app.feature.climbing.upload.resolveInitialAttemptPlaybackStartTimeMs
import com.ddgo.app.feature.climbing.upload.toScreenRect
import com.ddgo.app.feature.climbing.upload.uncroppedVideoViewportCropSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs

internal data class AttemptVideoSectionState(
    val videoUri: String?,
    val numberedHolds: List<HoldNumbered> = emptyList(),
    val rawHolds: List<Hold> = emptyList(),
    val viewportCropBounds: RawVerticalCropBounds? = null,
    val analysisPoints: List<AnalysisPoint> = emptyList(),
    val markers: List<PoseScrubberMarker> = emptyList(),
    val attemptPoseSequence: List<Pose> = emptyList(),
    val overlayCache: AttemptPoseOverlayCache? = null,
    val wallArrivalTimeMs: Long? = null,
    val personObservationStartTimeMs: Long? = null,
    val seekRequestId: Long = 0L,
    val seekRequestTimeMs: Long? = null
)

internal data class AttemptVideoOverlayRenderState(
    val displayedPositionMs: Long,
    val videoContentRect: VideoContentRect,
    val currentOverlayFrame: AttemptPoseOverlayFrame?,
    val currentOverlayPose: Pose?
)

@Composable
internal fun AttemptVideoSection(
    state: AttemptVideoSectionState,
    modifier: Modifier = Modifier,
    lineColor: Color,
    pointColor: Color,
    scrubberColors: PoseScrubberColors,
    controlSurfaceColor: Color,
    showScrubberTimeLabels: Boolean = true,
    hiddenLandmarkIndices: Set<Int> = emptySet(),
    hiddenPointIndices: Set<Int> = emptySet(),
    pointRadiusScale: Float = 1f,
    topSafeInset: Dp = 24.dp,
    bottomSafeInset: Dp = 64.dp,
    controlAreaHeight: Dp = 132.dp,
    viewportHeightOverride: Dp? = null,
    logDisplayedPoseRawData: Boolean = false,
    onDisplayedPositionChanged: (Long) -> Unit = {},
    topOverlayContent: @Composable BoxScope.() -> Unit = {},
    midOverlayContent: @Composable BoxScope.(AttemptVideoOverlayRenderState) -> Unit = { _ -> },
    overlayContent: @Composable BoxScope.(AttemptVideoOverlayRenderState) -> Unit = { _ -> }
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val poseTimestamps = remember(state.attemptPoseSequence) {
        state.attemptPoseSequence
            .map(Pose::frameTimeMs)
            .distinct()
            .sorted()
    }
    val exoPlayer = remember(context, state.videoUri) {
        state.videoUri?.let { videoUri ->
            ExoPlayer.Builder(context).build().apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
                prepare()
            }
        }
    }
    val startFlag = ImageBitmap.imageResource(id = R.drawable.start_flag)
    val endFlag = ImageBitmap.imageResource(id = R.drawable.end_flag)

    var playerVideoSize by remember(state.videoUri) { mutableStateOf(VideoSize.UNKNOWN) }
    var fullVideoLayerSize by remember(state.videoUri) { mutableStateOf(IntSize.Zero) }
    var currentPositionMs by remember(state.videoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(state.videoUri) { mutableLongStateOf(0L) }
    var isScrubbing by rememberSaveable(state.videoUri) { mutableStateOf(false) }
    var scrubPositionMs by rememberSaveable(state.videoUri) { mutableLongStateOf(0L) }
    var hasInitialAutoSeekApplied by rememberSaveable(state.videoUri) { mutableStateOf(false) }
    var wasPlayingBeforeScrub by remember(state.videoUri) { mutableStateOf(false) }

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
        state.viewportCropBounds,
        fullVideoLayerSize,
        videoAspectRatio,
        density,
        topSafeInset,
        bottomSafeInset
    ) {
        if (fullVideoLayerSize.width <= 0 || fullVideoLayerSize.height <= 0) {
            uncroppedVideoViewportCropSpec(videoAspectRatio)
        } else if (state.viewportCropBounds != null) {
            calculateVerticalVideoViewportCropSpecFromBounds(
                topFraction = state.viewportCropBounds.topFraction,
                bottomFraction = state.viewportCropBounds.bottomFraction,
                videoAspectRatio = videoAspectRatio,
                fullVideoHeightPx = fullVideoLayerSize.height.toFloat(),
                topSafeInsetPx = with(density) { topSafeInset.toPx() },
                bottomSafeInsetPx = with(density) { bottomSafeInset.toPx() }
            )
        } else {
            calculateVerticalVideoViewportCropSpecFromRawHolds(
                holds = state.rawHolds,
                videoAspectRatio = videoAspectRatio,
                fullVideoHeightPx = fullVideoLayerSize.height.toFloat(),
                topSafeInsetPx = with(density) { topSafeInset.toPx() },
                bottomSafeInsetPx = with(density) { bottomSafeInset.toPx() }
            )
        }
    }
    val viewportHeightOverridePx = remember(viewportHeightOverride, density) {
        viewportHeightOverride?.let { with(density) { it.roundToPx() } }
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
    val overlayRenderState = remember(
        displayedPositionMs,
        videoContentRect,
        currentOverlayFrame
    ) {
        AttemptVideoOverlayRenderState(
            displayedPositionMs = displayedPositionMs,
            videoContentRect = videoContentRect,
            currentOverlayFrame = currentOverlayFrame,
            currentOverlayPose = currentOverlayFrame?.pose
        )
    }
    var lastLoggedOverlayFrameTimeMs by remember(state.videoUri) { mutableStateOf<Long?>(null) }
    var hasLoggedMissingOverlayFrame by remember(state.videoUri) { mutableStateOf(false) }
    val resolvedMarkers = remember(state.markers, state.analysisPoints) {
        if (state.markers.isNotEmpty()) {
            state.markers
        } else {
            state.analysisPoints.map { point ->
                PoseScrubberMarker(
                    index = point.index,
                    timeMs = point.timeMs,
                    kind = point.kind
                )
            }
        }
    }

    LaunchedEffect(state.videoUri) {
        currentPositionMs = 0L
        durationMs = 0L
        scrubPositionMs = 0L
        isScrubbing = false
        hasInitialAutoSeekApplied = false
        onDisplayedPositionChanged(0L)

        state.videoUri?.let { videoUri ->
            exoPlayer?.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
            playerVideoSize = exoPlayer?.videoSize ?: VideoSize.UNKNOWN
        }
    }

    LaunchedEffect(displayedPositionMs) {
        onDisplayedPositionChanged(displayedPositionMs)
    }

    LaunchedEffect(
        logDisplayedPoseRawData,
        state.videoUri,
        displayedPositionMs,
        currentOverlayFrame?.frameTimeMs
    ) {
        if (!logDisplayedPoseRawData || !BuildConfig.DEBUG) return@LaunchedEffect

        val overlayFrame = currentOverlayFrame ?: run {
            lastLoggedOverlayFrameTimeMs = null
            if (!hasLoggedMissingOverlayFrame) {
                AttemptPoseRawLogger.logMissingFrame(
                    videoUri = state.videoUri,
                    displayedPositionMs = displayedPositionMs
                )
                hasLoggedMissingOverlayFrame = true
            }
            return@LaunchedEffect
        }

        hasLoggedMissingOverlayFrame = false
        if (lastLoggedOverlayFrameTimeMs == overlayFrame.frameTimeMs) {
            return@LaunchedEffect
        }

        lastLoggedOverlayFrameTimeMs = overlayFrame.frameTimeMs
        AttemptPoseRawLogger.log(
            videoUri = state.videoUri,
            displayedPositionMs = displayedPositionMs,
            pose = overlayFrame.pose
        )
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
        state.videoUri,
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

    LaunchedEffect(exoPlayer, state.videoUri, isScrubbing) {
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

    if (exoPlayer == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(controlSurfaceColor)
        )
        return
    }

    CroppedVideoViewport(
        cropSpec = viewportCropSpec,
        fullVideoAspectRatio = videoAspectRatio,
        topCropPx = topCropOffsetPx,
        viewportHeightOverridePx = viewportHeightOverridePx,
        modifier = modifier.fillMaxWidth(),
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                ) {
                    PoseOverlay(
                        pose = overlayFrame.pose,
                        contentRect = videoContentRect,
                        modifier = Modifier.fillMaxSize(),
                        lineColor = lineColor,
                        pointColor = pointColor,
                        hiddenLandmarkIndices = hiddenLandmarkIndices,
                        hiddenPointIndices = hiddenPointIndices,
                        pointRadiusScale = pointRadiusScale
                    )
                }
            }

            if (state.numberedHolds.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawHoldSegOverlays(
                            holds = state.numberedHolds,
                            contentRect = videoContentRect,
                            startFlag = startFlag,
                            endFlag = endFlag
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
            ) {
                midOverlayContent(overlayRenderState)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(4f)
            ) {
                overlayContent(overlayRenderState)
            }
        },
        overlayLayer = {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        if (exoPlayer.playbackState == Player.STATE_ENDED) {
                            exoPlayer.seekTo(0L)
                        }
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
            )

            topOverlayContent()

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(controlAreaHeight)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.22f to controlSurfaceColor.copy(alpha = 0.08f),
                                0.55f to controlSurfaceColor.copy(alpha = 0.46f),
                                0.82f to controlSurfaceColor.copy(alpha = 0.84f),
                                1.0f to controlSurfaceColor
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
                    markers = resolvedMarkers,
                    colors = scrubberColors,
                    trackAnchoredToBottom = true,
                    showTimeLabels = showScrubberTimeLabels,
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
                        scrubPositionMs = poseTimestamps.findNearestTimestamp(displayedPositionMs)
                            ?: displayedPositionMs
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
                        val finalTimeMs = poseTimestamps.findNearestTimestamp(scrubPositionMs)
                            ?: scrubPositionMs
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
        }
    )
}

private fun DrawScope.drawHoldSegOverlays(
    holds: List<HoldNumbered>,
    contentRect: VideoContentRect,
    startFlag: ImageBitmap,
    endFlag: ImageBitmap
) {
    val drawArea = resolveHoldDrawArea(
        contentRect = contentRect,
        fallbackSize = IntSize(size.width.toInt(), size.height.toInt())
    )
    val outlineWidth = 2.dp.toPx()

    holds.forEach { numbered ->
        val rect = numbered.hold.toScreenRect(
            offX = drawArea.left,
            offY = drawArea.top,
            scaledW = drawArea.width,
            scaledH = drawArea.height
        )
        val polygon = numbered.hold.toScreenPolygon(
            offX = drawArea.left,
            offY = drawArea.top,
            scaledW = drawArea.width,
            scaledH = drawArea.height
        )
        val polygonPath = polygon.toPath()
        val hasPolygon = polygon.size >= 3
        val baseColor = holdLabelToComposeColor(numbered.hold.colorLabel)
        val fillColor = baseColor.copy(alpha = 0.24f)
        val outlineColor = baseColor.copy(alpha = 0.78f)
        val textColor = resolveHoldOverlayNumberTextColor(baseColor)
        val bounds = polygon.boundsOrNull() ?: rect.toOverlayBounds()

        if (hasPolygon) {
            drawPath(path = polygonPath, color = fillColor)
            drawPath(
                path = polygonPath,
                color = outlineColor,
                style = Stroke(
                    width = outlineWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            drawRect(
                color = fillColor,
                topLeft = Offset(rect.l, rect.t),
                size = Size(rect.r - rect.l, rect.b - rect.t)
            )
            drawRect(
                color = outlineColor,
                topLeft = Offset(rect.l, rect.t),
                size = Size(rect.r - rect.l, rect.b - rect.t),
                style = Stroke(width = outlineWidth)
            )
        }

        val center = Offset(
            x = bounds.centerX,
            y = bounds.centerY
        )
        val textSizePx = minOf(bounds.width, bounds.height)
            .times(0.34f)
            .coerceIn(12.sp.toPx(), 22.sp.toPx())

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = textColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = textSizePx
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

        if (numbered.isStart || numbered.isEnd) {
            val flagBitmap = if (numbered.isStart) startFlag else endFlag
            val targetHeightPx = minOf(bounds.width, bounds.height)
                .times(0.52f)
                .coerceIn(24.dp.toPx(), 40.dp.toPx())
            val aspectRatio = if (flagBitmap.height > 0) {
                flagBitmap.width.toFloat() / flagBitmap.height.toFloat()
            } else {
                1f
            }
            val targetWidthPx = (targetHeightPx * aspectRatio)
                .coerceAtLeast(targetHeightPx * 0.75f)
            val anchorPoint = resolveFlagAnchorPoint(
                polygon = polygon,
                rect = rect,
                bounds = bounds
            )
            val left = anchorPoint.x
            val top = anchorPoint.y - (targetHeightPx * 0.75f)

            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    isFilterBitmap = true
                }
                canvas.nativeCanvas.drawBitmap(
                    flagBitmap.asAndroidBitmap(),
                    null,
                    android.graphics.RectF(
                        left,
                        top,
                        left + targetWidthPx,
                        top + targetHeightPx
                    ),
                    paint
                )
            }
        }
    }
}

private fun resolveFlagAnchorPoint(
    polygon: List<Offset>,
    rect: ScreenRect,
    bounds: OverlayBounds
): Offset {
    val highestPolygonPoint = polygon
        .takeIf { it.size >= 3 }
        ?.minWithOrNull(
            compareBy<Offset> { it.y }
                .thenBy { it.x }
        )

    return highestPolygonPoint ?: Offset(
        x = rect.l,
        y = bounds.top
    )
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

private fun Hold.toScreenPolygon(
    offX: Float,
    offY: Float,
    scaledW: Float,
    scaledH: Float
): List<Offset> = polygon.map { point ->
    Offset(
        x = offX + point.x * scaledW,
        y = offY + point.y * scaledH
    )
}

private fun List<Offset>.toPath(): Path = Path().apply {
    if (size < 3) return@apply
    moveTo(this@toPath[0].x, this@toPath[0].y)
    for (index in 1 until size) {
        lineTo(this@toPath[index].x, this@toPath[index].y)
    }
    close()
}

private fun resolveHoldOverlayNumberTextColor(baseColor: Color): Color =
    if (baseColor.luminance() < 0.45f) Color.White else Color.Black

private data class OverlayBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float
        get() = right - left
    val height: Float
        get() = bottom - top
    val centerX: Float
        get() = (left + right) / 2f
    val centerY: Float
        get() = (top + bottom) / 2f
}

private fun ScreenRect.toOverlayBounds(): OverlayBounds = OverlayBounds(
    left = l,
    top = t,
    right = r,
    bottom = b
)

private fun List<Offset>.boundsOrNull(): OverlayBounds? {
    if (isEmpty()) return null
    val minX = minOf { it.x }
    val maxX = maxOf { it.x }
    val minY = minOf { it.y }
    val maxY = maxOf { it.y }
    if (maxX <= minX || maxY <= minY) return null
    return OverlayBounds(
        left = minX,
        top = minY,
        right = maxX,
        bottom = maxY
    )
}

private object AttemptPoseRawLogger {
    private const val TAG = "AttemptPoseRaw"
    private const val LANDMARK_CHUNK_SIZE = 6

    fun log(
        videoUri: String?,
        displayedPositionMs: Long,
        pose: Pose
    ) {
        Log.i(
            TAG,
            "video=${videoUri?.substringAfterLast('/')} displayedPositionMs=$displayedPositionMs frameTimeMs=${pose.frameTimeMs} landmarkCount=${pose.landmarks.size}"
        )

        pose.landmarks
            .chunked(LANDMARK_CHUNK_SIZE)
            .forEachIndexed { chunkIndex, chunk ->
                Log.i(
                    TAG,
                    "landmarks[$chunkIndex]=${chunk.joinToString(separator = " | ") { landmark -> landmark.toRawLogString() }}"
                )
            }

        if (pose.landmarksPx.isNotEmpty()) {
            Log.i(
                TAG,
                "landmarksPx=${pose.landmarksPx.entries.joinToString(separator = " | ") { (key, point) -> "$key=${point.toRawLogString()}" }}"
            )
        }

        if (pose.worldLandmarksSample.isNotEmpty()) {
            Log.i(
                TAG,
                "worldLandmarksSample=${pose.worldLandmarksSample.entries.joinToString(separator = " | ") { (key, point) -> "$key=${point.toRawLogString()}" }}"
            )
        }
    }

    fun logMissingFrame(
        videoUri: String?,
        displayedPositionMs: Long
    ) {
        Log.i(
            TAG,
            "video=${videoUri?.substringAfterLast('/')} displayedPositionMs=$displayedPositionMs overlayFrame=null"
        )
    }

    private fun PoseLandmark.toRawLogString(): String {
        return buildString {
            append("i=")
            append(index)
            append(",x=")
            append(formatFloat(x))
            append(",y=")
            append(formatFloat(y))
            append(",z=")
            append(formatFloat(z))
            append(",visibility=")
            append(formatNullableFloat(visibility))
            append(",presence=")
            append(formatNullableFloat(presence))
        }
    }

    private fun PosePixelPoint.toRawLogString(): String {
        return "x=${formatFloat(x)},y=${formatFloat(y)}"
    }

    private fun PoseWorldPoint.toRawLogString(): String {
        return "x=${formatFloat(x)},y=${formatFloat(y)},z=${formatFloat(z)}"
    }

    private fun formatFloat(value: Float): String = String.format(Locale.US, "%.4f", value)

    private fun formatNullableFloat(value: Float?): String {
        return value?.let(::formatFloat) ?: "null"
    }
}
