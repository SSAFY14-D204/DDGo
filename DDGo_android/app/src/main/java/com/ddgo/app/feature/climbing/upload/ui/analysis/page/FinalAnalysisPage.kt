package com.ddgo.app.feature.climbing.upload.ui.analysis.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisSectionTabs
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.AttemptChipRow
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHero
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.FailureCausePanel
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.ProblemStatsPanel
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.StabilityPanel

internal enum class FinalAnalysisTab(val label: String) {
    Stats("문제 통계"),
    Stability("안정도"),
    Failure("실패 원인")
}

internal data class FinalAnalysisPageState(
    val heroState: AttemptPreviewHeroState,
    val selectedAttempt: Int,
    val totalAttempts: Int,
    val currentSummary: AnalysisAttemptSummary,
    val overallSuccess: Boolean,
    val averageReachedHolds: Int,
    val totalHolds: Int,
    val averageBalanceRatio: Int,
    val combinedTimeline: List<Float>,
    val focusFraction: Float?,
    val actionText: String
)

@Composable
internal fun FinalAnalysisPage(
    state: FinalAnalysisPageState,
    selectedTab: FinalAnalysisTab,
    onNavigateBack: () -> Unit,
    onTabSelected: (FinalAnalysisTab) -> Unit,
    onAttemptSelected: (Int) -> Unit,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnalysisBgColor)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        FinalAnalysisTopBar(onNavigateBack = onNavigateBack)

        AttemptPreviewHero(state = state.heroState)

        AnalysisSectionTabs(
            labels = FinalAnalysisTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelected = { onTabSelected(FinalAnalysisTab.entries[it]) }
        )

        when (selectedTab) {
            FinalAnalysisTab.Stats -> {
                ProblemStatsPanel(
                    overallSuccess = state.overallSuccess,
                    averageReachedHolds = state.averageReachedHolds,
                    totalHolds = state.totalHolds,
                    averageBalanceRatio = state.averageBalanceRatio,
                    timeline = state.combinedTimeline,
                    focusFraction = state.focusFraction
                )
            }

            FinalAnalysisTab.Stability -> {
                StabilityPanel(
                    currentSummary = state.currentSummary,
                    timeline = state.combinedTimeline,
                    focusFraction = state.focusFraction
                )
            }

            FinalAnalysisTab.Failure -> {
                FailureCausePanel(summary = state.currentSummary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AttemptChipRow(
            attemptCount = state.totalAttempts,
            selectedAttempt = state.selectedAttempt,
            onSelect = onAttemptSelected,
            modifier = Modifier.padding(horizontal = 22.dp)
        )

        Spacer(modifier = Modifier.height(22.dp))

        AnalysisGradientButton(
            text = state.actionText,
            onClick = onPrimaryAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        )

        Spacer(
            modifier = Modifier
                .height(24.dp)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun FinalAnalysisTopBar(
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNavigateBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }

        Text(
            text = "문제 분석",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
