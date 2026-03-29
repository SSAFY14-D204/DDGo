package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.ChallengeAttemptComparisonItem
import com.ddgo.app.feature.climbing.upload.ChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.ChallengeTrendPoint
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentCircularProgressIndicator
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentLinearProgressBar
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisAccentBrushFor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisSurfaceBrushFor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.hasAnalysisGradientAccent

@Composable
internal fun ChallengeAnalysisContentSection(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ChallengeContentTabs(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = { selectedTabIndex = it }
        )
        if (selectedTabIndex == 0) {
            ChallengeResultOverviewCard(summary = summary)
            ChallengeScoreSection(summary = summary)
            ChallengeOverallFeedbackSectionRefined(summary = summary)
        } else {
            ChallengeAttemptComparisonSection(
                summary = summary,
                trendPoints = summary.trendPoints,
                attempts = summary.attempts
            )
            ChallengeKeySummaryCard(summary = summary)
        }
    }
}

@Composable
private fun ChallengeContentTabs(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("종합 분석", "시도 비교")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = index == selectedTabIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(index) }
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) {
                                AnalysisText
                            } else {
                                AnalysisText.copy(alpha = 0.64f)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(34.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                                .background(brush = requireNotNull(analysisAccentBrushFor(AnalysisPrimary)))
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.14f))
        )
    }
}

