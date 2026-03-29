package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.record.presentation.HeartRatePoint
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.previewStartMs
import com.ddgo.app.feature.climbing.upload.toVideoTimeString
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentCircularProgressIndicator
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentLinearProgressBar
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisAccentBrushFor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisSurfaceBrushFor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.hasAnalysisGradientAccent
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.FootUpperLimbContributionChart
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.StabilityInsightTimelineChart
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun AttemptAnalysisContentSection(
    currentSummary: FinalAnalysisAttemptSummary,
    previousSummary: FinalAnalysisAttemptSummary?,
    analysisStartTimeMs: Long?,
    heartRateSeries: List<HeartRatePoint>,
    reachedHoldsText: String,
    reachedHoldsSuffix: String?,
    isSuccess: Boolean,
    feedbackLine: String,
    riskLine: String,
    coachingLine: String,
    selectedTabIndex: Int,
    onStabilityTimelineSelected: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val durationMs = remember(
        currentSummary.videoDurationMs,
        currentSummary.analysisPoints,
        currentSummary.primaryCruxDurationMs
    ) {
        estimateDurationMs(currentSummary)
    }
    val analysisStartFraction = remember(analysisStartTimeMs, durationMs) {
        analysisStartTimeMs
            ?.takeIf { durationMs > 0L }
            ?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
    }
    val cruxRange = remember(
        currentSummary.primaryCruxDurationMs,
        currentSummary.stabilityFocusFraction,
        durationMs
    ) {
        buildCruxRange(
            durationMs = durationMs,
            focusFraction = currentSummary.stabilityFocusFraction,
            cruxDurationMs = currentSummary.primaryCruxDurationMs
        )
    }
    val recoveryInsight = remember(
        currentSummary.stabilityRecoveryScore,
        currentSummary.stabilityTimeline,
        currentSummary.isSuccess,
        durationMs,
        analysisStartFraction
    ) {
        buildRecoveryInsight(
            summary = currentSummary,
            durationMs = durationMs,
            analysisStartFraction = analysisStartFraction
        )
    }
    val contributionInsight = remember(
        currentSummary.lowerBodyDriveScore,
        currentSummary.loadFocusLabel,
        currentSummary.insideSupportRatio,
        currentSummary.isSuccess
    ) {
        buildContributionScoreInsight(currentSummary)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (selectedTabIndex == 0) {
            AttemptResultOverviewCard(
                currentSummary = currentSummary,
                reachedHoldsText = reachedHoldsText,
                reachedHoldsSuffix = reachedHoldsSuffix
            )

            AttemptMetricScoreSection(
                retentionScore = currentSummary.stabilityRetentionScore ?: currentSummary.insideSupportRatio,
                recoveryScore = currentSummary.stabilityRecoveryScore,
                lowerBodyDriveScore = contributionInsight.lowerBodyScore,
                retentionCaption = buildRetentionCaption(
                    currentSummary.stabilityRetentionScore ?: currentSummary.insideSupportRatio
                ),
                recoveryCaption = recoveryInsight.caption,
                lowerBodyCaption = contributionInsight.driveCaption
            )

        ChartPanel(
            title = "안전성 점수 그래프",
            subtitle = ""
        ) {
            StabilityInsightTimelineChart(
                data = currentSummary.stabilityTimeline,
                durationMs = durationMs,
                dangerFractions = emptyList(),
                cruxStartFraction = cruxRange?.first,
                cruxEndFraction = cruxRange?.second,
                failureFraction = null,
                heartRateSeries = heartRateSeries,
                onTimeSelected = onStabilityTimelineSelected?.let { onSelected ->
                    { timeMs -> onSelected(previewStartMs(timeMs, lookbackMs = 2_000L)) }
                },
                modifier = Modifier.fillMaxWidth()
            )
/*

            Spacer(modifier = Modifier.height(1.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniInfoCard(
                    title = "가장 흔들린 시점",
                    value = recoveryInsight.lowestPointLabel,
                    onClick = recoveryInsight.lowestPointTimeMs?.let { timeMs ->
                        { onLowestPointSelected(previewStartMs(timeMs, lookbackMs = 2_000L)) }
                    },
                    modifier = Modifier.weight(1f)
                )
                MiniInfoCard(
                    title = "다시 안정된 시점",
                    value = recoveryInsight.recoveryPointLabel,
                    onClick = recoveryInsight.recoveryPointTimeMs?.let { timeMs ->
                        { onRecoveryPointSelected(previewStartMs(timeMs, lookbackMs = 2_000L)) }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
*/
        }

            AttemptKeySummaryCard(
                isSuccess = isSuccess,
                feedbackLine = feedbackLine,
                riskLine = riskLine,
                coachingLine = coachingLine
            )
        } else {
            AttemptBodyLoadMapCard(
                distribution = currentSummary.bodyLoadDistribution,
                loadFocusLabel = currentSummary.loadFocusLabel,
                topJointLoads = currentSummary.topJointLoads,
                loadFocusValue = contributionInsight.loadFocusValue,
                loadFocusAccentColor = contributionInsight.loadFocusAccentColor
            )

        ChartPanel(
            title = "몸 사용 흐름",
            subtitle = ""
        ) {
            FootUpperLimbContributionChart(
                lowerBodyScore = contributionInsight.lowerBodyScore,
                upperLimbScore = contributionInsight.upperLimbScore,
                lowerBodyLabel = contributionInsight.lowerBodyBarLabel,
                upperLimbLabel = contributionInsight.upperLimbBarLabel,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = contributionInsight.summaryLine,
                color = AnalysisText,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            }
        }
    }
}

@Composable
internal fun AttemptAnalysisTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("통계", "신체 부하")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
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
                    Text(
                        text = title,
                        color = if (isSelected) {
                            AnalysisText
                        } else {
                            AnalysisText.copy(alpha = 0.64f)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .width(30.dp)
                                .height(2.dp)
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
private fun LegacyAttemptResultOverviewCard(
    currentSummary: FinalAnalysisAttemptSummary,
    reachedHoldsText: String,
    reachedHoldsSuffix: String?,
    modifier: Modifier = Modifier
) {
    val reachedHoldValue = if (reachedHoldsText == FinalAnalysisUnknownMetricText) {
        FinalAnalysisUnknownMetricText
    } else {
        "${reachedHoldsText}번"
    }
    val reachedHoldSuffix = if (
        reachedHoldsText == FinalAnalysisUnknownMetricText ||
        reachedHoldsSuffix.isNullOrBlank()
    ) {
        null
    } else {
        "/${reachedHoldsSuffix.removePrefix("/")}번"
    }
    val cruxValue = currentSummary.primaryCruxHoldNo
        ?.let { "${it}번 홀드" }
        ?: FinalAnalysisUnknownMetricText
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AttemptOverallScoreCard(
                    title = "이번 시도 결과",
                    score = currentSummary.overallMovementScore,
                    isSuccess = currentSummary.isSuccess,
                    modifier = Modifier
                        .width(136.dp)
                        .fillMaxHeight()
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AttemptOverviewMetricBlock(
                        label = "문제 풀이 여부",
                        value = if (currentSummary.isSuccess) "성공" else "실패",
                        accentColor = if (currentSummary.isSuccess) AnalysisPrimary else AnalysisFailure,
                        contentSpacing = 1.dp
                    )
                    AttemptOverviewMetricBlock(
                        label = "도달 홀드",
                        value = reachedHoldValue,
                        trailingValue = reachedHoldSuffix,
                        accentColor = if (currentSummary.isSuccess) AnalysisPrimary else AnalysisFailure,
                        contentSpacing = 1.dp
                    )
                    AttemptOverviewMetricBlock(
                        label = "대표 크럭스",
                        value = cruxValue,
                        accentColor = if (currentSummary.isSuccess) AnalysisPrimary else AnalysisFailure,
                        contentSpacing = 1.dp
                    )
                }
            }

        }
    }
}

