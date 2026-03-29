package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.analysis.mapper.AnalysisFormatters
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisGrowthSummaryUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTrendPointUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

@Composable
internal fun AnalysisGrowthSection(
    summary: AnalysisGrowthSummaryUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AnalysisSectionTitle(title = summary.title)

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = summary.headline
                        .replace("안정률이", "안정 점수가")
                        .replace("%", "점"),
                    style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 34.sp),
                    color = AnalysisPalette.TextPrimary
                )
            }

            GrowthVisualBoard(summary = summary)
            GrowthTrendChart(points = summary.trendPoints)

            if (summary.trendBadges.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AnalysisPalette.Border)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                summary.metrics.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowItems.forEach { stat ->
                            AnalysisMiniStatCard(
                                label = stat.label,
                                value = stat.value,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowthTrendSummaryRow(
    badges: List<AnalysisBadgeUiModel>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEachIndexed { index, badge ->
            val displayLabel = badge.label
                .replace("안정률", "안정 점수")
                .replace("%", "점")

            Text(
                text = displayLabel,
                modifier = Modifier.weight(1f),
                color = growthBadgeTextColor(badge.tone),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (index != badges.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(AnalysisPalette.Border)
                )
            }
        }
    }
}

private fun growthBadgeTextColor(tone: AnalysisBadgeTone): Color {
    return when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextSecondary
    }
}

@Composable
private fun GrowthVisualBoard(
    summary: AnalysisGrowthSummaryUiModel
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AnalysisPalette.SurfaceMuted
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularGrowthGauge(
                    score = summary.stabilityScore,
                    title = "평균 안정성",
                    valueLabel = "${(summary.stabilityScore * 100f).toInt()}점"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GrowthProgressBar(
                        label = "완등 챌린지",
                        value = summary.completionScore,
                        valueLabel = AnalysisFormatters.formatPercent(summary.completionScore),
                        fill = SolidColor(AnalysisPalette.Success)
                    )
                    GrowthProgressBar(
                        label = "평균 하체 주도성",
                        value = summary.lowerBodyDriveProgress,
                        valueLabel = if (summary.averageLowerBodyDriveScore > 0f) {
                            "${summary.averageLowerBodyDriveScore.toInt()}점"
                        } else {
                            "-"
                        },
                        fill = SolidColor(AnalysisPalette.WarningBright)
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularGrowthGauge(
    score: Float,
    title: String,
    valueLabel: String
) {
    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 12.dp.toPx()
            drawArc(
                color = AnalysisPalette.Border,
                startAngle = 145f,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = AnalysisPalette.AccentStrong,
                startAngle = 145f,
                sweepAngle = 250f * score.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleLarge,
                color = AnalysisPalette.TextPrimary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = AnalysisPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun GrowthProgressBar(
    label: String,
    value: Float,
    valueLabel: String,
    fill: Brush
) {
    val progress = value.coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = AnalysisPalette.TextSecondary
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = AnalysisPalette.TextPrimary
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(AnalysisPalette.Border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fill)
            )
        }
    }
}

@Composable
private fun GrowthTrendChart(
    points: List<AnalysisTrendPointUiModel>
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = AnalysisPalette.SurfaceMuted
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "최근 평균 안정률 변화",
                style = MaterialTheme.typography.titleMedium,
                color = AnalysisPalette.TextPrimary
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (points.isEmpty()) return@Canvas

                val minValue = 0f
                val maxValue = 1f
                val range = 1f
                val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)

                val offsets = points.mapIndexed { index, point ->
                    val progress = ((point.value - minValue) / range).coerceIn(0f, 1f)
                    Offset(
                        x = stepX * index,
                        y = size.height - (progress * (size.height - 18.dp.toPx())) - 9.dp.toPx()
                    )
                }

                drawLine(
                    color = AnalysisPalette.Border,
                    start = Offset(0f, size.height - 6.dp.toPx()),
                    end = Offset(size.width, size.height - 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )

                offsets.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = AnalysisPalette.AccentStrong,
                        start = start,
                        end = end,
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                offsets.forEachIndexed { index, offset ->
                    val point = points[index]
                    drawCircle(
                        color = if (point.highlight) {
                            AnalysisPalette.AccentStrong
                        } else {
                            AnalysisPalette.Accent.copy(alpha = 0.7f)
                        },
                        radius = if (point.highlight) 6.dp.toPx() else 4.5.dp.toPx(),
                        center = offset
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.forEach { point ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(if (point.highlight) 8.dp else 6.dp),
                                shape = CircleShape,
                                color = if (point.highlight) {
                                    AnalysisPalette.AccentStrong
                                } else {
                                    AnalysisPalette.Accent.copy(alpha = 0.55f)
                                }
                            ) {}
                        }
                        Text(
                            text = AnalysisFormatters.formatPercent(point.value),
                            style = MaterialTheme.typography.labelLarge,
                            color = AnalysisPalette.TextPrimary
                        )
                        Text(
                            text = point.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = AnalysisPalette.TextHint
                        )
                    }
                }
            }
        }
    }
}
