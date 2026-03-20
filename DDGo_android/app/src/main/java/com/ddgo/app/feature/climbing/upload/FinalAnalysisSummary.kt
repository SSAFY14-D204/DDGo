package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiAnalysisVideoMetadata
import com.ddgo.app.domain.model.AnalysisPoint
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

internal data class FinalAnalysisAttemptSummary(
    val attemptNo: Int,
    val hasAiResult: Boolean,
    val isSuccess: Boolean,
    val analysisPoints: List<AnalysisPoint>,
    val reachedHolds: Int?,
    val reachedHoldsText: String,
    val processedFrames: Int?,
    val processedFramesText: String,
    val highConfidenceRatio: Int?,
    val highConfidenceRatioText: String,
    val insideSupportRatio: Int?,
    val insideSupportRatioText: String,
    val stableContactFrameCount: Int?,
    val stableContactFrameCountText: String,
    val stableContactRatio: Int?,
    val stableContactRatioText: String,
    val stabilityTimeline: List<Float>,
    val stabilityFocusFraction: Float?,
    val stabilityHighlights: List<String>,
    val stabilityNarrative: String,
    val failureHighlights: List<String>,
    val failureNarrative: String,
    val feedbackTypes: List<String>,
    val loadFocusLabel: String?,
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
    aiResults: List<AiAnalysisResult?> = emptyList()
): List<FinalAnalysisAttemptSummary> {
    val resolvedAttemptCount = max(attemptCount, aiResults.size).coerceAtLeast(1)
    return List(resolvedAttemptCount) { index ->
        aiResults.getOrNull(index)?.toFinalAnalysisAttemptSummary(
            attemptNo = index + 1,
            totalHolds = totalHolds
        )
            ?: emptyFinalAnalysisAttemptSummary(attemptNo = index + 1)
    }
}

private fun emptyFinalAnalysisAttemptSummary(
    attemptNo: Int
): FinalAnalysisAttemptSummary {
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = false,
        isSuccess = false,
        analysisPoints = emptyList(),
        reachedHolds = null,
        reachedHoldsText = FinalAnalysisUnknownMetricText,
        processedFrames = null,
        processedFramesText = FinalAnalysisUnknownMetricText,
        highConfidenceRatio = null,
        highConfidenceRatioText = FinalAnalysisUnknownMetricText,
        insideSupportRatio = null,
        insideSupportRatioText = FinalAnalysisUnknownMetricText,
        stableContactFrameCount = null,
        stableContactFrameCountText = FinalAnalysisUnknownMetricText,
        stableContactRatio = null,
        stableContactRatioText = FinalAnalysisUnknownMetricText,
        stabilityTimeline = DefaultFinalAnalysisTimeline,
        stabilityFocusFraction = null,
        stabilityHighlights = listOf(NoAiNarrative),
        stabilityNarrative = NoAiNarrative,
        failureHighlights = listOf(NoAiNarrative),
        failureNarrative = NoAiNarrative,
        feedbackTypes = emptyList(),
        loadFocusLabel = null,
        feedbackLine = "AI 분석 결과가 아직 충분하지 않아 종합 피드백을 만들지 못했습니다.",
        coachingLine = "영상과 홀드 정보가 충분해지면 더 구체적인 코칭을 제공할 수 있습니다.",
        effectiveModeLabel = "",
        fallbackLabel = null
    )
}

