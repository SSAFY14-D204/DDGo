package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengePreviewHeroState
import com.ddgo.app.feature.climbing.upload.withAlignedDisplayCrux
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ChallengeFinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {},
    onNavigateToCommunityCompose: (PendingCommunityComposeRequest) -> Unit = {}
) {
    val attemptVideoUris = viewModel.playbackAttemptUris.ifEmpty { viewModel.allAttemptUris }
    var isShareSheetVisible by rememberSaveable {
        mutableStateOf(false)
    }
    var shareSheetSelectedAttemptNos by rememberSaveable {
        mutableStateOf(listOf<Int>())
    }
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

    val attemptSummaries = remember(
        attemptCount,
        totalHolds,
        viewModel.attemptAiAnalysisResults,
        viewModel.attemptHoldReachResults,
        viewModel.attemptPolygonHoldContactDebugResults
    ) {
        buildFinalAnalysisAttemptSummaries(
            attemptCount = attemptCount,
            totalHolds = totalHolds,
            aiResults = viewModel.attemptAiAnalysisResults,
            holdReachResults = viewModel.attemptHoldReachResults,
            contactDebugResults = viewModel.attemptPolygonHoldContactDebugResults
        ).mapIndexed { index, summary ->
            summary.withAlignedDisplayCrux(
                aiAnalysisResult = viewModel.attemptAiAnalysisResults.getOrNull(index),
                contactDebugResult = viewModel.attemptPolygonHoldContactDebugResults.getOrNull(index)
            )
        }
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

        ChallengeFinalAnalysisClosePayload(
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
    val closeThenNavigate: ((() -> Unit) -> Unit) = { onClosedNavigate ->
        scope.launch {
            val currentChallengeId = viewModel.challengeId ?: 0L
            if (currentChallengeId <= 0L) {
                onClosedNavigate()
                return@launch
            }

            val closed = viewModel.closeChallengeForFinalAnalysis(
                challengeResult = challengeResult,
                averageCenterStabilityRatio = closeSummaryPayload.averageCenterStabilityRatio,
                mostCruxHoldNo = closeSummaryPayload.mostCruxHoldNo,
                maxCruxDurationMs = closeSummaryPayload.maxCruxDurationMs,
                finalComment = closeSummaryPayload.finalComment
            )
            if (closed) {
                onClosedNavigate()
            }
        }
    }

    val displayDate = remember(viewModel.createdChallenge?.startedAt) {
        formatAnalysisDate(viewModel.createdChallenge?.startedAt)
    }
    val shareOptions = remember(attemptVideoUris) {
        attemptVideoUris.mapIndexedNotNull { index, uri ->
            uri.takeIf { it.isNotBlank() }?.let { videoUri ->
                AnalysisCommunityShareOption(
                    attemptNo = index + 1,
                    videoUri = videoUri
                )
            }
        }
    }

    val pageState = remember(
        viewModel.gymName,
        displayDate,
        viewModel.difficultyLevel,
        viewModel.holdColor,
        viewModel.bestFrameBitmap,
        viewModel.detectedHolds,
        attemptVideoUris,
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
                previewHolds = viewModel.detectedHolds,
                attemptVideoUris = attemptVideoUris
            ),
            summary = challengeSummary
        )
    }

    BackHandler(enabled = !isShareSheetVisible) {
        onNavigateBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ChallengeFinalAnalysisPage(
            state = pageState,
            onNavigateBack = onNavigateBack,
            onPrimaryAction = { closeThenNavigate(onNavigateToMain) },
            onAttemptVideoShare = if (shareOptions.isNotEmpty()) {
                { attemptNo ->
                    shareSheetSelectedAttemptNos = setOf(attemptNo)
                        .toList()
                    isShareSheetVisible = true
                }
            } else {
                null
            }
        )

        UploadBackgroundUploadSnackbarHost(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 32.dp)
        )
    }

    if (isShareSheetVisible) {
        AnalysisCommunityShareSheet(
            options = shareOptions,
            initialSelectedAttemptNos = shareSheetSelectedAttemptNos.toSet(),
            onDismissRequest = { isShareSheetVisible = false },
            onConfirm = { selectedOptions ->
                isShareSheetVisible = false
                if (selectedOptions.isEmpty()) return@AnalysisCommunityShareSheet

                val request = buildPendingCommunityComposeRequest(
                    gymId = viewModel.gymId?.toLong(),
                    gymName = viewModel.gymName,
                    options = selectedOptions
                )
                closeThenNavigate { onNavigateToCommunityCompose(request) }
            }
        )
    }
}

private data class ChallengeFinalAnalysisClosePayload(
    val averageCenterStabilityRatio: Double?,
    val mostCruxHoldNo: Int?,
    val maxCruxDurationMs: Int?,
    val finalComment: String?
)
