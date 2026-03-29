package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal val AnalysisBgColor = Color(0xFF171717)
internal val AnalysisPanelColor = Color(0xFF212121)
internal val AnalysisCardColor = Color(0xFF2A2A2A)
internal val AnalysisDividerColor = Color(0xFF303030)
internal val AnalysisPrimary = Color(0xFF139CFF)
internal val AnalysisSecondary = Color(0xFF8B5CFF)
internal val AnalysisMuted = Color(0xFF8C8C8C)
internal val AnalysisText = Color(0xFFF5F5F5)
internal val AnalysisSuccess = Color(0xFF4C88FF)
internal val AnalysisFailure = Color(0xFFFF575F)

internal data class AnalysisAttemptSummary(
    val attemptNo: Int,
    val isSuccess: Boolean,
    val analysisPoints: List<AnalysisPoint>,
    val reachedHolds: Int,
    val balanceRatio: Int,
    val stabilityTimeline: List<Float>,
    val missionLines: List<String>
)

internal fun buildAttemptSummaries(
    attemptResults: List<Pair<Boolean, List<AnalysisPoint>>>,
    totalHolds: Int,
    holdReachResults: List<AttemptHoldReachResult> = emptyList()
): List<AnalysisAttemptSummary> {
    val attemptSize = max(max(attemptResults.size, holdReachResults.size), 1)
    val safeTotalHolds = totalHolds.coerceAtLeast(1)
    val safeAttemptResults = attemptResults.ifEmpty {
        listOf(false to defaultAnalysisPoints())
    }

    return List(attemptSize) { index ->
        val seed = safeAttemptResults[index % safeAttemptResults.size]
        val points = seed.second.takeIf { it.isNotEmpty() } ?: defaultAnalysisPoints()
        val holdReachResult = holdReachResults.getOrNull(index)
        val isSuccess = holdReachResult?.completedWithBothHandsOnEndHold ?:
            seed.first
        val progressBase = when {
            isSuccess -> 0.88f
            attemptSize == 1 -> 0.64f
            else -> (0.52f + (index / attemptSize.toFloat()) * 0.2f).coerceAtMost(0.82f)
        }
        val reachedHolds = holdReachResult
            ?.highestReachedHoldNo
            ?.coerceIn(0, safeTotalHolds)
            ?: (safeTotalHolds * progressBase).roundToInt().coerceIn(1, safeTotalHolds)
        val balanceRatio = when {
            isSuccess -> 82
            else -> (58 + index * 7 + points.size * 3).coerceAtMost(76)
        }

        AnalysisAttemptSummary(
            attemptNo = index + 1,
            isSuccess = isSuccess,
            analysisPoints = points,
            reachedHolds = reachedHolds,
            balanceRatio = balanceRatio,
            stabilityTimeline = generateStabilityTimeline(
                attemptNo = index + 1,
                isSuccess = isSuccess,
                points = points
            ),
            missionLines = buildMissionLines(points)
        )
    }
}

private fun defaultAnalysisPoints(): List<AnalysisPoint> = listOf(
    AnalysisPoint(1, 21_000L, "2지점 상태가 길었어요"),
    AnalysisPoint(2, 48_000L, "오른쪽 팔에 과도한 무게가 실렸어요"),
    AnalysisPoint(3, 66_000L, "무게 이동이 늦어졌어요")
)

internal fun buildMissionLines(points: List<AnalysisPoint>): List<String> {
    val suggestions = buildList {
        if (points.any { it.description.contains("상태") }) {
            add("정체에서 3지점 만든 뒤 다음 동작 하기")
        }
        if (points.any { it.description.contains("무게") || it.description.contains("팔") }) {
            add("리치 전에 발을 한 칸 올리고 손 뻗기")
        }
        if (points.any { it.description.contains("이동") || it.description.contains("늦") }) {
            add("힙을 벽에 붙이고 다리로 일어서기")
        }
    }

    return (suggestions + listOf(
        "시선 먼저 보내고 손보다 발 중심으로 움직이기",
        "불안정한 구간은 호흡 한 번 정리하고 다음 홀드 보기"
    )).distinct().take(3)
}

internal fun generateStabilityTimeline(
    attemptNo: Int,
    isSuccess: Boolean,
    points: List<AnalysisPoint>,
    sampleCount: Int = 28
): List<Float> {
    val impactFractions = points
        .filter { it.timeMs > 0L }
        .map { (it.timeMs / 70_000f).coerceIn(0.08f, 0.92f) }

    return List(sampleCount) { index ->
        val t = if (sampleCount == 1) 0f else index / (sampleCount - 1).toFloat()
        val base = 0.48f +
            0.12f * sin((t * PI * 2.2f + attemptNo * 0.35f).toFloat()) +
            0.08f * cos((t * PI * 5.1f).toFloat())
        val dips = impactFractions.sumOf { fraction ->
            val distance = kotlin.math.abs(t - fraction)
            (0.2f * (1f - (distance / 0.14f).coerceAtMost(1f))).toDouble()
        }.toFloat()
        val finishingLift = if (isSuccess) 0.18f * t else 0.04f * (1f - t)
        (base - dips + finishingLift).coerceIn(0.12f, 0.96f)
    }
}