@Composable
private fun ChallengeResultOverviewCard(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    val (bestReachedValue, bestReachedTrailingValue) = buildHoldMetricParts(
        value = summary.bestReachedHoldsText,
        suffix = summary.bestReachedHoldsSuffix
    )
    val cruxValue = summary.repeatedCruxHoldLabel ?: FinalAnalysisUnknownMetricText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "챌린지 결과",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            ChallengeOverallScoreCard(score = summary.overallMovementScore)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AnalysisCardColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ChallengeOverviewMetricBlock(
                    label = "문제 풀이 여부",
                    value = if (summary.overallSuccess) "성공" else "미완등",
                    accentColor = if (summary.overallSuccess) AnalysisPrimary else AnalysisFailure,
                    modifier = Modifier.weight(1f)
                )

                ChallengeOverviewMetricDivider()

                ChallengeOverviewMetricBlock(
                    label = "최고 도달 홀드",
                    value = bestReachedValue,
                    trailingValue = bestReachedTrailingValue,
                    accentColor = if (summary.overallSuccess) AnalysisPrimary else AnalysisFailure,
                    modifier = Modifier.weight(1f)
                )

                ChallengeOverviewMetricDivider()

                ChallengeOverviewMetricBlock(
                    label = "대표 크럭스",
                    value = cruxValue,
                    accentColor = AnalysisText,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChallengeOverallScoreCard(
    score: Int?,
    modifier: Modifier = Modifier
) {
    val accentColor = challengeScoreAccentColor(score)
    val safeScore = score?.coerceIn(0, 100)
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "종합 점수",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            if (useBrandAccent) {
                AnalysisAccentText(
                    text = safeScore?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
                    accentColor = accentColor,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Text(
                    text = safeScore?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
                    color = accentColor,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .then(
                        if (useBrandAccent) {
                            Modifier.background(brush = requireNotNull(analysisSurfaceBrushFor(accentColor)))
                        } else {
                            Modifier.background(accentColor.copy(alpha = 0.14f))
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = challengeScoreGradeLabel(safeScore),
                    color = AnalysisText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .width(100.dp)
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            if (useBrandAccent) {
                AnalysisAccentCircularProgressIndicator(
                    progress = (safeScore ?: 0) / 100f,
                    accentColor = accentColor,
                    modifier = Modifier
                        .width(92.dp)
                        .height(92.dp),
                    trackColor = AnalysisCardColor,
                    strokeWidth = 8.dp
                )
            } else {
                CircularProgressIndicator(
                    progress = { (safeScore ?: 0) / 100f },
                    modifier = Modifier
                        .width(92.dp)
                        .height(92.dp),
                    color = accentColor,
                    trackColor = AnalysisCardColor,
                    strokeWidth = 8.dp
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (useBrandAccent) {
                    AnalysisAccentText(
                        text = safeScore?.toString() ?: "--",
                        accentColor = accentColor,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 28.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else {
                    Text(
                        text = safeScore?.toString() ?: "--",
                        color = accentColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "100점 만점",
                    color = AnalysisMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ChallengeOverviewMetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingValue: String? = null,
    accentColor: Color = AnalysisText
) {
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)
    val primaryValueFontSize = when {
        value.length >= 7 -> 24.sp
        value.length >= 5 -> 26.sp
        else -> 28.sp
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            color = AnalysisText,
            fontSize = 12.sp
        )

        if (trailingValue != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (useBrandAccent) {
                    AnalysisAccentText(
                        text = value,
                        accentColor = accentColor,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = primaryValueFontSize,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                } else {
                    Text(
                        text = value,
                        color = accentColor,
                        fontSize = primaryValueFontSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
                Text(
                    text = trailingValue,
                    color = AnalysisMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 22.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        } else {
            if (useBrandAccent) {
                AnalysisAccentText(
                    text = value,
                    accentColor = accentColor,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = primaryValueFontSize,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = value,
                    color = accentColor,
                    fontSize = primaryValueFontSize,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 32.sp,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChallengeAttemptComparisonSection(
    summary: ChallengeFinalAnalysisSummary,
    trendPoints: List<ChallengeTrendPoint>,
    attempts: List<ChallengeAttemptComparisonItem>,
    modifier: Modifier = Modifier
) {
    val trendMap = remember(trendPoints) {
        trendPoints.associateBy { it.attemptNo }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "시도 비교",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            attempts.forEach { item ->
                ChallengeAttemptComparisonCard(
                    item = item,
                    trendPoint = trendMap[item.attemptNo]
                )
            }

        }
    }
}

@Composable
private fun ChallengeAttemptComparisonCard(
    item: ChallengeAttemptComparisonItem,
    trendPoint: ChallengeTrendPoint?,
    modifier: Modifier = Modifier
) {
    val stabilityScore = item.stabilityRetentionScore ?: trendPoint?.insideSupportPercent
    val recoveryScore = item.stabilityRecoveryScore
    val driveScore = item.lowerBodyDriveScore
    val pointValue = item.tagLabels.firstOrNull()
        ?: item.loadFocusLabel
        ?: FinalAnalysisUnknownMetricText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.attemptNo}차 시도",
                    color = AnalysisText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ChallengeStatusChip(
                    text = if (item.isSuccess) "성공" else "미완등",
                    background = if (item.isSuccess) {
                        ChallengeSuccessChipColor
                    } else {
                        AnalysisFailure
                    }
                )
            }

            stabilityScore?.let {
                ChallengeComparisonScoreBar(
                    title = "안정성 유지",
                    score = it,
                    accentColor = AnalysisPrimary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChallengeComparisonScoreBar(
                    title = "안정성 회복력",
                    score = recoveryScore,
                    accentColor = Color(0xFFFFC857)
                )
                ChallengeComparisonScoreBar(
                    title = "하체 주도성",
                    score = driveScore,
                    accentColor = ChallengeSuccessChipColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChallengeInfoTile(
                    title = "도달 홀드",
                    value = formatHoldMetric(item.reachedHoldsText, item.reachedHoldsSuffix),
                    modifier = Modifier.weight(1f)
                )
                ChallengeInfoTile(
                    title = "핵심 포인트",
                    value = pointValue,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = item.summaryLine,
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeComparisonHighlightsRow(
    highlights: List<String>,
    modifier: Modifier = Modifier
) {
    val primaryHighlight = highlights.firstOrNull() ?: return
    val secondaryHighlight = highlights.getOrNull(1)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(AnalysisCardColor)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = primaryHighlight,
                color = AnalysisText,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        secondaryHighlight?.let {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = it,
                    color = AnalysisMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChallengeOverallFeedbackSection(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    val strengths = remember(summary) { buildChallengeStrengths(summary).take(2) }
    val improvements = remember(summary) { buildChallengeImprovements(summary).take(4) }

    if (strengths.isEmpty() && improvements.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "종합 분석",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        strengths.takeIf { it.isNotEmpty() }?.let { items ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "잘한 점",
                    color = AnalysisText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1A2418))
                        .border(1.dp, Color(0xFF304A2A), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEach { item ->
                            ChallengeFeedbackBulletRow(
                                marker = "✓",
                                markerColor = AnalysisSuccess,
                                text = item
                            )
                        }
                    }
                }
            }
        }

        improvements.takeIf { it.isNotEmpty() }?.let { items ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "종합 피드백",
                    color = AnalysisText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF241F14))
                        .border(1.dp, Color(0xFF4F4022), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEachIndexed { index, item ->
                            ChallengeFeedbackBulletRow(
                                marker = (index + 1).toString(),
                                markerColor = Color(0xFFFFC857),
                                text = item
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ChallengeFeedbackBulletRow(
    marker: String,
    markerColor: Color,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = marker,
            color = markerColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChallengeOverallFeedbackSectionRefined(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    val strengths = remember(summary) { buildChallengeStrengths(summary).take(2) }
    val improvements = remember(summary) { buildChallengeImprovements(summary).take(4) }

    if (strengths.isEmpty() && improvements.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "종합 분석",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        strengths.takeIf { it.isNotEmpty() }?.let { items ->
            ChallengeFeedbackReportCard(
                title = "잘한 점",
                accentColor = ChallengeSuccessChipColor,
                items = items,
                numbered = false
            )
        }

        improvements.takeIf { it.isNotEmpty() }?.let { items ->
            ChallengeFeedbackReportCard(
                title = "종합 피드백",
                accentColor = Color(0xFFFFC857),
                items = items,
                numbered = true
            )
        }
        }
    }
}

@Composable
private fun ChallengeFeedbackReportCard(
    title: String,
    accentColor: Color,
    items: List<String>,
    numbered: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                )
                Text(
                    text = title,
                    color = AnalysisText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { index, item ->
                    ChallengeFeedbackReportRow(
                        marker = if (numbered) (index + 1).toString() else "✓",
                        markerColor = accentColor,
                        text = item
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeFeedbackReportRow(
    marker: String,
    markerColor: Color,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = marker,
                color = markerColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = text,
            color = AnalysisText,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ChallengeOverviewMetricDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxHeight()
            .width(1.dp)
            .background(AnalysisCardColor)
    )
}

@Composable
private fun ChallengeScoreSection(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        ChallengeScoreRowState(
            title = "종합 안정성 유지",
            score = summary.averageInsideSupportRatio,
            caption = when {
                summary.averageInsideSupportRatio == null -> "집계할 안정성 데이터가 아직 부족해요."
                summary.averageInsideSupportRatio >= 75 -> "전체 시도에서 중심을 비교적 안정적으로 유지했어요."
                summary.averageInsideSupportRatio >= 60 -> "대체로 안정적이지만 몇몇 구간은 흔들렸어요."
                else -> "흔들리거나 균형이 무너진 구간이 자주 나타났어요."
            }
        ),
        ChallengeScoreRowState(
            title = "종합 안정성 회복력",
            score = summary.aggregateRecoveryScore,
            caption = summary.aggregateRecoveryCaption
        ),
        ChallengeScoreRowState(
            title = "종합 하체 주도성",
            score = summary.aggregateDriveScore,
            caption = summary.aggregateDriveCaption
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "항목별 점수",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            items.forEachIndexed { index, item ->
                ChallengeScoreRow(item = item)
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }
            }

            ChallengeLoadFocusInfoCard(
                value = summary.aggregateLoadFocusValue,
                accentColor = loadFocusAccentColor(summary.aggregateLoadFocusValue)
            )
        }
    }
}

private data class ChallengeScoreRowState(
    val title: String,
    val score: Int?,
    val caption: String
)

@Composable
private fun ChallengeScoreRow(
    item: ChallengeScoreRowState,
    modifier: Modifier = Modifier
) {
    val score = item.score?.coerceIn(0, 100)
    val accentColor = challengeScoreAccentColor(score)
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.title,
                color = AnalysisText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = score?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
                color = AnalysisText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (useBrandAccent) {
            AnalysisAccentLinearProgressBar(
                progress = (score ?: 0) / 100f,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        } else {
            LinearProgressIndicator(
                progress = { (score ?: 0) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }

        Text(
            text = item.caption,
            color = AnalysisMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChallengeLoadFocusInfoCard(
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "종합 부담 집중 부위",
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeStabilityTrendPanel(
    trendPoints: List<ChallengeTrendPoint>,
    highlights: List<String>,
    modifier: Modifier = Modifier
) {
    val stabilityRows = trendPoints.map { point ->
        ChallengeStabilityRowUi(
            attemptNo = point.attemptNo,
            value = point.insideSupportPercent,
            fallback = point.reachedPercent
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "시도별 안정성 흐름",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text = "시도마다 안정성이 어떻게 달라졌는지 한눈에 비교할 수 있어요.",
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            ChallengeStabilityProgressList(
                rows = stabilityRows,
                modifier = Modifier.fillMaxWidth()
            )

            if (highlights.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    highlights.take(2).forEach { highlight ->
                        InsightChip(text = highlight)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengeAttemptRecapSection(
    attempts: List<ChallengeAttemptComparisonItem>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "시도별 요약",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            attempts.forEach { item ->
                ChallengeAttemptRecapCard(item = item)
            }
        }
    }
}

@Composable
private fun ChallengeAttemptRecapCard(
    item: ChallengeAttemptComparisonItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.attemptNo}차 시도",
                    color = AnalysisText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ChallengeStatusChip(
                    text = if (item.isSuccess) "성공" else "실패",
                    background = if (item.isSuccess) {
                        ChallengeSuccessChipColor
                    } else {
                        AnalysisFailure
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChallengeInfoTile(
                    title = "도달 홀드",
                    value = formatHoldMetric(item.reachedHoldsText, item.reachedHoldsSuffix),
                    modifier = Modifier.weight(1f)
                )
                ChallengeInfoTile(
                    title = "핵심 포인트",
                    value = item.tagLabels.firstOrNull()
                        ?: (item.loadFocusLabel ?: FinalAnalysisUnknownMetricText),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = item.summaryLine,
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeKeySummaryCard(
    summary: ChallengeFinalAnalysisSummary,
    modifier: Modifier = Modifier
) {
    val patternLine = summary.patternHighlights.firstOrNull()
        ?: summary.challengeNatureLine

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "챌린지 핵심",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            ChallengeSummaryLineRow(
                label = "전체 결과",
                text = summary.summaryLine,
                accentColor = if (summary.overallSuccess) AnalysisSuccess else AnalysisPrimary
            )
            ChallengeSummaryLineRow(
                label = "완등 흐름",
                text = summary.completionLine,
                accentColor = AnalysisPrimary
            )
            ChallengeSummaryLineRow(
                label = "반복 패턴",
                text = patternLine,
                accentColor = Color(0xFFFFA667)
            )
        }
    }
}

@Composable
private fun ChallengeSummaryLineRow(
    label: String,
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .then(
                    if (useBrandAccent) {
                        Modifier.background(brush = requireNotNull(analysisAccentBrushFor(accentColor)))
                    } else {
                        Modifier.background(accentColor)
                    }
                )
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (useBrandAccent) {
                Text(
                    text = label,
                    color = AnalysisText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = label,
                    color = AnalysisText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = text,
                color = AnalysisText,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeStatusChip(
    text: String,
    background: Color,
    modifier: Modifier = Modifier
) {
    val useBrandAccent = hasAnalysisGradientAccent(background)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (useBrandAccent) {
                    Modifier.background(brush = requireNotNull(analysisSurfaceBrushFor(background)))
                } else {
                    Modifier.background(background.copy(alpha = 0.18f))
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (useBrandAccent) {
            AnalysisAccentText(
                text = text,
                accentColor = background,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        } else {
            Text(
                text = text,
                color = background,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChallengeInfoTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeComparisonScoreBar(
    title: String,
    score: Int?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = score?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
                color = AnalysisText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (useBrandAccent) {
            AnalysisAccentLinearProgressBar(
                progress = (score ?: 0).coerceIn(0, 100) / 100f,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        } else {
            LinearProgressIndicator(
                progress = { (score ?: 0).coerceIn(0, 100) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
private fun InsightChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AnalysisMuted.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChallengeStabilityProgressList(
    rows: List<ChallengeStabilityRowUi>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            ChallengeStabilityProgressRow(row = row)
        }
    }
}

@Composable
private fun ChallengeStabilityProgressRow(
    row: ChallengeStabilityRowUi,
    modifier: Modifier = Modifier
) {
    val useBrandAccent = row.score != null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${row.attemptNo}차 시도",
                color = AnalysisText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (useBrandAccent) {
                AnalysisAccentText(
                    text = row.valueText,
                    accentColor = AnalysisPrimary,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            } else {
                Text(
                    text = row.valueText,
                    color = AnalysisMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            row.score?.let { score ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(score.coerceIn(0, 100) / 100f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(brush = requireNotNull(analysisAccentBrushFor(AnalysisPrimary)))
                )
            }
        }

        Text(
            text = row.caption,
            color = AnalysisMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class ChallengeStabilityRowUi(
    val attemptNo: Int,
    val score: Int?,
    val valueText: String,
    val caption: String
) {
    constructor(attemptNo: Int, value: Int?, fallback: Int?) : this(
        attemptNo = attemptNo,
        score = value,
        valueText = value?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
        caption = when {
            value == null && fallback != null -> "안정성 점수는 없지만 도달 흐름을 참고할 수 있어요."
            value == null -> "안정성 데이터를 충분히 읽지 못했어요."
            value >= 75 -> "전체적으로 안정적인 흐름이 잘 유지됐어요."
            value >= 60 -> "몇 구간에서 흔들렸지만 전반 흐름은 괜찮았어요."
            else -> "흔들림이 반복돼 안정성이 떨어진 시도가 있었어요."
        }
    )
}

private fun formatHoldMetric(value: String, suffix: String?): String {
    if (value == FinalAnalysisUnknownMetricText) {
        return value
    }
    return buildString {
        append(value)
        append(suffix.orEmpty())
        append("번")
    }
}

private fun buildHoldMetricParts(
    value: String,
    suffix: String?
): Pair<String, String?> {
    if (value == FinalAnalysisUnknownMetricText) {
        return FinalAnalysisUnknownMetricText to null
    }

    val trailingValue = if (suffix.isNullOrBlank()) {
        null
    } else {
        "/${suffix.removePrefix("/")}번"
    }
    return "${value}번" to trailingValue
}

private fun buildChallengeStrengths(summary: ChallengeFinalAnalysisSummary): List<String> {
    return buildList {
        if (summary.overallSuccess) {
            add("여러 시도 끝에 완등까지 연결하며 흐름을 끝까지 이어냈어요.")
        }
        if ((summary.averageInsideSupportRatio ?: 0) >= 70) {
            add("시도 전체에서 중심을 비교적 안정적으로 유지했어요.")
        }
        if ((summary.aggregateRecoveryScore ?: 0) >= 65) {
            add("흔들린 뒤에도 다시 자세를 회복하는 흐름이 좋았어요.")
        }
        if ((summary.aggregateDriveScore ?: 0) >= 65) {
            add("다리로 밀어 올리는 사용 흐름이 비교적 안정적이었어요.")
        }
        if (isEmpty()) {
            add("시도마다 도달 구간이 조금씩 넓어지면서 문제 이해도가 올라갔어요.")
        }
    }
}

private fun buildChallengeImprovements(summary: ChallengeFinalAnalysisSummary): List<String> {
    return buildList {
        if ((summary.averageInsideSupportRatio ?: 100) < 65) {
            add("크럭스 구간에서는 발 위치를 먼저 고정해 중심이 무너지지 않도록 해보세요.")
        }
        if ((summary.aggregateRecoveryScore ?: 100) < 60) {
            add("흔들린 뒤 바로 복구할 수 있도록 쉬운 홀드에서 잠깐 자세를 정리해보세요.")
        }
        if ((summary.aggregateDriveScore ?: 100) < 60) {
            add("팔로 버티기보다 다리로 밀어 올리는 타이밍을 더 의식해보세요.")
        }
        if (summary.repeatedPatternLabels.any { it.contains("발 사용 부족") }) {
            add("발을 더 정확하게 올리는 연습으로 상지 부담을 줄여보세요.")
        }
        if (summary.repeatedPatternLabels.any { it.contains("중심 흔들림") }) {
            add("동작 전에 힙과 코어를 먼저 고정해 흔들림을 줄여보세요.")
        }
        if (summary.repeatedPatternLabels.any { it.contains("손 탐색") }) {
            add("손을 뻗기 전에 다음 홀드 위치를 먼저 확신하고 진입해보세요.")
        }
        if (summary.repeatedLoadFocusLabel?.contains("팔") == true) {
            add("팔에 부담이 몰린 만큼, 쉬운 구간에서는 곧은 팔과 하체 지지를 더 활용해보세요.")
        }
        if (isEmpty()) {
            add("다음 시도에서는 지금 유지한 흐름을 그대로 살리면서 크럭스 진입 타이밍만 더 다듬어보세요.")
        }
    }.distinct()
}

private val ChallengeSuccessChipColor = Color(0xFF62D26F)

private fun challengeScoreAccentColor(score: Int?): Color {
    return when {
        score == null -> AnalysisMuted
        score >= 85 -> Color(0xFF62D26F)
        score >= 70 -> AnalysisPrimary
        score >= 55 -> Color(0xFFFFC857)
        else -> Color(0xFFFF7D7D)
    }
}

private fun challengeScoreGradeLabel(score: Int?): String {
    return when {
        score == null -> FinalAnalysisUnknownMetricText
        score >= 85 -> "우수함"
        score >= 70 -> "좋음"
        score >= 55 -> "보통"
        else -> "보완 필요"
    }
}

private fun loadFocusAccentColor(value: String): Color {
    return when {
        value == FinalAnalysisUnknownMetricText -> AnalysisMuted
        value.contains("팔") || value.contains("손") -> AnalysisFailure
        value.contains("다리") || value.contains("발") || value.contains("몸통") || value.contains("코어") ->
            AnalysisPrimary
        else -> AnalysisText
    }
}
