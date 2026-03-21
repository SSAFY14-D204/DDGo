package com.ddgo.app.feature.climbing.upload.ui.analysis.page

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisSectionTabs
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.AttemptChipRow
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHero
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.FailureCausePanel
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.ProblemStatsPanel
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.StabilityPanel

internal enum class FinalAnalysisTab(val label: String) {
    Stats("\uD1B5\uACC4"),
    Stability("\uC548\uC815\uC131"),
    Failure("\uC2E4\uD328 \uC6D0\uC778")
}

internal data class FinalAnalysisPageState(
    val heroState: AttemptPreviewHeroState,
    val selectedAttempt: Int,
    val totalAttempts: Int,
    val currentSummary: FinalAnalysisAttemptSummary,
    val reachedHoldsText: String,
    val reachedHoldsSuffix: String?,
    val feedbackTypes: List<String>,
    val loadFocusLabel: String?,
    val feedbackLine: String,
    val coachingLine: String,
    val focusReasonText: String?,
    val statsFocusFraction: Float?,
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

        FinalAnalysisFeedbackCard(
            feedbackTypes = state.feedbackTypes,
            loadFocusLabel = state.loadFocusLabel,
            feedbackLine = state.feedbackLine,
            coachingLine = state.coachingLine,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
        )

        AnalysisSectionTabs(
            labels = FinalAnalysisTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelected = { onTabSelected(FinalAnalysisTab.entries[it]) }
        )

        when (selectedTab) {
            FinalAnalysisTab.Stats -> {
                ProblemStatsPanel(
                    isSuccess = state.currentSummary.isSuccess,
                    reachedHoldsTitle = "\uCD5C\uACE0 \uB3C4\uB2EC \uD640\uB4DC",
                    reachedHoldsText = state.reachedHoldsText,
                    reachedHoldsSuffix = state.reachedHoldsSuffix,
                    insideSupportTitle = "\uADE0\uD615 \uC720\uC9C0 \uBE44\uC728",
                    insideSupportRatioText = state.currentSummary.insideSupportRatioText,
                    stableContactTitle = "\uC190\uBC1C \uC9C0\uC9C0 \uC548\uC815\uB3C4",
                    stableContactRatioText = state.currentSummary.stableContactRatioText,
                    timeline = state.currentSummary.stabilityTimeline,
                    focusFraction = state.statsFocusFraction,
                    focusReasonText = state.focusReasonText,
                    focusGuideText = "\uBC1D\uC740 \uC138\uB85C\uC120\uC740 \uC774 \uC2DC\uB3C4\uC5D0\uC11C \uAC00\uC7A5 \uBC84\uAC70\uC6E0\uB358 \uAD6C\uAC04\uC785\uB2C8\uB2E4."
                )
            }

            FinalAnalysisTab.Stability -> {
                StabilityPanel(
                    currentSummary = state.currentSummary,
                    timeline = state.currentSummary.stabilityTimeline,
                    focusFraction = state.currentSummary.stabilityFocusFraction,
                    focusReasonText = state.focusReasonText
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
private fun FinalAnalysisFeedbackCard(
    feedbackTypes: List<String>,
    loadFocusLabel: String?,
    feedbackLine: String,
    coachingLine: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "\uC885\uD569 \uD53C\uB4DC\uBC31",
                color = AnalysisMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (feedbackTypes.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    feedbackTypes.forEach { type ->
                        FeedbackTypeChip(text = feedbackTypeChipLabel(type))
                    }
                }
            }
            loadFocusLabel?.let { focus ->
                Text(
                    text = "\uBD80\uB2F4\uC774 \uD070 \uBD80\uC704: $focus",
                    color = AnalysisMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
            Text(
                text = feedbackLine,
                color = AnalysisText,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "\uCF54\uCE6D: $coachingLine",
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun FeedbackTypeChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(AnalysisMuted.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun feedbackTypeChipLabel(type: String): String {
    return when (type) {
        "\uBC1C \uC0AC\uC6A9 \uBD80\uC871" -> "\uBC1C \uD65C\uC6A9 \uBD80\uC871"
        "\uD314 \uC0AC\uC6A9 \uACFC\uB2E4" -> "\uD314 \uC758\uC874 \uD07C"
        "\uACFC\uD55C \uBC84\uD2F0\uAE30" -> "\uC624\uB798 \uBC84\uD2F0\uAE30"
        else -> type
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
                contentDescription = "\uB4A4\uB85C\uAC00\uAE30",
                tint = Color.White
            )
        }

        Text(
            text = "\uBD84\uC11D \uB9AC\uD3EC\uD2B8",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
