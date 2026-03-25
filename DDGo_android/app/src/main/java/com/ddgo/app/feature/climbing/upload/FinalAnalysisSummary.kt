package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.PolygonTrackedLimb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class FinalAnalysisAttemptSummary(
    val attemptNo: Int,
    val hasAiResult: Boolean,
    val isSuccess: Boolean,
    val analysisPoints: List<AnalysisPoint>,
    val videoDurationMs: Long?,
    val reachedHolds: Int?,
    val reachedHoldsText: String,
    val processedFrames: Int?,
    val processedFramesText: String,
    val highConfidenceRatio: Int?,
    val highConfidenceRatioText: String,
    val insideSupportRatio: Int?,
    val insideSupportRatioText: String,
    val stabilityRetentionScore: Int?,
    val stableContactFrameCount: Int?,
    val stableContactFrameCountText: String,
    val stableContactRatio: Int?,
    val stableContactRatioText: String,
    val stabilityRecoveryScore: Int?,
    val stabilityTimeline: List<Float>,
    val stabilityFocusFraction: Float?,
    val stabilityHighlights: List<String>,
    val stabilityNarrative: String,
    val failureHighlights: List<String>,
    val failureNarrative: String,
    val primaryCruxHoldNo: Int?,
    val primaryCruxDurationMs: Int?,
    val primaryReasonLabel: String?,
    val dangerEventCount: Int?,
    val feedbackTypes: List<String>,
    val loadFocusLabel: String?,
    val lowerBodyDriveScore: Int?,
    val overallMovementScore: Int?,
    val feedbackLine: String,
    val coachingLine: String,
    val effectiveModeLabel: String,
    val fallbackLabel: String?
)

internal const val FinalAnalysisUnknownMetricText = "정보 없음"
private const val StabilityTimelineSampleCount = 28
private const val NoAiNarrative = "AI 분석 응답이 아직 없습니다."
internal val DefaultFinalAnalysisTimeline = List(StabilityTimelineSampleCount) { 0.5f }

internal fun buildFinalAnalysisAttemptSummaries(
    attemptCount: Int,
    totalHolds: Int = 0,
    aiResults: List<AiAnalysisResult?> = emptyList(),
    holdReachResults: List<AttemptHoldReachResult> = emptyList(),
    contactDebugResults: List<PolygonHoldContactDebugResult> = emptyList()
): List<FinalAnalysisAttemptSummary> {
    val resolvedAttemptCount = max(
        max(attemptCount, aiResults.size),
        holdReachResults.size
    ).coerceAtLeast(1)
    return List(resolvedAttemptCount) { index ->
        val holdReachResult = holdReachResults.getOrNull(index)
        val aiAnalysisResult = aiResults.getOrNull(index)
        val contactDebugResult = contactDebugResults.getOrNull(index)
        val baseSummary = aiAnalysisResult?.toFinalAnalysisAttemptSummary(
            attemptNo = index + 1,
            totalHolds = totalHolds,
            holdReachResult = holdReachResult
        )
            ?: emptyFinalAnalysisAttemptSummary(
                attemptNo = index + 1,
                totalHolds = totalHolds,
                holdReachResult = holdReachResult
            )
        baseSummary
            .withAlignedDisplayCrux(
                aiAnalysisResult = aiAnalysisResult,
                contactDebugResult = contactDebugResult
            )
            .withComputedLowerBodyDriveScore(
                aiAnalysisResult = aiAnalysisResult,
                contactDebugResult = contactDebugResult
            )
            .withComputedStabilityRetentionScore(
                aiAnalysisResult = aiAnalysisResult,
                contactDebugResult = contactDebugResult
            )
            .withComputedStabilityRecoveryScore(
                contactDebugResult = contactDebugResult
            )
            .withComputedOverallMovementScore()
    }
}

internal fun FinalAnalysisAttemptSummary.withAlignedDisplayCrux(
    aiAnalysisResult: AiAnalysisResult?,
    contactDebugResult: PolygonHoldContactDebugResult?
): FinalAnalysisAttemptSummary {
    val displayCrux = resolveAlignedDisplayCruxSummary(
        aiAnalysisResult = aiAnalysisResult,
        contactDebugResult = contactDebugResult
    ) ?: return this

    if (
        primaryCruxHoldNo == displayCrux.holdNo &&
        primaryCruxDurationMs == displayCrux.durationMs &&
        stabilityFocusFraction == displayCrux.focusFraction
    ) {
        return this
    }

    return copy(
        primaryCruxHoldNo = displayCrux.holdNo,
        primaryCruxDurationMs = displayCrux.durationMs,
        stabilityFocusFraction = displayCrux.focusFraction ?: stabilityFocusFraction
    )
}

internal fun FinalAnalysisAttemptSummary.withComputedLowerBodyDriveScore(
    aiAnalysisResult: AiAnalysisResult?,
    contactDebugResult: PolygonHoldContactDebugResult?
): FinalAnalysisAttemptSummary {
    val computedScore = calculateAttemptLowerBodyDriveScore(
        summary = this,
        aiAnalysisResult = aiAnalysisResult,
        contactDebugResult = contactDebugResult
    ) ?: return this

    return if (lowerBodyDriveScore == computedScore) {
        this
    } else {
        copy(lowerBodyDriveScore = computedScore)
    }
}

internal fun FinalAnalysisAttemptSummary.withComputedStabilityRetentionScore(
    aiAnalysisResult: AiAnalysisResult?,
    contactDebugResult: PolygonHoldContactDebugResult?
): FinalAnalysisAttemptSummary {
    val computedScore = calculateStabilityRetentionScore(
        summary = this,
        aiAnalysisResult = aiAnalysisResult,
        contactDebugResult = contactDebugResult
    ) ?: return this

    return if (stabilityRetentionScore == computedScore) {
        this
    } else {
        copy(
            insideSupportRatio = computedScore,
            insideSupportRatioText = "${computedScore}점",
            stabilityRetentionScore = computedScore
        )
    }
}

internal fun FinalAnalysisAttemptSummary.withComputedStabilityRecoveryScore(
    contactDebugResult: PolygonHoldContactDebugResult?
): FinalAnalysisAttemptSummary {
    val computedScore = calculateRecoveryScore(
        summary = this,
        contactDebugResult = contactDebugResult
    ) ?: return this

    return if (stabilityRecoveryScore == computedScore) {
        this
    } else {
        copy(stabilityRecoveryScore = computedScore)
    }
}

internal fun FinalAnalysisAttemptSummary.withComputedOverallMovementScore(): FinalAnalysisAttemptSummary {
    val computedScore = calculateOverallMovementScore(this) ?: return this

    return if (overallMovementScore == computedScore) {
        this
    } else {
        copy(overallMovementScore = computedScore)
    }
}

private data class AlignedDisplayCruxSummary(
    val holdNo: Int,
    val durationMs: Int?,
    val focusFraction: Float?
)

private fun resolveAlignedDisplayCruxSummary(
    aiAnalysisResult: AiAnalysisResult?,
    contactDebugResult: PolygonHoldContactDebugResult?
): AlignedDisplayCruxSummary? {
    val analysis = aiAnalysisResult ?: return null
    val analysisStartTimeMs = contactDebugResult?.findFourPointContactStartTimeMs()
    val candidates = (analysis.cruxResult.topCandidates + analysis.cruxResult.allCandidates)
        .filter { candidate ->
            candidate.holdId > 1 && candidate.bestSegment != null
        }
        .distinctBy { candidate ->
            Triple(
                candidate.holdId,
                candidate.bestSegment?.startTimeMs,
                candidate.bestSegment?.endTimeMs
            )
        }

    val preferredCandidate = candidates.firstOrNull { candidate ->
        val segment = candidate.bestSegment ?: return@firstOrNull false
        analysisStartTimeMs == null || segment.startTimeMs >= analysisStartTimeMs
    } ?: candidates.firstOrNull { candidate ->
        val segment = candidate.bestSegment ?: return@firstOrNull false
        analysisStartTimeMs == null || segment.endTimeMs >= analysisStartTimeMs
    } ?: return null

    val segment = preferredCandidate.bestSegment ?: return null
    val effectiveStartTimeMs = max(segment.startTimeMs, analysisStartTimeMs ?: 0L)
    val effectiveEndTimeMs = max(segment.endTimeMs, effectiveStartTimeMs)
    val durationMs = (segment.endTimeMs - effectiveStartTimeMs)
        .takeIf { it > 0L }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
    val focusFraction = analysis.calculateDisplayCruxFocusFraction(
        startTimeMs = effectiveStartTimeMs,
        endTimeMs = effectiveEndTimeMs
    )

    return AlignedDisplayCruxSummary(
        holdNo = preferredCandidate.holdId,
        durationMs = durationMs,
        focusFraction = focusFraction
    )
}

