package com.ddgo.app.feature.climbing.upload

import kotlin.math.roundToInt

internal data class ChallengeAttemptComparisonItem(
    val attemptNo: Int,
    val isSuccess: Boolean,
    val reachedHoldsText: String,
    val reachedHoldsSuffix: String?,
    val insideSupportRatioText: String,
    val stableContactRatioText: String,
    val tagLabels: List<String>,
    val loadFocusLabel: String?,
    val summaryLine: String
)

internal data class ChallengeTrendPoint(
    val attemptNo: Int,
    val reachedPercent: Int?,
    val insideSupportPercent: Int?,
    val stableContactPercent: Int?
)

internal data class ChallengeCruxDistributionItem(
    val holdNo: Int,
    val count: Int
)

internal data class ChallengeFinalAnalysisSummary(
    val attemptCount: Int,
    val overallSuccess: Boolean,
    val successAttemptCount: Int,
    val bestAttemptNo: Int?,
    val bestReachedHoldsText: String,
    val bestReachedHoldsSuffix: String?,
    val averageReachedHoldsText: String,
    val averageReachedHoldsSuffix: String?,
    val averageInsideSupportRatioText: String,
    val averageStableContactRatioText: String,
    val summaryLine: String,
    val completionLine: String,
    val challengeNatureLine: String,
    val trendHighlights: List<String>,
    val patternHighlights: List<String>,
    val repeatedPatternLabels: List<String>,
    val repeatedCruxHoldLabel: String?,
    val repeatedLoadFocusLabel: String?,
    val combinedTimeline: List<Float>,
    val focusFraction: Float?,
    val focusReasonText: String?,
    val trendPoints: List<ChallengeTrendPoint>,
    val cruxDistribution: List<ChallengeCruxDistributionItem>,
    val attempts: List<ChallengeAttemptComparisonItem>
)