internal fun formatAnalysisDate(rawDateTime: String?): String {
    val parsedDate = parseDate(rawDateTime) ?: LocalDate.now()
    return "${parsedDate.year}년 ${parsedDate.monthValue}월 ${parsedDate.dayOfMonth}일"
}

private fun parseDate(rawDateTime: String?): LocalDate? {
    if (rawDateTime.isNullOrBlank()) return null

    return try {
        LocalDate.parse(rawDateTime)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(rawDateTime).toLocalDate()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(rawDateTime).toLocalDate()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}

internal fun buildMissionText(line: String): AnnotatedString {
    val highlightTargets = listOf("3지점", "발", "다리", "힙", "호흡")
    return AnnotatedString.Builder().apply {
        var cursor = 0
        while (cursor < line.length) {
            val match = highlightTargets
                .mapNotNull { target ->
                    val index = line.indexOf(target, startIndex = cursor)
                    if (index >= 0) target to index else null
                }
                .minByOrNull { it.second }

            if (match == null) {
                append(line.substring(cursor))
                break
            }

            val (target, start) = match
            if (start > cursor) {
                append(line.substring(cursor, start))
            }
            withStyle(
                SpanStyle(
                    color = AnalysisPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(target)
            }
            cursor = start + target.length
        }
    }.toAnnotatedString()
}

internal fun buildLevelLabels(level: String, increment: Int): Triple<String, String, String> {
    val trimmedLevel = level.trim()
    val match = Regex("""[Vv](\d+)""").find(trimmedLevel)
    return if (match != null) {
        val currentValue = match.groupValues[1].toIntOrNull() ?: 0
        Triple("LV$currentValue", "LV${currentValue + 1}", "+$increment")
    } else {
        Triple(trimmedLevel.ifBlank { "현재" }, "다음", "+$increment")
    }
}

@Composable
internal fun AnalysisSectionTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelected(index) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (index == selectedIndex) AnalysisText else AnalysisMuted
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(AnalysisDividerColor)
    ) {
        labels.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(
                        if (index == selectedIndex) AnalysisPrimary else Color.Transparent
                    )
            )
        }
    }
}

