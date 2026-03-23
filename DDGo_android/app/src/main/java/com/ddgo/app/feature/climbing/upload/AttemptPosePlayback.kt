package com.ddgo.app.feature.climbing.upload

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.HoldNumbered
import androidx.media3.common.VideoSize
import kotlin.math.abs

internal data class VideoContentRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

internal data class VideoFrameMask(
    val topHeightPx: Float,
    val bottomHeightPx: Float
) {
    val isVisible: Boolean
        get() = topHeightPx > 0f || bottomHeightPx > 0f
}

internal data class PoseScrubberColors(
    val trackColor: Color,
    val progressColor: Color,
    val thumbColor: Color,
    val textColor: Color
)

internal data class PoseScrubberMarker(
    val index: Int,
    val timeMs: Long,
    val isSelected: Boolean = false
)

private val SHARED_POSE_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 27 to 29, 29 to 31,
    24 to 26, 26 to 28, 28 to 30, 30 to 32
)

internal fun findNearestPoseForPlayback(
    poses: List<Pose>,
    positionMs: Long
): Pose? {
    if (poses.isEmpty() || positionMs < 0L) return null

    return poses.minByOrNull { pose ->
        abs(pose.frameTimeMs - positionMs)
    }
}

@Composable
internal fun TrackedPointTrailOverlay(
    trailSegmentsByKind: Map<OverlayTrackKind, List<List<OverlayPoint>>>,
    currentTrackedPoints: Map<OverlayTrackKind, OverlayPoint>,
    contentRect: VideoContentRect,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (contentRect.width <= 0f || contentRect.height <= 0f) return@Canvas

        val trailStrokeWidth = 3.dp.toPx()
        val currentPointRadius = 6.dp.toPx()

        trailSegmentsByKind.forEach { (kind, segments) ->
            val color = overlayTrackColor(kind)
            segments.forEach { segment ->
                if (segment.size < 2) return@forEach
                segment.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = color.copy(alpha = 0.78f),
                        start = start.toScreenOffset(contentRect),
                        end = end.toScreenOffset(contentRect),
                        strokeWidth = trailStrokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        currentTrackedPoints.forEach { (kind, point) ->
            drawCircle(
                color = overlayTrackColor(kind),
                radius = currentPointRadius,
                center = point.toScreenOffset(contentRect)
            )
        }
    }
}

internal fun List<Long>.findNearestTimestamp(targetMs: Long): Long? {
    if (isEmpty()) return null

    val target = targetMs.coerceAtLeast(0L)
    val index = binarySearch(target)
    if (index >= 0) return this[index]

    val insertionPoint = -(index + 1)
    val before = getOrNull(insertionPoint - 1)
    val after = getOrNull(insertionPoint)

    return when {
        before == null -> after
        after == null -> before
        target - before <= after - target -> before
        else -> after
    }
}

internal fun Float.positionToDuration(
    width: Float,
    durationMs: Long
): Long {
    if (width <= 0f || durationMs <= 0L) return 0L
    return ((coerceIn(0f, width) / width) * durationMs.toFloat()).toLong()
}

internal fun Long.toVideoTimeString(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1000L).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

internal fun markerPositionFraction(
    timeMs: Long,
    durationMs: Long
): Float {
    if (durationMs <= 0L) return 0f
    return (timeMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun resolveDisplayedVideoAspectRatio(videoSize: VideoSize): Float {
    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return DEFAULT_VIDEO_ASPECT_RATIO
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return DEFAULT_VIDEO_ASPECT_RATIO
    }

    return displayedWidth / displayedHeight
}

internal fun calculateVideoContentRect(
    containerSize: IntSize,
    videoSize: VideoSize
): VideoContentRect {
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return VideoContentRect(0f, 0f, 0f, 0f)
    }

    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return VideoContentRect(0f, 0f, containerWidth, containerHeight)
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return VideoContentRect(0f, 0f, containerWidth, containerHeight)
    }

    val videoAspectRatio = displayedWidth / displayedHeight
    val containerAspectRatio = containerWidth / containerHeight

    return if (containerAspectRatio > videoAspectRatio) {
        val fittedHeight = containerHeight
        val fittedWidth = fittedHeight * videoAspectRatio
        VideoContentRect(
            left = (containerWidth - fittedWidth) / 2f,
            top = 0f,
            width = fittedWidth,
            height = fittedHeight
        )
    } else {
        val fittedWidth = containerWidth
        val fittedHeight = fittedWidth / videoAspectRatio
        VideoContentRect(
            left = 0f,
            top = (containerHeight - fittedHeight) / 2f,
            width = fittedWidth,
            height = fittedHeight
        )
    }
}

