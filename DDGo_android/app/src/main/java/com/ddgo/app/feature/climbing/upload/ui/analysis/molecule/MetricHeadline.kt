package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisText

@Composable
internal fun MetricHeadline(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    suffix: String? = null,
    valueColor: Color = AnalysisText
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        caption?.let {
            Text(
                text = it,
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = title,
            color = AnalysisText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
            suffix?.let {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = it,
                    color = AnalysisMuted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }
    }
}
