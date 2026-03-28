package com.ddgo.app.feature.climbing.upload

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import com.ddgo.app.R
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.usecase.HoldNumbered
import androidx.media3.common.VideoSize
import kotlin.math.abs
import kotlin.math.roundToInt

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

internal data class VideoViewportCropSpec(
    val topCropFraction: Float,
    val bottomCropFraction: Float,
    val visibleHeightFraction: Float,
    val viewportAspectRatio: Float,
    val isActive: Boolean
)

internal data class CroppedVideoViewportPlacement(
    val fullVideoHeightPx: Int,
    val viewportHeightPx: Int,
    val transformedLayerOffsetXPx: Int,
    val transformedLayerOffsetYPx: Int,
    val transformedLayerScale: Float
)

internal data class PoseScrubberColors(
    val trackColor: Color,
    val progressColor: Color,
    val thumbColor: Color,
    val textColor: Color
)

internal data class PoseScrubberMarker(
    val index: Int,
    val timeMs: Long,
    val kind: AnalysisPointKind = AnalysisPointKind.GENERIC,
    val isSelected: Boolean = false,
    val flagAnchorFractionX: Float = 0.5f
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
    if (contentRect.width <= 0f || contentRect.height <= 0f) {
        return VideoFrameMask(topHeightPx = 0f, bottomHeightPx = 0f)
    }
    val cropSpec = calculateVerticalVideoViewportCropSpec(
        holds = holds,
        videoAspectRatio = videoAspectRatio,
        fullVideoHeightPx = contentRect.height,
        safeInsetPx = safeInsetPx
    )
    if (!cropSpec.isActive) {
        return VideoFrameMask(topHeightPx = 0f, bottomHeightPx = 0f)
    }

    return VideoFrameMask(
        topHeightPx = cropSpec.topCropFraction * contentRect.height,
        bottomHeightPx = cropSpec.bottomCropFraction * contentRect.height
    )
}

internal fun uncroppedVideoViewportCropSpec(
    videoAspectRatio: Float
): VideoViewportCropSpec {
    val safeAspectRatio = if (videoAspectRatio > 0f) {
        videoAspectRatio
    } else {
        DEFAULT_VIDEO_ASPECT_RATIO
    }

    return VideoViewportCropSpec(
        topCropFraction = 0f,
        bottomCropFraction = 0f,
        visibleHeightFraction = 1f,
        viewportAspectRatio = safeAspectRatio,
        isActive = false
    )
}

internal fun calculateVerticalVideoViewportCropSpecFromBounds(
    topFraction: Float,
    bottomFraction: Float,
    videoAspectRatio: Float,
    fullVideoHeightPx: Float,
    safeInsetPx: Float
): VideoViewportCropSpec = calculateVerticalVideoViewportCropSpecFromBounds(
    topFraction = topFraction,
    bottomFraction = bottomFraction,
    videoAspectRatio = videoAspectRatio,
    fullVideoHeightPx = fullVideoHeightPx,
    topSafeInsetPx = safeInsetPx,
    bottomSafeInsetPx = safeInsetPx
)

internal fun calculateVerticalVideoViewportCropSpecFromBounds(
    topFraction: Float,
    bottomFraction: Float,
    videoAspectRatio: Float,
    fullVideoHeightPx: Float,
    topSafeInsetPx: Float,
    bottomSafeInsetPx: Float
): VideoViewportCropSpec {
    val uncroppedSpec = uncroppedVideoViewportCropSpec(videoAspectRatio)
    if (videoAspectRatio >= 1f || fullVideoHeightPx <= 0f) {
        return uncroppedSpec
    }

    val clampedTopFraction = topFraction.coerceIn(0f, 1f)
    val clampedBottomFraction = bottomFraction.coerceIn(clampedTopFraction, 1f)
    if (clampedBottomFraction <= clampedTopFraction) {
        return uncroppedSpec
    }

    val topSafeInsetFraction = (topSafeInsetPx / fullVideoHeightPx)
        .coerceAtLeast(0f)
    val bottomSafeInsetFraction = (bottomSafeInsetPx / fullVideoHeightPx)
        .coerceAtLeast(0f)
    val visibleTopFraction = (clampedTopFraction - topSafeInsetFraction).coerceIn(0f, 1f)
    val visibleBottomFraction = (clampedBottomFraction + bottomSafeInsetFraction)
        .coerceIn(visibleTopFraction, 1f)
    val visibleHeightFraction = (visibleBottomFraction - visibleTopFraction)
        .coerceIn(0f, 1f)
    val isActive = visibleHeightFraction < FULL_VIDEO_VISIBLE_HEIGHT_EPSILON &&
        (visibleTopFraction > 0f || visibleBottomFraction < 1f)

    if (!isActive || visibleHeightFraction <= 0f) {
        return uncroppedSpec
    }

    return VideoViewportCropSpec(
        topCropFraction = visibleTopFraction,
        bottomCropFraction = (1f - visibleBottomFraction).coerceAtLeast(0f),
        visibleHeightFraction = visibleHeightFraction,
        viewportAspectRatio = videoAspectRatio / visibleHeightFraction,
        isActive = true
    )
}

