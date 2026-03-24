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
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSecondary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.StabilityLineChart
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisInsightCard
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.ChartLineLegendItem

@Composable
internal fun StabilityPanel(
    currentSummary: FinalAnalysisAttemptSummary,
    timeline: List<Float>,
    focusFraction: Float?,
    focusReasonText: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Text(
            text = "안정성 지표",
            color = AnalysisText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailStatCard(
                title = "균형 유지 비율",
                description = "몸의 중심이 안정적으로 유지된 구간 비율",
                value = currentSummary.insideSupportRatioText,
                modifier = Modifier.weight(1f)
            )
            DetailStatCard(
                title = "손발 지지 안정도",
                description = "손발 지지가 안정적으로 이어진 구간 비율",
                value = currentSummary.stableContactRatioText,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Column {
                Text(
                    text = "구간별 균형 흐름",
                    color = AnalysisText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "시간 순서대로 균형이 얼마나 안정적이었는지 보여주는 흐름 그래프입니다. 정확한 수치보다 올라가고 내려가는 흐름을 보면 됩니다.",
                    color = AnalysisMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                StabilityLineChart(
                    data = timeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(208.dp),
                    focusFraction = focusFraction
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ChartLineLegendItem(
                        color = AnalysisPrimary,
                        label = "파란선: 순간 변화"
                    )
                    ChartLineLegendItem(
                        color = AnalysisSecondary,
                        label = "보라선: 전체 흐름"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailGuideChip(text = "위로 갈수록 안정")
                    DetailGuideChip(text = "아래로 갈수록 흔들림")
                }
                focusFraction?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "밝은 세로선은 이 시도에서 가장 버거웠던 구간입니다.",
                        color = AnalysisMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    focusReasonText?.let { reason ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "왜 버거웠나요?",
                                color = AnalysisText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = reason,
                                color = AnalysisMuted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        AnalysisInsightCard(
            title = "핵심 해석",
            highlights = currentSummary.stabilityHighlights,
            emptyText = currentSummary.stabilityNarrative
        )
    }
}

@Composable
private fun DetailStatCard(
    title: String,
    description: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = AnalysisMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DetailGuideChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AnalysisMuted.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