internal fun calculateVerticalVideoFrameMask(
    holds: List<HoldNumbered>,
    contentRect: VideoContentRect,
    videoAspectRatio: Float,
    safeInsetPx: Float
): VideoFrameMask {
    if (videoAspectRatio >= 1f) {
        return VideoFrameMask(topHeightPx = 0f, bottomHeightPx = 0f)
    }
    if (holds.isEmpty() || contentRect.width <= 0f || contentRect.height <= 0f) {
        return VideoFrameMask(topHeightPx = 0f, bottomHeightPx = 0f)
    }

    val topmostHoldTopPx = holds.minOf { hold ->
        contentRect.top + (hold.hold.boundingBox.top.coerceIn(0f, 1f) * contentRect.height)
    }
    val bottommostHoldBottomPx = holds.maxOf { hold ->
        contentRect.top + (hold.hold.boundingBox.bottom.coerceIn(0f, 1f) * contentRect.height)
    }

    val videoBottomPx = contentRect.top + contentRect.height
    val visibleTopPx = (topmostHoldTopPx - safeInsetPx).coerceIn(contentRect.top, videoBottomPx)
    val visibleBottomPx = (bottommostHoldBottomPx + safeInsetPx).coerceIn(contentRect.top, videoBottomPx)

    return VideoFrameMask(
        topHeightPx = (visibleTopPx - contentRect.top).coerceAtLeast(0f),
        bottomHeightPx = (videoBottomPx - visibleBottomPx).coerceAtLeast(0f)
    )
}

@Composable
internal fun VerticalVideoFrameMaskOverlay(
    frameMask: VideoFrameMask,
    contentRect: VideoContentRect,
    backgroundColor: Color,
    edgeFade: Dp,
    modifier: Modifier = Modifier
) {
    if (!frameMask.isVisible || contentRect.width <= 0f || contentRect.height <= 0f) return

    val density = LocalDensity.current
    val contentLeft = with(density) { contentRect.left.toDp() }
    val contentTop = with(density) { contentRect.top.toDp() }
    val contentWidth = with(density) { contentRect.width.toDp() }
    val contentBottom = with(density) { (contentRect.top + contentRect.height).toDp() }
    val topMaskHeight = with(density) { frameMask.topHeightPx.toDp() }
    val bottomMaskHeight = with(density) { frameMask.bottomHeightPx.toDp() }
    val safeEdgeFade = minOf(edgeFade, topMaskHeight)
    val bottomEdgeFade = minOf(edgeFade, bottomMaskHeight)

    Box(modifier = modifier) {
        if (frameMask.topHeightPx > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = contentLeft, y = contentTop)
                    .width(contentWidth)
                    .height(topMaskHeight)
                    .background(backgroundColor)
            )
            if (safeEdgeFade > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = contentLeft,
                            y = contentTop + topMaskHeight - safeEdgeFade
                        )
                        .width(contentWidth)
                        .height(safeEdgeFade)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(backgroundColor.copy(alpha = 0.94f), Color.Transparent)
                            )
                        )
                )
            }
        }

        if (frameMask.bottomHeightPx > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = contentLeft,
                        y = contentBottom - bottomMaskHeight
                    )
                    .width(contentWidth)
                    .height(bottomMaskHeight)
                    .background(backgroundColor)
            )
            if (bottomEdgeFade > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = contentLeft,
                            y = contentBottom - bottomMaskHeight
                        )
                        .width(contentWidth)
                        .height(bottomEdgeFade)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, backgroundColor.copy(alpha = 0.94f))
                            )
                        )
                )
            }
        }
    }
}