internal fun calculateVerticalVideoViewportCropSpec(
    holds: List<HoldNumbered>,
    videoAspectRatio: Float,
    fullVideoHeightPx: Float,
    safeInsetPx: Float
): VideoViewportCropSpec = calculateVerticalVideoViewportCropSpec(
    holds = holds,
    videoAspectRatio = videoAspectRatio,
    fullVideoHeightPx = fullVideoHeightPx,
    topSafeInsetPx = safeInsetPx,
    bottomSafeInsetPx = safeInsetPx
)

internal fun calculateVerticalVideoViewportCropSpec(
    holds: List<HoldNumbered>,
    videoAspectRatio: Float,
    fullVideoHeightPx: Float,
    topSafeInsetPx: Float,
    bottomSafeInsetPx: Float
): VideoViewportCropSpec {
    val uncroppedSpec = uncroppedVideoViewportCropSpec(videoAspectRatio)
    if (videoAspectRatio >= 1f || holds.isEmpty() || fullVideoHeightPx <= 0f) {
        return uncroppedSpec
    }

    val topmostHoldTopFraction = holds.minOf { hold ->
        hold.hold.boundingBox.top.coerceIn(0f, 1f)
    }
    val bottommostHoldBottomFraction = holds.maxOf { hold ->
        hold.hold.boundingBox.bottom.coerceIn(0f, 1f)
    }
    return calculateVerticalVideoViewportCropSpecFromBounds(
        topFraction = topmostHoldTopFraction,
        bottomFraction = bottommostHoldBottomFraction,
        videoAspectRatio = videoAspectRatio,
        fullVideoHeightPx = fullVideoHeightPx,
        topSafeInsetPx = topSafeInsetPx,
        bottomSafeInsetPx = bottomSafeInsetPx
    )
}

internal fun calculateVerticalVideoViewportCropSpecFromRawHolds(
    holds: List<Hold>,
    videoAspectRatio: Float,
    fullVideoHeightPx: Float,
    safeInsetPx: Float
): VideoViewportCropSpec = calculateVerticalVideoViewportCropSpecFromRawHolds(
    holds = holds,
    videoAspectRatio = videoAspectRatio,
    fullVideoHeightPx = fullVideoHeightPx,
    topSafeInsetPx = safeInsetPx,
    bottomSafeInsetPx = safeInsetPx
)

internal fun calculateVerticalVideoViewportCropSpecFromRawHolds(
    holds: List<Hold>,
    videoAspectRatio: Float,
    fullVideoHeightPx: Float,
    topSafeInsetPx: Float,
    bottomSafeInsetPx: Float
): VideoViewportCropSpec {
    val uncroppedSpec = uncroppedVideoViewportCropSpec(videoAspectRatio)
    if (videoAspectRatio >= 1f || holds.isEmpty() || fullVideoHeightPx <= 0f) {
        return uncroppedSpec
    }

    val rawBounds = calculateRawVerticalCropBounds(holds) ?: return uncroppedSpec
    return calculateVerticalVideoViewportCropSpecFromBounds(
        topFraction = rawBounds.topFraction,
        bottomFraction = rawBounds.bottomFraction,
        videoAspectRatio = videoAspectRatio,
        fullVideoHeightPx = fullVideoHeightPx,
        topSafeInsetPx = topSafeInsetPx,
        bottomSafeInsetPx = bottomSafeInsetPx
    )
}

