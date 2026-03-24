package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.feature.climbing.upload.UploadBackgroundUploadSnackbarHost
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.buildChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.buildFinalAnalysisAttemptSummaries
import com.ddgo.app.feature.climbing.upload.formatAnalysisDate
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisTab
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengePreviewHeroState
import kotlin.math.max

@Composable
fun ChallengeFinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {}
) {
    val totalHolds = viewModel.totalSelectedHoldCount
        .takeIf { it > 0 }
        ?: viewModel.detectedHolds.size.takeIf { it > 0 }
        ?: 0
    val attemptCount = max(
        max(
            viewModel.playbackAttemptUris.size,
            viewModel.attemptAiAnalysisResults.size
        ),
        viewModel.attemptHoldReachResults.size
    ).coerceAtLeast(1)
    val attemptSummaries = remember(
        attemptCount,
        totalHolds,
        viewModel.attemptAiAnalysisResults,
        viewModel.attemptHoldReachResults
    ) {
        buildFinalAnalysisAttemptSummaries(
            attemptCount = attemptCount,
            totalHolds = totalHolds,
            aiResults = viewModel.attemptAiAnalysisResults,
            holdReachResults = viewModel.attemptHoldReachResults
        )
    }
    val challengeSummary = remember(attemptSummaries, totalHolds) {
        buildChallengeFinalAnalysisSummary(
            attemptSummaries = attemptSummaries,
            totalHolds = totalHolds
        )
    }
    var selectedTab by rememberSaveable {
        mutableStateOf(ChallengeFinalAnalysisTab.Overview)
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
        challengeSummary
    ) {
        ChallengeFinalAnalysisPageState(
            heroState = ChallengePreviewHeroState(
                gymName = viewModel.gymName,
                displayDate = displayDate,
                difficultyLabel = viewModel.difficultyLevel,
                holdColorLabel = viewModel.holdColor,
                attemptCount = challengeSummary.attemptCount,
                overallSuccess = challengeSummary.overallSuccess,
                successAttemptCount = challengeSummary.successAttemptCount,
                previewBitmap = viewModel.bestFrameBitmap,
                previewHolds = viewModel.detectedHolds
            ),
            summary = challengeSummary
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChallengeFinalAnalysisPage(
            state = pageState,
            selectedTab = selectedTab,
            onNavigateBack = onNavigateBack,
            onTabSelected = { selectedTab = it },
            onPrimaryAction = onNavigateToMain
        )

        UploadBackgroundUploadSnackbarHost(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 32.dp)
        )
    }
}
