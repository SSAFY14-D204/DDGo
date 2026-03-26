package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.PolygonTrackedLimb
import com.ddgo.app.feature.climbing.upload.FinalAnalysisAttemptSummary
import com.ddgo.app.feature.climbing.upload.UploadBackgroundUploadSnackbarHost
import com.ddgo.app.feature.climbing.upload.UploadViewModel
import com.ddgo.app.feature.climbing.upload.buildChallengeFinalAnalysisSummary
import com.ddgo.app.feature.climbing.upload.buildFinalAnalysisAttemptSummaries
import com.ddgo.app.feature.climbing.upload.formatAnalysisDate
import com.ddgo.app.feature.climbing.upload.withAlignedDisplayCrux
import com.ddgo.app.feature.climbing.upload.ui.analysis.organism.AttemptPreviewHeroState
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPage
import com.ddgo.app.feature.climbing.upload.ui.analysis.page.FinalAnalysisPageState
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun FinalAnalysisRoute(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToChallenge: () -> Unit = {},
    onNavigateToMain: () -> Unit = {},
    onNavigateToCommunityCompose: (PendingCommunityComposeRequest) -> Unit = {}
) {
    val attemptVideoUris = viewModel.playbackAttemptUris.ifEmpty { viewModel.allAttemptUris }
    val uploadedAttemptCount = attemptVideoUris.size
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
    val initialSelectedAttempt = 1
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
    var isShareSheetVisible by rememberSaveable {
        mutableStateOf(false)
    }
    var shareSheetSelectedAttemptNos by rememberSaveable {
        mutableStateOf(listOf<Int>())
    }

    val safeSelectedAttempt = selectedAttempt.coerceIn(1, attemptCount)
    val safeSelectedAttemptIndex = (safeSelectedAttempt - 1).coerceAtLeast(0)
    val currentSummary = attemptSummaries[(safeSelectedAttempt - 1).coerceIn(0, attemptSummaries.lastIndex)]
    val previousSummary = attemptSummaries.getOrNull(safeSelectedAttempt - 2)
    val isSingleUploadedAttempt = uploadedAttemptCount <= 1
    val selectedAttemptVideoUri = attemptVideoUris
        .getOrNull((safeSelectedAttempt - 1).coerceAtLeast(0))
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

    LaunchedEffect(safeSelectedAttemptIndex, attemptCount) {
        if (attemptCount > 0) {
            viewModel.selectAttempt(safeSelectedAttemptIndex)
        }
    }

    val aiPresentationPoints = remember(
        safeSelectedAttemptIndex,
        viewModel.attemptPresentationResults,
        currentSummary.analysisPoints
    ) {
        viewModel.attemptPresentationResults
            .getOrNull(safeSelectedAttemptIndex)
            ?.second
            ?.takeIf { it.isNotEmpty() }
            ?: currentSummary.analysisPoints
    }
    val currentAttemptContactDebugResult = viewModel.currentAttemptPolygonHoldContactDebugResult
    val analysisStartTimeMs = remember(currentAttemptContactDebugResult) {
        currentAttemptContactDebugResult?.findClimbStartTimeMs()
    }
    val currentAttemptEndHoldNo = remember(
        viewModel.currentAttemptDisplayHolds,
        viewModel.selectedEndHold
    ) {
        viewModel.currentAttemptDisplayHolds
            .firstOrNull { it.isEnd }
            ?.holdNo
            ?: viewModel.selectedEndHold?.holdNo
    }
    val currentAttemptAiAnalysisResult = viewModel.currentAttemptAiAnalysisResult
    val displaySummary = remember(
        currentSummary,
        currentAttemptAiAnalysisResult,
        currentAttemptContactDebugResult
    ) {
        currentSummary.withAlignedDisplayCrux(
            aiAnalysisResult = currentAttemptAiAnalysisResult,
            contactDebugResult = currentAttemptContactDebugResult
        )
    }

    val timelinePoints = remember(
        currentSummary.analysisPoints,
        aiPresentationPoints,
        currentSummary.videoDurationMs,
        currentAttemptContactDebugResult,
        analysisStartTimeMs,
        currentAttemptEndHoldNo
    ) {
        buildAttemptFocusTimelinePoints(
            summary = currentSummary,
            fallbackPoints = aiPresentationPoints,
            contactDebugResult = currentAttemptContactDebugResult,
            analysisStartTimeMs = analysisStartTimeMs,
            endHoldNo = currentAttemptEndHoldNo
        )
    }

    val reachedHoldsSuffix = remember(displaySummary.reachedHolds, totalHolds) {
        if (displaySummary.reachedHolds != null && totalHolds > 0) {
            "/$totalHolds"
        } else {
            null
        }
    }
    val focusReasonText = remember(
        displaySummary.feedbackTypes,
        displaySummary.loadFocusLabel
    ) {
        buildFocusReasonText(
            feedbackTypes = displaySummary.feedbackTypes,
            loadFocusLabel = displaySummary.loadFocusLabel
        )
    }
    val riskLine = remember(displaySummary, focusReasonText) {
        buildRiskLine(
            summary = displaySummary,
            focusReasonText = focusReasonText
        )
    }
    val feedbackLine = remember(displaySummary) {
        buildDisplayFeedbackLine(summary = displaySummary)
    }
    val coachingLine = remember(displaySummary) {
        buildDisplayCoachingLine(summary = displaySummary)
    }
    val displayRiskLine = remember(displaySummary, focusReasonText, riskLine) {
        buildDisplayRiskLine(
            summary = displaySummary,
            focusReasonText = focusReasonText,
            fallback = riskLine
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
        displaySummary,
        previousSummary,
        selectedAttemptVideoUri,
        timelinePoints,
        viewModel.currentAttemptPoseSequence,
        viewModel.currentAttemptOverlayCache,
        viewModel.currentAttemptPrePoseEntry,
        viewModel.allRawHolds,
        analysisStartTimeMs,
        seekRequestId,
        pendingSeekTimeMs,
        reachedHoldsSuffix,
        feedbackLine,
        displayRiskLine,
        coachingLine,
        isSingleUploadedAttempt
    ) {
        FinalAnalysisPageState(
            heroState = AttemptPreviewHeroState(
                gymName = viewModel.gymName,
                displayDate = displayDate,
                difficultyLabel = viewModel.difficultyLevel,
                holdColorLabel = viewModel.holdColor,
                selectedAttempt = safeSelectedAttempt,
                isSuccess = displaySummary.isSuccess,
                analysisModeLabel = displaySummary.effectiveModeLabel.takeIf { it.isNotBlank() },
                fallbackLabel = displaySummary.fallbackLabel,
                previewBitmap = viewModel.bestFrameBitmap,
                previewHolds = viewModel.detectedHolds,
                numberedHolds = viewModel.currentAttemptDisplayHolds,
                selectedAttemptVideoUri = selectedAttemptVideoUri,
                analysisPoints = timelinePoints,
                attemptPoseSequence = viewModel.currentAttemptPoseSequence,
                overlayCache = viewModel.currentAttemptOverlayCache,
                rawHolds = viewModel.allRawHolds,
                wallArrivalTimeMs = viewModel.currentAttemptPrePoseEntry?.wallArrivalTimeMs,
                personObservationStartTimeMs = viewModel.currentAttemptPrePoseEntry?.personObservationStartTimeMs,
                usesPoseTimeline = viewModel.currentAttemptPrePoseEntry != null,
                seekRequestId = seekRequestId,
                seekRequestTimeMs = pendingSeekTimeMs
            ),
            selectedAttempt = safeSelectedAttempt,
            totalAttempts = attemptCount,
            currentSummary = displaySummary,
            previousSummary = previousSummary,
            analysisStartTimeMs = analysisStartTimeMs,
            timelinePoints = timelinePoints,
            reachedHoldsText = displaySummary.reachedHoldsText,
            reachedHoldsSuffix = reachedHoldsSuffix,
            feedbackLine = feedbackLine,
            riskLine = displayRiskLine,
            coachingLine = coachingLine,
            previousActionText = if (safeSelectedAttempt > 1) {
                "이전 시도 분석 결과 보기"
            } else {
                null
            },
            actionText = when {
                isSingleUploadedAttempt -> "홈으로 이동"
                safeSelectedAttempt < attemptCount -> "다음 시도 분석 결과 보기"
                else -> "챌린지 종합 분석 결과 보기"
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FinalAnalysisPage(
            state = pageState,
            onNavigateBack = onNavigateBack,
            onAnalysisPointSelected = { timeMs ->
                pendingSeekTimeMs = timeMs
                seekRequestId += 1L
            },
            onSecondaryAction = if (safeSelectedAttempt > 1) {
                {
                    selectedAttempt = (safeSelectedAttempt - 1).coerceAtLeast(1)
                    pendingSeekTimeMs = null
                }
            } else {
                null
            },
            onShareAction = if (shareOptions.isNotEmpty()) {
                {
                    shareSheetSelectedAttemptNos = listOf(safeSelectedAttempt)
                    isShareSheetVisible = true
                }
            } else {
                null
            },
            onPrimaryAction = {
                when {
                    isSingleUploadedAttempt -> {
                        scope.launch {
                            val hasChallengeToClose = viewModel.createdChallenge != null
                            if (!hasChallengeToClose) {
                                onNavigateToMain()
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
                                onNavigateToMain()
                            }
                        }
                    }

                    safeSelectedAttempt < attemptCount -> {
                        selectedAttempt = safeSelectedAttempt + 1
                        pendingSeekTimeMs = null
                    }

                    else -> {
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
                scope.launch {
                    val hasChallengeToClose = viewModel.createdChallenge != null
                    if (!hasChallengeToClose) {
                        onNavigateToCommunityCompose(request)
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
                        onNavigateToCommunityCompose(request)
                    }
                }
            }
        )
    }
}

private data class ChallengeCloseRouteSummary(
    val averageCenterStabilityRatio: Double?,
    val mostCruxHoldNo: Int?,
    val maxCruxDurationMs: Int?,
    val finalComment: String?
)

private fun buildAttemptShareTitle(
    gymName: String,
    attemptNo: Int
): String {
    val safeGymName = gymName.ifBlank { "DDGo" }
    return "$safeGymName ${attemptNo}차 시도 분석 결과"
}

private fun buildAttemptShareText(
    gymName: String,
    attemptNo: Int,
    summary: FinalAnalysisAttemptSummary,
    totalHolds: Int
): String {
    val resultLabel = if (summary.isSuccess) "성공" else "실패"
    val reachedText = buildString {
        append(summary.reachedHoldsText)
        if (summary.reachedHolds != null && totalHolds > 0) {
            append("/$totalHolds")
        }
    }
    val cruxText = summary.primaryCruxHoldNo?.let { "${it}번 홀드" } ?: "정보 없음"
    val scoreText = summary.overallMovementScore?.let { "${it}점" } ?: "정보 없음"
    val safeGymName = gymName.ifBlank { "DDGo" }
    return buildString {
        appendLine("$safeGymName ${attemptNo}차 시도 분석 결과")
        appendLine("문제 풀이 여부: $resultLabel")
        appendLine("종합 점수: $scoreText")
        appendLine("도달 홀드: $reachedText")
        append("대표 크럭스: $cruxText")
    }
}

private fun buildAttemptFocusTimelinePoints(
    summary: FinalAnalysisAttemptSummary,
    fallbackPoints: List<AnalysisPoint>,
    contactDebugResult: PolygonHoldContactDebugResult?,
    analysisStartTimeMs: Long?,
    endHoldNo: Int?
): List<AnalysisPoint> {
    val sourcePoints = summary.analysisPoints.ifEmpty {
        fallbackPoints.filter { it.kind == AnalysisPointKind.GENERIC }
    }

    val hasRefinedFocusPoints = sourcePoints.any { point ->
        point.kind == AnalysisPointKind.STALL || point.kind == AnalysisPointKind.CLIMB_END
    }
    if (hasRefinedFocusPoints) {
        return sourcePoints
            .filter { point ->
                point.timeMs >= 0L &&
                    (analysisStartTimeMs == null || point.timeMs >= analysisStartTimeMs) &&
                    (point.kind == AnalysisPointKind.STALL ||
                        point.kind == AnalysisPointKind.GENERIC ||
                        point.kind == AnalysisPointKind.CLIMB_END)
            }
            .sortedBy { point -> point.timeMs }
            .distinctBy { point ->
                point.kind to refinedTimelineDescription(
                    description = point.description,
                    kind = point.kind
                )
            }
            .mapIndexed { index, point ->
                point.copy(
                    index = index + 1,
                    description = refinedTimelineDescription(
                        description = point.description,
                        kind = point.kind
                    )
                )
            }
    }

    val keyPoints = buildImportantTimelinePoints(
        points = sourcePoints,
        analysisStartTimeMs = analysisStartTimeMs
    ).toMutableList()
    buildSuccessTimelinePoint(
        summary = summary,
        sourcePoints = sourcePoints,
        contactDebugResult = contactDebugResult,
        endHoldNo = endHoldNo,
        analysisStartTimeMs = analysisStartTimeMs
    )?.let(keyPoints::add)

    val points = keyPoints
        .filter { it.timeMs >= 0L }
        .sortedBy { it.timeMs }
        .toMutableList()

    val climbEndTimeMs = summary.videoDurationMs
        ?.takeIf { it > 0L }
        ?: points.maxOfOrNull { it.timeMs }

    climbEndTimeMs?.let { endTimeMs ->
        if (points.none { it.kind == AnalysisPointKind.CLIMB_END }) {
            points += AnalysisPoint(
                index = 0,
                timeMs = endTimeMs,
                description = "등반 종료",
                kind = AnalysisPointKind.CLIMB_END
            )
        }
    }

    return points
        .distinctBy { point ->
            point.timeMs to point.kind to refinedTimelineDescription(
                description = point.description,
                kind = point.kind
            )
        }
        .sortedBy { point -> point.timeMs }
        .mapIndexed { index, point ->
            point.copy(
                index = index + 1,
                description = refinedTimelineDescription(
                    description = point.description,
                    kind = point.kind
                )
            )
        }
}

private fun buildImportantTimelinePoints(
    points: List<AnalysisPoint>,
    analysisStartTimeMs: Long?
): List<AnalysisPoint> {
    if (points.isEmpty()) return emptyList()

    val prioritized = points
        .filter { point ->
            point.kind == AnalysisPointKind.GENERIC &&
                point.timeMs >= 0L &&
                (analysisStartTimeMs == null || point.timeMs >= analysisStartTimeMs)
        }
        .sortedBy { point -> point.timeMs }
        .sortedByDescending(::refinedTimelinePointPriority)

    val selected = prioritized
        .distinctBy { point -> refinedTimelineDescription(point.description, point.kind) }
        .take(1)
        .sortedBy { point -> point.timeMs }

    return if (selected.isNotEmpty()) {
        selected
    } else {
        points
            .filter { point ->
                point.kind == AnalysisPointKind.GENERIC &&
                    point.timeMs >= 0L &&
                    (analysisStartTimeMs == null || point.timeMs >= analysisStartTimeMs)
            }
            .sortedBy { point -> point.timeMs }
            .take(1)
    }
}

private fun buildSuccessTimelinePoint(
    summary: FinalAnalysisAttemptSummary,
    sourcePoints: List<AnalysisPoint>,
    contactDebugResult: PolygonHoldContactDebugResult?,
    endHoldNo: Int?,
    analysisStartTimeMs: Long?
): AnalysisPoint? {
    if (!summary.isSuccess) return null

    val climbEndTimeMs = summary.videoDurationMs
        ?.takeIf { it > 0L }
        ?: sourcePoints.maxOfOrNull { point -> point.timeMs }
        ?: return null

    val successTimeMs = contactDebugResult
        ?.findSuccessfulTopContactTimeMs(
            endHoldNo = endHoldNo,
            analysisStartTimeMs = analysisStartTimeMs
        )
        ?.coerceAtMost((climbEndTimeMs - 200L).coerceAtLeast(0L))
        ?: sourcePoints
            .filter { point ->
                point.kind == AnalysisPointKind.GENERIC &&
                    point.timeMs >= 0L &&
                    (analysisStartTimeMs == null || point.timeMs >= analysisStartTimeMs)
            }
            .maxOfOrNull { point -> point.timeMs }
            ?.coerceAtMost((climbEndTimeMs - 800L).coerceAtLeast(0L))
        ?: (climbEndTimeMs - 1_200L).coerceAtLeast(0L)

    return AnalysisPoint(
        index = 0,
        timeMs = successTimeMs,
        description = "완등",
        kind = AnalysisPointKind.GENERIC
    )
}

private fun PolygonHoldContactDebugResult.findClimbStartTimeMs(): Long? {
    return frames.firstOrNull { frame ->
        val limbStatesByLimb = frame.limbStates.associateBy { it.limb }
        listOf(
            PolygonTrackedLimb.LEFT_HAND,
            PolygonTrackedLimb.RIGHT_HAND,
            PolygonTrackedLimb.LEFT_FOOT,
            PolygonTrackedLimb.RIGHT_FOOT
        ).all { limb ->
            limbStatesByLimb[limb]?.activeHoldNo != null
        }
    }?.frameTimeMs
}

private fun PolygonHoldContactDebugResult.findSuccessfulTopContactTimeMs(
    endHoldNo: Int?,
    analysisStartTimeMs: Long?
): Long? {
    val resolvedEndHoldNo = endHoldNo?.takeIf { it > 0 } ?: return null
    return frames.firstOrNull { frame ->
        if (analysisStartTimeMs != null && frame.frameTimeMs < analysisStartTimeMs) {
            return@firstOrNull false
        }
        val limbStatesByLimb = frame.limbStates.associateBy { it.limb }
        limbStatesByLimb[PolygonTrackedLimb.LEFT_HAND]?.activeHoldNo == resolvedEndHoldNo &&
            limbStatesByLimb[PolygonTrackedLimb.RIGHT_HAND]?.activeHoldNo == resolvedEndHoldNo
    }?.frameTimeMs
}

private fun refinedTimelinePointPriority(point: AnalysisPoint): Int {
    val lowered = point.description.lowercase()
    val holdNo = extractTimelineHoldNumber(point.description)

    return when {
        point.description.contains("완등") || point.description.contains("성공") -> 4
        point.description.contains("균형") || lowered.contains("balance") || lowered.contains("stability") -> 3
        point.description.contains("접촉") || lowered.contains("contact") -> 3
        point.description.contains("팔") || lowered.contains("arm") -> 2
        point.description.contains("발") || point.description.contains("하체") ||
            lowered.contains("foot") || lowered.contains("leg") -> 2
        holdNo != null && holdNo > 1 -> 2
        else -> 1
    }
}

private fun refinedTimelineDescription(
    description: String,
    kind: AnalysisPointKind
): String {
    if (kind == AnalysisPointKind.CLIMB_END) return "등반 종료"
    if (description.contains("완등") || description.contains("성공")) return "완등"

    extractTimelineHoldNumber(description)
        ?.takeIf { holdNo -> holdNo > 1 }
        ?.let { holdNo ->
            val lowered = description.lowercase()
            return when {
                description.contains("균형") || lowered.contains("balance") || lowered.contains("stability") ->
                    "${holdNo}번 홀드 흔들림"

                description.contains("접촉") || lowered.contains("contact") ->
                    "${holdNo}번 홀드 접촉"

                description.contains("팔") || lowered.contains("arm") ->
                    "${holdNo}번 홀드 버팀"

                else -> "${holdNo}번 홀드 공략"
            }
        }

    val lowered = description.lowercase()

    return when {
        description.contains("균형") || lowered.contains("balance") || lowered.contains("stability") ->
            "균형 흔들림"

        description.contains("접촉") || lowered.contains("contact") ->
            "접촉 불안정"

        description.contains("팔") || lowered.contains("arm") ->
            "상지 부담"

        description.contains("발") || description.contains("하체") ||
            lowered.contains("foot") || lowered.contains("leg") ->
            "하체 포인트"

        else -> "핵심 장면"
    }
}

private fun timelinePointPriority(point: AnalysisPoint): Int {
    val lowered = point.description.lowercase()
    val holdNo = extractTimelineHoldNumber(point.description)

    return when {
        holdNo != null && holdNo > 1 -> 5
        point.description.contains("성공") || point.description.contains("완등") -> 4
        point.description.contains("균형") || lowered.contains("balance") || lowered.contains("stability") -> 3
        point.description.contains("접촉") || lowered.contains("contact") -> 3
        point.description.contains("팔") || lowered.contains("arm") -> 2
        point.description.contains("발") || point.description.contains("하체") ||
            lowered.contains("foot") || lowered.contains("leg") -> 2
        else -> 1
    }
}

private fun simplifyTimelineDescription(
    description: String,
    kind: AnalysisPointKind
): String {
    if (kind == AnalysisPointKind.CLIMB_END) return "등반 종료"
    if (description.contains("성공") || description.contains("완등")) return "완등"

    extractTimelineHoldNumber(description)
        ?.takeIf { holdNo -> holdNo > 1 }
        ?.let { holdNo -> return "${holdNo}번 홀드" }

    val lowered = description.lowercase()

    return when {
        description.contains("균형") || lowered.contains("balance") || lowered.contains("stability") ->
            "균형 흔들림"

        description.contains("접촉") || lowered.contains("contact") ->
            "접촉 불안정"

        description.contains("팔") || lowered.contains("arm") ->
            "상지 부담"

        description.contains("발") || description.contains("하체") ||
            lowered.contains("foot") || lowered.contains("leg") ->
            "하체 포인트"

        else -> "핵심 장면"
    }
}

private fun extractTimelineHoldNumber(description: String): Int? {
    val patterns = listOf(
        Regex("""(\d+)\s*번?\s*홀드"""),
        Regex("""hold\s*(\d+)""", RegexOption.IGNORE_CASE),
        Regex("""(\d+)\s*hold""", RegexOption.IGNORE_CASE)
    )

    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.find(description)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull { value -> value.isNotBlank() }
            ?.toIntOrNull()
    }
}

private fun buildFocusReasonText(
    feedbackTypes: List<String>,
    loadFocusLabel: String?
): String? {
    val causeKeywords = feedbackTypes
        .take(2)
        .map(::toFocusReasonKeyword)
        .filter { it.isNotBlank() }

    val causeSentence = when {
        causeKeywords.size >= 2 -> "${causeKeywords.joinToString("과 ")}가 함께 나타났어요."
        causeKeywords.size == 1 -> "${causeKeywords.first()} 흐름이 보여요."
        else -> null
    }

    return when {
        causeSentence != null && loadFocusLabel != null ->
            "$causeSentence 특히 $loadFocusLabel 쪽 부담이 같이 커졌어요."

        causeSentence != null -> causeSentence
        loadFocusLabel != null -> "$loadFocusLabel 쪽 부담이 두드러졌어요."
        else -> null
    }
}

private fun buildRiskLine(
    summary: FinalAnalysisAttemptSummary,
    focusReasonText: String?
): String {
    if ("중심 흔들림" in summary.feedbackTypes && "팔 사용 과다" in summary.feedbackTypes) {
        return "중심이 흔들릴 때 팔 힘이 먼저 커졌어요."
    }

    if ("발 사용 부족" in summary.feedbackTypes && "팔 사용 과다" in summary.feedbackTypes) {
        return "발보다 팔에 힘이 먼저 실렸어요."
    }

    if ("중심 흔들림" in summary.feedbackTypes) {
        return "핵심 구간에서 중심이 한 번 크게 흔들렸어요."
    }

    focusReasonText
        ?.takeIf { it.isNotBlank() }
        ?.let { return it.substringBefore('.').trimEnd() + "." }

    summary.dangerEventCount?.let { dangerEventCount ->
        return when {
            dangerEventCount <= 0 -> "끝까지 같은 리듬을 유지하는 게 중요했어요."
            dangerEventCount == 1 -> "한 번 흔들린 구간이 보여요."
            else -> "흔들린 장면이 여러 번 보였어요."
        }
    }

    summary.loadFocusLabel
        ?.takeIf { it.isNotBlank() }
        ?.let { return "$it 쪽 부담이 커졌어요." }

    return "이번 시도는 핵심 구간에서 흐름이 한 번 끊겼어요."
}


private fun buildDisplayFeedbackLine(summary: FinalAnalysisAttemptSummary): String {
    val cruxHoldNo = summary.primaryCruxHoldNo
    val reachedHolds = summary.reachedHolds

    return when {
        summary.isSuccess && cruxHoldNo != null ->
            "완등에 성공했고, 대표 크럭스는 ${cruxHoldNo}번 홀드 부근이었어요."

        summary.isSuccess ->
            "완등에 성공했고, 전체 흐름도 비교적 안정적으로 이어졌어요."

        cruxHoldNo != null && reachedHolds != null ->
            "${cruxHoldNo}번 홀드 부근에서 흐름이 끊겼고, 최고 ${reachedHolds}번 홀드까지 도달했어요."

        reachedHolds != null ->
            "최고 ${reachedHolds}번 홀드까지 도달했고, 그 이후 구간에서 흐름이 무너졌어요."

        else -> summary.feedbackLine
    }
}

private fun buildDisplayRiskLine(
    summary: FinalAnalysisAttemptSummary,
    focusReasonText: String?,
    fallback: String
): String {
    val cruxHoldNo = summary.primaryCruxHoldNo
    val recoveryScore = summary.stabilityRecoveryScore

    return when {
        cruxHoldNo != null && recoveryScore != null && recoveryScore < 55 ->
            "${cruxHoldNo}번 홀드 부근에서 흔들린 뒤 회복이 늦어 흐름이 끊겼어요."

        cruxHoldNo != null && !focusReasonText.isNullOrBlank() ->
            "${cruxHoldNo}번 홀드 부근에서 ${focusReasonText.removeSuffix(".")} 패턴이 반복됐어요."

        !focusReasonText.isNullOrBlank() ->
            focusReasonText.removeSuffix(".") + " 장면이 반복됐어요."

        else -> fallback
    }
}

private fun buildDisplayCoachingLine(summary: FinalAnalysisAttemptSummary): String {
    val cruxHoldNo = summary.primaryCruxHoldNo
    val lowerBodyDriveScore = summary.lowerBodyDriveScore
    val recoveryScore = summary.stabilityRecoveryScore
    val loadFocusLabel = summary.loadFocusLabel

    return when {
        cruxHoldNo != null && lowerBodyDriveScore != null && lowerBodyDriveScore < 55 ->
            "${cruxHoldNo}번 홀드 전후에서는 손보다 발과 몸통을 먼저 세팅해 보세요."

        recoveryScore != null && recoveryScore < 55 ->
            "흔들린 직후 바로 이어가기보다, 한 박자 안정권을 회복한 뒤 다음 동작으로 넘어가 보세요."

        !loadFocusLabel.isNullOrBlank() ->
            "$loadFocusLabel 부담이 커지기 전에 쉬운 구간에서 자세를 다시 정리해 보세요."

        else -> summary.coachingLine
    }
}

private fun toFocusReasonKeyword(type: String): String {
    return when (type) {
        "중심 이탈" -> "중심 이탈"
        "접촉 불안정" -> "접촉 불안정"
        "상지 보상" -> "상지 보상"
        "과도한 버티기" -> "과도한 버티기"
        else -> type
    }
}