@Composable
private fun LegacyAttemptOverviewMetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingValue: String? = null,
    accentColor: Color = AnalysisText,
    contentSpacing: Dp = 10.dp
) {
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)
    val primaryValueFontSize = when {
        value.length >= 7 -> 24.sp
        value.length >= 5 -> 26.sp
        else -> 28.sp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(contentSpacing)
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
}

@Composable
private fun LegacyAttemptOverallScoreCard(
    title: String? = null,
    score: Int?,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isSuccess) scoreAccentColor(score) else AnalysisFailure
    val safeScore = score?.coerceIn(0, 100)
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        title?.let {
            Text(
                text = it,
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Box(
            modifier = Modifier.size(108.dp),
            contentAlignment = Alignment.Center
        ) {
            if (useBrandAccent) {
                AnalysisAccentCircularProgressIndicator(
                    progress = (safeScore ?: 0) / 100f,
                    accentColor = accentColor,
                    modifier = Modifier.size(96.dp),
                    trackColor = AnalysisCardColor,
                    strokeWidth = 8.dp
                )
            } else {
                CircularProgressIndicator(
                    progress = { (safeScore ?: 0) / 100f },
                    modifier = Modifier.size(96.dp),
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
                text = buildScoreGradeLabel(safeScore),
                color = AnalysisText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        }
    }
}

@Composable
private fun AttemptResultOverviewCard(
    currentSummary: FinalAnalysisAttemptSummary,
    reachedHoldsText: String,
    reachedHoldsSuffix: String?,
    modifier: Modifier = Modifier
) {
    val reachedHoldValue = if (reachedHoldsText == FinalAnalysisUnknownMetricText) {
        FinalAnalysisUnknownMetricText
    } else {
        "${reachedHoldsText}번"
    }
    val reachedHoldTrailingValue = if (
        reachedHoldsText == FinalAnalysisUnknownMetricText ||
        reachedHoldsSuffix.isNullOrBlank()
    ) {
        null
    } else {
        "/${reachedHoldsSuffix.removePrefix("/")}번"
    }
    val cruxValue = currentSummary.primaryCruxHoldNo
        ?.let { "${it}번 홀드" }
        ?: FinalAnalysisUnknownMetricText

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "이번 시도 결과",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            AttemptOverallScoreCard(
                score = currentSummary.overallMovementScore,
                isSuccess = currentSummary.isSuccess
            )

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
                AttemptOverviewMetricBlock(
                    label = "문제 풀이 여부",
                    value = if (currentSummary.isSuccess) "성공" else "실패",
                    accentColor = if (currentSummary.isSuccess) AnalysisPrimary else AnalysisFailure,
                    modifier = Modifier.weight(1f)
                )

                AttemptOverviewMetricDivider()

                AttemptOverviewMetricBlock(
                    label = "도달 홀드",
                    value = reachedHoldValue,
                    trailingValue = reachedHoldTrailingValue,
                    accentColor = if (currentSummary.isSuccess) AnalysisPrimary else AnalysisFailure,
                    modifier = Modifier.weight(1f)
                )

                AttemptOverviewMetricDivider()

                AttemptOverviewMetricBlock(
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
private fun AttemptOverviewMetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    trailingValue: String? = null,
    accentColor: Color = AnalysisText,
    contentSpacing: Dp = 10.dp
) {
    val useBrandAccent = hasAnalysisGradientAccent(accentColor)
    val primaryValueFontSize = when {
        value.length >= 7 -> 24.sp
        value.length >= 5 -> 26.sp
        else -> 28.sp
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(contentSpacing)
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
private fun AttemptOverallScoreCard(
    title: String? = null,
    score: Int?,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isSuccess) scoreAccentColor(score) else AnalysisFailure
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
            title?.let {
                Text(
                    text = it,
                    color = AnalysisText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            } ?: Text(
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
                    text = buildScoreGradeLabel(safeScore),
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
private fun AttemptOverviewMetricDivider() {
    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxHeight()
            .width(1.dp)
            .background(AnalysisCardColor)
    )
}

@Composable
private fun AttemptScoreSection(
    retentionScore: Int?,
    recoveryScore: Int?,
    lowerBodyDriveScore: Int?,
    retentionCaption: String,
    recoveryCaption: String,
    lowerBodyCaption: String,
    loadFocusValue: String,
    loadFocusCaption: String,
    loadFocusAccentColor: Color,
    showLoadFocus: Boolean = true,
    modifier: Modifier = Modifier
) {
    val items = listOf(
            AttemptScoreRowState("안성성 유지", retentionScore, retentionCaption),
        AttemptScoreRowState("안정성 회복력", recoveryScore, recoveryCaption),
        AttemptScoreRowState("하체 주도성", lowerBodyDriveScore, lowerBodyCaption)
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
                AttemptScoreRow(item = item)
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AnalysisCardColor)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AnalysisCardColor)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(loadFocusAccentColor)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "부담 집중 부위",
                            color = AnalysisMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = loadFocusValue,
                            color = loadFocusAccentColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = loadFocusCaption,
                            color = AnalysisMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptMetricScoreSection(
    retentionScore: Int?,
    recoveryScore: Int?,
    lowerBodyDriveScore: Int?,
    retentionCaption: String,
    recoveryCaption: String,
    lowerBodyCaption: String,
    modifier: Modifier = Modifier
) {
    val items = listOf(
            AttemptScoreRowState("안성성 유지", retentionScore, retentionCaption),
        AttemptScoreRowState("안정성 회복력", recoveryScore, recoveryCaption),
        AttemptScoreRowState("하체 주도성", lowerBodyDriveScore, lowerBodyCaption)
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
                AttemptScoreRow(item = item)
                if (index != items.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AnalysisCardColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptLoadFocusCard(
    value: String,
    caption: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AnalysisCardColor)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "부담 집중 부위",
                    color = AnalysisMuted,
                    fontSize = 12.sp
                )
                Text(
                    text = value,
                    color = AnalysisText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
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
}

@Composable
private fun AttemptScoreRow(
    item: AttemptScoreRowState,
    modifier: Modifier = Modifier
) {
    val accentColor = scoreAccentColor(item.score)
    val progress = ((item.score ?: 0).coerceIn(0, 100) / 100f)
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
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.score?.let { "${it}점" } ?: FinalAnalysisUnknownMetricText,
                color = AnalysisText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (useBrandAccent) {
            AnalysisAccentLinearProgressBar(
                progress = progress,
                accentColor = accentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        } else {
            LinearProgressIndicator(
                progress = { progress },
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
private fun AttemptKeySummaryCard(
    isSuccess: Boolean,
    feedbackLine: String,
    riskLine: String,
    coachingLine: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "이번 시도 핵심",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            SummaryLineRow(
                label = if (isSuccess) "성공 포인트" else "실패 원인",
                text = feedbackLine,
                accentColor = if (isSuccess) AnalysisPrimary else AnalysisFailure
            )
            SummaryLineRow(
                label = "주의 포인트",
                text = riskLine,
                accentColor = Color(0xFFFF8A57)
            )
            SummaryLineRow(
                label = "다음 시도",
                text = coachingLine,
                accentColor = AnalysisPrimary
            )
        }
    }
}

@Composable
private fun SummaryLineRow(
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
                .size(width = 4.dp, height = 44.dp)
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
            Text(
                text = label,
                color = AnalysisText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = text,
                color = AnalysisText,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ResultTagCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ChartPanel(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
private fun MiniInfoCard(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AnalysisCardColor)
            .border(
                width = if (onClick != null) 1.dp else 0.dp,
                color = if (onClick != null) AnalysisPrimary.copy(alpha = 0.24f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
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
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (onClick != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(brush = requireNotNull(analysisSurfaceBrushFor(AnalysisPrimary)))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    AnalysisAccentText(
                        text = "탭해서 재생",
                        accentColor = AnalysisPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = AnalysisText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

private data class RecoveryInsight(
    val label: String,
    val caption: String,
    val accentColor: Color,
    val lowestPointLabel: String,
    val recoveryPointLabel: String,
    val lowestPointTimeMs: Long? = null,
    val recoveryPointTimeMs: Long? = null
)

private data class ContributionInsight(
    val driveLabel: String,
    val driveCaption: String,
    val driveAccentColor: Color,
    val loadFocusValue: String,
    val loadFocusCaption: String,
    val loadFocusAccentColor: Color,
    val lowerBodyScore: Int,
    val upperLimbScore: Int,
    val lowerBodyBarLabel: String,
    val upperLimbBarLabel: String,
    val summaryLine: String
)

private fun buildResultCaption(
    currentSummary: FinalAnalysisAttemptSummary,
    previousSummary: FinalAnalysisAttemptSummary?
): String {
    val currentReached = currentSummary.reachedHolds
    val previousReached = previousSummary?.reachedHolds

    return when {
        previousSummary == null -> "도달 홀드와 대표 지표를 먼저 확인해 보세요."
        currentReached == null || previousReached == null -> "이전 시도와 비교할 수 있는 도달 기록이 아직 부족해요."
        currentReached > previousReached -> "이전 시도보다 ${currentReached - previousReached}홀드 더 올라갔어요."
        currentReached < previousReached -> "이전 시도보다 ${previousReached - currentReached}홀드 낮게 마무리됐어요."
        else -> "이전 시도와 비슷한 높이까지 도달했어요."
    }
}

private fun buildRetentionCaption(value: Int?): String {
    return when {
        value == null -> "집계할 안정성 데이터가 아직 부족해요."
        value >= 75 -> "중심을 비교적 안정적으로 유지한 시도가 많았어요."
        value >= 60 -> "대체로 안정적이었지만 몇 구간은 흔들렸어요."
        else -> "흔들리거나 중심이 무너진 구간이 자주 나타났어요."
    }
}

private fun buildOverallMovementCaption(score: Int?): String {
    return when {
        score == null -> "움직임 종합 점수를 준비하고 있어요."
        score >= 82 -> "균형과 흐름이 모두 안정적이었어요."
        score >= 70 -> "전반적인 움직임 흐름이 좋았어요."
        score >= 58 -> "좋은 장면이 있었지만 보정도 보였어요."
        score >= 46 -> "특정 구간의 균형과 연결을 더 다듬어 보세요."
        else -> "핵심 구간에서 움직임 보정이 많이 필요해요."
    }
}

private fun buildScoreGradeLabel(score: Int?): String {
    return when {
        score == null -> FinalAnalysisUnknownMetricText
        score >= 85 -> "우수함"
        score >= 70 -> "좋음"
        score >= 55 -> "보통"
        else -> "보완 필요"
    }
}

private fun scoreAccentColor(score: Int?): Color {
    return when {
        score == null -> AnalysisMuted
        score >= 85 -> AnalysisSuccess
        score >= 70 -> AnalysisPrimary
        score >= 55 -> Color(0xFFFFC857)
        else -> AnalysisFailure
    }
}

private data class AttemptScoreRowState(
    val title: String,
    val score: Int?,
    val caption: String
)

private fun buildRecoveryInsight(
    summary: FinalAnalysisAttemptSummary,
    durationMs: Long,
    analysisStartFraction: Float?
): RecoveryInsight {
    val timeline = summary.stabilityTimeline
    val startIndex = timeline.startIndex(analysisStartFraction)
    if (timeline.size - startIndex < 3) {
        return RecoveryInsight(
            label = "판단 보류",
            caption = "회복 흐름을 읽기 어려워요.",
            accentColor = AnalysisMuted,
            lowestPointLabel = "데이터 부족",
            recoveryPointLabel = "데이터 부족"
        )
    }

    val lowestIndex = (startIndex..timeline.lastIndex).minByOrNull { timeline[it] } ?: startIndex
    val lowestPointTimeMs = fractionToTimeMs(
        fraction = lowestIndex.toFloat() / timeline.lastIndex.toFloat(),
        durationMs = durationMs
    )
    val lowestPointLabel = fractionToTimeLabel(
        fraction = lowestIndex.toFloat() / timeline.lastIndex.toFloat(),
        durationMs = durationMs
    )
    val recoveryIndex = findRecoveryIndex(timeline, lowestIndex)
    val recoveryPointTimeMs = recoveryIndex?.let {
        fractionToTimeMs(
            fraction = it.toFloat() / timeline.lastIndex.toFloat(),
            durationMs = durationMs
        )
    }
    val recoveryPointLabel = recoveryIndex?.let {
        fractionToTimeLabel(
            fraction = it.toFloat() / timeline.lastIndex.toFloat(),
            durationMs = durationMs
        )
    } ?: "회복 신호 약함"

    val recoverySamples = recoveryIndex?.minus(lowestIndex)
    val recoveryScore = summary.stabilityRecoveryScore ?: when {
        recoverySamples == null && summary.isSuccess -> 56
        recoverySamples == null -> 26
        recoverySamples <= 2 -> 82
        recoverySamples <= 3 -> 70
        recoverySamples <= 6 -> 56
        recoverySamples <= 8 -> 42
        else -> 28
    }
    val recoveryCaption = when {
        recoveryScore >= 76 -> "$lowestPointLabel 이후 자세를 빠르게 다시 세웠어요."
        recoveryScore >= 64 -> "$lowestPointLabel 이후 비교적 빠르게 안정됐어요."
        recoveryScore >= 52 -> "흔들린 뒤 다시 흐름을 회복하는 장면이 보였어요."
        recoveryScore >= 40 -> "회복은 했지만 안정권으로 돌아오는 데 시간이 걸렸어요."
        else -> "흔들린 뒤 자세를 다시 회복하는 데 오래 걸렸어요."
    }
    val recoveryAccentColor = when {
        recoveryScore >= 68 -> AnalysisPrimary
        recoveryScore >= 52 -> Color(0xFFFFC271)
        else -> AnalysisFailure
    }
    return when {
        recoverySamples == null && summary.isSuccess -> RecoveryInsight(
            label = "보통",
            caption = "완등은 했지만 회복 장면이 또렷하게 읽히진 않았어요.",
            accentColor = Color(0xFFFFC271),
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )

        recoverySamples == null -> RecoveryInsight(
            label = "매우 느림",
            caption = "흔들린 뒤 자세를 다시 세우는 흐름이 약했어요.",
            accentColor = AnalysisFailure,
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )

        recoverySamples <= 2 -> RecoveryInsight(
            label = "매우 빠름",
            caption = "$lowestPointLabel 뒤 바로 안정권으로 회복했어요.",
            accentColor = AnalysisPrimary,
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )

        recoverySamples <= 3 -> RecoveryInsight(
            label = "빠름",
            caption = "$lowestPointLabel 뒤 비교적 빠르게 안정됐어요.",
            accentColor = AnalysisPrimary,
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )

        recoverySamples <= 6 -> RecoveryInsight(
            label = "보통",
            caption = "흔들린 뒤 자세를 다시 세우는 데 조금 시간이 걸렸어요.",
            accentColor = Color(0xFFFFC271),
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )

        recoverySamples <= 8 -> RecoveryInsight(
            label = "느림",
            caption = "회복은 했지만 안정권으로 돌아오는 속도가 느렸어요.",
            accentColor = AnalysisFailure,
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )

        else -> RecoveryInsight(
            label = "매우 느림",
            caption = "흔들린 뒤 회복 구간이 길게 이어졌어요.",
            accentColor = AnalysisFailure,
            lowestPointLabel = lowestPointLabel,
            recoveryPointLabel = recoveryPointLabel,
            lowestPointTimeMs = lowestPointTimeMs,
            recoveryPointTimeMs = recoveryPointTimeMs
        )
    }
}

private fun buildContributionInsight(summary: FinalAnalysisAttemptSummary): ContributionInsight {
    val loadFocus = summary.loadFocusLabel
    val isArmFocus = loadFocus.matchesAny("arm", "팔", "손")
    val isLegFocus = loadFocus.matchesAny("leg", "다리", "발")
    val isCoreFocus = loadFocus.matchesAny("core", "몸통", "코어")

    var lowerBodyScore = 54
    var upperLimbScore = 46

    when {
        isArmFocus -> {
            lowerBodyScore -= 16
            upperLimbScore += 18
        }

        isLegFocus -> {
            lowerBodyScore += 16
            upperLimbScore -= 10
        }

        isCoreFocus -> {
            lowerBodyScore += 10
            upperLimbScore -= 4
        }
    }

    when {
        (summary.insideSupportRatio ?: 0) >= 75 -> lowerBodyScore += 8
        (summary.insideSupportRatio ?: 100) < 55 -> upperLimbScore += 8
    }

    if (summary.isSuccess) {
        lowerBodyScore += 6
    }

    lowerBodyScore = lowerBodyScore.coerceIn(18, 82)
    upperLimbScore = upperLimbScore.coerceIn(18, 82)

    val driveLabel = fiveLevelLabel(lowerBodyScore)
    val driveCaption = when (driveLabel) {
        "매우 높음" -> "다리로 밀어 올린 장면이 많았어요."
        "높음" -> "다리 사용이 더 잘 보였어요."
        "보통" -> "다리와 팔을 비슷하게 썼어요."
        "낮음" -> "팔로 먼저 버티는 장면이 많았어요."
        else -> "팔 힘이 먼저 들어갔어요."
    }
    val driveAccentColor = when (driveLabel) {
        "매우 높음", "높음" -> AnalysisPrimary
        "보통" -> Color(0xFFFFC271)
        else -> AnalysisFailure
    }

    val loadFocusValue = loadFocus ?: FinalAnalysisUnknownMetricText
    val loadFocusCaption = when {
        isArmFocus -> "팔 부담이 컸어요."
        isLegFocus -> "한쪽 다리 의존이 컸어요."
        isCoreFocus -> "몸통으로 버틴 장면이 많았어요."
        else -> "부담 부위가 뚜렷하진 않았어요."
    }
    val loadFocusAccentColor = when {
        isArmFocus -> AnalysisFailure
        isLegFocus || isCoreFocus -> AnalysisPrimary
        else -> AnalysisMuted
    }

    return ContributionInsight(
        driveLabel = driveLabel,
        driveCaption = driveCaption,
        driveAccentColor = driveAccentColor,
        loadFocusValue = loadFocusValue,
        loadFocusCaption = loadFocusCaption,
        loadFocusAccentColor = loadFocusAccentColor,
        lowerBodyScore = lowerBodyScore,
        upperLimbScore = upperLimbScore,
        lowerBodyBarLabel = fiveLevelLabel(lowerBodyScore),
        upperLimbBarLabel = fiveLevelLabel(upperLimbScore),
        summaryLine = when {
            isArmFocus -> "이번 시도는 팔에 힘이 많이 들어가 다리 사용이 약했어요."
            isLegFocus -> "다리로 버티고 밀어 올리는 장면이 잘 보였어요."
            isCoreFocus -> "몸통으로 균형을 잡으며 이어간 장면이 보였어요."
            else -> "팔과 다리를 함께 썼지만 몇 장면에서는 팔로 버텼어요."
        }
    )
}

private fun buildContributionScoreInsight(summary: FinalAnalysisAttemptSummary): ContributionInsight {
    val loadFocus = summary.loadFocusLabel
    val isArmFocus = loadFocus.matchesAny("arm", "왼", "오")
    val isLegFocus = loadFocus.matchesAny("leg", "다리", "발")
    val isCoreFocus = loadFocus.matchesAny("core", "몸통", "코어")

    val lowerBodyScore = (summary.lowerBodyDriveScore ?: run {
        var fallbackScore = 54
        when {
            isArmFocus -> fallbackScore -= 16
            isLegFocus -> fallbackScore += 16
            isCoreFocus -> fallbackScore += 10
        }
        when {
            (summary.insideSupportRatio ?: 0) >= 75 -> fallbackScore += 8
            (summary.insideSupportRatio ?: 100) < 55 -> fallbackScore -= 6
        }
        if (summary.isSuccess) {
            fallbackScore += 6
        }
        fallbackScore
    }).coerceIn(0, 100)
    val upperLimbScore = (100 - lowerBodyScore).coerceIn(0, 100)

    val loadFocusValue = loadFocus ?: FinalAnalysisUnknownMetricText
    val loadFocusCaption = when {
        isArmFocus -> "팔과 손에 부담이 몰린 장면이 비교적 많았어요."
        isLegFocus -> "발과 다리로 지지한 장면이 비교적 많이 보였어요."
        isCoreFocus -> "몸통과 코어로 버티며 연결한 장면이 많았어요."
        else -> "특정 부위로 부담이 뚜렷하게 쏠리진 않았어요."
    }
    val loadFocusAccentColor = when {
        isArmFocus -> AnalysisFailure
        isLegFocus || isCoreFocus -> AnalysisPrimary
        else -> AnalysisMuted
    }

    return ContributionInsight(
        driveLabel = "${lowerBodyScore}점",
        driveCaption = when {
            lowerBodyScore >= 80 -> "다리와 골반이 먼저 버티며 중심을 만든 장면이 잘 보였어요."
            lowerBodyScore >= 68 -> "하체로 밀어 올리며 동작을 이어가는 흐름이 비교적 잘 보였어요."
            lowerBodyScore >= 56 -> "하체 사용은 있었지만 구간에 따라 팔 의존이 함께 보였어요."
            lowerBodyScore >= 44 -> "팔이 먼저 버티고 하체가 늦게 따라오는 장면이 꽤 있었어요."
            else -> "팔에 힘이 먼저 들어가고 하체 연결이 늦는 패턴이 자주 보였어요."
        },
        driveAccentColor = when {
            lowerBodyScore >= 68 -> AnalysisPrimary
            lowerBodyScore >= 52 -> Color(0xFFFFC271)
            else -> AnalysisFailure
        },
        loadFocusValue = loadFocusValue,
        loadFocusCaption = loadFocusCaption,
        loadFocusAccentColor = loadFocusAccentColor,
        lowerBodyScore = lowerBodyScore,
        upperLimbScore = upperLimbScore,
        lowerBodyBarLabel = "${lowerBodyScore}점",
        upperLimbBarLabel = "${upperLimbScore}점",
        summaryLine = when {
            lowerBodyScore >= 72 -> "하체가 먼저 중심을 만들고 팔은 연결해 주는 흐름이 비교적 안정적으로 이어졌어요."
            lowerBodyScore >= 56 -> "하체 사용은 있었지만 구간에 따라 팔 의존이 함께 나타난 시도였어요."
            else -> "이번 시도는 팔이 먼저 버티고 하체 연결이 늦는 장면이 비교적 자주 나타났어요."
        }
    )
}

private fun estimateDurationMs(summary: FinalAnalysisAttemptSummary): Long {
    val latestPointMs = summary.analysisPoints.maxOfOrNull { it.timeMs }
    return summary.videoDurationMs
        ?.takeIf { it > 0L }
        ?: listOfNotNull(
            latestPointMs?.plus(5_000L),
            summary.primaryCruxDurationMs?.toLong()?.times(3L),
            30_000L
        ).maxOrNull()
        ?: 30_000L
}

private fun buildCruxRange(
    durationMs: Long,
    focusFraction: Float?,
    cruxDurationMs: Int?
): Pair<Float, Float>? {
    val focus = focusFraction ?: return null
    val durationFraction = ((cruxDurationMs ?: 4_000).toFloat() / durationMs.toFloat())
        .coerceIn(0.08f, 0.28f)
    val start = (focus - durationFraction / 2f).coerceAtLeast(0f)
    val end = (focus + durationFraction / 2f).coerceAtMost(1f)
    return start to end
}

private fun buildFailureFraction(
    summary: FinalAnalysisAttemptSummary,
    durationMs: Long,
    lowestPointFraction: Float?
): Float? {
    if (summary.isSuccess) {
        return null
    }

    val latestPointFraction = summary.analysisPoints.maxOfOrNull { it.timeMs }
        ?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }

    return when {
        latestPointFraction != null && lowestPointFraction != null &&
            abs(latestPointFraction - lowestPointFraction) < 0.16f -> lowestPointFraction

        lowestPointFraction != null -> lowestPointFraction
        else -> latestPointFraction ?: summary.stabilityFocusFraction
    }
}

private fun findRecoveryIndex(timeline: List<Float>, lowestIndex: Int): Int? {
    if (lowestIndex >= timeline.lastIndex) return null

    val lowestValue = timeline[lowestIndex]
    val recoveryTarget = maxOf(0.58f, lowestValue + 0.18f)

    for (index in lowestIndex + 1..timeline.lastIndex) {
        val current = timeline[index]
        val nextWindow = timeline.subList(index, minOf(index + 2, timeline.lastIndex) + 1)
        val windowAverage = nextWindow.average().toFloat()
        if (current >= recoveryTarget && windowAverage >= recoveryTarget - 0.04f) {
            return index
        }
    }
    return null
}

private fun List<Float>.startIndex(startFraction: Float?): Int {
    if (isEmpty()) return 0
    val fraction = startFraction ?: return 0
    return (fraction.coerceIn(0f, 1f) * lastIndex.toFloat())
        .roundToInt()
        .coerceIn(0, lastIndex)
}

private fun fractionToTimeLabel(
    fraction: Float,
    durationMs: Long
): String {
    return fractionToTimeMs(fraction, durationMs).toVideoTimeString()
}

private fun fractionToTimeMs(
    fraction: Float,
    durationMs: Long
): Long {
    return (durationMs * fraction.coerceIn(0f, 1f)).roundToInt().toLong()
}

private fun String?.matchesAny(vararg keywords: String): Boolean {
    val safeValue = this?.lowercase().orEmpty()
    return keywords.any { keyword -> safeValue.contains(keyword.lowercase()) }
}

private fun fiveLevelLabel(score: Int): String {
    return when {
        score >= 72 -> "매우 높음"
        score >= 62 -> "높음"
        score >= 50 -> "보통"
        score >= 38 -> "낮음"
        else -> "매우 낮음"
    }
}
