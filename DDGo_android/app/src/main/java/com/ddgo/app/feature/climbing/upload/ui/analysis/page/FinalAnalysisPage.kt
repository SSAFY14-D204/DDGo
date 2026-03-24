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
    Stats("통계"),
    Stability("안정성"),
    Failure("실패 원인")
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
    onAnalysisPointSelected: (Long) -> Unit,
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
                    reachedHoldsTitle = "최고 도달 홀드",
                    reachedHoldsText = state.reachedHoldsText,
                    reachedHoldsSuffix = state.reachedHoldsSuffix,
                    insideSupportTitle = "균형 유지 비율",
                    insideSupportRatioText = state.currentSummary.insideSupportRatioText,
                    stableContactTitle = "손발 지지 안정도",
                    stableContactRatioText = state.currentSummary.stableContactRatioText,
                    timeline = state.currentSummary.stabilityTimeline,
                    focusFraction = state.statsFocusFraction,
                    focusReasonText = state.focusReasonText,
                    focusGuideText = "밝은 세로선은 이 시도에서 가장 버거웠던 구간입니다."
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
                FailureCausePanel(
                    summary = state.currentSummary,
                    onTimestampClick = onAnalysisPointSelected
                )
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
                text = "종합 피드백",
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
                    text = "부담이 큰 부위: $focus",
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
                text = "코칭: $coachingLine",
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
        "발 사용 부족" -> "발 활용 부족"
        "팔 사용 과다" -> "팔 의존 큼"
        "과한 버티기" -> "오래 버티기"
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
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }

        Text(
            text = "분석 리포트",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
