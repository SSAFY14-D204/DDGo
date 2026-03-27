package com.ddgo.app.feature.analysis.components

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisCoachCardUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTimelineItemUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSection
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSectionState

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnalysisBackChip(
                        label = AnalysisStrings.BackToChallenge,
                        onClick = onBack,
                        compact = true
                    )
                    AttemptHeroCard(detail = detail)
                }
            }

            detail.videoUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { videoUrl ->
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AttemptVideoSection(
                                state = AttemptVideoSectionState(videoUri = videoUrl),
                                lineColor = AnalysisPalette.AccentStrong,
                                pointColor = AnalysisPalette.Accent,
                                scrubberColors = PoseScrubberColors(
                                    trackColor = AnalysisPalette.Border,
                                    progressColor = AnalysisPalette.AccentStrong,
                                    thumbColor = AnalysisPalette.Accent,
                                    textColor = AnalysisPalette.TextPrimary
                                ),
                                controlSurfaceColor = AnalysisPalette.SurfaceMuted
                            )
                        }
                    }
                }

            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AttemptResultAndScoreSection(detail = detail)
                    AttemptTimelineSection(items = detail.timelineItems)
                    AttemptCoachSection(cards = detail.coachCards)
                }
            }

            item {
                Box(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun AttemptHeroCard(
    detail: AnalysisAttemptDetailUiModel
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
        ) {
            AnalysisGlow(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-10).dp)
                    .size(108.dp),
                colors = listOf(
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0f)
                )
            )

            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
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
                    AnalysisBadge(badge = detail.resultBadge)
                }

                Text(
                    text = detail.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AnalysisPalette.OnAccent.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AttemptResultAndScoreSection(
    detail: AnalysisAttemptDetailUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnalysisSectionTitle(title = "이번 시도 결과")

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = AnalysisPalette.SurfaceMuted
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "종합 점수",
                                style = MaterialTheme.typography.titleMedium,
                                color = AnalysisPalette.TextPrimary
                            )
                            Text(
                                text = detail.overallMovementScore?.let { "${it}점" } ?: "-",
                                style = MaterialTheme.typography.headlineMedium,
                                color = scoreColor(detail.overallMovementScore),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        AnalysisBadge(badge = detail.resultBadge)
                    }

                    DividerLine()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AttemptInlineMetric(
                            label = "문제 풀이 여부",
                            value = detail.attemptResultLabel,
                            modifier = Modifier.weight(1f)
                        )
                        AttemptInlineDivider()
                        AttemptInlineMetric(
                            label = "도달 홀드",
                            value = detail.reachedHoldLabel,
                            trailingValue = detail.reachedHoldSuffix,
                            modifier = Modifier.weight(1f)
                        )
                        AttemptInlineDivider()
                        AttemptInlineMetric(
                            label = "대표 크럭스",
                            value = detail.cruxHoldLabel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            DividerLine()

            AnalysisSectionTitle(title = "항목별 점수")

            AttemptDetailScoreRow(
                label = "안정성 유지",
                progress = detail.stabilityScore,
                valueLabel = detail.stabilityValueLabel
            )
            AttemptDetailScoreRow(
                label = "안정성 회복력",
                progress = detail.recoveryScore,
                valueLabel = detail.recoveryValueLabel
            )
            AttemptDetailScoreRow(
                label = "하체 주도성",
                progress = detail.lowerBodyDriveScore,
                valueLabel = detail.lowerBodyDriveValueLabel
            )

            DividerLine()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "부담 집중 부위",
                    style = MaterialTheme.typography.titleSmall,
                    color = AnalysisPalette.TextSecondary
                )
                Text(
                    text = detail.loadFocusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AttemptDetailScoreRow(
    label: String,
    progress: Float,
    valueLabel: String
) {
    val percentScore = (progress.coerceIn(0f, 1f) * 100f).toInt()

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
                style = MaterialTheme.typography.titleMedium,
                color = AnalysisPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            shape = RoundedCornerShape(999.dp),
            color = AnalysisPalette.Border
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(10.dp),
                shape = RoundedCornerShape(999.dp),
                color = scoreColor(percentScore)
            ) {}
        }
    }
}

@Composable
private fun AttemptTimelineSection(
    items: List<AnalysisTimelineItemUiModel>
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(
                title = "핵심 흐름",
                subtitle = "이번 시도에서 중요하게 남는 흐름을 순서대로 정리했어요."
            )

            items.forEachIndexed { index, item ->
                AttemptTimelineRow(
                    step = index + 1,
                    item = item
                )
                if (index < items.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun AttemptTimelineRow(
    step: Int,
    item: AnalysisTimelineItemUiModel
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(999.dp),
            color = timelineStepColor(step)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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

@Composable
private fun AttemptCoachSection(
    cards: List<AnalysisCoachCardUiModel>
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AnalysisSectionTitle(
                title = "이번 시도 핵심",
                subtitle = "이번 시도에서 바로 가져갈 핵심 포인트만 짧게 정리했어요."
            )

            cards.forEachIndexed { index, card ->
                AttemptCoachRow(card = card)
                if (index < cards.lastIndex) {
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun AttemptCoachRow(
    card: AnalysisCoachCardUiModel
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp),
            shape = RoundedCornerShape(999.dp),
            color = toneColor(card.tone)
        ) {}

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                color = AnalysisPalette.TextPrimary
            )
            Text(
                text = card.body,
                style = MaterialTheme.typography.bodyMedium,
                color = AnalysisPalette.TextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttemptInlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingValue: String? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = AnalysisPalette.TextSecondary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = AnalysisPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            trailingValue?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = AnalysisPalette.TextHint
                )
            }
        }
    }
}

@Composable
private fun AttemptInlineDivider() {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp),
        color = AnalysisPalette.Divider
    ) {}
}

@Composable
private fun DividerLine() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp),
        color = AnalysisPalette.Divider
    ) {}
}

private fun toneColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextHint
    }

private fun scoreColor(score: Int?): Color =
    when {
        score == null -> AnalysisPalette.TextHint
        score >= 85 -> AnalysisPalette.Success
        score >= 70 -> AnalysisPalette.AccentStrong
        score >= 55 -> AnalysisPalette.Warning
        else -> AnalysisPalette.Danger
    }

private fun timelineStepColor(step: Int): Color =
    when ((step - 1) % 5) {
        0 -> AnalysisPalette.Warning
        1 -> AnalysisPalette.AccentStrong
        2 -> AnalysisPalette.Success
        3 -> AnalysisPalette.Danger
        else -> AnalysisPalette.TextSecondary
    }
