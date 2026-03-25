package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.feature.climbing.upload.ChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.UploadBackgroundUploadSnackbarHost
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.buildChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.buildFinalAnalysisAttemptSummaries
import com.ddgo.app.feature.climbing.upload.formatAnalysisDate
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengeFinalAnalysisPageState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.ChallengePreviewHeroState
import com.ddgo.app.feature.climbing.upload.withAlignedDisplayCrux
import kotlin.math.max

@Composable
fun ChallengeFinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {}
) {
    val context = LocalContext.current
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

    Box(modifier = Modifier.fillMaxSize()) {
        ChallengeFinalAnalysisPage(
            state = pageState,
            onNavigateBack = onNavigateBack,
            onPrimaryAction = onNavigateToMain,
            onAttemptVideoShare = if (attemptVideoUris.isNotEmpty()) {
                { attemptNo, videoUri ->
                    val summary = attemptSummaries.getOrNull(attemptNo - 1)
                    shareAttemptAnalysis(
                        context = context,
                        videoUriString = videoUri,
                        shareTitle = buildChallengeAttemptShareTitle(
                            gymName = viewModel.gymName,
                            attemptNo = attemptNo
                        ),
                        shareText = buildChallengeAttemptShareText(
                            gymName = viewModel.gymName,
                            attemptNo = attemptNo,
                            summary = summary,
                            totalHolds = totalHolds
                        )
                    )
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
}

private fun buildChallengeAttemptShareTitle(
    gymName: String,
    attemptNo: Int
): String {
    val safeGymName = gymName.ifBlank { "DDGo" }
    return "$safeGymName ${attemptNo}차 시도 분석 결과"
}

private fun buildChallengeAttemptShareText(
    gymName: String,
    attemptNo: Int,
    summary: FinalAnalysisAttemptSummary?,
    totalHolds: Int
): String {
    val safeGymName = gymName.ifBlank { "DDGo" }
    val resultLabel = when {
        summary == null -> "정보 없음"
        summary.isSuccess -> "성공"
        else -> "실패"
    }
    val scoreText = summary?.overallMovementScore?.let { "${it}점" } ?: "정보 없음"
    val reachedText = buildString {
        append(summary?.reachedHoldsText ?: "정보 없음")
        if (summary?.reachedHolds != null && totalHolds > 0) {
            append("/$totalHolds")
        }
    }
    val cruxText = summary?.primaryCruxHoldNo?.let { "${it}번 홀드" } ?: "정보 없음"
    return buildString {
        appendLine("$safeGymName ${attemptNo}차 시도 분석 결과")
        appendLine("문제 풀이 여부: $resultLabel")
        appendLine("종합 점수: $scoreText")
        appendLine("도달 홀드: $reachedText")
        append("대표 크럭스: $cruxText")
    }
}
