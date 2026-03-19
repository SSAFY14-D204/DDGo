package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.model.AnalysisChallengeSummaryUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 선택한 챌린지의 종합 요약을 보여주는 섹션입니다.
 *
 * 역할:
 * - 핵심 코멘트를 먼저 보여주고,
 * - 통계는 동일한 카드 패턴으로 정리해 가독성을 높입니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnalysisChallengeSummarySection(
    summary: AnalysisChallengeSummaryUiModel
) {
    AnalysisCardSurface {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnalysisSectionTitle(title = summary.title)

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = AnalysisPalette.SurfaceMuted
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = summary.headline,
                        style = MaterialTheme.typography.titleLarge,
                        color = AnalysisPalette.TextPrimary
                    )
                }
            }

            FlowRow(
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                summary.stats.take(4).forEach { stat ->
                    AnalysisMiniStatCard(
                        label = stat.label,
                        value = stat.value,
                        modifier = Modifier.width(148.dp)
                    )
                }
            }
        }
    }
}
