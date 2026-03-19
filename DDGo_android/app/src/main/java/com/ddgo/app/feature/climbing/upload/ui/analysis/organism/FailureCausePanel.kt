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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess
import com.ddgo.app.feature.climbing.upload.AnalysisText

@Composable
internal fun FailureCausePanel(
    summary: AnalysisAttemptSummary,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (summary.analysisPoints.isEmpty()) {
                    Text(
                        text = "현재 시도에는 분석 포인트가 아직 없어요.",
                        color = AnalysisMuted,
                        fontSize = 14.sp
                    )
                } else {
                    summary.analysisPoints.forEach { point ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        color = AnalysisPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append("%02d:%02d ".format(point.timeMs / 60_000L, (point.timeMs / 1_000L) % 60L))
                                }
                                append(point.description.replace("\n", " "))
                            },
                            color = AnalysisText,
                            fontSize = 15.sp,
                            lineHeight = 23.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (summary.isSuccess) {
                "완등한 시도이지만 반복해서 잘 먹힌 리듬을 다음 시도에도 유지해보세요."
            } else {
                "같은 포인트에서 반복해서 흔들리면 미션 탭의 교정 포인트를 먼저 적용하는 편이 좋습니다."
            },
            color = AnalysisMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}
