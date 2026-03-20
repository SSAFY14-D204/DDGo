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
    val stabilityNarrative: String,
    val failureNarrative: String,
    val effectiveModeLabel: String,
    val fallbackLabel: String?
)

internal const val FinalAnalysisUnknownMetricText = "정보 없음"
private const val StabilityTimelineSampleCount = 28
private const val NoAiNarrative = "아직 이 시도에 대한 AI 응답이 없어요."
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
        stabilityNarrative = NoAiNarrative,
        failureNarrative = NoAiNarrative,
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
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = true,
        isSuccess = totalHolds > 0 && (reachedHolds ?: 0) >= totalHolds,
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
        stabilityNarrative = buildStabilityNarrative(
            highConfidenceRatio = highConfidenceRatio,
            insideSupportRatio = insideSupportRatio,
            stableContactRatio = stableContactRatio
        ),
        failureNarrative = buildFailureNarrative(),
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
                ?.let { add("코어 부하 ${it.formatOneDecimal()}") }
            candidate.bestSegment
                ?.meanNegativeMarginCm
                ?.takeIf { it != 0.0 }
                ?.let { add("음수 마진 ${it.roundToInt()}cm") }
            candidate.bestSegment
                ?.okFraction
                ?.let { ratio ->
                    add("안정 접촉 ${(ratio * 100.0).roundToInt().coerceIn(0, 100)}%")
                }
        }

        AnalysisPoint(
            index = index + 1,
            timeMs = candidate.bestSegment?.startTimeMs ?: ((index + 1) * 15_000L),
            description = buildString {
                append("${candidate.holdId}번 홀드")
                if (details.isNotEmpty()) {
                    append(": ")
                    append(details.joinToString(" / "))
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

private fun AiAnalysisResult.buildStabilityNarrative(
    highConfidenceRatio: Int?,
    insideSupportRatio: Int?,
    stableContactRatio: Int?
): String {
    val statements = buildList {
        fallbackNarrativePrefix()?.let(::add)
        highConfidenceRatio?.let { add("고신뢰 프레임 비율은 ${it}%였어요.") }
        insideSupportRatio?.let { add("지지면 내부 유지 비율은 ${it}%였어요.") }
        stableContactRatio?.let { add("안정 접촉 비율은 ${it}%였어요.") }
        extractPointSupportFrameCount()?.takeIf { it > 0 }?.let { add("점 지지 프레임은 ${it}개였어요.") }
        extractFallbackAllLimbsFrameCount()?.takeIf { it > 0 }
            ?.let { add("${it}개 프레임은 전체 사지 보정 지지를 사용했어요.") }
        extractDominantPhaseLabel()?.let { add("가장 많이 나타난 구간은 ${it}였어요.") }
        extractRecoveryRatioPercent()?.let { add("회복 구간 비율은 ${it}%였어요.") }
        extractFitMeanErrorCm()?.let { add("포즈 피팅 평균 오차는 ${it}cm였어요.") }
    }

    return statements.ifEmpty {
        listOf("안정성을 요약하기에 충분한 물리 분석 데이터가 아직 없어요.")
    }.joinToString(" ")
}

private fun AiAnalysisResult.buildFailureNarrative(): String {
    val topCandidate = cruxResult.topCandidates.firstOrNull()
        ?: cruxResult.allCandidates.firstOrNull()

    if (topCandidate == null) {
        return listOfNotNull(
            fallbackNarrativePrefix(),
            "크럭스 후보가 없어 실패 원인을 충분히 요약하지 못했어요."
        ).joinToString(" ")
    }

    val statements = buildList {
        fallbackNarrativePrefix()?.let(::add)
        add("가장 강한 크럭스 신호는 ${topCandidate.holdId}번 홀드에서 나타났어요.")
        cleanedReasonText(topCandidate.reasonTags.ifEmpty { topCandidate.bestSegment?.reasonTags.orEmpty() })
            ?.let { add("주요 원인은 ${it}였어요.") }
        topCandidate.bestSegment
            ?.dominantLimbs
            ?.map(::formatToken)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.let { add("주로 사용한 부위는 ${it}였어요.") }
        topCandidate.bestSegment
            ?.dominantModes
            ?.map(::formatToken)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.let { add("주요 동작은 ${it}였어요.") }
        topCandidate.bestSegment?.meanNegativeMarginCm
            ?.roundToInt()
            ?.takeIf { it != 0 }
            ?.let { add("평균 음수 마진은 ${it}cm였어요.") }
        topCandidate.bestSegment?.meanTotalBodyLoad
            ?.takeIf { it > 0.0 }
            ?.let { add("평균 전신 부하는 ${it.formatOneDecimal()}였어요.") }
        topCandidate.bestSegment?.meanCoreLoad
            ?.takeIf { it > 0.0 }
            ?.let { add("평균 코어 부하는 ${it.formatOneDecimal()}였어요.") }
        topCandidate.bestSegment?.okFraction
            ?.let { add("안정 접촉 비율은 ${(it * 100.0).roundToInt().coerceIn(0, 100)}%였어요.") }
        extractPeakBodyLoadGroupLabel()?.let { add("가장 큰 부하가 집중된 부위는 ${it}였어요.") }
        extractDominantPhaseLabel()?.let { add("주된 프레임 구간은 ${it}였어요.") }
        extractPointSupportFrameCount()?.takeIf { it > 0 }
            ?.let { add("점 지지 프레임이 ${it}개 있어 흔들림이 있었어요.") }
    }

    return statements.joinToString(" ")
}

private fun AiAnalysisResult.fallbackNarrativePrefix(): String? {
    if (requestedMode == mode) return null

    return when (fallbackReason) {
        AiAnalysisFallbackReason.MISSING_WEIGHT ->
            "체중 정보가 없어 ${mode.toDisplayLabel()} 모드로 대체했어요."
        AiAnalysisFallbackReason.PHYSICS_REQUEST_FAILED ->
            "물리 분석 요청이 실패해 ${mode.toDisplayLabel()} 모드로 대체했어요."
        null ->
            "${requestedMode.toDisplayLabel()} 요청이 ${mode.toDisplayLabel()} 모드로 대체됐어요."
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
    return physicsResult
        ?.getObjectOrNull("body_load_summary")
        ?.entries
        ?.maxByOrNull { (_, payload) ->
            payload.asObjectOrNull()?.getDoubleOrNull("max_abs_load_proxy") ?: Double.NEGATIVE_INFINITY
        }
        ?.key
        ?.let(::formatBodyLoadGroupToken)
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
    return reasonTags.firstOrNull()
        ?.let(::formatToken)
        ?.takeIf { it.isNotBlank() }
}

private fun formatToken(token: String): String {
    return when (token.lowercase()) {
        "left_hand" -> "왼손"
        "right_hand" -> "오른손"
        "left_foot" -> "왼발"
        "right_foot" -> "오른발"
        "pull" -> "당기기"
        "push" -> "밀기"
        "stabilize" -> "버티기"
        "long_dwell" -> "오래 머무름"
        "load_spike" -> "하중 급증"
        "instability" -> "불안정"
        "negative_margin" -> "음수 마진"
        "loss_of_balance" -> "균형 붕괴"
        "contact_loss" -> "접촉 손실"
        else -> token.replace('_', ' ').replace('-', ' ').trim()
    }
}

private fun formatPhaseToken(token: String): String {
    return when (token.lowercase()) {
        "static_support" -> "정적 지지"
        "loaded_transition" -> "하중 전이"
        "dynamic_transition" -> "동적 전이"
        "recovery" -> "회복"
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