private fun PolygonHoldContactDebugResult.findFourPointContactStartTimeMs(): Long? {
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

private data class StabilityRetentionFrame(
    val insideSupport: Boolean?,
    val stabilityMarginM: Float?,
    val confidence: Float?,
    val comProjXz: Pair<Float, Float>?
)

private data class StabilityRetentionSignal(
    val insideSupportScore: Int,
    val marginReserveScore: Int,
    val comJitterScore: Int?,
    val marginJitterScore: Int?,
    val lowMarginRatio: Float,
    val negativeMarginRatio: Float
)

internal fun calculateStabilityRetentionScore(
    summary: FinalAnalysisAttemptSummary,
    aiAnalysisResult: AiAnalysisResult?,
    contactDebugResult: PolygonHoldContactDebugResult? = null
): Int? {
    val signal = aiAnalysisResult?.extractStabilityRetentionSignal(
        summary = summary,
        contactDebugResult = contactDebugResult
    )

    if (signal == null) {
        return summary.insideSupportRatio?.coerceIn(0, 100)
    }

    val jitterScore = signal.comJitterScore ?: signal.marginJitterScore ?: signal.insideSupportScore
    val retentionScore = (
        24f +
            signal.insideSupportScore * 0.60f +
            signal.marginReserveScore * 0.12f +
            jitterScore * 0.05f +
            stabilityRetentionSuccessBonus(
                isSuccess = summary.isSuccess,
                insideSupportScore = signal.insideSupportScore,
                marginReserveScore = signal.marginReserveScore
            ) -
            stabilityRetentionSeverePenalty(
                lowMarginRatio = signal.lowMarginRatio,
                negativeMarginRatio = signal.negativeMarginRatio
            )
        ).roundToInt()

    return applyStabilityRetentionFloor(
        isSuccess = summary.isSuccess,
        insideSupportScore = signal.insideSupportScore,
        score = retentionScore
    ).coerceIn(0, 100)
}

private fun AiAnalysisResult.extractStabilityRetentionSignal(
    summary: FinalAnalysisAttemptSummary,
    contactDebugResult: PolygonHoldContactDebugResult?
): StabilityRetentionSignal? {
    val frames = physicsResult
        ?.getArrayOrNull("frames")
        ?.mapNotNull { frame ->
            frame.asObjectOrNull()
                ?.getObjectOrNull("support_stability")
                ?.toStabilityRetentionFrame()
        }
        .orEmpty()

    if (frames.isEmpty()) {
        return null
    }

    val durationMs = estimateRecoveryDurationMs(summary)
    val analysisStartFraction = contactDebugResult
        ?.findFourPointContactStartTimeMs()
        ?.takeIf { durationMs > 0L }
        ?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
    val startIndex = if (analysisStartFraction == null) {
        0
    } else {
        (analysisStartFraction * (frames.lastIndex.coerceAtLeast(0)).toFloat())
            .roundToInt()
            .coerceIn(0, frames.lastIndex)
    }
    val scopedFrames = frames.drop(startIndex)
        .filter { frame -> frame.confidence == null || frame.confidence >= 0.35f }

    if (scopedFrames.size < 3) {
        return null
    }

    val insideSupportFrames = scopedFrames.mapNotNull { it.insideSupport }
    val insideSupportScore = if (insideSupportFrames.isNotEmpty()) {
        ((insideSupportFrames.count { it } * 100f) / insideSupportFrames.size.toFloat())
            .roundToInt()
            .coerceIn(0, 100)
    } else {
        summary.insideSupportRatio ?: return null
    }

    val margins = scopedFrames.mapNotNull { it.stabilityMarginM }
    val marginReserveScore = if (margins.isNotEmpty()) {
        (margins.map { margin ->
            (margin / 0.020f).coerceIn(0f, 1f)
        }.average() * 100.0).roundToInt().coerceIn(0, 100)
    } else {
        insideSupportScore
    }

    val comJitterScore = scopedFrames
        .mapNotNull { it.comProjXz }
        .takeIf { it.size >= 5 }
        ?.let(::calculateComJitterScore)

    val marginJitterScore = margins
        .takeIf { it.size >= 4 }
        ?.let(::calculateMarginJitterScore)

    val lowMarginRatio = if (margins.isNotEmpty()) {
        margins.count { it < 0.010f }.toFloat() / margins.size.toFloat()
    } else {
        0f
    }

    val negativeMarginRatio = if (margins.isNotEmpty()) {
        margins.count { it < -0.005f }.toFloat() / margins.size.toFloat()
    } else {
        0f
    }

    return StabilityRetentionSignal(
        insideSupportScore = insideSupportScore,
        marginReserveScore = marginReserveScore,
        comJitterScore = comJitterScore,
        marginJitterScore = marginJitterScore,
        lowMarginRatio = lowMarginRatio,
        negativeMarginRatio = negativeMarginRatio
    )
}

internal fun calculateRecoveryScore(
    summary: FinalAnalysisAttemptSummary,
    contactDebugResult: PolygonHoldContactDebugResult? = null
): Int? {
    val timeline = summary.stabilityTimeline
    if (timeline.size < 3) return null

    val durationMs = estimateRecoveryDurationMs(summary)
    val analysisStartFraction = contactDebugResult
        ?.findFourPointContactStartTimeMs()
        ?.takeIf { durationMs > 0L }
        ?.let { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
    val startIndex = timeline.startIndex(analysisStartFraction)
    if (timeline.size - startIndex < 3) return null

    val lowestIndex = (startIndex..timeline.lastIndex).minByOrNull { timeline[it] } ?: return null
    val recoveryIndex = findRecoveryIndex(timeline, lowestIndex)
    val recoverySamples = recoveryIndex?.minus(lowestIndex)

    return when {
        recoverySamples == null && summary.isSuccess -> 56
        recoverySamples == null -> 26
        recoverySamples <= 2 -> 82
        recoverySamples <= 3 -> 70
        recoverySamples <= 6 -> 56
        recoverySamples <= 8 -> 42
        else -> 28
    }
}

private fun calculateAttemptLowerBodyDriveScore(
    summary: FinalAnalysisAttemptSummary,
    aiAnalysisResult: AiAnalysisResult?,
    contactDebugResult: PolygonHoldContactDebugResult?
): Int? {
    var score = 54
    var signalCount = 0

    summary.insideSupportRatio?.let { insideSupportRatio ->
        signalCount += 1
        score += when {
            insideSupportRatio >= 78 -> 7
            insideSupportRatio >= 62 -> 3
            insideSupportRatio < 48 -> -4
            else -> 0
        }
    }

    summary.stableContactRatio?.let { stableContactRatio ->
        signalCount += 1
        score += when {
            stableContactRatio >= 78 -> 4
            stableContactRatio >= 62 -> 1
            stableContactRatio < 50 -> -3
            else -> 0
        }
    }

    aiAnalysisResult?.let { analysis ->
        val topCandidate = analysis.cruxResult.topCandidates.firstOrNull()
            ?: analysis.cruxResult.allCandidates.firstOrNull()
        val dominantLimbTokens = topCandidate?.bestSegment?.dominantLimbs.orEmpty()
            .map { it.lowercase() }
        val dominantModeTokens = topCandidate?.bestSegment?.dominantModes.orEmpty()
            .map { it.lowercase() }
        val feedbackTypes = analysis.buildFeedbackTypes(
            insideSupportRatio = summary.insideSupportRatio,
            stableContactRatio = summary.stableContactRatio
        )
        val peakLoadGroup = analysis.extractPeakBodyLoadGroupToken()

        if (dominantLimbTokens.isNotEmpty()) {
            signalCount += 1
            val hasFootDominance = dominantLimbTokens.any { it.contains("foot") }
            val hasHandDominance = dominantLimbTokens.any { it.contains("hand") }
            score += when {
                hasFootDominance && !hasHandDominance -> 11
                hasFootDominance -> 5
                hasHandDominance -> -7
                else -> 0
            }
        }

        if (dominantModeTokens.isNotEmpty()) {
            signalCount += 1
            val hasStepOrPush = dominantModeTokens.any { it.contains("step") || it.contains("push") }
            val hasGripOrPull = dominantModeTokens.any { it.contains("grip") || it.contains("pull") }
            score += when {
                hasStepOrPush && !hasGripOrPull -> 8
                hasStepOrPush -> 4
                hasGripOrPull -> -4
                else -> 0
            }
        }

        if (peakLoadGroup != null) {
            signalCount += 1
            score += when (peakLoadGroup.lowercase()) {
                "left_leg", "right_leg" -> 11
                "core" -> 7
                "left_arm", "right_arm" -> -7
                else -> 0
            }
        }

        if (feedbackTypes.isNotEmpty()) {
            signalCount += 1
            if ("발 사용 부족" in feedbackTypes) score -= 8
            if ("팔 사용 과다" in feedbackTypes) score -= 6
            if ("중심 흔들림" in feedbackTypes) score -= 2
        }
    }

    contactDebugResult?.buildLowerBodyDriveContactSignal()?.let { contactSignal ->
        signalCount += 1
        score += contactSignal.scoreDelta
    }

    if (summary.isSuccess) {
        score += 5
    }

    return if (signalCount == 0) null else score.coerceIn(0, 100)
}

private data class LowerBodyDriveContactSignal(
    val scoreDelta: Int
)

private fun PolygonHoldContactDebugResult.buildLowerBodyDriveContactSignal(): LowerBodyDriveContactSignal? {
    val analysisStartTimeMs = findFourPointContactStartTimeMs()
    val analyzedFrames = frames.filter { frame ->
        frame.activeContacts.isNotEmpty() &&
            (analysisStartTimeMs == null || frame.frameTimeMs >= analysisStartTimeMs)
    }
    if (analyzedFrames.isEmpty()) {
        return null
    }

    val totalFrames = analyzedFrames.size.toFloat()
    val bothFeetRatio = analyzedFrames.count { frame ->
        val activeFeet = frame.activeContacts.map { it.limb }.filterNot(PolygonTrackedLimb::isHand).toSet()
        activeFeet.containsAll(setOf(PolygonTrackedLimb.LEFT_FOOT, PolygonTrackedLimb.RIGHT_FOOT))
    } / totalFrames
    val anyFootRatio = analyzedFrames.count { frame ->
        frame.activeContacts.any { !it.limb.isHand }
    } / totalFrames
    val handOnlyRatio = analyzedFrames.count { frame ->
        val hasHand = frame.activeContacts.any { it.limb.isHand }
        val hasFoot = frame.activeContacts.any { !it.limb.isHand }
        hasHand && !hasFoot
    } / totalFrames

    val footEngageCount = analyzedFrames.sumOf { frame ->
        frame.limbStates.count { state ->
            state.transition == "engage" && !state.limb.isHand
        }
    }
    val handEngageCount = analyzedFrames.sumOf { frame ->
        frame.limbStates.count { state ->
            state.transition == "engage" && state.limb.isHand
        }
    }

    var scoreDelta = 0
    scoreDelta += when {
        bothFeetRatio >= 0.42f -> 11
        bothFeetRatio >= 0.28f -> 7
        bothFeetRatio < 0.12f -> -4
        else -> 0
    }
    scoreDelta += when {
        anyFootRatio >= 0.78f -> 9
        anyFootRatio >= 0.60f -> 4
        anyFootRatio < 0.38f -> -5
        else -> 0
    }
    scoreDelta += when {
        handOnlyRatio >= 0.30f -> -8
        handOnlyRatio >= 0.18f -> -5
        handOnlyRatio <= 0.06f -> 4
        else -> 0
    }

    val totalEngageCount = footEngageCount + handEngageCount
    if (totalEngageCount >= 3) {
        val footEngageShare = footEngageCount / totalEngageCount.toFloat()
        scoreDelta += when {
            footEngageShare >= 0.46f -> 9
            footEngageShare >= 0.36f -> 4
            footEngageShare < 0.22f -> -6
            footEngageShare < 0.30f -> -3
            else -> 0
        }
    }

    return LowerBodyDriveContactSignal(scoreDelta = scoreDelta)
}

private fun AiAnalysisResult.calculateDisplayCruxFocusFraction(
    startTimeMs: Long,
    endTimeMs: Long
): Float? {
    val resolvedDurationMs = videoMetadata.durationMs()
        ?: cruxResult.topCandidates.mapNotNull { it.bestSegment?.endTimeMs }.maxOrNull()
        ?: cruxResult.allCandidates.mapNotNull { it.bestSegment?.endTimeMs }.maxOrNull()
        ?: endTimeMs.takeIf { it > 0L }
        ?: return null

    if (resolvedDurationMs <= 0L) return null

    val segmentMidMs = if (endTimeMs > startTimeMs) {
        (startTimeMs + endTimeMs) / 2L
    } else {
        startTimeMs
    }

    return (segmentMidMs.toFloat() / resolvedDurationMs.toFloat()).coerceIn(0f, 1f)
}

private fun emptyFinalAnalysisAttemptSummary(
    attemptNo: Int,
    totalHolds: Int = 0,
    holdReachResult: AttemptHoldReachResult? = null
): FinalAnalysisAttemptSummary {
    val reachedHolds = holdReachResult
        ?.highestReachedHoldNo
        ?.takeIf { it > 0 }
    val isSuccess = holdReachResult?.completedWithBothHandsOnEndHold == true
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = false,
        isSuccess = isSuccess,
        analysisPoints = emptyList(),
        videoDurationMs = null,
        reachedHolds = reachedHolds,
        reachedHoldsText = reachedHolds?.toString() ?: FinalAnalysisUnknownMetricText,
        processedFrames = null,
        processedFramesText = FinalAnalysisUnknownMetricText,
        highConfidenceRatio = null,
        highConfidenceRatioText = FinalAnalysisUnknownMetricText,
        insideSupportRatio = null,
        insideSupportRatioText = FinalAnalysisUnknownMetricText,
        stabilityRetentionScore = null,
        stableContactFrameCount = null,
        stableContactFrameCountText = FinalAnalysisUnknownMetricText,
        stableContactRatio = null,
        stableContactRatioText = FinalAnalysisUnknownMetricText,
        stabilityRecoveryScore = null,
        stabilityTimeline = DefaultFinalAnalysisTimeline,
        stabilityFocusFraction = null,
        stabilityHighlights = listOf(NoAiNarrative),
        stabilityNarrative = NoAiNarrative,
        failureHighlights = listOf(NoAiNarrative),
        failureNarrative = NoAiNarrative,
        primaryCruxHoldNo = null,
        primaryCruxDurationMs = null,
        primaryReasonLabel = null,
        dangerEventCount = null,
        feedbackTypes = emptyList(),
        loadFocusLabel = null,
        lowerBodyDriveScore = null,
        overallMovementScore = null,
        feedbackLine = buildFallbackFeedbackLine(
            isSuccess = isSuccess,
            reachedHolds = reachedHolds,
            totalHolds = totalHolds,
            hasHoldReachResult = holdReachResult != null
        ),
        coachingLine = buildFallbackCoachingLine(
            isSuccess = isSuccess,
            hasHoldReachResult = holdReachResult != null
        ),
        effectiveModeLabel = "",
        fallbackLabel = null
    )
}

private fun AiAnalysisResult.toFinalAnalysisAttemptSummary(
    attemptNo: Int,
    totalHolds: Int,
    holdReachResult: AttemptHoldReachResult? = null
): FinalAnalysisAttemptSummary {
    val analysisPoints = toFinalAnalysisPoints()
    val videoDurationMs = extractVideoDurationMs()
    val reachedHolds = holdReachResult?.highestReachedHoldNo ?: extractReachedHoldNo()
    val processedFrames = extractProcessedFrames()
    val highConfidenceRatio = extractHighConfidenceRatioPercent(processedFrames = processedFrames)
    val insideSupportRatio = extractInsideSupportRatioPercent()
    val stableContactFrameCount = extractOkContactForceFrameCount()
    val stableContactRatio = extractStableContactRatioPercent(
        processedFrames = processedFrames,
        stableContactFrameCount = stableContactFrameCount
    )
    val stabilityHighlights = buildStabilityHighlights(
        highConfidenceRatio = highConfidenceRatio,
        insideSupportRatio = insideSupportRatio,
        stableContactRatio = stableContactRatio
    )
    val primaryCruxHoldNo = extractPrimaryCruxHoldNo()
    val primaryCruxDurationMs = extractPrimaryCruxDurationMs()
    val primaryReasonLabel = extractPrimaryReasonLabel()
    val failureHighlights = buildFailureHighlights()
    val isSuccess = holdReachResult?.completedWithBothHandsOnEndHold
        ?: (totalHolds > 0 && (reachedHolds ?: 0) >= totalHolds)
    val loadFocusLabel = extractPeakBodyLoadGroupLabel()
    val feedbackTypes = buildFeedbackTypes(
        insideSupportRatio = insideSupportRatio,
        stableContactRatio = stableContactRatio
    )
    val dangerEventCount = extractDangerEventCount(
        insideSupportRatio = insideSupportRatio,
        stableContactRatio = stableContactRatio
    )
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = true,
        isSuccess = isSuccess,
        analysisPoints = analysisPoints,
        videoDurationMs = videoDurationMs,
        reachedHolds = reachedHolds,
        reachedHoldsText = reachedHolds?.toString() ?: FinalAnalysisUnknownMetricText,
        processedFrames = processedFrames,
        processedFramesText = processedFrames?.toString() ?: FinalAnalysisUnknownMetricText,
        highConfidenceRatio = highConfidenceRatio,
        highConfidenceRatioText = highConfidenceRatio?.let { "$it%" } ?: FinalAnalysisUnknownMetricText,
        insideSupportRatio = insideSupportRatio,
        insideSupportRatioText = insideSupportRatio?.let { "$it%" } ?: FinalAnalysisUnknownMetricText,
        stabilityRetentionScore = null,
        stableContactFrameCount = stableContactFrameCount,
        stableContactFrameCountText = stableContactFrameCount?.toString() ?: FinalAnalysisUnknownMetricText,
        stableContactRatio = stableContactRatio,
        stableContactRatioText = stableContactRatio?.let { "$it%" } ?: FinalAnalysisUnknownMetricText,
        stabilityRecoveryScore = null,
        stabilityTimeline = extractStabilityTimeline(),
        stabilityFocusFraction = extractStabilityFocusFraction(),
        stabilityHighlights = stabilityHighlights,
        stabilityNarrative = stabilityHighlights.joinToString(" "),
        failureHighlights = failureHighlights,
        failureNarrative = failureHighlights.joinToString(" "),
        primaryCruxHoldNo = primaryCruxHoldNo,
        primaryCruxDurationMs = primaryCruxDurationMs,
        primaryReasonLabel = primaryReasonLabel,
        dangerEventCount = dangerEventCount,
        feedbackTypes = feedbackTypes,
        loadFocusLabel = loadFocusLabel,
        lowerBodyDriveScore = null,
        overallMovementScore = null,
        feedbackLine = buildFeedbackLine(
            isSuccess = isSuccess,
            reachedHolds = reachedHolds,
            totalHolds = totalHolds,
            insideSupportRatio = insideSupportRatio,
            stableContactRatio = stableContactRatio
        ),
        coachingLine = buildCoachingLine(
            feedbackTypes = feedbackTypes,
            loadFocusLabel = loadFocusLabel
        ),
        effectiveModeLabel = mode.toDisplayLabel(),
        fallbackLabel = if (requestedMode != mode) "${mode.toDisplayLabel()} 대체" else null
    )
}

