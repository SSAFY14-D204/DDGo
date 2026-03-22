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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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

/**
 * 메인 분석 탭에서 선택한 챌린지의 상세 분석을 보여주는 화면입니다.
 *
 * 역할:
 * - 문제 자체를 먼저 이해하게 하고,
 * - 시도 흐름과 성장 추세를 보여준 뒤 아래에서 시도 목록으로 이어집니다.
 */
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnalysisBackChip(
                    label = AnalysisStrings.BackToDashboard,
                    onClick = onBack
                )
            }

            item {
                ChallengeHeroCard(detail = detail)
            }

            item {
                ChallengeFlowRow(flowItems = detail.attemptFlow)
            }

            item {
                ChallengeGrowthCard(points = detail.growthPoints)
            }

            item {
                AnalysisChallengeSummarySection(summary = detail.summary)
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

/**
 * 챌린지의 가장 중요한 정보만 먼저 보여주는 상단 카드입니다.
 *
 * 역할:
 * - 문제명, 난이도, 상태를 제일 먼저 보이게 합니다.
 * - 핵심 문장 하나만 남겨 화면의 시작점을 단순하게 만듭니다.
 */
@Composable
private fun ChallengeHeroCard(
    detail: AnalysisChallengeDetailUiModel
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 4.dp
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
                .padding(22.dp)
        ) {
            AnalysisGlow(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
                    .size(132.dp),
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0f)
                )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.badges.forEach { badge ->
                        AnalysisBadge(badge = badge)
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AnalysisPalette.OnAccent
                    )
                    Text(
                        text = detail.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnalysisPalette.OnAccent.copy(alpha = 0.84f)
                    )
                }
            }
        }
    }
}

/**
 * 시도 결과 흐름을 가볍게 훑어볼 수 있는 행입니다.
 *
 * 역할:
 * - 시도별 결과 변화만 간단히 보여주고 바로 다음 섹션으로 넘어가게 합니다.
 * - 큰 카드 대신 작은 칩 흐름으로 정리해 화면을 덜 무겁게 만듭니다.
 */
@Composable
private fun ChallengeFlowRow(
    flowItems: List<AnalysisAttemptFlowItemUiModel>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnalysisSectionTitle(title = "\uC2DC\uB3C4 \uD750\uB984")

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
                            text = "${item.attemptNo}\uCC28",
                            style = MaterialTheme.typography.labelLarge,
                            color = AnalysisPalette.TextPrimary
                        )
                        if (item.isLatest) {
                            Text(
                                text = "\uCD5C\uC2E0",
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

/**
 * 시도별 안정성 흐름을 조용한 그래프로 보여주는 카드입니다.
 *
 * 역할:
 * - 챌린지 안에서 내가 어떻게 안정적으로 좋아졌는지 한눈에 보여줍니다.
 * - 화면을 복잡하게 만들지 않도록 한 가지 그래프와 짧은 보조 수치만 사용합니다.
 */
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
            AnalysisSectionTitle(title = "\uC2DC\uB3C4\uBCC4 \uC131\uC7A5")

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = AnalysisPalette.SurfaceMuted
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
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
}

/**
 * 성장 카드 안에서 어떤 지표를 볼지 전환하는 작은 탭입니다.
 *
 * 역할:
 * - 한 카드 안에서 안정률과 최대 홀드 흐름을 번갈아 볼 수 있게 해줍니다.
 * - 탭 자체가 과하게 튀지 않도록, 카드 톤 안에서 조용한 선택 UI로 구성합니다.
 */
@Composable
private fun ChallengeGrowthMetricTabs(
    selectedMetric: ChallengeGrowthMetric,
    onMetricSelected: (ChallengeGrowthMetric) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChallengeGrowthMetric.values().forEach { metric ->
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

/**
 * 현재 선택된 성장 지표를 선 그래프로 보여줍니다.
 *
 * 역할:
 * - 시도 순서에 따라 안정률 또는 최대 홀드가 어떻게 달라졌는지 한눈에 보여줍니다.
 * - 값의 절대 단위가 달라도 같은 그래프 영역에서 읽을 수 있도록 내부에서 정규화합니다.
 */
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
                ChallengeGrowthMetric.RiskEvents -> point.riskEventCount.toFloat()
            }
        }

        val minValue = 0f
        val maxValue = when (selectedMetric) {
            ChallengeGrowthMetric.Stability -> 1f
            ChallengeGrowthMetric.MaxHold -> values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            ChallengeGrowthMetric.RiskEvents -> values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
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

/**
 * 각 시도의 핵심 값을 짧은 카드로 요약합니다.
 *
 * 역할:
 * - 그래프만 보고 끝나지 않도록 시도별 실제 수치를 같이 보여줍니다.
 * - 선택한 탭에 따라 대표 값과 보조 문구가 자연스럽게 바뀌도록 구성합니다.
 */
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
                ChallengeGrowthMetric.MaxHold -> "${point.maxHoldNo}\uD640\uB4DC"
                ChallengeGrowthMetric.RiskEvents -> "${point.riskEventCount}\uD68C"
            }
            val secondaryValue = when (selectedMetric) {
                ChallengeGrowthMetric.Stability -> "\uCD5C\uB300 ${point.maxHoldNo}\uD640\uB4DC"
                ChallengeGrowthMetric.MaxHold -> "\uC548\uC815 ${(point.stabilityScore * 100f).toInt()}%"
                ChallengeGrowthMetric.RiskEvents -> "\uC548\uC815 ${(point.stabilityScore * 100f).toInt()}%"
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
                        text = "${point.label}\uCC28",
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

/**
 * 챌린지 성장 카드에서 전환할 수 있는 지표 종류입니다.
 *
 * 역할:
 * - 카드 내부 탭과 그래프 계산 기준을 하나의 기준으로 묶습니다.
 * - 화면 문구와 실제 데이터 전환 로직이 어긋나지 않게 유지합니다.
 */
private enum class ChallengeGrowthMetric(val label: String) {
    Stability("\uC548\uC815\uB960"),
    MaxHold("\uCD5C\uB300 \uD640\uB4DC"),
    RiskEvents("\uC704\uD5D8 \uC774\uBCA4\uD2B8")
}

/** 상세 화면 상단에서 쓰는 간결한 뒤로가기 칩입니다. */
@Composable
private fun AnalysisBackChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = AnalysisPalette.Surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = null,
                tint = AnalysisPalette.TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = AnalysisPalette.TextPrimary
            )
        }
    }
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
