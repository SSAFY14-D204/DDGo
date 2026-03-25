package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText

@Composable
internal fun FootUpperLimbContributionChart(
    lowerBodyScore: Int,
    upperLimbScore: Int,
    lowerBodyLabel: String,
    upperLimbLabel: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ContributionBar(
            title = "다리로 밀기",
            valueLabel = lowerBodyLabel,
            progress = lowerBodyScore / 100f,
            fillColor = AnalysisPrimary,
            accentValue = true
        )
        ContributionBar(
            title = "팔로 버티기",
            valueLabel = upperLimbLabel,
            progress = upperLimbScore / 100f,
            fillColor = AnalysisFailure
        )
    }
}

@Composable
private fun ContributionBar(
    title: String,
    valueLabel: String,
    progress: Float,
    fillColor: Color,
    accentValue: Boolean = false,
    modifier: Modifier = Modifier
) {
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
                text = title,
                color = AnalysisText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueLabel,
                color = if (accentValue) AnalysisText else AnalysisMuted,
                fontSize = 12.sp,
                fontWeight = if (accentValue) FontWeight.SemiBold else FontWeight.Normal
            )
        }

        AnalysisAccentLinearProgressBar(
            progress = progress.coerceIn(0.08f, 1f),
            accentColor = fillColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
        )
    }
}