internal fun calculateOverallMovementScore(summary: FinalAnalysisAttemptSummary): Int? {
    val weightedScores = buildList<Pair<Int, Float>> {
        (summary.stabilityRetentionScore ?: summary.insideSupportRatio)
            ?.let { add(it.coerceIn(0, 100) to 0.32f) }
        summary.stabilityRecoveryScore
            ?.let { add(it.coerceIn(0, 100) to 0.24f) }
        summary.lowerBodyDriveScore
            ?.let { add(it.coerceIn(0, 100) to 0.26f) }
        summary.stableContactRatio
            ?.let { add(it.coerceIn(0, 100) to 0.18f) }
    }

    if (weightedScores.isEmpty()) return null

    val totalWeight = weightedScores.sumOf { it.second.toDouble() }.toFloat().coerceAtLeast(0.01f)
    var score = weightedScores.sumOf { (value, weight) ->
        value.toDouble() * weight.toDouble()
    }.toFloat() / totalWeight

    if (summary.isSuccess) {
        score += 3f
    }

    return score.roundToInt().coerceIn(0, 100)
}

private fun buildFallbackFeedbackLine(
    isSuccess: Boolean,
    reachedHolds: Int?,
    totalHolds: Int,
    hasHoldReachResult: Boolean
): String {
    return when {
        isSuccess ->
            "양손으로 종료 홀드를 잡아 완등에 성공했습니다."

        reachedHolds != null && totalHolds > 0 ->
            "${reachedHolds}번 홀드까지는 도달했지만 종료 홀드를 양손으로 안정적으로 잡지는 못했습니다."

        hasHoldReachResult ->
            "종료 홀드까지 이어지지 않아 시도를 마무리하지 못했습니다."

        else ->
            "AI 분석 결과가 아직 충분하지 않아 종합 피드백을 만들지 못했습니다."
    }
}

