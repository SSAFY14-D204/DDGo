package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptFlowItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptGrowthPointUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisChallengeDetailUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

@Composable
internal fun AnalysisChallengeDetailScreen(
    detail: AnalysisChallengeDetailUiModel,
    onBack: () -> Unit,
    onAttemptSelected: (Int) -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            AnalysisPalette.BackgroundTop,
            AnalysisPalette.BackgroundBottom,
            AnalysisPalette.BackgroundTop
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        AnalysisGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = (-24).dp),
            colors = listOf(
                AnalysisPalette.Accent.copy(alpha = 0.18f),
                AnalysisPalette.Accent.copy(alpha = 0f)
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AnalysisBackChip(
                    label = AnalysisStrings.BackToDashboard,
                    onClick = onBack,
                    compact = true
                )
            }

            item {
                ChallengeHeroCard(detail = detail)
            }

            item {
                AnalysisChallengeSummarySection(summary = detail.summary)
            }

            if (detail.growthPoints.isNotEmpty()) {
                item {
                    ChallengeGrowthCard(points = detail.growthPoints)
                }
            }

            item {
                AnalysisAttemptsSection(
                    attempts = detail.attempts,
                    onAttemptSelected = onAttemptSelected
                )
            }
        }
    }
}

@Composable
private fun ChallengeHeroCard(
    detail: AnalysisChallengeDetailUiModel
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AnalysisPalette.HeroStart,
                            AnalysisPalette.HeroEnd
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            AnalysisGlow(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-10).dp)
                    .size(104.dp),
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0f)
                )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.badges.forEach { badge ->
                        AnalysisBadge(badge = badge)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = AnalysisPalette.OnAccent
                    )
                    Text(
                        text = detail.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = AnalysisPalette.OnAccent.copy(alpha = 0.84f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeFlowRow(
    flowItems: List<AnalysisAttemptFlowItemUiModel>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnalysisSectionTitle(title = "시도 흐름")

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(flowItems) { item ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = flowSoftColor(item.tone)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = flowStrongColor(item.tone),
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                        Text(
                            text = "${item.attemptNo}차",
                            style = MaterialTheme.typography.labelLarge,
                            color = AnalysisPalette.TextPrimary
                        )
                        if (item.isLatest) {
                            Text(
                                text = "최근",
                                style = MaterialTheme.typography.labelSmall,
                                color = AnalysisPalette.TextHint
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeGrowthCard(
    points: List<AnalysisAttemptGrowthPointUiModel>
) {
    var selectedMetric by rememberSaveable {
        mutableStateOf(ChallengeGrowthMetric.Stability)
    }

    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(title = "시도별 성장")

            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ChallengeGrowthMetricTabs(
                    selectedMetric = selectedMetric,
                    onMetricSelected = { selectedMetric = it }
                )

                ChallengeGrowthTrendChart(
                    points = points,
                    selectedMetric = selectedMetric
                )

                ChallengeGrowthSnapshots(
                    points = points,
                    selectedMetric = selectedMetric
                )
            }
        }
    }
}

@Composable
private fun ChallengeGrowthMetricTabs(
    selectedMetric: ChallengeGrowthMetric,
    onMetricSelected: (ChallengeGrowthMetric) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChallengeGrowthMetric.entries.forEach { metric ->
            val selected = metric == selectedMetric
            Surface(
                modifier = Modifier.weight(1f),
                onClick = { onMetricSelected(metric) },
                shape = RoundedCornerShape(999.dp),
                color = if (selected) AnalysisPalette.SurfaceSelected else Color.White
            ) {
                Text(
                    text = metric.label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 11.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) AnalysisPalette.AccentStrong else AnalysisPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ChallengeGrowthTrendChart(
    points: List<AnalysisAttemptGrowthPointUiModel>,
    selectedMetric: ChallengeGrowthMetric
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        if (points.isEmpty()) return@Canvas

        val values = points.map { point ->
            when (selectedMetric) {
                ChallengeGrowthMetric.Stability -> point.stabilityScore
                ChallengeGrowthMetric.MaxHold -> point.maxHoldNo.toFloat()
                ChallengeGrowthMetric.LowerBodyDrive -> point.lowerBodyDriveScore.toFloat()
            }
        }

        val minValue = 0f
        val maxValue = when (selectedMetric) {
            ChallengeGrowthMetric.Stability -> 1f
            ChallengeGrowthMetric.MaxHold -> values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            ChallengeGrowthMetric.LowerBodyDrive -> 100f
        }
        val range = (maxValue - minValue).coerceAtLeast(1f)
        val stepX = if (values.size == 1) 0f else size.width / (values.size - 1)

        val offsets = values.mapIndexed { index, value ->
            val progress = ((value - minValue) / range).coerceIn(0f, 1f)
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
            drawCircle(
                color = flowStrongColor(points[index].tone),
                radius = 5.dp.toPx(),
                center = offset
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = offset
            )
        }
    }
}

@Composable
private fun ChallengeGrowthSnapshots(
    points: List<AnalysisAttemptGrowthPointUiModel>,
    selectedMetric: ChallengeGrowthMetric
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(points) { point ->
            val primaryValue = when (selectedMetric) {
                ChallengeGrowthMetric.Stability -> "${(point.stabilityScore * 100f).toInt()}%"
                ChallengeGrowthMetric.MaxHold -> "${point.maxHoldNo}번"
                ChallengeGrowthMetric.LowerBodyDrive -> "${point.lowerBodyDriveScore}점"
            }
            val secondaryValue = when (selectedMetric) {
                ChallengeGrowthMetric.Stability -> "최대 홀드 ${point.maxHoldNo}번"
                ChallengeGrowthMetric.MaxHold -> "안정률 ${(point.stabilityScore * 100f).toInt()}%"
                ChallengeGrowthMetric.LowerBodyDrive -> "안정률 ${(point.stabilityScore * 100f).toInt()}%"
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${point.label}차",
                        style = MaterialTheme.typography.labelLarge,
                        color = AnalysisPalette.TextPrimary
                    )
                    Text(
                        text = primaryValue,
                        style = MaterialTheme.typography.titleMedium,
                        color = AnalysisPalette.TextPrimary
                    )
                    Text(
                        text = secondaryValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = AnalysisPalette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnalysisBackChip(
    label: String,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = AnalysisPalette.Surface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 8.dp else 10.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                tint = AnalysisPalette.TextPrimary
            )
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                color = AnalysisPalette.TextPrimary
            )
        }
    }
}

private enum class ChallengeGrowthMetric(val label: String) {
    Stability("안정률"),
    MaxHold("최대 홀드"),
    LowerBodyDrive("하체 주도성")
}

private fun flowSoftColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentSoft
        AnalysisBadgeTone.Success -> AnalysisPalette.SuccessSoft
        AnalysisBadgeTone.Danger -> AnalysisPalette.DangerSoft
        AnalysisBadgeTone.Warning -> AnalysisPalette.WarningSoft
        AnalysisBadgeTone.Neutral -> AnalysisPalette.SurfaceMuted
    }

private fun flowStrongColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextSecondary
    }
