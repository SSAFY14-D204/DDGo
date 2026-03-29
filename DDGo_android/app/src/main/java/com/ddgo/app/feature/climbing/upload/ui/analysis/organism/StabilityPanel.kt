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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.StabilityInsightTimelineChart
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun StabilityPanel(
    currentSummary: FinalAnalysisAttemptSummary,
    focusReasonText: String?,
    modifier: Modifier = Modifier
) {
    val estimatedDurationMs = remember(
        currentSummary.videoDurationMs,
        currentSummary.analysisPoints,
        currentSummary.primaryCruxDurationMs
    ) {
        estimateDurationMs(currentSummary)
    }
    val lowestPointFraction = remember(currentSummary.stabilityTimeline) {
        currentSummary.stabilityTimeline.lowestPointFraction()
    }
    val dangerFractions = remember(
        currentSummary.stabilityTimeline,
        currentSummary.dangerEventCount,
        currentSummary.stabilityFocusFraction
    ) {
        buildDangerFractions(currentSummary)
    }
    val cruxRange = remember(
        currentSummary.primaryCruxDurationMs,
        currentSummary.stabilityFocusFraction,
        estimatedDurationMs
    ) {
        buildCruxRange(
            durationMs = estimatedDurationMs,
            focusFraction = currentSummary.stabilityFocusFraction,
            cruxDurationMs = currentSummary.primaryCruxDurationMs
        )
    }
    val failureFraction = remember(
        currentSummary.isSuccess,
        currentSummary.analysisPoints,
        currentSummary.stabilityTimeline,
        currentSummary.stabilityFocusFraction,
        lowestPointFraction,
        estimatedDurationMs
    ) {
        buildFailureFraction(
            summary = currentSummary,
            durationMs = estimatedDurationMs,
            lowestPointFraction = lowestPointFraction
        )
    }
    val lowestPointLabel = remember(lowestPointFraction, estimatedDurationMs) {
        lowestPointFraction?.let {
            formatTimeLabel((estimatedDurationMs * it).roundToInt().toLong())
        } ?: "확인 필요"
    }
    val cruxRangeLabel = remember(cruxRange, estimatedDurationMs) {
        cruxRange?.let { range ->
            "${formatTimeLabel((estimatedDurationMs * range.first).roundToInt().toLong())} ~ " +
                formatTimeLabel((estimatedDurationMs * range.second).roundToInt().toLong())
        } ?: "크럭스 구간 없음"
    }
    val failureLabel = remember(failureFraction, currentSummary.isSuccess, estimatedDurationMs) {
        when {
            currentSummary.isSuccess -> "완등"
            failureFraction != null -> formatTimeLabel(
                (estimatedDurationMs * failureFraction).roundToInt().toLong()
            )
            else -> "확인 필요"
        }
    }
    val summaryLine = remember(
        currentSummary.isSuccess,
        focusReasonText,
        lowestPointLabel,
        failureLabel
    ) {
        focusReasonText?.takeIf { it.isNotBlank() }
            ?: if (currentSummary.isSuccess) {
                "완등 전까지 안정성이 크게 무너지지 않았어요."
            } else {
                "$lowestPointLabel 부근에서 안정성이 가장 크게 흔들렸어요."
            }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Text(
            text = "흔들린 구간만 한눈에 보기",
            color = AnalysisText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "그래프가 내려가는 구간만 위험 표시를 남겼어요.",
            color = AnalysisMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StabilityInsightTimelineChart(
                    data = currentSummary.stabilityTimeline,
                    durationMs = estimatedDurationMs,
                    dangerFractions = dangerFractions,
                    cruxStartFraction = cruxRange?.first,
                    cruxEndFraction = cruxRange?.second,
                    failureFraction = failureFraction,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CompactSummaryCard(
                        title = "가장 흔들린 시점",
                        value = lowestPointLabel,
                        modifier = Modifier.weight(1f)
                    )
                    CompactSummaryCard(
                        title = if (currentSummary.isSuccess) "완등 시점" else "실패 시점",
                        value = failureLabel,
                        modifier = Modifier.weight(1f)
                    )
                }

                CompactSummaryCard(
                    title = "대표 크럭스",
                    value = currentSummary.primaryCruxHoldNo?.let { "${it}번 홀드 · $cruxRangeLabel" }
                        ?: cruxRangeLabel,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = summaryLine,
                    color = AnalysisText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun CompactSummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
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
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            )
        }
    }
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

private fun buildDangerFractions(summary: FinalAnalysisAttemptSummary): List<Float> {
    val desiredCount = (summary.dangerEventCount ?: 0).coerceIn(0, 2)
    if (desiredCount == 0) return emptyList()

    val timeline = summary.stabilityTimeline
    if (timeline.size < 3) return emptyList()

    val candidates = buildList {
        for (index in 1 until timeline.lastIndex) {
            val current = timeline[index]
            val previous = timeline[index - 1]
            val next = timeline[index + 1]
            val localDrop = ((previous + next) / 2f - current).coerceAtLeast(0f)

            if (current <= 0.42f && current <= previous && current <= next && localDrop >= 0.03f) {
                add(index to current)
            }
        }
    }.sortedBy { it.second }

    val selected = mutableListOf<Float>()
    candidates.forEach { (index, _) ->
        val fraction = index.toFloat() / timeline.lastIndex.toFloat()
        if (selected.none { abs(it - fraction) < 0.12f }) {
            selected += fraction
        }
        if (selected.size >= desiredCount) return@forEach
    }

    if (selected.isNotEmpty()) {
        return selected
    }

    val globalMin = timeline.minOrNull() ?: return emptyList()
    return if (globalMin <= 0.35f) {
        listOf(timeline.indexOf(globalMin).toFloat() / timeline.lastIndex.toFloat())
    } else {
        emptyList()
    }
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

    val latestPointMs = summary.analysisPoints.maxOfOrNull { it.timeMs }
        ?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }

    if (latestPointMs != null && lowestPointFraction != null && abs(latestPointMs - lowestPointFraction) < 0.16f) {
        return lowestPointFraction
    }

    return lowestPointFraction
        ?: latestPointMs
        ?: summary.stabilityFocusFraction
}

private fun List<Float>.lowestPointFraction(): Float? {
    if (size < 2) return null
    val lowestIndex = indices.minByOrNull { this[it] } ?: return null
    return lowestIndex.toFloat() / lastIndex.toFloat()
}

private fun formatTimeLabel(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
