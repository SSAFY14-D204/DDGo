package com.ddgo.app.feature.climbing.upload.ui.analysis.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.resolveAnalysisSeekTimeMs
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptAnalysisContentSection
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptAnalysisTimelineRow
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHero
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import kotlinx.coroutines.launch

internal data class FinalAnalysisPageState(
    val heroState: AttemptPreviewHeroState,
    val selectedAttempt: Int,
    val totalAttempts: Int,
    val currentSummary: FinalAnalysisAttemptSummary,
    val previousSummary: FinalAnalysisAttemptSummary?,
    val analysisStartTimeMs: Long?,
    val timelinePoints: List<AnalysisPoint>,
    val reachedHoldsText: String,
    val reachedHoldsSuffix: String?,
    val feedbackLine: String,
    val riskLine: String,
    val coachingLine: String,
    val previousActionText: String?,
    val actionText: String
)

@Composable
internal fun FinalAnalysisPage(
    state: FinalAnalysisPageState,
    onNavigateBack: () -> Unit,
    onAnalysisPointSelected: (Long) -> Unit,
    onSecondaryAction: (() -> Unit)?,
    onPrimaryAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var selectedTimelinePointMs by rememberSaveable(state.selectedAttempt) {
        mutableLongStateOf(-1L)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AnalysisBgColor)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        FinalAnalysisTopBar(onNavigateBack = onNavigateBack)

        AttemptPreviewHero(state = state.heroState)

        AttemptAnalysisTimelineRow(
            points = state.timelinePoints,
            selectedTimeMs = selectedTimelinePointMs.takeIf { it >= 0L },
            modifier = Modifier.padding(top = 8.dp),
            onPointSelected = { point ->
                selectedTimelinePointMs = point.timeMs
                onAnalysisPointSelected(
                    resolveAnalysisSeekTimeMs(
                        point = point,
                        usesPoseDetectorTimeline = state.heroState.usesPoseTimeline
                    )
                )
            }
        )

        AttemptAnalysisContentSection(
            currentSummary = state.currentSummary,
            previousSummary = state.previousSummary,
            analysisStartTimeMs = state.analysisStartTimeMs,
            reachedHoldsText = state.reachedHoldsText,
            reachedHoldsSuffix = state.reachedHoldsSuffix,
            isSuccess = state.currentSummary.isSuccess,
            feedbackLine = state.feedbackLine,
            riskLine = state.riskLine,
            coachingLine = state.coachingLine,
            onLowestPointSelected = { timeMs ->
                selectedTimelinePointMs = timeMs
                scope.launch {
                    scrollState.animateScrollTo(0)
                    onAnalysisPointSelected(timeMs)
                }
            },
            onRecoveryPointSelected = { timeMs ->
                selectedTimelinePointMs = timeMs
                scope.launch {
                    scrollState.animateScrollTo(0)
                    onAnalysisPointSelected(timeMs)
                }
            },
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
        )

        if (state.previousActionText != null && onSecondaryAction != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnalysisSecondaryButton(
                    text = state.previousActionText,
                    onClick = onSecondaryAction,
                    modifier = Modifier.fillMaxWidth()
                )
                AnalysisGradientButton(
                    text = state.actionText,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            AnalysisGradientButton(
                text = state.actionText,
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
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
                contentDescription = "뒤로 가기",
                tint = Color.White
            )
        }

        Text(
            text = "시도 분석 결과",
            modifier = Modifier.align(Alignment.Center),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AnalysisSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AnalysisCardColor)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AnalysisText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
