package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.record.presentation.HeartRatePoint
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import kotlin.math.roundToInt

private data class HeartRateAxisState(
    val points: List<HeartRatePoint>,
    val minBpm: Int,
    val maxBpm: Int
)

@Composable
internal fun StabilityInsightTimelineChart(
    data: List<Float>,
    durationMs: Long,
    dangerFractions: List<Float>,
    cruxStartFraction: Float?,
    cruxEndFraction: Float?,
    failureFraction: Float?,
    heartRateSeries: List<HeartRatePoint> = emptyList(),
    onTimeSelected: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "stability_insight_chart_progress"
    )
    val heartRateAxisState = remember(heartRateSeries, durationMs) {
        buildHeartRateAxisState(
            heartRateSeries = heartRateSeries,
            durationMs = durationMs
        )
    }

    LaunchedEffect(data, heartRateSeries) {
        progress = 1f
    }

    val chartHorizontalPadding = 8.dp
    val axisLabelWidth = 18.dp
    val axisLabelEndPadding = 0.dp

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(axisLabelWidth)
                    .height(232.dp)
                    .padding(top = 16.dp, bottom = 16.dp, end = axisLabelEndPadding),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "안정",
                    color = AnalysisMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible
                )

                Text(
                    text = "불안",
                    color = AnalysisMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(232.dp)
                    .then(
                        if (onTimeSelected != null && durationMs > 0L) {
                            Modifier.pointerInput(durationMs, onTimeSelected) {
                                detectTapGestures { offset ->
                                    val horizontalPaddingPx = chartHorizontalPadding.toPx()
                                    val chartWidthPx = (size.width - horizontalPaddingPx * 2f)
                                        .coerceAtLeast(1f)
                                    val fraction = ((offset.x - horizontalPaddingPx) / chartWidthPx)
                                        .coerceIn(0f, 1f)
                                    onTimeSelected(
                                        (durationMs.toFloat() * fraction).roundToInt().toLong()
                                    )
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (data.size < 2) return@Canvas

                    val topPadding = 20.dp.toPx()
                    val bottomPadding = 12.dp.toPx()
                    val horizontalPadding = chartHorizontalPadding.toPx()
                    val chartWidth = size.width - horizontalPadding * 2f
                    val chartHeight = size.height - topPadding - bottomPadding
                    val visibleCount =
                        (data.size * animatedProgress).roundToInt().coerceIn(2, data.size)

                    fun xOfFraction(fraction: Float): Float =
                        horizontalPadding + chartWidth * fraction.coerceIn(0f, 1f)

                    fun xOf(index: Int): Float =
                        horizontalPadding +
                            index.toFloat() / (data.size - 1).toFloat() * chartWidth

                    fun yOf(value: Float): Float =
                        topPadding + chartHeight * (1f - value.coerceIn(0f, 1f))

                    cruxStartFraction?.let { start ->
                        cruxEndFraction?.let { end ->
                            if (end > start) {
                                drawRoundRect(
                                    color = Color(0xFFFFB357).copy(alpha = 0.14f),
                                    topLeft = Offset(xOfFraction(start), topPadding),
                                    size = Size(
                                        width = xOfFraction(end) - xOfFraction(start),
                                        height = chartHeight
                                    ),
                                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                                )
                            }
                        }
                    }

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.03f),
                        topLeft = Offset(horizontalPadding, topPadding),
                        size = Size(chartWidth, chartHeight),
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
                    )

                    repeat(2) { index ->
                        val y = topPadding + chartHeight * ((index + 1) / 3f)
                        drawLine(
                            color = Color.White.copy(alpha = 0.07f),
                            start = Offset(horizontalPadding, y),
                            end = Offset(size.width - horizontalPadding, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val fillPath = Path().apply {
                        moveTo(xOf(0), yOf(data[0]))
                        for (index in 1 until visibleCount) {
                            lineTo(xOf(index), yOf(data[index]))
                        }
                        lineTo(xOf(visibleCount - 1), size.height - bottomPadding)
                        lineTo(xOf(0), size.height - bottomPadding)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AnalysisPrimary.copy(alpha = 0.22f),
                                AnalysisPrimary.copy(alpha = 0.03f)
                            ),
                            startY = topPadding,
                            endY = size.height - bottomPadding
                        )
                    )

                    val linePath = Path().apply {
                        moveTo(xOf(0), yOf(data[0]))
                        for (index in 1 until visibleCount) {
                            val previousX = xOf(index - 1)
                            val previousY = yOf(data[index - 1])
                            val currentX = xOf(index)
                            val currentY = yOf(data[index])
                            val controlX = (previousX + currentX) / 2f
                            cubicTo(controlX, previousY, controlX, currentY, currentX, currentY)
                        }
                    }

                    drawPath(
                        path = linePath,
                        brush = AnalysisBrandAccentBrush,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )

                    if (heartRateAxisState != null && heartRateAxisState.points.size >= 2) {
                        val visibleDurationMs =
                            (durationMs.toFloat() * animatedProgress).roundToInt().toLong()
                                .coerceAtLeast(1L)
                        val visibleHeartRatePoints = heartRateAxisState.points
                            .filter { it.timestampMs <= visibleDurationMs }
                            .ifEmpty { heartRateAxisState.points.take(1) }

                        if (visibleHeartRatePoints.size >= 2) {
                            fun heartRateXOf(point: HeartRatePoint): Float {
                                val fraction = if (durationMs > 0L) {
                                    point.timestampMs.toFloat() / durationMs.toFloat()
                                } else {
                                    0f
                                }
                                return xOfFraction(fraction)
                            }

                            fun heartRateYOf(bpm: Int): Float {
                                val range = (heartRateAxisState.maxBpm - heartRateAxisState.minBpm)
                                    .coerceAtLeast(1)
                                    .toFloat()
                                val fraction =
                                    ((bpm - heartRateAxisState.minBpm).toFloat() / range)
                                        .coerceIn(0f, 1f)
                                return topPadding + chartHeight * (1f - fraction)
                            }

                            val heartRatePath = Path().apply {
                                moveTo(
                                    heartRateXOf(visibleHeartRatePoints.first()),
                                    heartRateYOf(visibleHeartRatePoints.first().bpm)
                                )
                                for (index in 1 until visibleHeartRatePoints.size) {
                                    val previousPoint = visibleHeartRatePoints[index - 1]
                                    val currentPoint = visibleHeartRatePoints[index]
                                    val previousX = heartRateXOf(previousPoint)
                                    val previousY = heartRateYOf(previousPoint.bpm)
                                    val currentX = heartRateXOf(currentPoint)
                                    val currentY = heartRateYOf(currentPoint.bpm)
                                    val controlX = (previousX + currentX) / 2f
                                    cubicTo(controlX, previousY, controlX, currentY, currentX, currentY)
                                }
                            }

                            drawPath(
                                path = heartRatePath,
                                color = AnalysisFailure,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    dangerFractions.forEach { fraction ->
                        val value = valueAtFraction(data, fraction)
                        val center = Offset(xOfFraction(fraction), yOf(value))
                        drawLine(
                            color = AnalysisFailure.copy(alpha = 0.25f),
                            start = Offset(center.x, center.y),
                            end = Offset(center.x, size.height - bottomPadding),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawCircle(
                            color = AnalysisFailure.copy(alpha = 0.22f),
                            radius = 10.dp.toPx(),
                            center = center
                        )
                        drawCircle(
                            color = AnalysisFailure,
                            radius = 5.dp.toPx(),
                            center = center
                        )
                    }

                    failureFraction?.let { fraction ->
                        val x = xOfFraction(fraction)
                        drawLine(
                            color = AnalysisFailure.copy(alpha = 0.85f),
                            start = Offset(x, topPadding),
                            end = Offset(x, topPadding + chartHeight),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
                if (cruxStartFraction != null && cruxEndFraction != null) {
                    val estimatedCruxLabelWidth = 42.dp
                    val chartWidth = (maxWidth - chartHorizontalPadding * 2).coerceAtLeast(0.dp)
                    val centerFraction = ((cruxStartFraction + cruxEndFraction) / 2f).coerceIn(0f, 1f)
                    val unclampedOffset =
                        chartHorizontalPadding + (chartWidth * centerFraction) - (estimatedCruxLabelWidth / 2)
                    val maxOffset =
                        (maxWidth - chartHorizontalPadding - estimatedCruxLabelWidth)
                            .coerceAtLeast(chartHorizontalPadding)
                    val clampedOffset = unclampedOffset.coerceIn(chartHorizontalPadding, maxOffset)

                    Text(
                        text = "크럭스",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = clampedOffset, y = (-6).dp),
                        color = Color(0xFFFFC271),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                heartRateAxisState?.let { axis ->
                    Text(
                        text = "${axis.maxBpm} bpm",
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 28.dp),
                        color = AnalysisFailure,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${axis.minBpm} bpm",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 16.dp),
                        color = AnalysisFailure.copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(axisLabelWidth))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartTimeLabel(
                    text = formatTimeLabel(0L),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Text(
                    text = formatTimeLabel(durationMs / 2),
                    modifier = Modifier.weight(1f),
                    color = AnalysisMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                ChartTimeLabel(
                    text = formatTimeLabel(durationMs),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End
                )
        }
    }

}
}

@Composable
private fun ChartTimeLabel(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Text(
        text = text,
        modifier = modifier,
        color = AnalysisMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textAlign = textAlign
    )
}

private fun valueAtFraction(data: List<Float>, fraction: Float): Float {
    if (data.isEmpty()) return 0.5f
    val clamped = fraction.coerceIn(0f, 1f)
    val rawIndex = clamped * data.lastIndex.toFloat()
    val startIndex = rawIndex.toInt().coerceIn(0, data.lastIndex)
    val endIndex = (startIndex + 1).coerceIn(0, data.lastIndex)
    if (startIndex == endIndex) return data[startIndex]

    val localFraction = rawIndex - startIndex
    return data[startIndex] + (data[endIndex] - data[startIndex]) * localFraction
}

private fun formatTimeLabel(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun buildHeartRateAxisState(
    heartRateSeries: List<HeartRatePoint>,
    durationMs: Long
): HeartRateAxisState? {
    val filteredPoints = heartRateSeries
        .filter { point ->
            point.bpm > 0 &&
                point.timestampMs >= 0L &&
                (durationMs <= 0L || point.timestampMs <= durationMs)
        }
        .sortedBy(HeartRatePoint::timestampMs)
        .distinctBy(HeartRatePoint::timestampMs)

    if (filteredPoints.isEmpty()) return null

    val rawMinBpm = filteredPoints.minOf(HeartRatePoint::bpm)
    val rawMaxBpm = filteredPoints.maxOf(HeartRatePoint::bpm)
    val padding = if (rawMinBpm == rawMaxBpm) {
        5
    } else {
        ((rawMaxBpm - rawMinBpm) * 0.12f).roundToInt().coerceAtLeast(4)
    }

    return HeartRateAxisState(
        points = filteredPoints,
        minBpm = (rawMinBpm - padding).coerceAtLeast(40),
        maxBpm = (rawMaxBpm + padding).coerceAtMost(220)
    )
}