internal fun calculateRawVerticalCropBounds(rawHolds: List<Hold>): RawVerticalCropBounds? {
    if (rawHolds.isEmpty()) return null

    val topFractions = rawHolds
        .map { hold -> hold.boundingBox.top.coerceIn(0f, 1f) }
        .sorted()
        .take(RAW_CROP_SAMPLE_COUNT)
    val bottomFractions = rawHolds
        .map { hold -> hold.boundingBox.bottom.coerceIn(0f, 1f) }
        .sortedDescending()
        .take(RAW_CROP_SAMPLE_COUNT)
    if (topFractions.isEmpty() || bottomFractions.isEmpty()) {
        return null
    }

    val topFraction = topFractions.average().toFloat()
    val bottomFraction = bottomFractions.average().toFloat()
    if (bottomFraction <= topFraction) {
        return null
    }

    return RawVerticalCropBounds(
        topFraction = topFraction,
        bottomFraction = bottomFraction
    )
}

internal fun calculateExpandedVerticalCropBoundsFromSelectedHolds(
    selectedHolds: List<HoldNumbered>
): RawVerticalCropBounds? {
    if (selectedHolds.isEmpty()) return null

    val topFraction = selectedHolds.maxOf { hold ->
        hold.hold.boundingBox.top.coerceIn(0f, 1f)
    }
    val bottomFraction = selectedHolds.minOf { hold ->
        hold.hold.boundingBox.bottom.coerceIn(0f, 1f)
    }

    return expandVerticalCropBoundsByVisibleHeightTenth(
        RawVerticalCropBounds(
            topFraction = topFraction,
            bottomFraction = bottomFraction
        )
    )
}

internal fun calculateExpandedVerticalCropBoundsFromSelectedHoldExtents(
    selectedHolds: List<HoldNumbered>
): RawVerticalCropBounds? {
    if (selectedHolds.isEmpty()) return null

    val topFraction = selectedHolds.minOf { hold ->
        hold.hold.boundingBox.top.coerceIn(0f, 1f)
    }
    val bottomFraction = selectedHolds.maxOf { hold ->
        hold.hold.boundingBox.bottom.coerceIn(0f, 1f)
    }

    return expandVerticalCropBoundsByVisibleHeightTenth(
        RawVerticalCropBounds(
            topFraction = topFraction,
            bottomFraction = bottomFraction
        )
    )
}

internal fun calculateExpandedVerticalCropBoundsFromRawHoldExtents(
    rawHolds: List<Hold>
): RawVerticalCropBounds? {
    if (rawHolds.isEmpty()) return null

    val topFraction = rawHolds.minOf { hold ->
        hold.boundingBox.top.coerceIn(0f, 1f)
    }
    val bottomFraction = rawHolds.maxOf { hold ->
        hold.boundingBox.bottom.coerceIn(0f, 1f)
    }

    return expandVerticalCropBoundsByVisibleHeightTenth(
        RawVerticalCropBounds(
            topFraction = topFraction,
            bottomFraction = bottomFraction
        )
    )
}

internal fun expandVerticalCropBoundsByVisibleHeightTenth(
    bounds: RawVerticalCropBounds
): RawVerticalCropBounds? {
    val clampedTopFraction = bounds.topFraction.coerceIn(0f, 1f)
    val clampedBottomFraction = bounds.bottomFraction.coerceIn(clampedTopFraction, 1f)
    val heightFraction = clampedBottomFraction - clampedTopFraction
    if (heightFraction <= 0f) return null

    val marginFraction = heightFraction / 10f
    val expandedTopFraction = (clampedTopFraction - marginFraction).coerceIn(0f, 1f)
    val expandedBottomFraction = (clampedBottomFraction + marginFraction)
        .coerceIn(expandedTopFraction, 1f)
    if (expandedBottomFraction <= expandedTopFraction) return null

    return RawVerticalCropBounds(
        topFraction = expandedTopFraction,
        bottomFraction = expandedBottomFraction
    )
}

