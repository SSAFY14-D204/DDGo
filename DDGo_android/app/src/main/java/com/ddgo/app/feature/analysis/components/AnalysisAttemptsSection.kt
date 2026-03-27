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
import com.ddgo.app.feature.analysis.model.AnalysisAttemptListItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.style.AnalysisPalette

@Composable
internal fun AnalysisAttemptsSection(
    attempts: List<AnalysisAttemptListItemUiModel>,
    onAttemptSelected: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalysisSectionTitle(title = "시도 기록")

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            attempts.forEach { attempt ->
                AttemptListRow(
                    attempt = attempt,
                    onClick = { onAttemptSelected(attempt.attemptNo) }
                )
            }
        }
    }
}

@Composable
private fun AttemptListRow(
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
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = attempt.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnalysisPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "등반 시간 ${attempt.subtitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnalysisPalette.TextSecondary
                    )
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodySmall,
                        color = AnalysisPalette.TextHint
                    )
                    Text(
                        text = attempt.resultBadge.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = badgeTextColor(attempt.resultBadge.tone),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = AnalysisPalette.TextHint
            )
        }
    }
}

private fun badgeTextColor(tone: AnalysisBadgeTone) =
    when (tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextSecondary
    }
