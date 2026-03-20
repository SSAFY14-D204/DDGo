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
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisInsightCard

@Composable
internal fun FailureCausePanel(
    summary: FinalAnalysisAttemptSummary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${summary.attemptNo}차 시도",
                color = AnalysisText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (summary.isSuccess) "성공" else "실패",
                color = if (summary.isSuccess) AnalysisSuccess else AnalysisFailure,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnalysisInsightCard(
            title = "핵심 해석",
            highlights = summary.failureHighlights,
            emptyText = summary.failureNarrative
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "시간대별 분석 포인트",
                    color = AnalysisText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (summary.analysisPoints.isEmpty()) {
                    Text(
                        text = "아직 이 시도에 대한 AI 분석 포인트가 없어요.",
                        color = AnalysisMuted,
                        fontSize = 14.sp
                    )
                } else {
                    summary.analysisPoints.forEach { point ->
                        FailurePointCard(point = point)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailurePointCard(
    point: AnalysisPoint,
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