internal fun resolveHybridVerticalCropBounds(
    rawBounds: RawVerticalCropBounds?,
    selectedHolds: List<HoldNumbered>
): RawVerticalCropBounds? {
    if (rawBounds == null) return null
    if (selectedHolds.isEmpty()) return rawBounds

    val selectedTopFraction = selectedHolds.minOf { hold ->
        hold.hold.boundingBox.top.coerceIn(0f, 1f)
    }
    val selectedBottomFraction = selectedHolds.maxOf { hold ->
        hold.hold.boundingBox.bottom.coerceIn(0f, 1f)
    }
    val resolvedTopFraction = maxOf(rawBounds.topFraction, selectedTopFraction)
    val resolvedBottomFraction = minOf(rawBounds.bottomFraction, selectedBottomFraction)

    return if (resolvedBottomFraction > resolvedTopFraction) {
        RawVerticalCropBounds(
            topFraction = resolvedTopFraction,
            bottomFraction = resolvedBottomFraction
        )
    } else {
        RawVerticalCropBounds(
            topFraction = selectedTopFraction,
            bottomFraction = selectedBottomFraction
        ).takeIf { bounds -> bounds.bottomFraction > bounds.topFraction }
    }
}
internal fun calculateCroppedVideoViewportPlacement(
    fullVideoWidthPx: Int,
    fullVideoHeightPx: Int,
    cropSpec: VideoViewportCropSpec,
    topCropPx: Float,
    viewportHeightOverridePx: Int? = null
): CroppedVideoViewportPlacement {
    val safeFullVideoWidthPx = fullVideoWidthPx.coerceAtLeast(1)
    val safeFullVideoHeightPx = fullVideoHeightPx.coerceAtLeast(1)
    val baseViewportHeightPx = if (cropSpec.isActive) {
        (safeFullVideoHeightPx * cropSpec.visibleHeightFraction)
            .roundToInt()
            .coerceIn(1, safeFullVideoHeightPx)
    } else {
        safeFullVideoHeightPx
    }
    val clampedViewportHeightOverridePx = viewportHeightOverridePx
        ?.coerceIn(1, baseViewportHeightPx)
    val viewportHeightPx = clampedViewportHeightOverridePx ?: baseViewportHeightPx
    val derivedTopCropPx = if (cropSpec.isActive) {
        (safeFullVideoHeightPx * cropSpec.topCropFraction).roundToInt()
    } else {
        0
    }
    val resolvedTopCropPx = topCropPx
        .roundToInt()
        .takeIf { it > 0 }
        ?: derivedTopCropPx
    val maxTopCropPx = (safeFullVideoHeightPx - baseViewportHeightPx).coerceAtLeast(0)
    val clampedTopCropPx = resolvedTopCropPx.coerceIn(0, maxTopCropPx)

    if (viewportHeightPx >= baseViewportHeightPx) {
        return CroppedVideoViewportPlacement(
            fullVideoHeightPx = safeFullVideoHeightPx,
            viewportHeightPx = viewportHeightPx,
            transformedLayerOffsetXPx = 0,
            transformedLayerOffsetYPx = if (cropSpec.isActive) -clampedTopCropPx else 0,
            transformedLayerScale = 1f
        )
    }

    val transformedLayerScale = viewportHeightPx.toFloat() / baseViewportHeightPx.toFloat()
    val transformedLayerOffsetXPx = (
        (safeFullVideoWidthPx - (safeFullVideoWidthPx * transformedLayerScale)) / 2f
        ).roundToInt()
    val transformedLayerOffsetYPx = if (cropSpec.isActive) {
        -(clampedTopCropPx * transformedLayerScale).roundToInt()
    } else {
        0
    }

    return CroppedVideoViewportPlacement(
        fullVideoHeightPx = safeFullVideoHeightPx,
        viewportHeightPx = viewportHeightPx,
        transformedLayerOffsetXPx = transformedLayerOffsetXPx,
        transformedLayerOffsetYPx = transformedLayerOffsetYPx,
        transformedLayerScale = transformedLayerScale
    )
}

