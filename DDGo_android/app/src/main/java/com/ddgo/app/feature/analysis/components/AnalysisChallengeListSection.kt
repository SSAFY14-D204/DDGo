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
import com.ddgo.app.feature.analysis.model.AnalysisChallengeListItemUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 대시보드의 챌린지 목록 섹션입니다.
 *
 * 역할:
 * - 사용자가 지난 챌린지를 훑어보고 원하는 챌린지 상세로 바로 들어갈 수 있게 합니다.
 * - 최근 챌린지는 시각적으로만 살짝 강조하고, 정보 구조는 모든 카드에서 동일하게 유지합니다.
 */
@Composable
internal fun AnalysisChallengeListSection(
    challenges: List<AnalysisChallengeListItemUiModel>,
    onChallengeSelected: (Long) -> Unit,
    title: String = AnalysisStrings.ChallengeListSection,
    subtitle: String? = null,
    footerActionLabel: String? = null,
    onFooterAction: (() -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnalysisSectionTitle(
            title = title,
            subtitle = subtitle
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            challenges.forEach { challenge ->
                ChallengeListCard(
                    challenge = challenge,
                    onClick = { onChallengeSelected(challenge.challengeId) }
                )
            }

            if (footerActionLabel != null && onFooterAction != null) {
                ChallengeListFooterAction(
                    label = footerActionLabel,
                    onClick = onFooterAction
                )
            }
        }
    }
}

/** 챌린지 목록의 개별 카드입니다. */
@Composable
private fun ChallengeListCard(
    challenge: AnalysisChallengeListItemUiModel,
    onClick: () -> Unit
) {
    val backgroundColor = if (challenge.isRecent) {
        AnalysisPalette.SurfaceSelected
    } else {
        AnalysisPalette.Surface
    }
    val borderColor = if (challenge.isRecent) {
        AnalysisPalette.Accent.copy(alpha = 0.32f)
    } else {
        AnalysisPalette.Border
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = challenge.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    color = AnalysisPalette.TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnalysisBadge(badge = challenge.resultBadge)
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = AnalysisPalette.TextHint
                    )
                }
            }

            Text(
                text = "${challenge.subtitle}  |  ${challenge.meta}",
                style = MaterialTheme.typography.bodySmall,
                color = AnalysisPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChallengeListFooterAction(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = AnalysisPalette.Surface,
        border = BorderStroke(1.dp, AnalysisPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = AnalysisPalette.TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = AnalysisPalette.TextHint
            )
        }
    }
}
