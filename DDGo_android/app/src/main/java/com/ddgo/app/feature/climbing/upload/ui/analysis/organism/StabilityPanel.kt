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
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.StabilityLineChart

@Composable
internal fun StabilityPanel(
    currentSummary: FinalAnalysisAttemptSummary,
    timeline: List<Float>,
    focusFraction: Float?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DetailStatCard(
                title = "지지면 내부 비율",
                value = currentSummary.insideSupportRatioText,
                modifier = Modifier.weight(1f)
            )
            DetailStatCard(
                title = "안정 접촉 비율",
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
                    text = "안정성 흐름",
                    color = AnalysisText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                StabilityLineChart(
                    data = timeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(208.dp),
                    focusFraction = focusFraction
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = currentSummary.stabilityNarrative,
            color = AnalysisMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun DetailStatCard(
    title: String,
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
                color = AnalysisMuted,
                fontSize = 13.sp
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
