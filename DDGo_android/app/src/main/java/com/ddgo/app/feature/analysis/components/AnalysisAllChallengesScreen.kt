package com.ddgo.app.feature.analysis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisChallengeListItemUiModel
import com.ddgo.app.feature.analysis.style.AnalysisPalette
import com.ddgo.app.feature.main.MainChromeDefaults

@Composable
internal fun AnalysisAllChallengesScreen(
    challenges: List<AnalysisChallengeListItemUiModel>,
    onBack: () -> Unit,
    onChallengeSelected: (Long) -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            AnalysisPalette.BackgroundTop,
            AnalysisPalette.BackgroundBottom,
            AnalysisPalette.BackgroundTop
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        AnalysisGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = (-24).dp),
            colors = listOf(
                AnalysisPalette.Accent.copy(alpha = 0.18f),
                AnalysisPalette.Accent.copy(alpha = 0f)
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = MainChromeDefaults.ContentBottomPadding + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnalysisBackChip(
                    label = AnalysisStrings.BackToDashboard,
                    onClick = onBack
                )
            }

            item {
                AnalysisChallengeListSection(
                    challenges = challenges,
                    onChallengeSelected = onChallengeSelected,
                    title = "전체 기록",
                    subtitle = "완료한 챌린지를 다시 열어 흐름과 결과를 확인해보세요."
                )
            }
        }
    }
}
