package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisChallengeSummaryUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

@Composable
internal fun AnalysisChallengeSummarySection(
    summary: AnalysisChallengeSummaryUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            AnalysisSectionTitle(title = summary.title)
            ChallengeSummaryResultBlock(summary = summary)
            DividerLine()
            ChallengeSummaryScoreBlock(summary = summary)

            if (!summary.repeatedLoadFocusLabel.isNullOrBlank()) {
                DividerLine()
                ChallengeSummaryTextBlock(
                    title = "반복 부담 부위",
                    body = summary.repeatedLoadFocusLabel
                )
            }

            if (summary.strengths.isNotEmpty() || summary.improvements.isNotEmpty()) {
                DividerLine()
                ChallengeSummaryFeedbackBlock(summary = summary)
            }
        }
    }
}

@Composable
private fun ChallengeSummaryResultBlock(
    summary: AnalysisChallengeSummaryUiModel
) {
    val accentTone = when {
        summary.overallScore == null -> AnalysisBadgeTone.Neutral
        summary.overallScore >= 85 -> AnalysisBadgeTone.Success
        summary.overallScore >= 70 -> AnalysisBadgeTone.Accent
        summary.overallScore >= 55 -> AnalysisBadgeTone.Warning
        else -> AnalysisBadgeTone.Danger
    }

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
                Text(
                    text = "종합 점수",
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary
                )
                Text(
                    text = "총 ${summary.attemptCount}차 시도",
                    style = MaterialTheme.typography.labelLarge,
                    color = AnalysisPalette.TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summary.overallScore?.let { "${it}점" } ?: "-",
                    style = MaterialTheme.typography.headlineMedium,
                    color = toneColor(accentTone),
                    fontWeight = FontWeight.Bold
                )

                AnalysisBadge(
                    badge = if (summary.overallSuccess) {
                        AnalysisBadgeUiModel(label = "문제 풀이 성공", tone = AnalysisBadgeTone.Success)
                    } else {
                        AnalysisBadgeUiModel(label = "미완등", tone = AnalysisBadgeTone.Danger)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChallengeInlineMetric(
                    label = "문제 풀이 여부",
                    value = if (summary.overallSuccess) "성공" else "미완등",
                    modifier = Modifier.weight(1f)
                )
                ChallengeInlineDivider()
                ChallengeInlineMetric(
                    label = "최고 도달 홀드",
                    value = summary.reachedHoldLabel,
                    trailingValue = summary.reachedHoldSuffix,
                    modifier = Modifier.weight(1f)
                )
                ChallengeInlineDivider()
                ChallengeInlineMetric(
                    label = "대표 크럭스",
                    value = summary.cruxHoldLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChallengeSummaryScoreBlock(
    summary: AnalysisChallengeSummaryUiModel
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "항목별 점수",
            style = MaterialTheme.typography.titleMedium,
            color = AnalysisPalette.TextPrimary
        )

        SummaryScoreRow(
            title = "평균 안정성 유지",
            score = summary.averageStabilityScore
        )
        SummaryScoreRow(
            title = "평균 안정성 회복력",
            score = summary.averageRecoveryScore
        )
        SummaryScoreRow(
            title = "평균 하체 주도성",
            score = summary.averageLowerBodyDriveScore
        )
    }
}

@Composable
private fun SummaryScoreRow(
    title: String,
    score: Int?
) {
    val tone = when {
        score == null -> AnalysisBadgeTone.Neutral
        score >= 85 -> AnalysisBadgeTone.Success
        score >= 70 -> AnalysisBadgeTone.Accent
        score >= 55 -> AnalysisBadgeTone.Warning
        else -> AnalysisBadgeTone.Danger
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = AnalysisPalette.TextPrimary
            )
            Text(
                text = score?.let { "${it}점" } ?: "-",
                style = MaterialTheme.typography.titleSmall,
                color = AnalysisPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    color = AnalysisPalette.Border,
                    shape = RoundedCornerShape(999.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((score ?: 0).coerceIn(0, 100) / 100f)
                    .height(8.dp)
                    .background(
                        color = toneColor(tone),
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

@Composable
private fun ChallengeSummaryTextBlock(
    title: String,
    body: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AnalysisPalette.TextPrimary
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = AnalysisPalette.TextSecondary
        )
    }
}

@Composable
private fun ChallengeSummaryFeedbackBlock(
    summary: AnalysisChallengeSummaryUiModel
) {
    val feedbackLines = buildList {
        summary.headline
            .takeIf { it.isNotBlank() }
            ?.let(::add)
        addAll(summary.improvements)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (summary.strengths.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "잘한 점",
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary
                )
                summary.strengths.forEach { line ->
                    FeedbackLine(
                        marker = "•",
                        body = line,
                        markerColor = AnalysisPalette.Success
                    )
                }
            }
        }

        if (feedbackLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "종합 피드백",
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary
                )
                feedbackLines.forEach { line ->
                    FeedbackLine(
                        marker = "•",
                        body = line,
                        markerColor = AnalysisPalette.Warning
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackLine(
    marker: String,
    body: String,
    markerColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.titleSmall,
            color = markerColor,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = AnalysisPalette.TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChallengeInlineMetric(
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
                style = MaterialTheme.typography.titleLarge,
                color = AnalysisPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold
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
private fun ChallengeInlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(AnalysisPalette.Divider)
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AnalysisPalette.Divider)
    )
}

private fun toneColor(tone: AnalysisBadgeTone): Color =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextHint
    }
