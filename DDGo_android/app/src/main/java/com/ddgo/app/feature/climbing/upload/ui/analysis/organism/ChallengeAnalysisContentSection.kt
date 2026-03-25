package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
            ChallengeKeySummaryCard(summary = summary)
        } else {
            ChallengeStabilityTrendPanel(
                trendPoints = summary.trendPoints,
                highlights = summary.trendHighlights
            )
            ChallengeAttemptRecapSection(attempts = summary.attempts)
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
                            color = if (isSelected) AnalysisPrimary else AnalysisMuted,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.SemiBold
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(34.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(topStart = 100.dp, topEnd = 100.dp))
                                .background(AnalysisPrimary)
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
    val bestReachedValue = formatHoldMetric(
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
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
                    accentColor = AnalysisPrimary,
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = safeScore?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
                color = accentColor,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = challengeScoreGradeLabel(safeScore),
                    color = accentColor,
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
            CircularProgressIndicator(
                progress = { (safeScore ?: 0) / 100f },
                modifier = Modifier
                    .width(92.dp)
                    .height(92.dp),
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
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
    accentColor: Color = AnalysisText
) {
    val valueFontSize = when {
        value.length >= 10 -> 18.sp
        value.length >= 6 -> 22.sp
        else -> 28.sp
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            color = AnalysisMuted,
            fontSize = 12.sp
        )

        Text(
            text = value,
            color = accentColor,
            fontSize = valueFontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = valueFontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
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
            title = "평균 안정성 유지율",
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
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
                caption = summary.aggregateLoadFocusCaption,
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
                color = accentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LinearProgressIndicator(
            progress = { (score ?: 0) / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = accentColor,
            trackColor = Color.White.copy(alpha = 0.08f)
        )

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
    caption: String,
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
            Text(
                text = caption,
                color = AnalysisMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 2,
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
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
                        AnalysisSuccess.copy(alpha = 0.2f)
                    } else {
                        AnalysisFailure.copy(alpha = 0.18f)
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
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
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
                text = label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
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
            Text(
                text = row.valueText,
                color = if (row.score != null) AnalysisPrimary else AnalysisMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
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
                        .background(AnalysisPrimary)
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
