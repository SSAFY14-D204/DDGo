package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptListItemUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 챌린지 상세 안의 시도 목록 섹션입니다.
 *
 * 역할:
 * - 각 시도의 결과와 핵심 수치를 한 줄씩 빠르게 비교할 수 있게 합니다.
 * - 시도 상세 화면으로 이동하는 진입점 역할만 하도록 정보량을 적절히 제한합니다.
 */
@Composable
internal fun AnalysisAttemptsSection(
    attempts: List<AnalysisAttemptListItemUiModel>,
    onAttemptSelected: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalysisSectionTitle(title = AnalysisStrings.AttemptsSection)

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            attempts.forEach { attempt ->
                AttemptListCard(
                    attempt = attempt,
                    onClick = { onAttemptSelected(attempt.attemptNo) }
                )
            }
        }
    }
}

/** 시도 목록의 개별 카드입니다. */
@Composable
private fun AttemptListCard(
    attempt: AnalysisAttemptListItemUiModel,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = AnalysisPalette.Surface,
        border = BorderStroke(1.dp, AnalysisPalette.Border),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = attempt.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                AnalysisBadge(badge = attempt.resultBadge)
            }

            Text(
                text = attempt.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AnalysisPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${attempt.stabilityLabel}  |  ${attempt.holdLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnalysisPalette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = AnalysisPalette.TextHint
                )
            }
        }
    }
}
