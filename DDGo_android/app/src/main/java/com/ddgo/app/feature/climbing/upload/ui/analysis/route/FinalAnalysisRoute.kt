package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.feature.climbing.upload.DefaultFinalAnalysisTimeline
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.buildFinalAnalysisAttemptSummaries
import com.ddgo.app.feature.climbing.upload.formatAnalysisDate
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisTab
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun FinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {}
) {
    val totalHolds = viewModel.totalSelectedHoldCount
        .takeIf { it > 0 }
        ?: viewModel.detectedHolds.size.takeIf { it > 0 }
        ?: 0
    val attemptCount = max(
        viewModel.playbackAttemptUris.size,
        viewModel.attemptAiAnalysisResults.size
    ).coerceAtLeast(1)
    val attemptSummaries = remember(
        attemptCount,
        totalHolds,
        viewModel.attemptAiAnalysisResults
    ) {
        buildFinalAnalysisAttemptSummaries(
            attemptCount = attemptCount,
            totalHolds = totalHolds,
            aiResults = viewModel.attemptAiAnalysisResults
        )
    }

    var selectedAttempt by rememberSaveable(attemptCount) {
        mutableIntStateOf(attemptCount)
    }
    var selectedTab by rememberSaveable {
        mutableStateOf(FinalAnalysisTab.Stats)
    }

    val safeSelectedAttempt = selectedAttempt.coerceIn(1, attemptCount)
    val currentSummary = attemptSummaries[(safeSelectedAttempt - 1).coerceIn(0, attemptSummaries.lastIndex)]
    val averageReachedHoldsText = remember(attemptSummaries) {
        val values = attemptSummaries.mapNotNull { it.reachedHolds }
        if (values.isEmpty()) {
            FinalAnalysisUnknownMetricText
        } else {
            values.average().roundToInt().toString()
        }
    }
    val averageReachedHoldsSuffix = remember(averageReachedHoldsText, totalHolds) {
        if (averageReachedHoldsText == FinalAnalysisUnknownMetricText || totalHolds <= 0) {
            null
        } else {
            "/$totalHolds"
        }
    }
    val averageInsideSupportRatioText = remember(attemptSummaries) {
        val values = attemptSummaries.mapNotNull { it.insideSupportRatio }
        if (values.isEmpty()) {
            FinalAnalysisUnknownMetricText
        } else {
            "${values.average().roundToInt()}%"
        }
    }
    val overallSuccess = remember(attemptSummaries) {
        attemptSummaries.any { it.isSuccess }
    }
    val combinedTimeline = remember(attemptSummaries) {
        val aiTimelines = attemptSummaries
            .filter { it.hasAiResult }
            .map { it.stabilityTimeline }
            .filter { it.isNotEmpty() }
        val maxLength = aiTimelines.maxOfOrNull { it.size } ?: 0
        if (maxLength == 0) {
            return@remember DefaultFinalAnalysisTimeline
        }
        List(maxLength) { index ->
            aiTimelines.map { timeline ->
                timeline.getOrElse(index) {
                    timeline.lastOrNull() ?: 0.5f
                }
            }.average().toFloat()
        }
    }
    val statsFocusFraction = remember(safeSelectedAttempt, attemptCount) {
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
        averageReachedHoldsText,
        averageReachedHoldsSuffix,
        averageInsideSupportRatioText,
        combinedTimeline,
        statsFocusFraction
    ) {
        FinalAnalysisPageState(
            heroState = AttemptPreviewHeroState(
                gymName = viewModel.gymName,
                displayDate = displayDate,
                difficultyLabel = viewModel.difficultyLevel,
                holdColorLabel = viewModel.holdColor,
                selectedAttempt = safeSelectedAttempt,
                isSuccess = currentSummary.isSuccess,
                analysisModeLabel = currentSummary.effectiveModeLabel.takeIf { it.isNotBlank() },
                fallbackLabel = currentSummary.fallbackLabel,
                previewBitmap = viewModel.bestFrameBitmap,
                previewHolds = viewModel.detectedHolds
            ),
            selectedAttempt = safeSelectedAttempt,
            totalAttempts = attemptCount,
            currentSummary = currentSummary,
            overallSuccess = overallSuccess,
            averageReachedHoldsText = averageReachedHoldsText,
            averageReachedHoldsSuffix = averageReachedHoldsSuffix,
            averageInsideSupportRatioText = averageInsideSupportRatioText,
            combinedTimeline = combinedTimeline,
            statsFocusFraction = statsFocusFraction,
            actionText = if (attemptCount > 1 && safeSelectedAttempt < attemptCount) {
                "다음 시도"
            } else {
                "홈으로"
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
