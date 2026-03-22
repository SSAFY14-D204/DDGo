package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ddgo.app.feature.climbing.upload.buildFinalAnalysisAttemptSummaries
import com.ddgo.app.feature.climbing.upload.formatAnalysisDate
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisTab
import kotlin.math.max

@Composable
fun FinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToChallenge: () -> Unit = {}
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
    val reachedHoldsSuffix = remember(currentSummary.reachedHolds, totalHolds) {
        if (currentSummary.reachedHolds != null && totalHolds > 0) {
            "/$totalHolds"
        } else {
            null
        }
    }
    val statsFocusFraction = remember(currentSummary) {
        currentSummary.stabilityFocusFraction
    }
    val focusReasonText = remember(
        currentSummary.feedbackTypes,
        currentSummary.loadFocusLabel
    ) {
        buildFocusReasonText(
            feedbackTypes = currentSummary.feedbackTypes,
            loadFocusLabel = currentSummary.loadFocusLabel
        )
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
        reachedHoldsSuffix,
        focusReasonText,
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
            reachedHoldsText = currentSummary.reachedHoldsText,
            reachedHoldsSuffix = reachedHoldsSuffix,
            feedbackTypes = currentSummary.feedbackTypes,
            loadFocusLabel = currentSummary.loadFocusLabel,
            feedbackLine = currentSummary.feedbackLine,
            coachingLine = currentSummary.coachingLine,
            focusReasonText = focusReasonText,
            statsFocusFraction = statsFocusFraction,
            actionText = if (attemptCount > 1 && safeSelectedAttempt < attemptCount) {
                "\uB2E4\uC74C \uC2DC\uB3C4"
            } else {
                "\uCC4C\uB9B0\uC9C0 \uC885\uD569 \uBD84\uC11D \uBCF4\uAE30"
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    onNavigateToChallenge()
                }
            }
        )

        UploadBackgroundUploadSnackbarHost(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 32.dp)
        )
    }
}

private fun buildFocusReasonText(
    feedbackTypes: List<String>,
    loadFocusLabel: String?
): String? {
    val causeKeywords = feedbackTypes
        .take(2)
        .map(::toFocusReasonKeyword)
    val causeSentence = when {
        causeKeywords.size >= 2 ->
            "${causeKeywords.joinToString("\uACFC ")}\uC774 \uD568\uAED8 \uB098\uD0C0\uB09C \uAD6C\uAC04\uC785\uB2C8\uB2E4."

        causeKeywords.size == 1 ->
            "${causeKeywords.first()}\uC774 \uB450\uB4DC\uB7EC\uC9C4 \uAD6C\uAC04\uC785\uB2C8\uB2E4."

        else ->
            null
    }

    return when {
        causeSentence != null && loadFocusLabel != null ->
            "$causeSentence \uD2B9\uD788 ${loadFocusLabel}\uC5D0 \uBD80\uB2F4\uC774 \uD06C\uAC8C \uC2E4\uB9B0 \uAD6C\uAC04\uC785\uB2C8\uB2E4."

        causeSentence != null ->
            causeSentence

        loadFocusLabel != null ->
            "${loadFocusLabel}\uC5D0 \uBD80\uB2F4\uC774 \uD06C\uAC8C \uC2E4\uB9B0 \uAD6C\uAC04\uC785\uB2C8\uB2E4."

        else ->
            null
    }
}

private fun toFocusReasonKeyword(type: String): String {
    return when (type) {
        "\uBC1C \uC0AC\uC6A9 \uBD80\uC871" -> "\uBC1C \uD65C\uC6A9 \uBD80\uC871"
        "\uC911\uC2EC \uD754\uB4E4\uB9BC" -> "\uC911\uC2EC \uD754\uB4E4\uB9BC"
        "\uD314 \uC0AC\uC6A9 \uACFC\uB2E4" -> "\uD314 \uD798 \uC758\uC874"
        "\uACFC\uD55C \uBC84\uD2F0\uAE30" -> "\uC624\uB798 \uBC84\uD2F0\uAE30"
        else -> type
    }
}
