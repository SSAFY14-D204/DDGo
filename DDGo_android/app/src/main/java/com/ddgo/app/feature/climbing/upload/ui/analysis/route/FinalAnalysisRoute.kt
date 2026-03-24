package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisTab
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun FinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToChallenge: () -> Unit = {}
) {
    val attemptVideoUris = viewModel.playbackAttemptUris.ifEmpty { viewModel.allAttemptUris }
    val totalHolds = viewModel.totalSelectedHoldCount
        .takeIf { it > 0 }
        ?: viewModel.detectedHolds.size.takeIf { it > 0 }
        ?: 0
    val attemptCount = max(
        max(
            attemptVideoUris.size,
            viewModel.attemptAiAnalysisResults.size
        ),
        viewModel.attemptHoldReachResults.size
    ).coerceAtLeast(1)
    val preferredAttempt = (viewModel.currentAttemptIndex + 1).coerceIn(1, attemptCount)
    val initialSelectedAttempt = when {
        attemptVideoUris.isNotEmpty() && preferredAttempt <= attemptVideoUris.size -> preferredAttempt
        attemptVideoUris.isNotEmpty() -> attemptVideoUris.size.coerceIn(1, attemptCount)
        else -> attemptCount
    }
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
    val scope = rememberCoroutineScope()

    var selectedAttempt by rememberSaveable(attemptCount, initialSelectedAttempt) {
        mutableIntStateOf(initialSelectedAttempt)
    }
    var seekRequestId by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var pendingSeekTimeMs by rememberSaveable {
        mutableStateOf<Long?>(null)
    }
    var selectedTab by rememberSaveable {
        mutableStateOf(FinalAnalysisTab.Stats)
    }

    val safeSelectedAttempt = selectedAttempt.coerceIn(1, attemptCount)
    val currentSummary = attemptSummaries[(safeSelectedAttempt - 1).coerceIn(0, attemptSummaries.lastIndex)]
    val selectedAttemptVideoUri = attemptVideoUris
        .getOrNull((safeSelectedAttempt - 1).coerceAtLeast(0))
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
        selectedAttemptVideoUri,
        seekRequestId,
        pendingSeekTimeMs,
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
                previewHolds = viewModel.detectedHolds,
                selectedAttemptVideoUri = selectedAttemptVideoUri,
                seekRequestId = seekRequestId,
                seekRequestTimeMs = pendingSeekTimeMs
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
                "다음 시도"
            } else {
                "챌린지 종합 분석 보기"
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FinalAnalysisPage(
            state = pageState,
            selectedTab = selectedTab,
            onNavigateBack = onNavigateBack,
            onTabSelected = { selectedTab = it },
            onAttemptSelected = {
                selectedAttempt = it.coerceIn(1, attemptCount)
                pendingSeekTimeMs = null
            },
            onAnalysisPointSelected = { timeMs ->
                pendingSeekTimeMs = timeMs
                seekRequestId += 1L
            },
            onPrimaryAction = {
                if (attemptCount > 1 && safeSelectedAttempt < attemptCount) {
                    selectedAttempt = safeSelectedAttempt + 1
                    pendingSeekTimeMs = null
                } else {
                    scope.launch {
                        val closed = viewModel.closeChallengeForFinalAnalysis(
                            challengeResult = challengeResult,
                            averageCenterStabilityRatio = closeSummaryPayload.averageCenterStabilityRatio,
                            mostCruxHoldNo = closeSummaryPayload.mostCruxHoldNo,
                            maxCruxDurationMs = closeSummaryPayload.maxCruxDurationMs,
                            finalComment = closeSummaryPayload.finalComment
                        )
                        if (closed) {
                            onNavigateToChallenge()
                        }
                    }
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

private data class ChallengeCloseRouteSummary(
    val averageCenterStabilityRatio: Double?,
    val mostCruxHoldNo: Int?,
    val maxCruxDurationMs: Int?,
    val finalComment: String?
)

private fun buildFocusReasonText(
    feedbackTypes: List<String>,
    loadFocusLabel: String?
): String? {
    val causeKeywords = feedbackTypes
        .take(2)
        .map(::toFocusReasonKeyword)
    val causeSentence = when {
        causeKeywords.size >= 2 ->
            "${causeKeywords.joinToString("과 ")}이 함께 나타난 구간입니다."

        causeKeywords.size == 1 ->
            "${causeKeywords.first()}이 두드러진 구간입니다."

        else ->
            null
    }

    return when {
        causeSentence != null && loadFocusLabel != null ->
            "$causeSentence 특히 ${loadFocusLabel}에 부담이 크게 실린 구간입니다."

        causeSentence != null ->
            causeSentence

        loadFocusLabel != null ->
            "${loadFocusLabel}에 부담이 크게 실린 구간입니다."

        else ->
            null
    }
}

private fun toFocusReasonKeyword(type: String): String {
    return when (type) {
        "발 사용 부족" -> "발 활용 부족"
        "중심 흔들림" -> "중심 흔들림"
        "팔 사용 과다" -> "팔 힘 의존"
        "과한 버티기" -> "오래 버티기"
        else -> type
    }
}
