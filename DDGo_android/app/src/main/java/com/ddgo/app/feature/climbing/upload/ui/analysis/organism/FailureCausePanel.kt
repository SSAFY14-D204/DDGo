package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.displayFeedbackTypeLabel
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisInsightCard

@Composable
internal fun FailureCausePanel(
    summary: FinalAnalysisAttemptSummary,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        AnalysisInsightCard(
            title = if (summary.isSuccess) "완등을 만든 포인트" else "실패가 나온 근거",
            highlights = buildFailureEvidence(summary),
            emptyText = if (summary.isSuccess) {
                "완등 흐름을 설명할 핵심 근거가 아직 충분하지 않아요."
            } else {
                summary.failureNarrative
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "핵심 장면",
                    color = AnalysisText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "시간을 누르면 영상이 그 지점으로 바로 이동해요.",
                    color = AnalysisMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                if (summary.analysisPoints.isEmpty()) {
                    Text(
                        text = "아직 표시할 분석 포인트가 없어요.",
                        color = AnalysisMuted,
                        fontSize = 14.sp
                    )
                } else {
                    summary.analysisPoints
                        .take(3)
                        .forEach { point ->
                            FailurePointCard(
                                point = point,
                                onTimestampClick = { onTimestampClick(point.timeMs) }
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun FailurePointCard(
    point: AnalysisPoint,
    onTimestampClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedPoint = point.toDisplayPoint()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AnalysisPrimary.copy(alpha = 0.16f))
                        .clickable(onClick = onTimestampClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = parsedPoint.timeLabel,
                        color = AnalysisPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = parsedPoint.title,
                    color = AnalysisText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (parsedPoint.details.isEmpty()) {
                Text(
                    text = point.description.replace("\n", " "),
                    color = AnalysisMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            } else {
                parsedPoint.details.forEach { detail ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(AnalysisPrimary)
                                .padding(horizontal = 3.dp, vertical = 3.dp)
                        )
                        Text(
                            text = detail,
                            color = AnalysisMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}

private fun buildFailureEvidence(summary: FinalAnalysisAttemptSummary): List<String> {
    return buildList {
        summary.primaryReasonLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { add("가장 큰 원인은 $it 쪽 흐름이었어요.") }
        summary.feedbackTypes.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { add("대표 패턴은 ${displayFeedbackTypeLabel(it)}이었어요.") }
        summary.loadFocusLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { add("$it 쪽에 힘이 몰리면서 자세가 무너졌어요.") }
        summary.primaryCruxHoldNo
            ?.let { add("${it}번 홀드 전후가 반복적으로 막힌 구간이었어요.") }
        summary.primaryCruxDurationMs
            ?.takeIf { it > 0 }
            ?.let { add("크럭스 구간이 ${formatDurationLabel(it)} 동안 이어졌어요.") }
        summary.dangerEventCount
            ?.let { count ->
                add(
                    if (count <= 0) {
                        "큰 위험 이벤트는 없었지만 자세 흐름이 매끄럽지 않았어요."
                    } else {
                        "위험 이벤트가 ${count}회 보여서 흔들린 순간이 분명했어요."
                    }
                )
            }
    }.distinct().take(3).ifEmpty {
        summary.failureHighlights.take(3)
    }
}

private data class FailurePointDisplay(
    val timeLabel: String,
    val title: String,
    val details: List<String>
)

private fun AnalysisPoint.toDisplayPoint(): FailurePointDisplay {
    val normalizedDescription = description.replace("\n", " ").trim()
    val segments = normalizedDescription.split(": ", limit = 2)
    val title = segments.firstOrNull().orEmpty().ifBlank { "분석 포인트" }
    val details = segments.getOrNull(1)
        ?.split(" / ")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()
        .take(3)

    return FailurePointDisplay(
        timeLabel = "%02d:%02d".format(timeMs / 60_000L, (timeMs / 1_000L) % 60L),
        title = title,
        details = details
    )
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
