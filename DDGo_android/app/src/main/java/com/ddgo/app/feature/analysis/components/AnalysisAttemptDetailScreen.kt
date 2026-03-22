package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisCoachCardUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTimelineItemUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 메인 분석 탭에서 개별 시도의 상세 분석을 보여주는 화면입니다.
 *
 * 역할:
 * - 이번 시도의 핵심 결과를 먼저 보여줍니다.
 * - 그 다음 지표, 흐름, 코칭 순서로 아래로 읽게 해서 화면을 단순하게 유지합니다.
 */
@Composable
internal fun AnalysisAttemptDetailScreen(
    detail: AnalysisAttemptDetailUiModel,
    onBack: () -> Unit
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
                    label = AnalysisStrings.BackToChallenge,
                    onClick = onBack
                )
            }

            item {
                AttemptHeroCard(detail = detail)
            }

            item {
                AttemptMetricsSection(detail = detail)
            }

            item {
                AttemptTimelineSection(items = detail.timelineItems)
            }

            item {
                AttemptCoachSection(cards = detail.coachCards)
            }
        }
    }
}

/**
 * 시도 상세 상단에서 결과와 핵심 메시지를 보여주는 카드입니다.
 *
 * 역할:
 * - 결과 배지와 요약 문장을 화면의 시작점으로 만듭니다.
 * - 필요한 텍스트만 남겨 첫인상이 복잡하지 않게 유지되도록 합니다.
 */
@Composable
private fun AttemptHeroCard(
    detail: AnalysisAttemptDetailUiModel
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
                    .offset(x = 18.dp, y = (-12).dp)
                    .size(132.dp),
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0f)
                )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnalysisBadge(badge = detail.resultBadge)

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
                        color = AnalysisPalette.OnAccent.copy(alpha = 0.82f)
                    )
                }

                Text(
                    text = detail.headline,
                    style = MaterialTheme.typography.titleLarge,
                    color = AnalysisPalette.OnAccent
                )
            }
        }
    }
}

/**
 * 시도 핵심 지표를 한 카드에 정돈해서 보여주는 섹션입니다.
 *
 * 역할:
 * - 상단에는 2열 수치 카드,
 * - 하단에는 해석용 진행 바를 두어 같은 카드 안에서 위계를 만듭니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttemptMetricsSection(
    detail: AnalysisAttemptDetailUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnalysisSectionTitle(title = AnalysisStrings.AttemptDetailSection)

            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                detail.metricCards.forEach { stat ->
                    AnalysisMiniStatCard(
                        label = stat.label,
                        value = stat.value,
                        modifier = Modifier.width(148.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = AnalysisPalette.SurfaceMuted
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AttemptSignalBar(
                        label = "\uC548\uC815\uB960",
                        score = detail.stabilityScore,
                        valueLabel = detail.stabilityValueLabel,
                        tone = AnalysisBadgeTone.Accent
                    )
                    AttemptSignalBar(
                        label = "\uCD5C\uB300 \uD640\uB4DC",
                        score = detail.reachScore,
                        valueLabel = detail.reachValueLabel,
                        tone = AnalysisBadgeTone.Success
                    )
                    AttemptSignalBar(
                        label = "\uC704\uD5D8 \uC774\uBCA4\uD2B8",
                        score = detail.dangerEventScore,
                        valueLabel = detail.dangerEventValueLabel,
                        tone = AnalysisBadgeTone.Warning
                    )
                    AttemptSignalBar(
                        label = "\uD06C\uB7ED\uC2A4 \uAD6C\uAC04 \uC2DC\uAC04",
                        score = detail.cruxFocusScore,
                        valueLabel = detail.cruxFocusValueLabel,
                        tone = AnalysisBadgeTone.Danger
                    )
                }
            }
        }
    }
}

/** 한 줄 막대 형태로 시도 신호를 보여줍니다. */
@Composable
private fun AttemptSignalBar(
    label: String,
    score: Float,
    valueLabel: String,
    tone: AnalysisBadgeTone
) {
    val clampedScore = score.coerceIn(0f, 1f)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = AnalysisPalette.TextPrimary
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = signalToneColor(tone)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(
                    color = signalToneSoftColor(tone),
                    shape = RoundedCornerShape(999.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clampedScore)
                    .background(
                        color = signalToneColor(tone),
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

/**
 * 시도 진행 흐름을 카드형 리스트로 보여주는 섹션입니다.
 *
 * 역할:
 * - 단계 번호와 설명 카드만 남겨 읽는 순서를 분명하게 합니다.
 * - 복잡한 장식 없이 흐름 자체가 먼저 보이게 합니다.
 */
@Composable
private fun AttemptTimelineSection(
    items: List<AnalysisTimelineItemUiModel>
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(title = AnalysisStrings.TimelineSection)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items.forEachIndexed { index, item ->
                    AttemptTimelineCard(
                        step = index + 1,
                        item = item
                    )
                }
            }
        }
    }
}

/** 타임라인 한 단계를 카드로 표현합니다. */
@Composable
private fun AttemptTimelineCard(
    step: Int,
    item: AnalysisTimelineItemUiModel
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = signalToneColor(item.tone)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = signalToneSoftColor(item.tone)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary
                )
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnalysisPalette.TextSecondary
                )
            }
        }
    }
}

/**
 * 분석 결과를 다음 행동으로 바꿔주는 코칭 섹션입니다.
 *
 * 역할:
 * - 카드 형태는 유지하되 같은 패턴으로 반복해서 읽기 쉽게 만듭니다.
 * - 색상은 포인트만 주고, 본문은 차분하게 읽히도록 유지합니다.
 */
@Composable
private fun AttemptCoachSection(
    cards: List<AnalysisCoachCardUiModel>
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(title = AnalysisStrings.CoachSection)

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                cards.forEach { card ->
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = signalToneSoftColor(card.tone)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(52.dp)
                                    .background(
                                        color = signalToneColor(card.tone),
                                        shape = RoundedCornerShape(999.dp)
                                    )
                            )

                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = card.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AnalysisPalette.TextPrimary
                                )
                                Text(
                                    text = card.body,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AnalysisPalette.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 시도 상세에서만 쓰는 간결한 뒤로가기 칩입니다. */
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

private fun signalToneColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextSecondary
    }

private fun signalToneSoftColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentSoft
        AnalysisBadgeTone.Success -> AnalysisPalette.SuccessSoft
        AnalysisBadgeTone.Danger -> AnalysisPalette.DangerSoft
        AnalysisBadgeTone.Warning -> AnalysisPalette.WarningSoft
        AnalysisBadgeTone.Neutral -> AnalysisPalette.SurfaceMuted
    }
