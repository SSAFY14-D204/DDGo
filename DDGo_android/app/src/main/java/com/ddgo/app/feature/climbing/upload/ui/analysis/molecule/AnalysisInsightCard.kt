package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText

@Composable
internal fun AnalysisInsightCard(
    title: String,
    highlights: List<String>,
    modifier: Modifier = Modifier,
    emptyText: String = "표시할 요약이 없어요."
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (highlights.isEmpty()) {
                Text(
                    text = emptyText,
                    color = AnalysisMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            } else {
                highlights.forEach { highlight ->
                    AnalysisInsightItem(text = highlight)
                }
            }
        }
    }
}

@Composable
private fun AnalysisInsightItem(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(AnalysisPrimary)
        )
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