private fun AiAnalysisResult.toFinalAnalysisAttemptSummary(
    attemptNo: Int,
    totalHolds: Int
): FinalAnalysisAttemptSummary {
    val analysisPoints = toFinalAnalysisPoints()
    val reachedHolds = extractReachedHoldNo()
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
    val failureHighlights = buildFailureHighlights()
    val isSuccess = totalHolds > 0 && (reachedHolds ?: 0) >= totalHolds
    val loadFocusLabel = extractPeakBodyLoadGroupLabel()
    val feedbackTypes = buildFeedbackTypes(
        insideSupportRatio = insideSupportRatio,
        stableContactRatio = stableContactRatio
    )
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = true,
        isSuccess = isSuccess,
        analysisPoints = analysisPoints,
        reachedHolds = reachedHolds,
        reachedHoldsText = reachedHolds?.toString() ?: FinalAnalysisUnknownMetricText,
        processedFrames = processedFrames,
        processedFramesText = processedFrames?.toString() ?: FinalAnalysisUnknownMetricText,
        highConfidenceRatio = highConfidenceRatio,
        highConfidenceRatioText = highConfidenceRatio?.let { "$it%" } ?: FinalAnalysisUnknownMetricText,
        insideSupportRatio = insideSupportRatio,
        insideSupportRatioText = insideSupportRatio?.let { "$it%" } ?: FinalAnalysisUnknownMetricText,
        stableContactFrameCount = stableContactFrameCount,
        stableContactFrameCountText = stableContactFrameCount?.toString() ?: FinalAnalysisUnknownMetricText,
        stableContactRatio = stableContactRatio,
        stableContactRatioText = stableContactRatio?.let { "$it%" } ?: FinalAnalysisUnknownMetricText,
        stabilityTimeline = extractStabilityTimeline(),
        stabilityFocusFraction = extractStabilityFocusFraction(),
        stabilityHighlights = stabilityHighlights,
        stabilityNarrative = stabilityHighlights.joinToString(" "),
        failureHighlights = failureHighlights,
        failureNarrative = failureHighlights.joinToString(" "),
        feedbackTypes = feedbackTypes,
        loadFocusLabel = loadFocusLabel,
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

private fun AiAnalysisResult.buildStabilityHighlights(
    highConfidenceRatio: Int?,
    insideSupportRatio: Int?,
    stableContactRatio: Int?
): List<String> {
    return buildList {
        fallbackNarrativePrefix()?.let(::add)
        insideSupportRatio?.let { add("균형 유지율 ${it}%") }
        stableContactRatio?.let { add("손발 지지 안정도 ${it}%") }
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
        add("크럭스 홀드 ${topCandidate.holdId}번")
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
            ?.let { add("손발 지지 안정도 ${(it * 100.0).roundToInt().coerceIn(0, 100)}%") }
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
            "전반적으로 균형과 손발 지지가 안정적이어서 흐름을 잘 이어갔습니다."

        isSuccess ->
            "완등에는 성공했지만 일부 난구간에서 균형과 손발 지지가 잠시 흔들렸습니다."

        "발 사용 부족" in feedbackTypes && "팔 사용 과다" in feedbackTypes && holdLabel != null ->
            "${holdLabel} 부근에서 발 사용이 줄고 상체 의존이 커지며 흐름이 끊겼습니다."

        "중심 흔들림" in feedbackTypes && holdLabel != null ->
            "${holdLabel} 부근에서 중심이 흔들리며 다음 동작 연결이 끊겼습니다."

        "과한 버티기" in feedbackTypes && holdLabel != null ->
            "${holdLabel} 부근에서 오래 버티며 리듬이 끊겼습니다."

        "팔 사용 과다" in feedbackTypes && holdLabel != null && peakLoadLabel != null ->
            "${holdLabel} 부근에서 ${peakLoadLabel}에 부담이 몰리며 상체 의존이 커졌습니다."

        reasonLabel != null && holdLabel != null ->
            "${holdLabel} 부근에서 ${reasonLabel}이 두드러져 난이도가 크게 올라갔습니다."

        reachedHolds != null && totalHolds > 0 ->
            "${reachedHolds}번 홀드까지는 비교적 안정적이었지만 이후 구간 연결이 어려웠습니다."

        else ->
            "핵심 구간에서 균형 유지와 다음 동작 연결이 동시에 어려웠습니다."
    }
}

private fun buildCoachingLine(
    feedbackTypes: List<String>,
    loadFocusLabel: String?
): String {
    return when {
        "발 사용 부족" in feedbackTypes ->
            "다음 홀드로 가기 전 발을 먼저 올려 몸을 세운 뒤 손을 보내보세요."

        "중심 흔들림" in feedbackTypes ->
            "손보다 중심을 먼저 옮기고 엉덩이를 벽에 붙인 채 다음 동작을 이어가 보세요."

        "과한 버티기" in feedbackTypes ->
            "한 자세에서 오래 멈추기보다 시선을 먼저 보내고 리듬 있게 다음 홀드로 연결해 보세요."

        "팔 사용 과다" in feedbackTypes ->
            "팔로 버티기보다 발로 밀어 올리고 팔은 균형만 잡는 느낌으로 써보세요."

        loadFocusLabel == "몸통" ->
            "복부 힘을 먼저 잡고 발을 디딘 뒤 손을 움직이면 흔들림을 줄일 수 있습니다."

        loadFocusLabel == "왼팔" || loadFocusLabel == "오른팔" ->
            "한쪽 팔로만 버티지 말고 발을 먼저 세워 상체 부담을 나눠보세요."

        loadFocusLabel == "왼다리" || loadFocusLabel == "오른다리" ->
            "한쪽 발에만 체중을 싣기보다 반대 발도 빨리 세워 하중을 분산해보세요."

        else ->
            "가장 어려운 구간에서는 중심을 먼저 옮기고 손발을 함께 연결하는 연습이 도움이 됩니다."
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
        "core" -> "몸통"
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
