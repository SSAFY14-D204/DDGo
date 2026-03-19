package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 분석 화면 전반에서 재사용하는 공통 UI 조각 모음입니다.
 *
 * 역할:
 * - 카드, 배지, 섹션 타이틀처럼 반복되는 표현을 한 곳에서 재사용합니다.
 * - 더 이상 쓰지 않는 subtitle/caption 계약을 제거해 UI와 모델의 드리프트를 줄입니다.
 */
@Composable
internal fun AnalysisTopBar(
    title: String
) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = AnalysisPalette.TextPrimary
    )
}

/** 배경 위에 부드러운 하이라이트를 만드는 장식 요소입니다. */
@Composable
internal fun AnalysisGlow(
    modifier: Modifier = Modifier,
    colors: List<Color>
) {
    Box(
        modifier = modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(brush = Brush.radialGradient(colors = colors))
    )
}

/** 섹션 타이틀과 짧은 보조 문구를 통일된 스타일로 보여줍니다. */
@Composable
internal fun AnalysisSectionTitle(
    title: String,
    subtitle: String? = null
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = AnalysisPalette.TextPrimary
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = AnalysisPalette.TextHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 결과 상태를 짧게 보여주는 공통 배지입니다. */
@Composable
internal fun AnalysisBadge(
    badge: AnalysisBadgeUiModel,
    modifier: Modifier = Modifier
) {
    val background = when (badge.tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentSoft
        AnalysisBadgeTone.Success -> AnalysisPalette.SuccessSoft
        AnalysisBadgeTone.Danger -> AnalysisPalette.DangerSoft
        AnalysisBadgeTone.Warning -> AnalysisPalette.WarningSoft
        AnalysisBadgeTone.Neutral -> AnalysisPalette.SurfaceMuted
    }
    val textColor = when (badge.tone) {
        AnalysisBadgeTone.Accent -> AnalysisPalette.AccentStrong
        AnalysisBadgeTone.Success -> AnalysisPalette.Success
        AnalysisBadgeTone.Danger -> AnalysisPalette.Danger
        AnalysisBadgeTone.Warning -> AnalysisPalette.Warning
        AnalysisBadgeTone.Neutral -> AnalysisPalette.TextSecondary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}

/** 분석 카드 공통 표면입니다. */
@Composable
internal fun AnalysisCardSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AnalysisPalette.Surface,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, AnalysisPalette.Border),
        shadowElevation = 4.dp
    ) {
        content()
    }
}

/** 라벨과 값만 보여주는 작은 통계 카드입니다. */
@Composable
internal fun AnalysisMiniStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = AnalysisPalette.SurfaceMuted,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = AnalysisPalette.TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = AnalysisPalette.TextPrimary
            )
        }
    }
}