private fun buildFallbackCoachingLine(
    isSuccess: Boolean,
    hasHoldReachResult: Boolean
): String {
    return when {
        isSuccess ->
            "마지막 종료 홀드에서도 양손 안착을 유지한 흐름을 다음 시도 기준으로 삼아보세요."

        hasHoldReachResult ->
            "종료 홀드에서는 한 손 리치 뒤 바로 반대 손까지 연결해 양손 안착을 만드는 연습이 도움이 됩니다."

        else ->
            "영상과 홀드 정보가 충분해지면 더 구체적인 코칭을 제공할 수 있습니다."
    }
}

private fun AiAnalysisResult.toFinalAnalysisPoints(): List<AnalysisPoint> {
    val candidates = cruxResult.topCandidates.ifEmpty {
        cruxResult.allCandidates.take(3)
    }

    return candidates.take(3).mapIndexed { index, candidate ->
        val details = buildList {
            cleanedReasonText(candidate.reasonTags.ifEmpty { candidate.bestSegment?.reasonTags.orEmpty() })
                ?.let(::add)
            candidate.bestSegment
                ?.dominantLimbs
                ?.map(::formatToken)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?.let { add("주요 신체 부위 $it") }
            candidate.bestSegment
                ?.dominantModes
                ?.map(::formatToken)
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?.let { add("주요 동작 $it") }
            candidate.bestSegment
                ?.meanCoreLoad
                ?.takeIf { it > 0.0 }
                ?.let(::formatCoreLoadInsight)
                ?.let(::add)
            candidate.bestSegment
                ?.meanNegativeMarginCm
                ?.takeIf { it != 0.0 }
                ?.let { add("균형 이탈 ${it.roundToInt()}cm") }
            candidate.bestSegment
                ?.okFraction
                ?.let { ratio ->
                    add("손발 지지 안정도 ${(ratio * 100.0).roundToInt().coerceIn(0, 100)}%")
                }
        }

        val displayDetails = details.take(3)

        AnalysisPoint(
            index = index + 1,
            timeMs = candidate.bestSegment?.startTimeMs ?: ((index + 1) * 15_000L),
            description = buildString {
                append("${candidate.holdId}번 홀드")
                if (displayDetails.isNotEmpty()) {
                    append(": ")
                    append(displayDetails.joinToString(" / "))
                }
            }
        )
    }
}

