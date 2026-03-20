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
    val averageStableContactRatioText = remember(attemptSummaries) {
        val values = attemptSummaries.mapNotNull { it.stableContactRatio }
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
        overallSuccess,
        averageReachedHoldsText,
        averageReachedHoldsSuffix,
        averageInsideSupportRatioText,
        averageStableContactRatioText,
        currentSummary.feedbackTypes,
        currentSummary.loadFocusLabel,
        currentSummary.feedbackLine,
        currentSummary.coachingLine,
        focusReasonText,
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
            averageStableContactRatioText = averageStableContactRatioText,
            feedbackTypes = currentSummary.feedbackTypes,
            loadFocusLabel = currentSummary.loadFocusLabel,
            feedbackLine = currentSummary.feedbackLine,
            coachingLine = currentSummary.coachingLine,
            focusReasonText = focusReasonText,
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
            "$causeSentence \uD2B9\uD788 $loadFocusLabel\uC5D0 \uBD80\uB2F4\uC774 \uD06C\uAC8C \uC2E4\uB9B0 \uAD6C\uAC04\uC785\uB2C8\uB2E4."

        causeSentence != null ->
            causeSentence

        loadFocusLabel != null ->
            "$loadFocusLabel\uC5D0 \uBD80\uB2F4\uC774 \uD06C\uAC8C \uC2E4\uB9B0 \uAD6C\uAC04\uC785\uB2C8\uB2E4."

        else ->
            null
    }
}

private fun toFocusReasonKeyword(type: String): String {
    return when (type) {
        "발 사용 부족" -> "\uBC1C \uD65C\uC6A9 \uBD80\uC871"
        "중심 흔들림" -> "\uC911\uC2EC \uD754\uB4E4\uB9BC"
        "팔 사용 과다" -> "\uD314 \uD798 \uC758\uC874"
        "과한 버티기" -> "\uC624\uB798 \uBC84\uD2F0\uAE30"
        else -> type
    }
}