@Composable
internal fun CroppedVideoViewport(
    cropSpec: VideoViewportCropSpec,
    fullVideoAspectRatio: Float,
    topCropPx: Float,
    viewportHeightOverridePx: Int? = null,
    modifier: Modifier = Modifier,
    onFullVideoSizeChanged: (IntSize) -> Unit = {},
    transformedLayer: @Composable BoxScope.() -> Unit,
    overlayLayer: @Composable BoxScope.() -> Unit = {}
) {
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            Box(
                modifier = Modifier
                    .layoutId(CROPPED_VIDEO_VIEWPORT_TRANSFORMED_LAYER_ID)
                    .onSizeChanged(onFullVideoSizeChanged),
                content = transformedLayer
            )
            Box(
                modifier = Modifier.layoutId(CROPPED_VIDEO_VIEWPORT_OVERLAY_LAYER_ID),
                content = overlayLayer
            )
        }
    ) { measurables, constraints ->
        val viewportWidthPx = constraints.maxWidth
            .takeIf { it != Constraints.Infinity && it > 0 }
            ?: constraints.minWidth
        if (viewportWidthPx <= 0) {
            return@Layout layout(0, 0) {}
        }

        val safeFullVideoAspectRatio = fullVideoAspectRatio.coerceAtLeast(MIN_VIDEO_ASPECT_RATIO)
        val fullVideoHeightPx = (viewportWidthPx / safeFullVideoAspectRatio)
            .roundToInt()
            .coerceAtLeast(1)
        val placement = calculateCroppedVideoViewportPlacement(
            fullVideoWidthPx = viewportWidthPx,
            fullVideoHeightPx = fullVideoHeightPx,
            cropSpec = cropSpec,
            topCropPx = topCropPx,
            viewportHeightOverridePx = viewportHeightOverridePx
        )

        val transformedMeasurable = measurables.first { measurable ->
            measurable.layoutId == CROPPED_VIDEO_VIEWPORT_TRANSFORMED_LAYER_ID
        }
        val overlayMeasurable = measurables.first { measurable ->
            measurable.layoutId == CROPPED_VIDEO_VIEWPORT_OVERLAY_LAYER_ID
        }
        val transformedPlaceable = transformedMeasurable.measure(
            Constraints.fixed(
                width = viewportWidthPx,
                height = placement.fullVideoHeightPx
            )
        )
        val overlayPlaceable = overlayMeasurable.measure(
            Constraints.fixed(
                width = viewportWidthPx,
                height = placement.viewportHeightPx
            )
        )

        layout(
            width = viewportWidthPx,
            height = placement.viewportHeightPx
        ) {
            transformedPlaceable.placeRelativeWithLayer(
                x = placement.transformedLayerOffsetXPx,
                y = placement.transformedLayerOffsetYPx
            ) {
                scaleX = placement.transformedLayerScale
                scaleY = placement.transformedLayerScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            overlayPlaceable.placeRelative(x = 0, y = 0)
        }
    }
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
    hiddenLandmarkIndices: Set<Int> = emptySet(),
    hiddenPointIndices: Set<Int> = emptySet(),
    pointRadiusScale: Float = 1f,
    showConnections: Boolean = true,
    showPoints: Boolean = true
) {
    Canvas(modifier = modifier) {
        if (contentRect.width <= 0f || contentRect.height <= 0f) return@Canvas

        val landmarksByIndex = pose.landmarks.associateBy { landmark -> landmark.index }
        val jointRadius = 4.dp.toPx() * pointRadiusScale.coerceAtLeast(0f)
        val strokeWidth = 2.dp.toPx()

        if (showConnections) {
            SHARED_POSE_CONNECTIONS.forEach { (startIndex, endIndex) ->
                if (startIndex in hiddenLandmarkIndices || endIndex in hiddenLandmarkIndices) {
                    return@forEach
                }
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
        }

        if (showPoints) {
            pose.landmarks.forEach { landmark ->
                if (landmark.index in hiddenLandmarkIndices || landmark.index in hiddenPointIndices) {
                    return@forEach
                }

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
}

@Composable
internal fun PoseVideoScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    markers: List<PoseScrubberMarker>,
    colors: PoseScrubberColors,
    trackAnchoredToBottom: Boolean = false,
    modifier: Modifier = Modifier,
    onTapSeek: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubStop: () -> Unit
) {
    val startFlag = ImageBitmap.imageResource(id = R.drawable.start_flag)
    val endFlag = ImageBitmap.imageResource(id = R.drawable.end_flag)
    val progress = if (durationMs > 0L) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val trackCanvasHeight = if (trackAnchoredToBottom) 44.dp else 32.dp

    @Composable
    fun ScrubberTrackCanvas() {
        var trackSize by remember { mutableStateOf(IntSize.Zero) }
        val density = LocalDensity.current
        val markerBadgeSizePx = with(density) { 16.dp.toPx() }
        val markerFlagSizePx = with(density) { 22.dp.toPx() }
        val markerLineGapPx = with(density) { 2.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackCanvasHeight)
                .onSizeChanged { trackSize = it }
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
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val trackHeight = 4.dp.toPx()
                val thumbRadius = 7.dp.toPx()
                val centerY = if (trackAnchoredToBottom) {
                    size.height - thumbRadius - 2.dp.toPx()
                } else {
                    size.height / 2f
                }

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
                        val fillColor = colors.progressColor.copy(
                            alpha = if (marker.isSelected) 1f else 0.58f
                        )
                        val markerHalfHeightPx = when (marker.kind) {
                            AnalysisPointKind.PERSON_OBSERVATION_START,
                            AnalysisPointKind.CLIMB_END -> markerFlagSizePx / 2f

                            else -> markerBadgeSizePx / 2f
                        }

                        if (
                            marker.kind != AnalysisPointKind.PERSON_OBSERVATION_START &&
                            marker.kind != AnalysisPointKind.CLIMB_END
                        ) {
                            drawLine(
                                color = fillColor,
                                start = Offset(markerX, markerCenterY + markerHalfHeightPx + markerLineGapPx),
                                end = Offset(markerX, centerY - trackHeight),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                drawCircle(
                    color = colors.thumbColor.copy(alpha = if (enabled) 1f else 0.55f),
                    radius = thumbRadius,
                    center = Offset(size.width * progress, centerY)
                )
            }

            if (trackSize.width > 0 && durationMs > 0L) {
                val thumbRadiusPx = with(density) { 7.dp.toPx() }
                val centerY = if (trackAnchoredToBottom) {
                    trackSize.height.toFloat() - thumbRadiusPx - with(density) { 2.dp.toPx() }
                } else {
                    trackSize.height / 2f
                }
                val markerCenterY = centerY - with(density) { 12.dp.toPx() }

                markers
                    .distinctBy { marker -> marker.index to marker.timeMs }
                    .forEach { marker ->
                        val markerX = trackSize.width * markerPositionFraction(marker.timeMs, durationMs)
                        when (marker.kind) {
                            AnalysisPointKind.PERSON_OBSERVATION_START -> {
                                ScrubberFlagMarker(
                                    image = startFlag,
                                    contentDescription = "Start marker",
                                    centerX = markerX,
                                    centerY = markerCenterY,
                                    sizePx = markerFlagSizePx,
                                    isSelected = marker.isSelected,
                                    flagAnchorFractionX = marker.flagAnchorFractionX
                                )
                            }

                            AnalysisPointKind.CLIMB_END -> {
                                ScrubberFlagMarker(
                                    image = endFlag,
                                    contentDescription = "End marker",
                                    centerX = markerX,
                                    centerY = markerCenterY,
                                    sizePx = markerFlagSizePx,
                                    isSelected = marker.isSelected,
                                    flagAnchorFractionX = marker.flagAnchorFractionX
                                )
                            }

                            else -> {
                                ScrubberNumberMarker(
                                    marker = marker,
                                    colors = colors,
                                    centerX = markerX,
                                    centerY = markerCenterY,
                                    sizePx = markerBadgeSizePx
                                )
                            }
                        }
                    }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (trackAnchoredToBottom) {
            ScrubberTrackCanvas()
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
        } else {
            ScrubberTrackCanvas()
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
}

@Composable
private fun BoxScope.ScrubberFlagMarker(
    image: ImageBitmap,
    contentDescription: String,
    centerX: Float,
    centerY: Float,
    sizePx: Float,
    isSelected: Boolean,
    flagAnchorFractionX: Float = 0.5f
) {
    val clampedAnchorFractionX = flagAnchorFractionX.coerceIn(0f, 1f)
    Image(
        bitmap = image,
        contentDescription = contentDescription,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (centerX - (sizePx * clampedAnchorFractionX)).roundToInt(),
                    y = (centerY - (sizePx / 2f)).roundToInt()
                )
            }
            .size(with(LocalDensity.current) { sizePx.toDp() })
            .alpha(if (isSelected) 1f else 0.72f)
    )
}

@Composable
private fun BoxScope.ScrubberNumberMarker(
    marker: PoseScrubberMarker,
    colors: PoseScrubberColors,
    centerX: Float,
    centerY: Float,
    sizePx: Float
) {
    val fillColor = colors.progressColor.copy(alpha = if (marker.isSelected) 1f else 0.58f)
    val textColor = if (fillColor.luminance() > 0.45f) {
        Color.Black
    } else {
        Color.White
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (centerX - (sizePx / 2f)).roundToInt(),
                    y = (centerY - (sizePx / 2f)).roundToInt()
                )
            }
            .size(with(LocalDensity.current) { sizePx.toDp() })
            .background(color = fillColor, shape = CircleShape)
    ) {
        Text(
            text = marker.index.toString(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private const val DEFAULT_VIDEO_ASPECT_RATIO = 9f / 16f
private const val FULL_VIDEO_VISIBLE_HEIGHT_EPSILON = 0.999f
private const val RAW_CROP_SAMPLE_COUNT = 5
private const val MIN_VIDEO_ASPECT_RATIO = 0.0001f
private const val CROPPED_VIDEO_VIEWPORT_TRANSFORMED_LAYER_ID = "cropped_video_viewport_transformed"
private const val CROPPED_VIDEO_VIEWPORT_OVERLAY_LAYER_ID = "cropped_video_viewport_overlay"