private fun AiAnalysisResult.extractProcessedFrames(): Int? {
    return physicsSummary?.getIntOrNull("processed_frames")?.takeIf { it > 0 }
        ?: physicsResult?.getObjectOrNull("video_metadata")?.getIntOrNull("processed_frames")?.takeIf { it > 0 }
        ?: videoMetadata?.processedFrames?.takeIf { it > 0 }
}

private fun AiAnalysisResult.extractReachedHoldNo(): Int? {
    val fromPhysicsFrames = physicsResult
        ?.getArrayOrNull("frames")
        ?.mapNotNull { frame ->
            frame.asObjectOrNull()
                ?.extractActiveHoldIds()
                ?.maxOrNull()
        }
        ?.maxOrNull()

    return fromPhysicsFrames?.takeIf { it > 0 }
        ?: cruxResult.topCandidates.firstOrNull()?.holdId?.takeIf { it > 0 }
        ?: cruxResult.allCandidates.firstOrNull()?.holdId?.takeIf { it > 0 }
}

private fun AiAnalysisResult.extractVideoDurationMs(): Long? {
    return videoMetadata.durationMs()
        ?: cruxResult.topCandidates.mapNotNull { it.bestSegment?.endTimeMs }.maxOrNull()
        ?: cruxResult.allCandidates.mapNotNull { it.bestSegment?.endTimeMs }.maxOrNull()
}

private fun AiAnalysisResult.extractHighConfidenceFrameCount(): Int? {
    return physicsSummary?.getIntOrNull("high_confidence_frame_count")?.takeIf { it >= 0 }
        ?: physicsResult?.getArrayOrNull("frames")
            ?.count { frame -> frame.asObjectOrNull().analysisConfidence() == "high" }
            ?.takeIf { it > 0 }
}

private fun AiAnalysisResult.extractHighConfidenceRatioPercent(
    processedFrames: Int?
): Int? {
    val totalFrames = processedFrames?.takeIf { it > 0 } ?: return null
    val highConfidenceFrameCount = extractHighConfidenceFrameCount() ?: return null
    return (highConfidenceFrameCount * 100.0 / totalFrames).roundToInt().coerceIn(0, 100)
}

private fun AiAnalysisResult.extractInsideSupportRatioPercent(): Int? {
    val summary = supportStabilitySummary()
    val ratio = summary?.getDoubleOrNull("inside_support_ratio")
    if (ratio != null) {
        return (ratio * 100).roundToInt().coerceIn(0, 100)
    }

    val insideCount = summary?.getDoubleOrNull("inside_support_count")
    val outsideCount = summary?.getDoubleOrNull("outside_support_count")
    val totalCount = (insideCount ?: 0.0) + (outsideCount ?: 0.0)
    if (totalCount <= 0.0) {
        return null
    }

    return ((insideCount ?: 0.0) / totalCount * 100.0).roundToInt().coerceIn(0, 100)
}

private fun AiAnalysisResult.extractOkContactForceFrameCount(): Int? {
    return physicsSummary?.getIntOrNull("ok_contact_force_frame_count")?.takeIf { it >= 0 }
        ?: physicsResult?.getArrayOrNull("frames")
            ?.count { frame -> frame.asObjectOrNull().contactForceStatus() == "ok" }
            ?.takeIf { it > 0 }
}

private fun AiAnalysisResult.extractStableContactRatioPercent(
    processedFrames: Int?,
    stableContactFrameCount: Int?
): Int? {
    val totalFrames = processedFrames?.takeIf { it > 0 } ?: return null
    val stableContactFrames = stableContactFrameCount ?: return null
    return (stableContactFrames * 100.0 / totalFrames).roundToInt().coerceIn(0, 100)
}

private fun AiAnalysisResult.extractStabilityTimeline(): List<Float> {
    val margins = physicsResult
        ?.getArrayOrNull("frames")
        ?.mapNotNull { frame ->
            frame.asObjectOrNull()
                ?.getObjectOrNull("support_stability")
                ?.getDoubleOrNull("stability_margin_m")
                ?.toFloat()
        }
        .orEmpty()

    if (margins.isNotEmpty()) {
        return margins.normalizeSeries().downsampleTo(StabilityTimelineSampleCount)
    }

    val candidates = cruxResult.topCandidates.ifEmpty { cruxResult.allCandidates.take(3) }
    if (candidates.isEmpty()) {
        return DefaultFinalAnalysisTimeline
    }

    val candidateSeries = candidates.mapIndexed { index, candidate ->
        val center = candidate.bestSegment?.let { segment ->
            normalizedSegmentFraction(segment.startTimeMs, segment.endTimeMs)
        } ?: if (candidates.size == 1) {
            0.5f
        } else {
            index / (candidates.size - 1).toFloat()
        }
        val score = candidate.bestSegment?.segmentCruxScore
            ?: candidate.physicsCruxScore
            ?: candidate.fastCruxScore
            ?: 0.7
        center to score.toFloat().coerceIn(0f, 1.5f)
    }

    return List(StabilityTimelineSampleCount) { index ->
        val fraction = if (StabilityTimelineSampleCount == 1) {
            0f
        } else {
            index / (StabilityTimelineSampleCount - 1).toFloat()
        }
        val pressure = candidateSeries.sumOf { (center, score) ->
            val distance = abs(fraction - center)
            val weight = (1f - (distance / 0.18f).coerceAtMost(1f))
            (weight * (score / 1.5f)).toDouble()
        }.toFloat()
        (0.74f - pressure * 0.42f).coerceIn(0.12f, 0.94f)
    }
}

private fun AiAnalysisResult.extractStabilityFocusFraction(): Float? {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()
        ?: return null
    val segment = topCandidate.bestSegment ?: return null
    return normalizedSegmentFraction(segment.startTimeMs, segment.endTimeMs)
}

private fun AiAnalysisResult.extractPrimaryCruxHoldNo(): Int? {
    return cruxResult.topCandidates
        .firstOrNull { candidate -> candidate.holdId > 1 }
        ?.holdId
        ?: cruxResult.allCandidates
            .firstOrNull { candidate -> candidate.holdId > 1 }
            ?.holdId
}

private fun AiAnalysisResult.extractPrimaryCruxDurationMs(): Int? {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()
        ?: return null
    val segment = topCandidate.bestSegment ?: return null
    val durationFromRange = (segment.endTimeMs - segment.startTimeMs)
        .takeIf { it > 0L }
        ?.coerceAtMost(Int.MAX_VALUE.toLong())
        ?.toInt()
    return durationFromRange
        ?: segment.durationSeconds
            .takeIf { it > 0.0 }
            ?.times(1000.0)
            ?.roundToInt()
}

private fun AiAnalysisResult.extractPrimaryReasonLabel(): String? {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()
        ?: return null
    return cleanedReasonText(
        topCandidate.reasonTags.ifEmpty { topCandidate.bestSegment?.reasonTags.orEmpty() }
    )
}

private fun AiAnalysisResult.extractDangerEventCount(
    insideSupportRatio: Int?,
    stableContactRatio: Int?
): Int {
    val riskTokens = setOf(
        "instability",
        "negative_margin",
        "loss_of_balance",
        "contact_loss",
        "load_spike"
    )
    val candidateRiskCount = cruxResult.allCandidates.count { candidate ->
        val candidateTokens = (
            candidate.reasonTags +
                candidate.bestSegment?.reasonTags.orEmpty()
            ).map { it.lowercase() }
        candidateTokens.any { it in riskTokens }
    }

    if (candidateRiskCount > 0) {
        return candidateRiskCount.coerceIn(0, 4)
    }

    val balance = insideSupportRatio ?: 100
    val contact = stableContactRatio ?: 100
    return when {
        balance < 45 || contact < 45 -> 3
        balance < 55 || contact < 55 -> 2
        balance < 65 || contact < 65 -> 1
        else -> 0
    }
}

