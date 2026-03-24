package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import kotlin.math.roundToInt

@Composable
internal fun StabilityInsightTimelineChart(
    data: List<Float>,
    durationMs: Long,
    dangerFractions: List<Float>,
    cruxStartFraction: Float?,
    cruxEndFraction: Float?,
    failureFraction: Float?,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "stability_insight_chart_progress"
    )

    LaunchedEffect(data) {
        progress = 1f
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                if (data.size < 2) return@Canvas

                val topPadding = 20.dp.toPx()
                val bottomPadding = 12.dp.toPx()
                val horizontalPadding = 8.dp.toPx()
                val chartWidth = size.width - horizontalPadding * 2f
                val chartHeight = size.height - topPadding - bottomPadding
                val visibleCount = (data.size * animatedProgress).roundToInt().coerceIn(2, data.size)

                fun xOfFraction(fraction: Float): Float =
                    horizontalPadding + chartWidth * fraction.coerceIn(0f, 1f)

                fun xOf(index: Int): Float =
                    horizontalPadding + index.toFloat() / (data.size - 1).toFloat() * chartWidth

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
                    color = AnalysisPrimary,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

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

            Text(
                text = "안정",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 8.dp),
                color = AnalysisText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "흔들림",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 16.dp),
                color = AnalysisMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            if (cruxStartFraction != null && cruxEndFraction != null) {
                Text(
                    text = "크럭스",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 12.dp, top = 8.dp),
                    color = Color(0xFFFFC271),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 0.dp, end = 8.dp),
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
