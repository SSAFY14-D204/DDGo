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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSecondary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.StabilityLineChart
import androidx.compose.material3.Text
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.ChartLineLegendItem
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.MetricHeadline

@Composable
internal fun ProblemStatsPanel(
    overallSuccess: Boolean,
    averageReachedHoldsText: String,
    averageReachedHoldsSuffix: String?,
    averageInsideSupportRatioText: String,
    averageStableContactRatioText: String,
    timeline: List<Float>,
    focusFraction: Float?,
    focusReasonText: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MetricHeadline(
            title = "완등 여부",
            value = if (overallSuccess) "성공" else "실패",
            valueColor = if (overallSuccess) AnalysisSuccess else AnalysisFailure
        )

        Spacer(modifier = Modifier.height(34.dp))

        MetricHeadline(
            title = "평균 도달 홀드",
            value = averageReachedHoldsText,
            suffix = averageReachedHoldsSuffix
        )

        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = "안정성 지표",
            color = AnalysisText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsSummaryCard(
                title = "균형 유지",
                description = "몸의 중심이 안정적으로 유지된 구간 비율",
                value = averageInsideSupportRatioText,
                modifier = Modifier.weight(1f)
            )
            StatsSummaryCard(
                title = "손발 지지 안정도",
                description = "손발 지지가 안정적으로 이어진 구간 비율",
                value = averageStableContactRatioText,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                Spacer(modifier = Modifier.height(14.dp))
                StabilityLineChart(
                    data = timeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp),
                    focusFraction = focusFraction
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsGuideChip(text = "위로 갈수록 안정")
                    StatsGuideChip(text = "아래로 갈수록 흔들림")
                }
                focusFraction?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "밝은 세로선은 현재 선택한 시도에서 가장 버거웠던 구간입니다.",
                        color = AnalysisMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    focusReasonText?.let { reason ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "\uC65C \uBC84\uAC70\uC6E0\uB098\uC694?",
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
    }
}

@Composable
private fun StatsSummaryCard(
    title: String,
    description: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 15.sp,
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
private fun StatsGuideChip(
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