private fun AiAnalysisResult.buildStabilityHighlights(
    highConfidenceRatio: Int?,
    insideSupportRatio: Int?,
    stableContactRatio: Int?
): List<String> {
    return buildList {
        fallbackNarrativePrefix()?.let(::add)
        insideSupportRatio?.let { add("균형 유지율 ${it}%") }
        stableContactRatio?.let { add("안정 접촉 비율 ${it}%") }
        extractDominantPhaseLabel()?.let { add("주요 구간: $it") }
        extractPointSupportFrameCount()?.takeIf { it > 0 }?.let { add("한 곳에만 의존한 구간이 반복됨") }
        highConfidenceRatio?.takeIf { it < 70 }?.let { add("분석 신뢰도 ${it}%") }
    }.distinct().take(4).ifEmpty {
        listOf("안정성을 요약하기에 충분한 물리 분석 데이터가 아직 없어요.")
    }
}

private fun AiAnalysisResult.buildFailureHighlights(): List<String> {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()

    if (topCandidate == null) {
        return listOfNotNull(
            fallbackNarrativePrefix(),
            "크럭스 후보가 없어 실패 원인을 충분히 요약하지 못했어요."
        ).ifEmpty {
            listOf(NoAiNarrative)
        }
    }

    return buildList {
        fallbackNarrativePrefix()?.let(::add)
        add("${topCandidate.holdId}번 홀드")
        extractPeakBodyLoadGroupLabel()?.let { add("부담 집중 부위: $it") }
        cleanedReasonText(topCandidate.reasonTags.ifEmpty { topCandidate.bestSegment?.reasonTags.orEmpty() })
            ?.let { add("핵심 원인: $it") }
        topCandidate.bestSegment
            ?.dominantLimbs
            ?.map(::formatToken)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.let { add("주요 사용 부위: $it") }
        topCandidate.bestSegment
            ?.dominantModes
            ?.map(::formatToken)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.let { add("주요 동작: $it") }
        topCandidate.bestSegment?.meanNegativeMarginCm
            ?.roundToInt()
            ?.takeIf { abs(it) >= 5 }
            ?.let { add("균형 이탈 ${it}cm") }
        topCandidate.bestSegment?.okFraction
            ?.let { add("안정 접촉 비율 ${(it * 100.0).roundToInt().coerceIn(0, 100)}%") }
        extractPeakBodyLoadGroupLabel()?.let { add("부담 집중 부위: $it") }
        extractPointSupportFrameCount()?.takeIf { it > 0 }
            ?.let { add("한 곳 의존 구간이 자주 나타남") }
    }.distinct().take(4)
}

private fun AiAnalysisResult.buildFeedbackTypes(
    insideSupportRatio: Int?,
    stableContactRatio: Int?
): List<String> {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()
    val dominantLimbTokens = topCandidate?.bestSegment?.dominantLimbs.orEmpty().map { it.lowercase() }
    val reasonTokens = (
        topCandidate?.reasonTags.orEmpty() +
            topCandidate?.bestSegment?.reasonTags.orEmpty()
        ).map { it.lowercase() }
    val peakLoadGroup = extractPeakBodyLoadGroupToken()
    val pointSupportFrameCount = extractPointSupportFrameCount() ?: 0
    val negativeMargin = topCandidate?.bestSegment?.meanNegativeMarginCm ?: 0.0
    val centerSway = (insideSupportRatio ?: 100) < 55 ||
        abs(negativeMargin) >= 10.0 ||
        pointSupportFrameCount > 0
    val footUnderuse = dominantLimbTokens.any { it.contains("hand") } &&
        dominantLimbTokens.none { it.contains("foot") } &&
        (
            peakLoadGroup == "left_arm" ||
                peakLoadGroup == "right_arm" ||
                (stableContactRatio ?: 100) < 60
            )
    val overHolding = reasonTokens.any {
        it == "long_dwell" || it == "longest_dwell" || it == "high_total_dwell"
    } || extractDominantPhaseToken() == "static_support"
    val armOveruse = peakLoadGroup == "left_arm" ||
        peakLoadGroup == "right_arm" ||
        (
            dominantLimbTokens.count { it.contains("hand") } >= 2 &&
                dominantLimbTokens.none { it.contains("foot") }
            )

    return buildList {
        if (footUnderuse) add("발 사용 부족")
        if (centerSway) add("중심 흔들림")
        if (armOveruse) add("팔 사용 과다")
        if (overHolding) add("과한 버티기")
    }.take(3)
}

private fun AiAnalysisResult.buildFeedbackLine(
    isSuccess: Boolean,
    reachedHolds: Int?,
    totalHolds: Int,
    insideSupportRatio: Int?,
    stableContactRatio: Int?
): String {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()
    val holdLabel = topCandidate?.holdId?.takeIf { it > 0 }?.let { "${it}번 홀드" }
    val reasonLabel = cleanedReasonText(
        topCandidate?.reasonTags.orEmpty().ifEmpty {
            topCandidate?.bestSegment?.reasonTags.orEmpty()
        }
    )
    val feedbackTypes = buildFeedbackTypes(
        insideSupportRatio = insideSupportRatio,
        stableContactRatio = stableContactRatio
    )
    val peakLoadLabel = extractPeakBodyLoadGroupLabel()

    return when {
        isSuccess && (insideSupportRatio ?: 0) >= 70 && (stableContactRatio ?: 0) >= 70 ->
            "균형과 손발 지지가 안정적이었어요."

        isSuccess ->
            "완등은 했지만 난구간에서 잠시 흔들렸어요."

        "발 사용 부족" in feedbackTypes && "팔 사용 과다" in feedbackTypes && holdLabel != null ->
            "${holdLabel}에서 발보다 팔 힘이 먼저 커졌어요."

        "중심 흔들림" in feedbackTypes && holdLabel != null ->
            "${holdLabel}에서 중심이 흔들렸어요."

        "과한 버티기" in feedbackTypes && holdLabel != null ->
            "${holdLabel}에서 너무 오래 버텼어요."

        "팔 사용 과다" in feedbackTypes && holdLabel != null && peakLoadLabel != null ->
            "${holdLabel}에서 ${peakLoadLabel} 부담이 커졌어요."

        reasonLabel != null && holdLabel != null ->
            "${holdLabel}에서 ${reasonLabel}이 두드러졌어요."

        reachedHolds != null && totalHolds > 0 ->
            "${reachedHolds}번 홀드 이후 연결이 어려웠어요."

        else ->
            "핵심 구간에서 흐름이 끊겼어요."
    }
}

private fun buildCoachingLine(
    feedbackTypes: List<String>,
    loadFocusLabel: String?
): String {
    return when {
        "발 사용 부족" in feedbackTypes ->
            "발을 먼저 세우고 손을 보내 보세요."

        "중심 흔들림" in feedbackTypes ->
            "중심을 먼저 옮기고 다음 동작을 이어가 보세요."

        "과한 버티기" in feedbackTypes ->
            "오래 버티기보다 리듬 있게 이어가 보세요."

        "팔 사용 과다" in feedbackTypes ->
            "팔로 버티기보다 발로 밀어 올려 보세요."

        loadFocusLabel == "몸통" ->
            "몸통 힘을 먼저 잡고 손을 움직여 보세요."

        loadFocusLabel == "왼팔" || loadFocusLabel == "오른팔" ->
            "한쪽 팔 대신 발로 하중을 나눠 보세요."

        loadFocusLabel == "왼다리" || loadFocusLabel == "오른다리" ->
            "반대 발도 빨리 세워 하중을 나눠 보세요."

        else ->
            "중심과 손발을 함께 연결하는 연습을 해보세요."
    }
}

private fun AiAnalysisResult.fallbackNarrativePrefix(): String? {
    if (requestedMode == mode) return null

    return when (fallbackReason) {
        AiAnalysisFallbackReason.MISSING_WEIGHT ->
            "체중 정보 없음 · ${mode.toDisplayLabel()} 사용"
        AiAnalysisFallbackReason.PHYSICS_REQUEST_FAILED ->
            "물리 분석 실패 · ${mode.toDisplayLabel()} 사용"
        null ->
            "${requestedMode.toDisplayLabel()} 요청 대체 · ${mode.toDisplayLabel()} 사용"
    }
}

