package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.StabilityLineChart
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.MetricHeadline

@Composable
internal fun ProblemStatsPanel(
    overallSuccess: Boolean,
    averageReachedHoldsText: String,
    averageReachedHoldsSuffix: String?,
    averageInsideSupportRatioText: String,
    timeline: List<Float>,
    focusFraction: Float?,
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

        MetricHeadline(
            caption = "평균 안정성",
            title = "지지면 내부 비율",
            value = averageInsideSupportRatioText
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            StabilityLineChart(
                data = timeline,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp),
                focusFraction = focusFraction
            )
        }
    }
}
