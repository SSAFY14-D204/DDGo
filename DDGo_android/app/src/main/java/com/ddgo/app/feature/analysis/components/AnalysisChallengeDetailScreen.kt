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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.model.AnalysisAttemptGrowthPointUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisChallengeDetailUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette
import com.ddgo.app.feature.main.MainChromeDefaults

@Composable
internal fun AnalysisChallengeDetailScreen(
    detail: AnalysisChallengeDetailUiModel,
    onBack: () -> Unit,
    onAttemptSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AnalysisPalette.BackgroundTop)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = MainChromeDefaults.ContentBottomPadding + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AnalysisBackChip(
                    label = "돌아가기",
                    onClick = onBack,
                    compact = true
                )
            }

            item {
                AnalysisChallengeSummarySection(
                    summary = detail.summary,
                    heroContent = {
                        ChallengeHeroCard(detail = detail)
                    }
                )
            }

            if (detail.growthPoints.size >= 2) {
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
    val resultBadge = detail.badges.lastOrNull()

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AnalysisPalette.HeroStart)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AnalysisPalette.OnAccent
                    )
                    resultBadge?.let { AnalysisBadge(badge = it) }
                }

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
                ChallengeGrowthMetric.Stability -> point.stabilityScore * 100f
                ChallengeGrowthMetric.MaxHold -> point.maxHoldNo.toFloat()
                ChallengeGrowthMetric.LowerBodyDrive -> point.lowerBodyDriveScore.toFloat()
            }
        }

        val minValue = 0f
        val maxValue = when (selectedMetric) {
            ChallengeGrowthMetric.Stability -> 100f
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
                ChallengeGrowthMetric.Stability -> "${(point.stabilityScore * 100f).toInt()}점"
                ChallengeGrowthMetric.MaxHold -> "${point.maxHoldNo}번"
                ChallengeGrowthMetric.LowerBodyDrive -> "${point.lowerBodyDriveScore}점"
            }
            val secondaryValue = when (selectedMetric) {
                ChallengeGrowthMetric.Stability -> "최대 홀드 ${point.maxHoldNo}번"
                ChallengeGrowthMetric.MaxHold -> "안정성 ${(point.stabilityScore * 100f).toInt()}점"
                ChallengeGrowthMetric.LowerBodyDrive -> "안정성 ${(point.stabilityScore * 100f).toInt()}점"
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
    Stability("안정성"),
    MaxHold("도달 홀드"),
    LowerBodyDrive("하체 주도성")
}

private fun flowStrongColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextSecondary
    }