internal fun buildChallengeFinalAnalysisSummary(
    attemptSummaries: List<FinalAnalysisAttemptSummary>,
    totalHolds: Int
): ChallengeFinalAnalysisSummary {
    val validAttemptSummaries = attemptSummaries.ifEmpty {
        listOf(fallbackAttemptSummary(attemptNo = 1))
    }
    val attemptCount = validAttemptSummaries.size
    val successAttemptCount = validAttemptSummaries.count { it.isSuccess }
    val overallSuccess = successAttemptCount > 0
    val bestAttempt = validAttemptSummaries.maxWithOrNull(
        compareBy<FinalAnalysisAttemptSummary>(
            { if (it.isSuccess) 1 else 0 },
            { it.reachedHolds ?: -1 },
            { it.insideSupportRatio ?: -1 },
            { it.stableContactRatio ?: -1 }
        )
    )
    val firstAttempt = validAttemptSummaries.firstOrNull()
    val lastAttempt = validAttemptSummaries.lastOrNull()
    val averageReachedHolds = validAttemptSummaries
        .mapNotNull { it.reachedHolds }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
    val averageInsideSupportRatio = validAttemptSummaries
        .mapNotNull { it.insideSupportRatio }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
    val averageStableContactRatio = validAttemptSummaries
        .mapNotNull { it.stableContactRatio }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.roundToInt()
    val repeatedCruxHoldNo = mostFrequentOrNull(
        validAttemptSummaries.mapNotNull { it.primaryCruxHoldNo }
    )
    val repeatedPatternLabels = mostFrequentList(
        values = validAttemptSummaries.flatMap { it.feedbackTypes },
        limit = 2
    ).map(::displayFeedbackTypeLabel)
    val repeatedLoadFocusLabel = mostFrequentOrNull(
        validAttemptSummaries.mapNotNull { it.loadFocusLabel }
    )
        val repeatedCruxHoldLabel = repeatedCruxHoldNo?.let { "${it}번 홀드" }
    val attempts = validAttemptSummaries.map { summary ->
        ChallengeAttemptComparisonItem(
            attemptNo = summary.attemptNo,
            isSuccess = summary.isSuccess,
            reachedHoldsText = summary.reachedHoldsText,
            reachedHoldsSuffix = if (summary.reachedHolds != null && totalHolds > 0) {
                "/$totalHolds"
            } else {
                null
            },
            insideSupportRatioText = summary.insideSupportRatioText,
            stableContactRatioText = summary.stableContactRatioText,
            tagLabels = summary.feedbackTypes.map(::displayFeedbackTypeLabel).take(2),
            loadFocusLabel = summary.loadFocusLabel,
            summaryLine = summary.feedbackLine
        )
    }
    val trendPoints = validAttemptSummaries.map { summary ->
        ChallengeTrendPoint(
            attemptNo = summary.attemptNo,
            reachedPercent = if (summary.reachedHolds != null && totalHolds > 0) {
                ((summary.reachedHolds.toFloat() / totalHolds.toFloat()) * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
            } else {
                null
            },
            insideSupportPercent = summary.insideSupportRatio?.coerceIn(0, 100),
            stableContactPercent = summary.stableContactRatio?.coerceIn(0, 100)
        )
    }
    val cruxDistribution = validAttemptSummaries
        .mapNotNull { it.primaryCruxHoldNo }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedBy { it.key }
        .map { (holdNo, count) ->
            ChallengeCruxDistributionItem(
                holdNo = holdNo,
                count = count
            )
        }

    return ChallengeFinalAnalysisSummary(
        attemptCount = attemptCount,
        overallSuccess = overallSuccess,
        successAttemptCount = successAttemptCount,
        bestAttemptNo = bestAttempt?.attemptNo,
        bestReachedHoldsText = bestAttempt?.reachedHoldsText ?: FinalAnalysisUnknownMetricText,
        bestReachedHoldsSuffix = if (bestAttempt?.reachedHolds != null && totalHolds > 0) {
            "/$totalHolds"
        } else {
            null
        },
        averageReachedHoldsText = averageReachedHolds?.toString() ?: FinalAnalysisUnknownMetricText,
        averageReachedHoldsSuffix = if (averageReachedHolds != null && totalHolds > 0) {
            "/$totalHolds"
        } else {
            null
        },
        averageInsideSupportRatioText = averageInsideSupportRatio?.let { "$it%" }
            ?: FinalAnalysisUnknownMetricText,
        averageStableContactRatioText = averageStableContactRatio?.let { "$it%" }
            ?: FinalAnalysisUnknownMetricText,
        summaryLine = buildChallengeSummaryLine(
            overallSuccess = overallSuccess,
            attemptCount = attemptCount,
            successAttemptCount = successAttemptCount,
            bestAttempt = bestAttempt,
            averageReachedHoldsText = averageReachedHolds?.toString() ?: FinalAnalysisUnknownMetricText,
            totalHolds = totalHolds
        ),
        completionLine = buildChallengeCompletionLine(
            overallSuccess = overallSuccess,
            bestAttempt = bestAttempt,
            successAttemptCount = successAttemptCount,
            totalHolds = totalHolds
        ),
        challengeNatureLine = buildChallengeNatureLine(
            repeatedPatternLabels = repeatedPatternLabels,
            repeatedLoadFocusLabel = repeatedLoadFocusLabel
        ),
        trendHighlights = buildTrendHighlights(
            firstAttempt = firstAttempt,
            lastAttempt = lastAttempt,
            bestAttempt = bestAttempt,
            totalHolds = totalHolds,
            successAttemptCount = successAttemptCount
        ),
        patternHighlights = buildPatternHighlights(
            repeatedCruxHoldLabel = repeatedCruxHoldLabel,
            repeatedPatternLabels = repeatedPatternLabels,
            repeatedLoadFocusLabel = repeatedLoadFocusLabel,
            bestAttempt = bestAttempt
        ),
        repeatedPatternLabels = repeatedPatternLabels,
        repeatedCruxHoldLabel = repeatedCruxHoldLabel,
        repeatedLoadFocusLabel = repeatedLoadFocusLabel,
        combinedTimeline = buildCombinedTimeline(validAttemptSummaries),
        focusFraction = buildChallengeFocusFraction(
            attemptSummaries = validAttemptSummaries,
            repeatedCruxHoldNo = repeatedCruxHoldNo
        ),
        focusReasonText = buildChallengeFocusReasonText(
            repeatedPatternLabels = repeatedPatternLabels,
            repeatedLoadFocusLabel = repeatedLoadFocusLabel
        ),
        trendPoints = trendPoints,
        cruxDistribution = cruxDistribution,
        attempts = attempts
    )
}

internal fun displayFeedbackTypeLabel(type: String): String {
    return when (type) {
        "발 사용 부족" -> "발 활용 부족"
        "중심 흔들림" -> "중심 흔들림"
        "팔 사용 과다" -> "팔 의존 큼"
        "과한 버티기" -> "오래 버티기"
        else -> type
    }
}

private fun fallbackAttemptSummary(
    attemptNo: Int
): FinalAnalysisAttemptSummary {
    return FinalAnalysisAttemptSummary(
        attemptNo = attemptNo,
        hasAiResult = false,
        isSuccess = false,
        analysisPoints = emptyList(),
        videoDurationMs = null,
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
        stabilityHighlights = emptyList(),
        stabilityNarrative = "",
        failureHighlights = emptyList(),
        failureNarrative = "",
        primaryCruxHoldNo = null,
        primaryCruxDurationMs = null,
        primaryReasonLabel = null,
        dangerEventCount = null,
        feedbackTypes = emptyList(),
        loadFocusLabel = null,
        feedbackLine = "분석 데이터가 충분하지 않아 종합 요약을 만들지 못했습니다.",
        coachingLine = "",
        effectiveModeLabel = "",
        fallbackLabel = null
    )
}

private fun buildChallengeSummaryLine(
    overallSuccess: Boolean,
    attemptCount: Int,
    successAttemptCount: Int,
    bestAttempt: FinalAnalysisAttemptSummary?,
    averageReachedHoldsText: String,
    totalHolds: Int
): String {
    val averageReachedText = formatReachedMetric(
        reachedText = averageReachedHoldsText,
        totalHolds = totalHolds,
        hasReachedValue = averageReachedHoldsText != FinalAnalysisUnknownMetricText
    )

    return if (overallSuccess) {
        "총 ${attemptCount}번 시도 중 ${successAttemptCount}번 완등에 성공했고, 평균 $averageReachedText 홀드까지 도달했습니다."
    } else {
        val bestReachedText = bestAttempt?.let {
            formatReachedMetric(
                reachedText = it.reachedHoldsText,
                totalHolds = totalHolds,
                hasReachedValue = it.reachedHolds != null
            )
        } ?: FinalAnalysisUnknownMetricText
                "총 ${attemptCount}번 시도했고, 평균 $averageReachedText 홀드까지 도달했습니다. 최고 기록은 ${bestReachedText}였습니다."
    }
}

private fun buildChallengeCompletionLine(
    overallSuccess: Boolean,
    bestAttempt: FinalAnalysisAttemptSummary?,
    successAttemptCount: Int,
    totalHolds: Int
): String {
    return when {
        overallSuccess && bestAttempt != null -> {
            "완등은 ${bestAttempt.attemptNo}번째 시도에서 나왔고, 전체 완등 성공 시도는 ${successAttemptCount}번입니다."
        }

        bestAttempt?.reachedHolds != null && totalHolds > 0 -> {
            "가장 높이 올라갔던 결과는 ${bestAttempt.attemptNo}번째 시도의 ${bestAttempt.reachedHolds}/$totalHolds 홀드 도달이었습니다."
        }

        else -> {
            "완등까지 이어지진 않았지만, 시도별 경향을 맥락 있게 정리했습니다."
        }
    }
}

private fun buildChallengeNatureLine(
    repeatedPatternLabels: List<String>,
    repeatedLoadFocusLabel: String?
): String {
    return when {
        repeatedPatternLabels.contains("발 활용 부족") &&
            repeatedPatternLabels.contains("중심 흔들림") -> {
            "이 문제는 발을 세우면서 중심을 놓치지 않는 연결이 핵심이었습니다."
        }

        repeatedPatternLabels.contains("중심 흔들림") -> {
            "이 문제는 다음 홀드로 가기 전에 중심을 안정적으로 잡는 흐름이 중요했습니다."
        }

        repeatedPatternLabels.contains("발 활용 부족") -> {
            "이 문제는 손으로 버티기보다 하체로 먼저 밀어 올리는 흐름이 중요했습니다."
        }

        repeatedPatternLabels.contains("팔 의존 큼") -> {
            "이 문제는 상체로 오래 버티기보다 하체와 함께 힘을 나누는 흐름이 중요했습니다."
        }

        repeatedPatternLabels.contains("오래 버티기") -> {
            "이 문제는 한 자세에서 먈추는 시간을 줄이고 리듬 있게 연결하는 흐름이 중요했습니다."
        }

        repeatedLoadFocusLabel == "머통" -> {
            "이 문제는 머통 고정과 체중 이동이 난이도를 크게 좌우했습니다."
        }

        else -> {
            "이 문제는 한 구간의 힘으로 버티기보다, 손발을 연결하며 흐름을 이어가는 것이 중요했습니다."
        }
    }
}

private fun buildTrendHighlights(
    firstAttempt: FinalAnalysisAttemptSummary?,
    lastAttempt: FinalAnalysisAttemptSummary?,
    bestAttempt: FinalAnalysisAttemptSummary?,
    totalHolds: Int,
    successAttemptCount: Int
): List<String> {
    return buildList {
        if (firstAttempt?.reachedHolds != null && lastAttempt?.reachedHolds != null) {
            add(
                "첫 시도 대비 마지막 시도 도달 홀드: " +
                    formatReachedMetric(
                        reachedText = firstAttempt.reachedHoldsText,
                        totalHolds = totalHolds,
                        hasReachedValue = true
                    ) +
                    " → " +
                    formatReachedMetric(
                        reachedText = lastAttempt.reachedHoldsText,
                        totalHolds = totalHolds,
                        hasReachedValue = true
                    )
            )
        }
        if (firstAttempt?.insideSupportRatio != null && lastAttempt?.insideSupportRatio != null) {
            add(
                "균형 유지율: ${firstAttempt.insideSupportRatio}% → ${lastAttempt.insideSupportRatio}%"
            )
        }
        if (firstAttempt?.stableContactRatio != null && lastAttempt?.stableContactRatio != null) {
            add(
                "손발 지지 안정도: ${firstAttempt.stableContactRatio}% → ${lastAttempt.stableContactRatio}%"
            )
        }
        when {
            successAttemptCount > 0 -> {
                add("완등 성공 시도는 총 ${successAttemptCount}번이었습니다.")
            }

            bestAttempt != null -> {
                add(
                    "가장 좋았던 흐름은 ${bestAttempt.attemptNo}번째 시도에서 나왔습니다."
                )
            }
        }
    }.ifEmpty {
        listOf(
            "시도 수가 적어 변화 추세를 단정하기보다, 전체 경향을 중심으로 정리했습니다."
        )
    }
}

private fun buildPatternHighlights(
    repeatedCruxHoldLabel: String?,
    repeatedPatternLabels: List<String>,
    repeatedLoadFocusLabel: String?,
    bestAttempt: FinalAnalysisAttemptSummary?
): List<String> {
    return buildList {
        repeatedCruxHoldLabel?.let {
            add("반복해서 가장 버거웠던 구간은 $it 부근이었습니다.")
        }
        if (repeatedPatternLabels.isNotEmpty()) {
            add(
                "시도 전반에서 자주 보인 핵심 원인은 ${repeatedPatternLabels.joinToString(", ")}이었습니다."
            )
        }
        repeatedLoadFocusLabel?.let {
            add("부담은 주로 $it 쪽에 몰렸습니다.")
        }
        bestAttempt?.attemptNo?.let {
            add("최고 기록은 ${it}번째 시도에서 나왔습니다.")
        }
    }.ifEmpty {
        listOf(
            "반복 패턴을 확실히 집어 내기에는 데이터가 부족해, 전체 경향 위주로 해석했습니다."
        )
    }
}

private fun buildChallengeFocusFraction(
    attemptSummaries: List<FinalAnalysisAttemptSummary>,
    repeatedCruxHoldNo: Int?
): Float? {
    val matchingFractions = attemptSummaries
        .filter { repeatedCruxHoldNo == null || it.primaryCruxHoldNo == repeatedCruxHoldNo }
        .mapNotNull { it.stabilityFocusFraction }
    if (matchingFractions.isNotEmpty()) {
        return matchingFractions.average().toFloat()
    }

    val allFractions = attemptSummaries.mapNotNull { it.stabilityFocusFraction }
    return allFractions.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}

private fun buildChallengeFocusReasonText(
    repeatedPatternLabels: List<String>,
    repeatedLoadFocusLabel: String?
): String? {
    val causeSentence = when {
        repeatedPatternLabels.size >= 2 -> {
            "${repeatedPatternLabels.joinToString("과 ")}이 함께 나타난 구간입니다."
        }

        repeatedPatternLabels.size == 1 -> {
            "${repeatedPatternLabels.first()}이 두드러진 구간입니다."
        }

        else -> null
    }

    return when {
        causeSentence != null && repeatedLoadFocusLabel != null -> {
        "$causeSentence 특히 ${repeatedLoadFocusLabel}에 부담이 커진 구간입니다."
        }

        causeSentence != null -> causeSentence
        repeatedLoadFocusLabel != null -> {
        "${repeatedLoadFocusLabel}에 부담이 커진 구간입니다."
        }

        else -> null
    }
}

private fun buildCombinedTimeline(
    attemptSummaries: List<FinalAnalysisAttemptSummary>
): List<Float> {
    val timelines = attemptSummaries
        .filter { it.hasAiResult }
        .map { it.stabilityTimeline }
        .filter { it.isNotEmpty() }
    val maxLength = timelines.maxOfOrNull { it.size } ?: return DefaultFinalAnalysisTimeline

    return List(maxLength) { index ->
        timelines.map { timeline ->
            timeline.getOrElse(index) {
                timeline.lastOrNull() ?: 0.5f
            }
        }.average().toFloat()
    }
}

private fun formatReachedMetric(
    reachedText: String,
    totalHolds: Int,
    hasReachedValue: Boolean
): String {
    if (!hasReachedValue || reachedText == FinalAnalysisUnknownMetricText) {
        return reachedText
    }
    return if (totalHolds > 0) "$reachedText/$totalHolds" else reachedText
}

private fun <T> mostFrequentOrNull(values: List<T>): T? {
    return values
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
}

private fun <T> mostFrequentList(
    values: List<T>,
    limit: Int
): List<T> {
    return values
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
}
