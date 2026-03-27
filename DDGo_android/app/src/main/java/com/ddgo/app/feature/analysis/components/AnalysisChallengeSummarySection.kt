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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.feature.analysis.model.AnalysisChallengeSummaryUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisMuted

@Composable
internal fun AnalysisChallengeSummarySection(
    summary: AnalysisChallengeSummaryUiModel,
    heroContent: (@Composable () -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnalysisCardSurface {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                heroContent?.invoke()
                ChallengeSummaryResultBlock(summary = summary)

                DividerLine()

                ChallengeSummaryScoreBlock(summary = summary)

                summary.repeatedLoadFocusLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let { loadFocusLabel ->
                        DividerLine()
                        ChallengeLoadFocusRow(loadFocusLabel = loadFocusLabel)
                    }
            }
        }

        val hasFeedbackCard = false
        val hasCuratedFeedbackCard = summary.strengths.isNotEmpty() ||
            summary.improvements.isNotEmpty()

        if (hasFeedbackCard) {
            AnalysisCardSurface {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (summary.strengths.isNotEmpty()) {
                        ChallengeFeedbackSection(
                            title = "보완 포인트",
                            lines = summary.strengths,
                            accentColor = AnalysisPalette.Success
                        )
                    }

                    summary.headline
                        .takeIf { it.isNotBlank() }
                        ?.let { headline ->
                            if (summary.improvements.isNotEmpty()) {
                                DividerLine()
                            }
                            ChallengeFeedbackSection(
                                title = "종합 피드백",
                                lines = listOf(headline),
                                accentColor = DdgoColorTokens.BrandBlue
                            )
                        }
                }
            }
        }

        if (hasCuratedFeedbackCard) {
            AnalysisCardSurface {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (summary.strengths.isNotEmpty()) {
                        ChallengeFeedbackSection(
                            title = "잘한 점",
                            lines = summary.strengths,
                            accentColor = AnalysisPalette.Success
                        )
                    }

                    if (summary.improvements.isNotEmpty()) {
                        if (summary.strengths.isNotEmpty()) {
                            DividerLine()
                        }
                        ChallengeFeedbackSection(
                            title = "보완 포인트",
                            lines = summary.improvements,
                            accentColor = AnalysisPalette.WarningBright
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeSummaryResultBlock(
    summary: AnalysisChallengeSummaryUiModel
) {
    val resultAccentColor = if (summary.overallSuccess) {
        DdgoColorTokens.BrandBlue
    } else {
        AnalysisPalette.Danger
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AnalysisPalette.SurfaceMuted
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "종합 점수",
                        color = AnalysisPalette.TextPrimary,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = summary.overallScore?.let { "${it}점" } ?: "-",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 34.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = resultAccentColor
                    )
                    Text(
                        text = "총 ${summary.attemptCount}회 시도",
                        color = AnalysisPalette.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                ChallengeOverallScoreRing(
                    score = summary.overallScore,
                    accentColor = resultAccentColor
                )
            }

            DividerLine()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChallengeInlineMetric(
                    label = "문제 풀이 여부",
                    value = if (summary.overallSuccess) "성공" else "실패",
                    accentColor = resultAccentColor,
                    modifier = Modifier.weight(1f)
                )
                ChallengeInlineDivider()
                ChallengeInlineMetric(
                    label = "최고 도달 홀드",
                    value = summary.reachedHoldLabel,
                    trailingValue = summary.reachedHoldSuffix,
                    accentColor = resultAccentColor,
                    modifier = Modifier.weight(1f)
                )
                ChallengeInlineDivider()
                ChallengeInlineMetric(
                    label = "대표 크럭스",
                    value = summary.cruxHoldLabel,
                    accentColor = AnalysisPalette.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChallengeOverallScoreRing(
    score: Int?,
    accentColor: Color
) {
    val safeScore = score?.coerceIn(0, 100)

    Box(
        modifier = Modifier.size(100.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { (safeScore ?: 0) / 100f },
            modifier = Modifier.size(92.dp),
            color = accentColor,
            trackColor = AnalysisCardColor,
            strokeWidth = 8.dp
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = safeScore?.toString() ?: "--",
                color = accentColor,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "100점 만점",
                color = AnalysisPalette.TextHint,
                fontSize = 10.sp
            )
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
        AnalysisSectionTitle(title = "항목별 점수")

        SummaryScoreRow(
            title = "안정성 유지",
            score = summary.averageStabilityScore
        )
        SummaryScoreRow(
            title = "안정성 회복력",
            score = summary.averageRecoveryScore
        )
        SummaryScoreRow(
            title = "하체 주도성",
            score = summary.averageLowerBodyDriveScore
        )
    }
}

@Composable
private fun SummaryScoreRow(
    title: String,
    score: Int?
) {
    val accentColor = challengeScoreColor(score)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = AnalysisPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = score?.let { "${it}점" } ?: "-",
                color = AnalysisPalette.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(
                    color = AnalysisPalette.Border,
                    shape = RoundedCornerShape(999.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((score ?: 0).coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
        }
    }
}

@Composable
private fun ChallengeLoadFocusRow(
    loadFocusLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "부담 집중 부위",
            color = AnalysisPalette.TextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text = loadFocusLabel,
            color = AnalysisPalette.Danger,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun ChallengeFeedbackSection(
    title: String,
    lines: List<String>,
    accentColor: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(24.dp)
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(999.dp)
                    )
            )
            AnalysisSectionTitle(title = title)
        }

        Column(
            modifier = Modifier.padding(start = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            lines.forEach { line ->
                ChallengeFeedbackRow(
                    line = line,
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
private fun ChallengeFeedbackRow(
    line: String,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .background(
                    color = accentColor,
                    shape = CircleShape
                )
        )

        Text(
            text = line,
            modifier = Modifier.weight(1f),
            color = AnalysisPalette.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChallengeInlineMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingValue: String? = null,
    accentColor: Color = AnalysisPalette.TextPrimary
) {
    val primaryValueFontSize = when {
        value.length >= 7 -> 18.sp
        value.length >= 5 || value.contains(" ") -> 20.sp
        trailingValue != null -> 22.sp
        else -> 24.sp
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            color = AnalysisPalette.TextSecondary,
            fontSize = 12.sp
        )

        if (trailingValue != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = primaryValueFontSize,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = trailingValue,
                    color = AnalysisPalette.TextHint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        } else {
            Text(
                text = value,
                color = accentColor,
                fontSize = primaryValueFontSize,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeInlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(AnalysisPalette.Border)
    )
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AnalysisPalette.Border)
    )
}

private fun challengeScoreColor(score: Int?): Color =
    when {
        score == null -> AnalysisMuted
        score >= 85 -> AnalysisPalette.Success
        score >= 70 -> DdgoColorTokens.BrandBlue
        score >= 55 -> AnalysisPalette.WarningBright
        else -> AnalysisPalette.Danger
    }