@Composable
internal fun PoseOverlay(
    pose: Pose,
    contentRect: VideoContentRect,
    modifier: Modifier = Modifier,
    lineColor: Color,
    pointColor: Color,
    hiddenLandmarkIndices: Set<Int> = emptySet()
) {
    Canvas(modifier = modifier) {
        if (contentRect.width <= 0f || contentRect.height <= 0f) return@Canvas

        val landmarksByIndex = pose.landmarks.associateBy { landmark -> landmark.index }
        val jointRadius = 4.dp.toPx()
        val strokeWidth = 2.dp.toPx()

        SHARED_POSE_CONNECTIONS.forEach { (startIndex, endIndex) ->
            val start = landmarksByIndex[startIndex] ?: return@forEach
            val end = landmarksByIndex[endIndex] ?: return@forEach

            drawLine(
                color = lineColor,
                start = Offset(
                    x = contentRect.left + (start.x.coerceIn(0f, 1f) * contentRect.width),
                    y = contentRect.top + (start.y.coerceIn(0f, 1f) * contentRect.height)
                ),
                end = Offset(
                    x = contentRect.left + (end.x.coerceIn(0f, 1f) * contentRect.width),
                    y = contentRect.top + (end.y.coerceIn(0f, 1f) * contentRect.height)
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        pose.landmarks.forEach { landmark ->
            if (landmark.index in hiddenLandmarkIndices) return@forEach

            drawCircle(
                color = pointColor,
                radius = jointRadius,
                center = Offset(
                    x = contentRect.left + (landmark.x.coerceIn(0f, 1f) * contentRect.width),
                    y = contentRect.top + (landmark.y.coerceIn(0f, 1f) * contentRect.height)
                )
            )
        }
    }
}

private fun OverlayPoint.toScreenOffset(contentRect: VideoContentRect): Offset = Offset(
    x = contentRect.left + (x.coerceIn(0f, 1f) * contentRect.width),
    y = contentRect.top + (y.coerceIn(0f, 1f) * contentRect.height)
)

private fun overlayTrackColor(kind: OverlayTrackKind): Color = when (kind) {
    OverlayTrackKind.HIP_CENTER -> Color(0xFFFFC857)
    OverlayTrackKind.LEFT_HAND_CENTER -> Color(0xFF66BB6A)
    OverlayTrackKind.RIGHT_HAND_CENTER -> Color(0xFF42A5F5)
    OverlayTrackKind.LEFT_FOOT_CENTER -> Color(0xFFFF7043)
    OverlayTrackKind.RIGHT_FOOT_CENTER -> Color(0xFFAB47BC)
}

@Composable
internal fun PoseVideoScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    markers: List<PoseScrubberMarker>,
    colors: PoseScrubberColors,
    modifier: Modifier = Modifier,
    onTapSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubStop: () -> Unit
) {
    val progress = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(enabled, durationMs) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        onTapSeek(offset.x.positionToDuration(size.width.toFloat(), durationMs))
                    }
                }
                .pointerInput(enabled, durationMs) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onScrubStart()
                            onScrubMove(
                                offset.x.positionToDuration(size.width.toFloat(), durationMs)
                            )
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onScrubMove(
                                change.position.x.positionToDuration(
                                    size.width.toFloat(),
                                    durationMs
                                )
                            )
                        },
                        onDragEnd = onScrubStop,
                        onDragCancel = onScrubStop
                    )
                }
        ) {
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 7.dp.toPx()

            drawLine(
                color = colors.trackColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
            drawLine(
                color = colors.progressColor,
                start = Offset(0f, centerY),
                end = Offset(size.width * progress, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
            val markerCenterY = centerY - 12.dp.toPx()
            markers
                .distinctBy { marker -> marker.index to marker.timeMs }
                .forEach { marker ->
                    val markerX = size.width * markerPositionFraction(marker.timeMs, durationMs)
                    val badgeRadius = 8.dp.toPx()
                    val fillColor = colors.progressColor.copy(
                        alpha = if (marker.isSelected) 1f else 0.58f
                    )
                    val textColor = if (fillColor.luminance() > 0.45f) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }

                    drawLine(
                        color = fillColor,
                        start = Offset(markerX, markerCenterY + badgeRadius + 2.dp.toPx()),
                        end = Offset(markerX, centerY - trackHeight),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = fillColor,
                        radius = badgeRadius,
                        center = Offset(markerX, markerCenterY)
                    )
                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            isAntiAlias = true
                            color = textColor
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 10.sp.toPx()
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val baseline = markerCenterY - ((paint.descent() + paint.ascent()) / 2f)
                        canvas.nativeCanvas.drawText(
                            marker.index.toString(),
                            markerX,
                            baseline,
                            paint
                        )
                    }
                }
            drawCircle(
                color = colors.thumbColor.copy(alpha = if (enabled) 1f else 0.55f),
                radius = thumbRadius,
                center = Offset(size.width * progress, centerY)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentPositionMs.toVideoTimeString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textColor
            )
            Text(
                text = durationMs.toVideoTimeString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textColor.copy(alpha = 0.8f)
            )
        }
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 9f / 16f
