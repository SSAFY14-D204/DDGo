package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
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
    val challengeSummary = remember(attemptSummaries, totalHolds) {
        buildChallengeFinalAnalysisSummary(
            attemptSummaries = attemptSummaries,
            totalHolds = totalHolds
        )
    }
    val closeSummaryPayload = remember(attemptSummaries, challengeSummary) {
        val averageCenterStabilityRatio = attemptSummaries
            .mapNotNull { it.insideSupportRatio }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.div(100.0)
        val mostCruxHoldNo = attemptSummaries
            .mapNotNull { it.primaryCruxHoldNo }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val maxCruxDurationMs = attemptSummaries
            .mapNotNull { it.primaryCruxDurationMs }
            .maxOrNull()
        val finalComment = listOf(
            challengeSummary.summaryLine,
            challengeSummary.completionLine,
            challengeSummary.challengeNatureLine
        ).filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        ChallengeCloseRouteSummary(
            averageCenterStabilityRatio = averageCenterStabilityRatio,
            mostCruxHoldNo = mostCruxHoldNo,
            maxCruxDurationMs = maxCruxDurationMs,
            finalComment = finalComment
        )
    }
    val challengeResult = remember(challengeSummary.overallSuccess, attemptSummaries) {
        when {
            attemptSummaries.isEmpty() -> "UNKNOWN"
            challengeSummary.overallSuccess -> "SUCCESS"
            else -> "FAIL"
        }
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

    LaunchedEffect(viewModel.challengeId, challengeResult) {
        viewModel.closeChallengeForFinalAnalysis(
            challengeResult = challengeResult,
            averageCenterStabilityRatio = closeSummaryPayload.averageCenterStabilityRatio,
            mostCruxHoldNo = closeSummaryPayload.mostCruxHoldNo,
            maxCruxDurationMs = closeSummaryPayload.maxCruxDurationMs,
            finalComment = closeSummaryPayload.finalComment
        )
    }

    ChallengeFinalAnalysisPage(
        state = pageState,
        selectedTab = selectedTab,
        onNavigateBack = onNavigateBack,
        onTabSelected = { selectedTab = it },
        onPrimaryAction = onNavigateToMain
    )
}

private data class ChallengeCloseRouteSummary(
    val averageCenterStabilityRatio: Double?,
    val mostCruxHoldNo: Int?,
    val maxCruxDurationMs: Int?,
    val finalComment: String?
)
