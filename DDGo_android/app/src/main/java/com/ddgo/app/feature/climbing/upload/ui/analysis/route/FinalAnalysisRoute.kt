package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.buildAttemptSummaries
import com.ddgo.app.feature.climbing.upload.formatAnalysisDate
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisTab
import kotlin.math.roundToInt

@Composable
fun FinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {}
) {
    val totalHolds = viewModel.detectedHolds.size.takeIf { it > 0 } ?: 14
    val attemptSummaries = remember(
        viewModel.allAttemptUris,
        viewModel.analysisPoints,
        viewModel.attemptDummyResults,
        totalHolds
    ) {
        buildAttemptSummaries(
            totalAttempts = viewModel.allAttemptUris.size,
            fallbackPoints = viewModel.analysisPoints,
            dummyResults = viewModel.attemptDummyResults,
            totalHolds = totalHolds
        )
    }
    val attemptCount = attemptSummaries.size.coerceAtLeast(1)

    var selectedAttempt by rememberSaveable(attemptCount) {
        mutableIntStateOf(attemptCount)
    }
    var selectedTab by rememberSaveable {
        mutableStateOf(FinalAnalysisTab.Stats)
    }

    val safeSelectedAttempt = selectedAttempt.coerceIn(1, attemptCount)
    val currentSummary = attemptSummaries[(safeSelectedAttempt - 1).coerceIn(0, attemptSummaries.lastIndex)]
    val averageReachedHolds = remember(attemptSummaries) {
        attemptSummaries.map { it.reachedHolds }.average().roundToInt()
    }
    val averageBalanceRatio = remember(attemptSummaries) {
        attemptSummaries.map { it.balanceRatio }.average().roundToInt()
    }
    val overallSuccess = remember(attemptSummaries) {
        attemptSummaries.any { it.isSuccess }
    }
    val combinedTimeline = remember(attemptSummaries) {
        val maxLength = attemptSummaries.maxOfOrNull { it.stabilityTimeline.size } ?: 0
        List(maxLength) { index ->
            attemptSummaries.map { summary ->
                summary.stabilityTimeline.getOrElse(index) {
                    summary.stabilityTimeline.lastOrNull() ?: 0.5f
                }
            }.average().toFloat()
        }
    }
    val focusFraction = remember(safeSelectedAttempt, attemptCount) {
        if (attemptCount <= 1) {
            null
        } else {
            (safeSelectedAttempt - 1).toFloat() / (attemptCount - 1).toFloat()
        }
    }
    val displayDate = remember(viewModel.createdChallenge?.startedAt) {
        formatAnalysisDate(viewModel.createdChallenge?.startedAt)
    }
    val pageState = remember(
        viewModel.gymName,
        displayDate,
        viewModel.difficultyLevel,
        viewModel.holdColor,
        viewModel.bestFrameBitmap,
        viewModel.detectedHolds,
        safeSelectedAttempt,
        attemptCount,
        currentSummary,
        overallSuccess,
        averageReachedHolds,
        totalHolds,
        averageBalanceRatio,
        combinedTimeline,
        focusFraction
    ) {
        FinalAnalysisPageState(
            heroState = AttemptPreviewHeroState(
                gymName = viewModel.gymName,
                displayDate = displayDate,
                difficultyLabel = viewModel.difficultyLevel,
                holdColorLabel = viewModel.holdColor,
                selectedAttempt = safeSelectedAttempt,
                isSuccess = currentSummary.isSuccess,
                previewBitmap = viewModel.bestFrameBitmap,
                previewHolds = viewModel.detectedHolds
            ),
            selectedAttempt = safeSelectedAttempt,
            totalAttempts = attemptCount,
            currentSummary = currentSummary,
            overallSuccess = overallSuccess,
            averageReachedHolds = averageReachedHolds,
            totalHolds = totalHolds,
            averageBalanceRatio = averageBalanceRatio,
            combinedTimeline = combinedTimeline,
            focusFraction = focusFraction,
            actionText = if (attemptCount > 1 && safeSelectedAttempt < attemptCount) {
                "다음 시도들과 비교분석 하기"
            } else {
                "분석 완료"
            }
        )
    }

    FinalAnalysisPage(
        state = pageState,
        selectedTab = selectedTab,
        onNavigateBack = onNavigateBack,
        onTabSelected = { selectedTab = it },
        onAttemptSelected = { selectedAttempt = it.coerceIn(1, attemptCount) },
        onPrimaryAction = {
            if (attemptCount > 1 && safeSelectedAttempt < attemptCount) {
                selectedAttempt = safeSelectedAttempt + 1
            } else {
                onNavigateToMain()
            }
        }
    )
}
