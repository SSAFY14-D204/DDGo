package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.model.AnalysisGrowthSummaryUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTrendPointUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 전체 챌린지/시도를 바탕으로 만든 성장 요약 섹션입니다.
 *
 * 역할:
 * - 사용자가 메인 분석 탭에 들어왔을 때 최근 변화가 즉시 눈에 들어오게 만듭니다.
 * - 텍스트뿐 아니라 게이지, 비교 바, 추세 그래프로 성장 흐름을 직관적으로 보여줍니다.
 */
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
                    text = summary.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AnalysisPalette.TextPrimary
                )
            }

            GrowthVisualBoard(summary = summary)
            GrowthTrendChart(points = summary.trendPoints)

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                summary.trendBadges.forEach { badge ->
                    AnalysisBadge(badge = badge)
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                summary.metrics.forEach { stat ->
                    AnalysisMiniStatCard(
                        label = stat.label,
                        value = stat.value,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 성장 카드 안에서 가장 먼저 눈에 들어오는 시각 요약 보드입니다.
 *
 * 역할:
 * - 평균 안정성은 원형 게이지로, 완등률과 리스크 관리는 비교 바 형태로 보여줍니다.
 * - 숫자를 읽기 전에 전체 수준을 감으로 파악할 수 있게 돕습니다.
 */
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
                    valueLabel = "${(summary.stabilityScore * 100).toInt()}%"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GrowthProgressBar(
                        label = "완등률",
                        value = summary.completionScore,
                        valueLabel = "${(summary.completionScore * 100).toInt()}%",
                        color = AnalysisPalette.Success
                    )
                    GrowthProgressBar(
                        label = "리스크 관리",
                        value = summary.riskControlScore,
                        valueLabel = "${(summary.riskControlScore * 100).toInt()}%",
                        color = AnalysisPalette.WarningBright
                    )
                }
            }
        }
    }
}

/** 안정성 점수를 직관적으로 보여주는 원형 게이지입니다. */
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

/** 보조 성과 지표를 비교해서 보여주는 수평 진행 바입니다. */
@Composable
private fun GrowthProgressBar(
    label: String,
    value: Float,
    valueLabel: String,
    color: androidx.compose.ui.graphics.Color
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
                    .background(color)
            )
        }
    }
}

/** 최근 챌린지 안정성 흐름을 가볍게 보여주는 미니 추세 그래프입니다. */
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
                text = "최근 챌린지 안정성 추세",
                style = MaterialTheme.typography.titleMedium,
                color = AnalysisPalette.TextPrimary
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                if (points.isEmpty()) return@Canvas

                val minValue = points.minOf { it.value }
                val maxValue = points.maxOf { it.value }
                val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
                val stepX = if (points.size == 1) 0f else size.width / (points.size - 1)

                val offsets = points.mapIndexed { index, point ->
                    val progress = (point.value - minValue) / range
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
                        color = if (point.highlight) AnalysisPalette.AccentStrong else AnalysisPalette.Accent.copy(alpha = 0.7f),
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
                                color = if (point.highlight) AnalysisPalette.AccentStrong else AnalysisPalette.Accent.copy(alpha = 0.55f)
                            ) {}
                        }
                        Text(
                            text = "${(point.value * 100).toInt()}%",
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