private fun AiAnalysisMode.toDisplayLabel(): String {
    return when (this) {
        AiAnalysisMode.FAST -> "빠른 분석"
        AiAnalysisMode.PHYSICS -> "물리 분석"
    }
}

private fun AiAnalysisResult.slowestTimingLabel(): String? {
    return timingsSeconds.maxByOrNull { it.value }
        ?.key
        ?.let(::formatTimingToken)
        ?.takeIf { it.isNotBlank() }
}

private fun AiAnalysisResult.supportStabilitySummary(): JsonObject? {
    return physicsResult?.getObjectOrNull("support_stability_summary")
        ?: physicsSummary?.getObjectOrNull("support_stability_summary")
}

private fun AiAnalysisResult.extractPointSupportFrameCount(): Int? {
    return physicsSummary?.getIntOrNull("point_support_frame_count")?.takeIf { it >= 0 }
        ?: supportStabilitySummary()
            ?.getObjectOrNull("support_type_counts")
            ?.getIntOrNull("point_support")
            ?.takeIf { it >= 0 }
}

private fun AiAnalysisResult.extractFallbackAllLimbsFrameCount(): Int? {
    return physicsResult
        ?.getObjectOrNull("support_mode_counts")
        ?.getIntOrNull("fallback_all_limbs")
        ?.takeIf { it >= 0 }
}

private fun AiAnalysisResult.extractDominantPhaseLabel(): String? {
    return physicsResult
        ?.getObjectOrNull("phase_counts")
        ?.maxCountKeyOrNull()
        ?.let(::formatPhaseToken)
}

private fun AiAnalysisResult.extractDominantPhaseToken(): String? {
    return physicsResult
        ?.getObjectOrNull("phase_counts")
        ?.maxCountKeyOrNull()
}

private fun AiAnalysisResult.extractRecoveryRatioPercent(): Int? {
    val ratio = physicsResult
        ?.getObjectOrNull("dynamic_sequence_gate")
        ?.getDoubleOrNull("recovery_ratio")
        ?: physicsSummary?.getDoubleOrNull("recovery_ratio")
        ?: return null
    return (ratio * 100.0).roundToInt().coerceIn(0, 100)
}

private fun AiAnalysisResult.extractFitMeanErrorCm(): Int? {
    val fitMeanErrorM = physicsResult
        ?.getObjectOrNull("dynamic_sequence_gate")
        ?.getDoubleOrNull("fit_mean_error_m")
        ?: physicsSummary?.getDoubleOrNull("fit_mean_error_m")
        ?: return null
    return (fitMeanErrorM * 100.0).roundToInt().coerceAtLeast(0)
}

private fun AiAnalysisResult.extractPeakBodyLoadGroupLabel(): String? {
    return extractPeakBodyLoadGroupToken()
        ?.let(::formatBodyLoadGroupToken)
}

private fun AiAnalysisResult.extractPeakBodyLoadGroupToken(): String? {
    return physicsResult
        ?.getObjectOrNull("body_load_summary")
        ?.entries
        ?.maxByOrNull { (_, payload) ->
            payload.asObjectOrNull()?.getDoubleOrNull("max_abs_load_proxy") ?: Double.NEGATIVE_INFINITY
        }
        ?.key
}

