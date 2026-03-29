package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.displayFeedbackTypeLabel
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisInsightCard
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.MetricHeadline
import kotlin.math.abs

@Composable
internal fun ProblemStatsPanel(
    currentSummary: FinalAnalysisAttemptSummary,
    previousSummary: FinalAnalysisAttemptSummary?,
    reachedHoldsText: String,
    reachedHoldsSuffix: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 28.dp)
    ) {
        MetricHeadline(
            title = if (currentSummary.isSuccess) "완등까지 갔어요" else "이번 시도 최고 도달",
            value = reachedHoldsText,
            suffix = reachedHoldsSuffix,
            caption = buildReachedCaption(
                currentSummary = currentSummary,
                previousSummary = previousSummary
            ),
            valueColor = if (currentSummary.isSuccess) AnalysisSuccess else AnalysisText
        )

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsMetricCard(
                title = "중심 안정",
                value = currentSummary.insideSupportRatioText,
                description = buildMetricDeltaLabel(
                    currentValue = currentSummary.insideSupportRatio,
                    previousValue = previousSummary?.insideSupportRatio,
                    suffix = "%p"
                ),
                accentColor = AnalysisPrimary,
                modifier = Modifier.weight(1f)
            )
            StatsMetricCard(
                title = "접촉 유지",
                value = currentSummary.stableContactRatioText,
                description = buildMetricDeltaLabel(
                    currentValue = currentSummary.stableContactRatio,
                    previousValue = previousSummary?.stableContactRatio,
                    suffix = "%p"
                ),
                accentColor = AnalysisPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsMetricCard(
                title = "위험 이벤트",
                value = currentSummary.dangerEventCount?.let { "${it}회" }
                    ?: FinalAnalysisUnknownMetricText,
                description = buildDangerDeltaLabel(
                    currentValue = currentSummary.dangerEventCount,
                    previousValue = previousSummary?.dangerEventCount
                ),
                accentColor = if ((currentSummary.dangerEventCount ?: 0) > 0) {
                    AnalysisFailure
                } else {
                    AnalysisPrimary
                },
                modifier = Modifier.weight(1f)
            )
            StatsMetricCard(
                title = "대표 크럭스",
                value = currentSummary.primaryCruxHoldNo?.let { "${it}번 홀드" }
                    ?: FinalAnalysisUnknownMetricText,
                description = currentSummary.primaryCruxDurationMs
                    ?.let { "${formatDurationLabel(it)} 동안 머문 구간" }
                    ?: "반복적으로 막힌 핵심 구간",
                accentColor = AnalysisSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        AnalysisInsightCard(
            title = "이전 시도와 비교",
            highlights = buildComparisonHighlights(
                currentSummary = currentSummary,
                previousSummary = previousSummary
            ),
            emptyText = "첫 번째 시도라 비교할 이전 시도가 없어요."
        )

        Spacer(modifier = Modifier.height(18.dp))

        AnalysisInsightCard(
            title = "이번 시도 포인트",
            highlights = buildStatHighlights(currentSummary),
            emptyText = "핵심 지표가 아직 충분하지 않아요."
        )
    }
}

@Composable
private fun StatsMetricCard(
    title: String,
    value: String,
    description: String,
    accentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = title,
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = AnalysisMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

private fun buildReachedCaption(
    currentSummary: FinalAnalysisAttemptSummary,
    previousSummary: FinalAnalysisAttemptSummary?
): String {
    val currentHolds = currentSummary.reachedHolds
    val previousHolds = previousSummary?.reachedHolds

    return when {
        previousSummary == null -> "이번 시도 결과"
        currentHolds == null || previousHolds == null -> "이전 시도와 비교할 수 있는 데이터가 부족해요."
        currentHolds == previousHolds -> "이전 시도와 같은 높이까지 도달했어요."
        currentHolds > previousHolds -> "이전 시도보다 ${currentHolds - previousHolds}홀드 더 올라갔어요."
        else -> "이전 시도보다 ${previousHolds - currentHolds}홀드 낮은 구간에서 멈췄어요."
    }
}

private fun buildMetricDeltaLabel(
    currentValue: Int?,
    previousValue: Int?,
    suffix: String
): String {
    return when {
        currentValue == null || previousValue == null -> "이전 시도와 비교 데이터가 없어요."
        currentValue == previousValue -> "이전 시도와 비슷한 수준이에요."
        currentValue > previousValue -> "이전보다 ${currentValue - previousValue}$suffix 좋아졌어요."
        else -> "이전보다 ${previousValue - currentValue}$suffix 낮아졌어요."
    }
}

private fun buildDangerDeltaLabel(
    currentValue: Int?,
    previousValue: Int?
): String {
    return when {
        currentValue == null || previousValue == null -> "흔들림이 감지된 횟수예요."
        currentValue == previousValue -> "이전 시도와 같은 수준이에요."
        currentValue < previousValue -> "이전보다 ${previousValue - currentValue}회 줄었어요."
        else -> "이전보다 ${currentValue - previousValue}회 늘었어요."
    }
}

private fun buildComparisonHighlights(
    currentSummary: FinalAnalysisAttemptSummary,
    previousSummary: FinalAnalysisAttemptSummary?
): List<String> {
    if (previousSummary == null) return emptyList()

    return buildList {
        val reachedDelta = compareMetric(
            current = currentSummary.reachedHolds,
            previous = previousSummary.reachedHolds
        )
        reachedDelta?.let { delta ->
            add(
                if (delta > 0) {
                    "최고 도달 홀드가 이전보다 ${delta}홀드 높아졌어요."
                } else if (delta < 0) {
                    "최고 도달 홀드가 이전보다 ${abs(delta)}홀드 낮아졌어요."
                } else {
                    "최고 도달 홀드는 이전 시도와 비슷했어요."
                }
            )
        }

        val stabilityDelta = compareMetric(
            current = currentSummary.insideSupportRatio,
            previous = previousSummary.insideSupportRatio
        )
        stabilityDelta?.let { delta ->
            add(
                if (delta > 0) {
                    "중심 안정도가 ${delta}%p 좋아졌어요."
                } else if (delta < 0) {
                    "중심 안정도가 ${abs(delta)}%p 낮아졌어요."
                } else {
                    "중심 안정도는 비슷하게 유지됐어요."
                }
            )
        }

        val dangerDelta = compareMetric(
            current = currentSummary.dangerEventCount,
            previous = previousSummary.dangerEventCount
        )
        dangerDelta?.let { delta ->
            add(
                if (delta < 0) {
                    "위험 이벤트가 ${abs(delta)}회 줄어서 흐름이 더 안정적이었어요."
                } else if (delta > 0) {
                    "위험 이벤트가 ${delta}회 늘어서 흔들린 구간이 더 많았어요."
                } else {
                    "위험 이벤트 수는 이전 시도와 비슷했어요."
                }
            )
        }
    }.take(3)
}

private fun buildStatHighlights(summary: FinalAnalysisAttemptSummary): List<String> {
    return buildList {
        summary.primaryCruxHoldNo?.let { add("가장 오래 막힌 구간은 ${it}번 홀드 전후였어요.") }
        summary.primaryCruxDurationMs
            ?.takeIf { it > 0 }
            ?.let { add("대표 크럭스는 ${formatDurationLabel(it)} 동안 이어졌어요.") }
        summary.feedbackTypes.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { add("이번 시도의 대표 패턴은 ${displayFeedbackTypeLabel(it)} 쪽이었어요.") }
        summary.loadFocusLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { add("$it 쪽에 힘이 몰리면서 자세 균형이 흔들렸어요.") }
    }.ifEmpty {
        summary.failureHighlights.take(3)
    }
}

private fun compareMetric(current: Int?, previous: Int?): Int? {
    if (current == null || previous == null) return null
    return current - previous
}

private fun formatDurationLabel(durationMs: Int): String {
    val totalSeconds = (durationMs / 1000f).toInt().coerceAtLeast(1)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) {
        "${minutes}분 ${seconds}초"
    } else {
        "${seconds}초"
    }
}