@Composable
internal fun AnalysisGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val brush = Brush.horizontalGradient(
        colors = listOf(AnalysisPrimary, AnalysisSecondary)
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) brush else Brush.horizontalGradient(listOf(Color(0xFF474747), Color(0xFF474747))))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun HoldOverviewPreview(
    bitmap: Bitmap?,
    holds: List<Hold>,
    modifier: Modifier = Modifier,
    showZoomBadge: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF101114))
    ) {
        when {
            holds.isNotEmpty() -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF191A1F), Color(0xFF101114))
                        )
                    )

                    val finishHold = holds.minByOrNull { it.boundingBox.top }
                    holds.forEach { hold ->
                        val polygon = hold.polygon.map { Offset(it.x * size.width, it.y * size.height) }
                        val holdColor = holdLabelToComposeColor(hold.colorLabel)

                        if (polygon.size >= 3) {
                            val path = Path().apply {
                                moveTo(polygon.first().x, polygon.first().y)
                                polygon.drop(1).forEach { point -> lineTo(point.x, point.y) }
                                close()
                            }
                            drawPath(path = path, color = holdColor.copy(alpha = 0.92f))
                            drawPath(
                                path = path,
                                color = Color.White.copy(alpha = 0.16f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        } else {
                            val rect = hold.toScreenRect(0f, 0f, size.width, size.height)
                            drawRoundRect(
                                color = holdColor,
                                topLeft = Offset(rect.l, rect.t),
                                size = Size(rect.r - rect.l, rect.b - rect.t),
                                cornerRadius = CornerRadius(18f, 18f)
                            )
                        }

                        if (hold == finishHold) {
                            val rect = hold.toScreenRect(0f, 0f, size.width, size.height)
                            val poleX = rect.r - 8.dp.toPx()
                            val poleTop = rect.t - 18.dp.toPx()
                            val poleBottom = rect.t + 8.dp.toPx()
                            drawLine(
                                color = Color.White,
                                start = Offset(poleX, poleBottom),
                                end = Offset(poleX, poleTop),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                            val flagPath = Path().apply {
                                moveTo(poleX, poleTop)
                                lineTo(poleX + 14.dp.toPx(), poleTop + 5.dp.toPx())
                                lineTo(poleX, poleTop + 10.dp.toPx())
                                close()
                            }
                            drawPath(flagPath, color = AnalysisFailure)
                        }
                    }
                }
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawHoldBlob(
                        color = Color(0xFFF16698),
                        center = center.copy(x = center.x - size.width * 0.16f, y = center.y + size.height * 0.04f),
                        width = size.width * 0.42f,
                        height = size.height * 0.28f,
                        rotation = -18f
                    )
                    drawHoldBlob(
                        color = Color(0xFF8B5CFF),
                        center = center.copy(x = center.x + size.width * 0.18f, y = center.y - size.height * 0.12f),
                        width = size.width * 0.34f,
                        height = size.height * 0.22f,
                        rotation = 14f
                    )
                }
            }
        }

        if (showZoomBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHoldBlob(
    color: Color,
    center: Offset,
    width: Float,
    height: Float,
    rotation: Float
) {
    rotate(degrees = rotation, pivot = center) {
        val left = center.x - width / 2f
        val top = center.y - height / 2f

        val path = Path().apply {
            moveTo(left + width * 0.5f, top + height * 0.06f)
            cubicTo(left + width * 0.88f, top, left + width, top + height * 0.36f, left + width * 0.84f, top + height * 0.7f)
            cubicTo(left + width * 0.69f, top + height, left + width * 0.28f, top + height * 1.02f, left + width * 0.12f, top + height * 0.78f)
            cubicTo(left - width * 0.02f, top + height * 0.58f, left + width * 0.06f, top + height * 0.2f, left + width * 0.22f, top + height * 0.1f)
            close()
        }
        drawPath(path = path, color = color)
        drawPath(
            path = Path().apply {
                moveTo(left + width * 0.34f, top + height * 0.12f)
                cubicTo(left + width * 0.48f, top + height * 0.02f, left + width * 0.58f, top + height * 0.12f, left + width * 0.44f, top + height * 0.22f)
                close()
            },
            color = Color.White.copy(alpha = 0.2f)
        )
    }
}

@Composable
internal fun AttemptChipRow(
    attemptCount: Int,
    selectedAttempt: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(attemptCount.coerceAtLeast(1)) { index ->
            val attemptNo = index + 1
            val isSelected = attemptNo == selectedAttempt
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Color(0xFFFFD24D) else Color.Transparent
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(attemptNo) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${attemptNo}차",
                    color = if (isSelected) Color.Black else AnalysisMuted,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
internal fun StabilityLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier,
    focusFraction: Float? = null
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 900),
        label = "analysis_chart_progress"
    )

    LaunchedEffect(data) {
        progress = 1f
    }

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val topPadding = 12.dp.toPx()
        val bottomPadding = 12.dp.toPx()
        val chartHeight = size.height - topPadding - bottomPadding
        val bandHeight = chartHeight / 3f
        val visibleCount = (data.size * animatedProgress).roundToInt().coerceIn(2, data.size)
        val minValue = data.min()
        val maxValue = data.max()
        val valueRange = (maxValue - minValue).coerceAtLeast(0.001f)

        fun xOf(index: Int): Float = index.toFloat() / (data.size - 1).toFloat() * size.width
        fun yOf(value: Float): Float =
            topPadding + chartHeight * (1f - ((value - minValue) / valueRange))

        drawRoundRect(
            color = AnalysisSuccess.copy(alpha = 0.08f),
            topLeft = Offset(0f, topPadding),
            size = Size(size.width, bandHeight),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )
        drawRect(
            color = Color.White.copy(alpha = 0.02f),
            topLeft = Offset(0f, topPadding + bandHeight),
            size = Size(size.width, bandHeight)
        )
        drawRoundRect(
            color = AnalysisFailure.copy(alpha = 0.08f),
            topLeft = Offset(0f, topPadding + bandHeight * 2f),
            size = Size(size.width, bandHeight),
            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
        )

        repeat(3) { lineIndex ->
            val y = topPadding + chartHeight * (lineIndex / 2f)
            drawLine(
                color = Color.White.copy(alpha = 0.06f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        focusFraction?.let { fraction ->
            val spotlightX = size.width * fraction.coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.White.copy(alpha = 0.18f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                topLeft = Offset(spotlightX - 16.dp.toPx(), topPadding),
                size = Size(32.dp.toPx(), chartHeight),
                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
            )
        }

        val primaryPath = Path().apply {
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

        val smoothed = data.mapIndexed { index, _ ->
            val from = (index - 2).coerceAtLeast(0)
            val to = (index + 2).coerceAtMost(data.lastIndex)
            data.subList(from, to + 1).average().toFloat()
        }
        val secondaryPath = Path().apply {
            moveTo(xOf(0), yOf(smoothed[0]))
            for (index in 1 until visibleCount) {
                val previousX = xOf(index - 1)
                val previousY = yOf(smoothed[index - 1])
                val currentX = xOf(index)
                val currentY = yOf(smoothed[index])
                val controlX = (previousX + currentX) / 2f
                cubicTo(controlX, previousY, controlX, currentY, currentX, currentY)
            }
        }

        drawPath(
            path = primaryPath,
            color = AnalysisPrimary,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = secondaryPath,
            color = AnalysisSecondary,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        val lastIndex = visibleCount - 1
        val markerX = xOf(lastIndex)
        val markerY = yOf(smoothed[lastIndex])
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(markerX, markerY)
        )
        drawCircle(
            color = AnalysisSecondary,
            radius = 3.dp.toPx(),
            center = Offset(markerX, markerY)
        )
    }
}