private fun AiAnalysisResult.normalizedSegmentFraction(
    startTimeMs: Long,
    endTimeMs: Long
): Float {
    val durationMs = videoMetadata.durationMs()
        ?: cruxResult.topCandidates.mapNotNull { it.bestSegment?.endTimeMs }.maxOrNull()
        ?: cruxResult.allCandidates.mapNotNull { it.bestSegment?.endTimeMs }.maxOrNull()
        ?: endTimeMs

    if (durationMs <= 0L) return 0.5f

    val segmentMidMs = if (endTimeMs > startTimeMs) {
        (startTimeMs + endTimeMs) / 2L
    } else {
        startTimeMs
    }

    return (segmentMidMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun cleanedReasonText(reasonTags: List<String>): String? {
    return reasonTags
        .asSequence()
        .map(::formatToken)
        .firstOrNull { formatted ->
            formatted.isNotBlank() && !formatted.contains(Regex("[A-Za-z]"))
        }
        ?.takeIf { it.isNotBlank() }
}

private fun formatToken(token: String): String {
    val normalized = token.lowercase()

    return when {
        normalized == "left_hand" -> "왼손"
        normalized == "right_hand" -> "오른손"
        normalized == "left_foot" -> "왼발"
        normalized == "right_foot" -> "오른발"
        normalized == "grip" -> "손으로 잡고 버티기"
        normalized == "step" -> "발로 디디기"
        normalized == "reach" -> "다음 홀드로 뻗기"
        normalized == "free" -> "이동 준비"
        normalized == "release" -> "놓기"
        normalized == "pull" -> "당기기"
        normalized == "push" -> "밀기"
        normalized == "stabilize" -> "버티기"
        normalized == "long_dwell" -> "오래 머무름"
        normalized == "longest_dwell" -> "가장 오래 머문 구간"
        normalized == "high_total_dwell" -> "머문 시간이 긴 구간"
        normalized == "load_spike" -> "부담 급증"
        normalized == "instability" -> "흔들림"
        normalized == "negative_margin" -> "균형 이탈"
        normalized == "loss_of_balance" -> "균형 붕괴"
        normalized == "contact_loss" -> "손발 지지 끊김"
        normalized == "high_load" || normalized == "high_total_load" || normalized == "peak_load" ->
            "힘이 많이 들어간 구간"
        normalized.contains("core") && normalized.contains("load") ->
            "몸통 힘 사용이 큰 구간"
        normalized.contains("arm") && normalized.contains("load") ->
            "팔에 힘이 많이 들어간 구간"
        normalized.contains("hand") && normalized.contains("load") ->
            "손으로 버티는 비중이 큰 구간"
        normalized.contains("foot") && normalized.contains("underuse") ->
            "발 사용이 부족한 구간"
        normalized.contains("arm") && normalized.contains("overuse") ->
            "팔 사용이 많은 구간"
        normalized.contains("balance") || normalized.contains("stability") || normalized.contains("margin") ->
            "균형이 흔들린 구간"
        normalized.contains("dwell") || normalized.contains("stall") || normalized.contains("pause") ->
            "동작이 잠시 멈춘 구간"
        else -> token.replace('_', ' ').replace('-', ' ').trim()
    }
}

private fun formatPhaseToken(token: String): String {
    return when (token.lowercase()) {
        "static_support" -> "버티는 구간"
        "loaded_transition" -> "힘이 실리는 구간"
        "dynamic_transition" -> "움직이는 구간"
        "recovery" -> "회복 구간"
        else -> token.replace('_', ' ').trim()
    }
}

private fun formatBodyLoadGroupToken(token: String): String {
    return when (token.lowercase()) {
        "core" -> "코어"
        "left_arm" -> "왼팔"
        "right_arm" -> "오른팔"
        "left_leg" -> "왼다리"
        "right_leg" -> "오른다리"
        else -> token.replace('_', ' ').trim()
    }
}

private fun formatTimingToken(token: String): String {
    return when (token.lowercase()) {
        "correction_s" -> "포즈 보정"
        "hold_tracking_s" -> "홀드 추적"
        "crux_scoring_s" -> "크럭스 점수 계산"
        "physics_pipeline_s" -> "물리 파이프라인"
        "total_s" -> "전체 분석"
        else -> token.replace('_', ' ').trim()
    }
}

private fun formatCoreLoadInsight(coreLoad: Double): String {
    return when {
        coreLoad >= 80.0 -> "몸통에 부담이 큰 구간"
        coreLoad >= 40.0 -> "몸통 힘 사용이 많아진 구간"
        else -> "몸통 힘을 사용한 구간"
    }
}

private fun List<Float>.normalizeSeries(): List<Float> {
    if (isEmpty()) return DefaultFinalAnalysisTimeline
    val minValue = minOrNull() ?: return DefaultFinalAnalysisTimeline
    val maxValue = maxOrNull() ?: return DefaultFinalAnalysisTimeline
    val range = (maxValue - minValue).coerceAtLeast(0.0001f)
    return map { ((it - minValue) / range).coerceIn(0f, 1f) }
}

private fun List<Float>.downsampleTo(targetSize: Int): List<Float> {
    if (isEmpty()) return DefaultFinalAnalysisTimeline
    if (size == targetSize) return this

    return List(targetSize) { index ->
        val mappedIndex = if (targetSize <= 1) {
            0
        } else {
            ((index.toFloat() / (targetSize - 1)) * (size - 1))
                .roundToInt()
                .coerceIn(0, lastIndex)
        }
        this[mappedIndex]
    }
}

private fun estimateRecoveryDurationMs(summary: FinalAnalysisAttemptSummary): Long {
    val latestPointMs = summary.analysisPoints.maxOfOrNull { it.timeMs }
    return summary.videoDurationMs
        ?.takeIf { it > 0L }
        ?: listOfNotNull(
            latestPointMs?.plus(5_000L),
            summary.primaryCruxDurationMs?.toLong()?.times(3L),
            30_000L
        ).maxOrNull()
        ?: 30_000L
}

private fun List<Float>.startIndex(startFraction: Float?): Int {
    if (isEmpty()) return 0
    val fraction = startFraction ?: return 0
    return (fraction.coerceIn(0f, 1f) * lastIndex.toFloat())
        .roundToInt()
        .coerceIn(0, lastIndex)
}

private fun findRecoveryIndex(timeline: List<Float>, lowestIndex: Int): Int? {
    if (lowestIndex >= timeline.lastIndex) return null

    val lowestValue = timeline[lowestIndex]
    val recoveryTarget = maxOf(0.58f, lowestValue + 0.18f)

    for (index in lowestIndex + 1..timeline.lastIndex) {
        val current = timeline[index]
        val nextWindow = timeline.subList(index, minOf(index + 2, timeline.lastIndex) + 1)
        val windowAverage = nextWindow.average().toFloat()
        if (current >= recoveryTarget && windowAverage >= recoveryTarget - 0.04f) {
            return index
        }
    }
    return null
}

private fun calculateComJitterScore(points: List<Pair<Float, Float>>): Int {
    val smoothedPoints = points.movingAverage(windowRadius = 2)
    val residualSquares = points.zip(smoothedPoints).map { (raw, smoothed) ->
        val dx = raw.first - smoothed.first
        val dz = raw.second - smoothed.second
        (dx * dx) + (dz * dz)
    }
    val jitterRms = sqrt(residualSquares.average().toFloat())
    return (100f * (1f - (jitterRms / 0.075f).coerceIn(0f, 1f)))
        .roundToInt()
        .coerceIn(0, 100)
}

private fun calculateMarginJitterScore(margins: List<Float>): Int {
    val deltas = margins.zipWithNext { previous, current -> abs(current - previous) }
    if (deltas.isEmpty()) return 100

    val meanDelta = deltas.average().toFloat()
    return (100f * (1f - (meanDelta / 0.065f).coerceIn(0f, 1f)))
        .roundToInt()
        .coerceIn(0, 100)
}

private fun stabilityRetentionSuccessBonus(
    isSuccess: Boolean,
    insideSupportScore: Int,
    marginReserveScore: Int
): Float {
    if (!isSuccess) return 0f

    val successBonus = when {
        insideSupportScore >= 70 -> 12f
        insideSupportScore >= 55 -> 10f
        insideSupportScore >= 45 -> 8f
        else -> 4f
    }

    val reserveBonus = when {
        marginReserveScore >= 35 -> 4f
        marginReserveScore >= 25 -> 3f
        else -> 0f
    }

    return successBonus + reserveBonus
}

private fun stabilityRetentionSeverePenalty(
    lowMarginRatio: Float,
    negativeMarginRatio: Float
): Float {
    return when {
        negativeMarginRatio >= 0.35f -> 6f
        negativeMarginRatio >= 0.20f -> 4f
        lowMarginRatio >= 0.45f -> 2f
        else -> 0f
    }
}

private fun applyStabilityRetentionFloor(
    isSuccess: Boolean,
    insideSupportScore: Int,
    score: Int
): Int {
    if (!isSuccess) return score

    return when {
        insideSupportScore >= 55 -> max(score, 65)
        insideSupportScore >= 45 -> max(score, 60)
        else -> score
    }
}

private fun List<Pair<Float, Float>>.movingAverage(windowRadius: Int): List<Pair<Float, Float>> {
    return indices.map { index ->
        val start = max(0, index - windowRadius)
        val end = minOf(lastIndex, index + windowRadius)
        val window = subList(start, end + 1)
        val meanX = window.map { it.first }.average().toFloat()
        val meanZ = window.map { it.second }.average().toFloat()
        meanX to meanZ
    }
}

private fun AiAnalysisVideoMetadata?.durationMs(): Long? {
    val metadata = this ?: return null
    val fpsValue = metadata.fps?.takeIf { it > 0f } ?: return null
    if (metadata.totalFrames <= 0) return null
    return ((metadata.totalFrames / fpsValue) * 1000f).roundToInt().toLong()
}

private fun JsonObject.getObjectOrNull(key: String): JsonObject? = this[key].asObjectOrNull()

private fun JsonObject.getArrayOrNull(key: String): JsonArray? = this[key].asArrayOrNull()

private fun JsonObject.getDoubleOrNull(key: String): Double? = this[key].asDoubleOrNull()

private fun JsonObject.getIntOrNull(key: String): Int? = this[key].asIntOrNull()

private fun JsonObject.getStringOrNull(key: String): String? = this[key].asStringOrNull()

private fun JsonObject.getBooleanOrNull(key: String): Boolean? = this[key].asBooleanOrNull()

private fun JsonObject.maxCountKeyOrNull(): String? {
    return entries
        .mapNotNull { (key, value) ->
            value.asIntOrNull()?.let { count -> key to count }
        }
        .maxByOrNull { it.second }
        ?.takeIf { it.second > 0 }
        ?.first
}

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

private fun JsonElement?.asPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private fun JsonElement?.asDoubleOrNull(): Double? = asPrimitiveOrNull()?.doubleOrNull

private fun JsonElement?.asIntOrNull(): Int? = asPrimitiveOrNull()?.intOrNull

private fun JsonElement?.asBooleanOrNull(): Boolean? {
    return asPrimitiveOrNull()
        ?.contentOrNull
        ?.lowercase()
        ?.let { value ->
            when (value) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }
}

private fun JsonObject?.analysisConfidence(): String? = this?.getStringOrNull("analysis_confidence")

private fun JsonObject?.contactForceStatus(): String? {
    return this?.getStringOrNull("contact_force_status")
        ?: this?.getObjectOrNull("contact_force_distribution")?.getStringOrNull("status")
}

private fun JsonObject.extractActiveHoldIds(): List<Int> {
    val activeHoldIds = this["active_hold_ids"]
    return when (activeHoldIds) {
        is JsonObject -> activeHoldIds.values.mapNotNull { it.asIntOrNull() }
        is JsonArray -> activeHoldIds.mapNotNull { it.asIntOrNull() }
        else -> emptyList()
    }
}

private fun JsonElement?.asStringOrNull(): String? = asPrimitiveOrNull()?.contentOrNull

private fun Double.formatOneDecimal(): String = String.format("%.1f", this)

private fun JsonObject.toStabilityRetentionFrame(): StabilityRetentionFrame {
    return StabilityRetentionFrame(
        insideSupport = getBooleanOrNull("inside_support"),
        stabilityMarginM = getDoubleOrNull("stability_margin_m")?.toFloat(),
        confidence = getDoubleOrNull("confidence")?.toFloat(),
        comProjXz = getArrayOrNull("com_proj_xz")
            ?.mapNotNull { element -> element.asDoubleOrNull()?.toFloat() }
            ?.takeIf { it.size >= 2 }
            ?.let { values -> values[0] to values[1] }
    )
}
