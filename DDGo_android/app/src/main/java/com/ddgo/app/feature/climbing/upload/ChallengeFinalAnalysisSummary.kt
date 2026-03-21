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
    val repeatedCruxHoldLabel = repeatedCruxHoldNo?.let { "$it\uBC88 \uD640\uB4DC" }
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
        "\uBC1C \uC0AC\uC6A9 \uBD80\uC871" -> "\uBC1C \uD65C\uC6A9 \uBD80\uC871"
        "\uC911\uC2EC \uD754\uB4E4\uB9BC" -> "\uC911\uC2EC \uD754\uB4E4\uB9BC"
        "\uD314 \uC0AC\uC6A9 \uACFC\uB2E4" -> "\uD314 \uC758\uC874 \uD07C"
        "\uACFC\uD55C \uBC84\uD2F0\uAE30" -> "\uC624\uB798 \uBC84\uD2F0\uAE30"
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
        primaryReasonLabel = null,
        feedbackTypes = emptyList(),
        loadFocusLabel = null,
        feedbackLine = "\uBD84\uC11D \uB370\uC774\uD130\uAC00 \uCDA9\uBD84\uD558\uC9C0 \uC54A\uC544 \uC885\uD569 \uC694\uC57D\uC744 \uB9CC\uB4E4\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.",
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
        "\uCD1D ${attemptCount}\uBC88 \uC2DC\uB3C4 \uC911 ${successAttemptCount}\uBC88 \uC644\uB4F1\uC5D0 \uC131\uACF5\uD588\uACE0, \uD3C9\uADE0 $averageReachedText \uD640\uB4DC\uAE4C\uC9C0 \uB3C4\uB2EC\uD588\uC2B5\uB2C8\uB2E4."
    } else {
        val bestReachedText = bestAttempt?.let {
            formatReachedMetric(
                reachedText = it.reachedHoldsText,
                totalHolds = totalHolds,
                hasReachedValue = it.reachedHolds != null
            )
        } ?: FinalAnalysisUnknownMetricText
        "\uCD1D ${attemptCount}\uBC88 \uC2DC\uB3C4\uD588\uACE0, \uD3C9\uADE0 $averageReachedText \uD640\uB4DC\uAE4C\uC9C0 \uB3C4\uB2EC\uD588\uC2B5\uB2C8\uB2E4. \uCD5C\uACE0 \uAE30\uB85D\uC740 $bestReachedText\uC600\uC2B5\uB2C8\uB2E4."
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
            "\uC644\uB4F1\uC740 ${bestAttempt.attemptNo}\uBC88\uC9F8 \uC2DC\uB3C4\uC5D0\uC11C \uB098\uC654\uACE0, \uC804\uCCB4 \uC644\uB4F1 \uC131\uACF5 \uC2DC\uB3C4\uB294 ${successAttemptCount}\uBC88\uC785\uB2C8\uB2E4."
        }

        bestAttempt?.reachedHolds != null && totalHolds > 0 -> {
            "\uAC00\uC7A5 \uB192\uC774 \uC62C\uB77C\uAC14\uB358 \uACB0\uACFC\uB294 ${bestAttempt.attemptNo}\uBC88\uC9F8 \uC2DC\uB3C4\uC758 ${bestAttempt.reachedHolds}/$totalHolds \uD640\uB4DC \uB3C4\uB2EC\uC774\uC5C8\uC2B5\uB2C8\uB2E4."
        }

        else -> {
            "\uC644\uB4F1\uAE4C\uC9C0 \uC774\uC5B4\uC9C0\uC9C4 \uC54A\uC558\uC9C0\uB9CC, \uC2DC\uB3C4\uBCC4 \uACBD\uD5A5\uC744 \uB9E5\uB77D \uC788\uAC8C \uC815\uB9AC\uD588\uC2B5\uB2C8\uB2E4."
        }
    }
}

private fun buildChallengeNatureLine(
    repeatedPatternLabels: List<String>,
    repeatedLoadFocusLabel: String?
): String {
    return when {
        repeatedPatternLabels.contains("\uBC1C \uD65C\uC6A9 \uBD80\uC871") &&
            repeatedPatternLabels.contains("\uC911\uC2EC \uD754\uB4E4\uB9BC") -> {
            "\uC774 \uBB38\uC81C\uB294 \uBC1C\uC744 \uC138\uC6B0\uBA74\uC11C \uC911\uC2EC\uC744 \uB193\uCE58\uC9C0 \uC54A\uB294 \uC5F0\uACB0\uC774 \uD575\uC2EC\uC774\uC5C8\uC2B5\uB2C8\uB2E4."
        }

        repeatedPatternLabels.contains("\uC911\uC2EC \uD754\uB4E4\uB9BC") -> {
            "\uC774 \uBB38\uC81C\uB294 \uB2E4\uC74C \uD640\uB4DC\uB85C \uAC00\uAE30 \uC804\uC5D0 \uC911\uC2EC\uC744 \uC548\uC815\uC801\uC73C\uB85C \uC7A1\uB294 \uD750\uB984\uC774 \uC911\uC694\uD588\uC2B5\uB2C8\uB2E4."
        }

        repeatedPatternLabels.contains("\uBC1C \uD65C\uC6A9 \uBD80\uC871") -> {
            "\uC774 \uBB38\uC81C\uB294 \uC190\uC73C\uB85C \uBC84\uD2F0\uAE30\uBCF4\uB2E4 \uD558\uCCB4\uB85C \uBA3C\uC800 \uBC00\uC5B4 \uC62C\uB9AC\uB294 \uD750\uB984\uC774 \uC911\uC694\uD588\uC2B5\uB2C8\uB2E4."
        }

        repeatedPatternLabels.contains("\uD314 \uC758\uC874 \uD07C") -> {
            "\uC774 \uBB38\uC81C\uB294 \uC0C1\uCCB4\uB85C \uC624\uB798 \uBC84\uD2F0\uAE30\uBCF4\uB2E4 \uD558\uCCB4\uC640 \uD568\uAED8 \uD798\uC744 \uB098\uB204\uB294 \uD750\uB984\uC774 \uC911\uC694\uD588\uC2B5\uB2C8\uB2E4."
        }

        repeatedPatternLabels.contains("\uC624\uB798 \uBC84\uD2F0\uAE30") -> {
            "\uC774 \uBB38\uC81C\uB294 \uD55C \uC790\uC138\uC5D0\uC11C \uBA08\uCD94\uB294 \uC2DC\uAC04\uC744 \uC904\uC774\uACE0 \uB9AC\uB4EC \uC788\uAC8C \uC5F0\uACB0\uD558\uB294 \uD750\uB984\uC774 \uC911\uC694\uD588\uC2B5\uB2C8\uB2E4."
        }

        repeatedLoadFocusLabel == "\uBA38\uD1B5" -> {
            "\uC774 \uBB38\uC81C\uB294 \uBA38\uD1B5 \uACE0\uC815\uACFC \uCCB4\uC911 \uC774\uB3D9\uC774 \uB09C\uC774\uB3C4\uB97C \uD06C\uAC8C \uC88C\uC6B0\uD588\uC2B5\uB2C8\uB2E4."
        }

        else -> {
            "\uC774 \uBB38\uC81C\uB294 \uD55C \uAD6C\uAC04\uC758 \uD798\uC73C\uB85C \uBC84\uD2F0\uAE30\uBCF4\uB2E4, \uC190\uBC1C\uC744 \uC5F0\uACB0\uD558\uBA70 \uD750\uB984\uC744 \uC774\uC5B4\uAC00\uB294 \uAC83\uC774 \uC911\uC694\uD588\uC2B5\uB2C8\uB2E4."
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
                "\uCCAB \uC2DC\uB3C4 \uB300\uBE44 \uB9C8\uC9C0\uB9C9 \uC2DC\uB3C4 \uB3C4\uB2EC \uD640\uB4DC: " +
                    formatReachedMetric(
                        reachedText = firstAttempt.reachedHoldsText,
                        totalHolds = totalHolds,
                        hasReachedValue = true
                    ) +
                    " \u2192 " +
                    formatReachedMetric(
                        reachedText = lastAttempt.reachedHoldsText,
                        totalHolds = totalHolds,
                        hasReachedValue = true
                    )
            )
        }
        if (firstAttempt?.insideSupportRatio != null && lastAttempt?.insideSupportRatio != null) {
            add(
                "\uADE0\uD615 \uC720\uC9C0\uC728: ${firstAttempt.insideSupportRatio}% \u2192 ${lastAttempt.insideSupportRatio}%"
            )
        }
        if (firstAttempt?.stableContactRatio != null && lastAttempt?.stableContactRatio != null) {
            add(
                "\uC190\uBC1C \uC9C0\uC9C0 \uC548\uC815\uB3C4: ${firstAttempt.stableContactRatio}% \u2192 ${lastAttempt.stableContactRatio}%"
            )
        }
        when {
            successAttemptCount > 0 -> {
                add("\uC644\uB4F1 \uC131\uACF5 \uC2DC\uB3C4\uB294 \uCD1D ${successAttemptCount}\uBC88\uC774\uC5C8\uC2B5\uB2C8\uB2E4.")
            }

            bestAttempt != null -> {
                add(
                    "\uAC00\uC7A5 \uC88B\uC558\uB358 \uD750\uB984\uC740 ${bestAttempt.attemptNo}\uBC88\uC9F8 \uC2DC\uB3C4\uC5D0\uC11C \uB098\uC654\uC2B5\uB2C8\uB2E4."
                )
            }
        }
    }.ifEmpty {
        listOf(
            "\uC2DC\uB3C4 \uC218\uAC00 \uC801\uC5B4 \uBCC0\uD654 \uCD94\uC138\uB97C \uB2E8\uC815\uD558\uAE30\uBCF4\uB2E4, \uC804\uCCB4 \uACBD\uD5A5\uC744 \uC911\uC2EC\uC73C\uB85C \uC815\uB9AC\uD588\uC2B5\uB2C8\uB2E4."
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
            add("\uBC18\uBCF5\uD574\uC11C \uAC00\uC7A5 \uBC84\uAC70\uC6E0\uB358 \uAD6C\uAC04\uC740 $it \uBD80\uADFC\uC774\uC5C8\uC2B5\uB2C8\uB2E4.")
        }
        if (repeatedPatternLabels.isNotEmpty()) {
            add(
                "\uC2DC\uB3C4 \uC804\uBC18\uC5D0\uC11C \uC790\uC8FC \uBCF4\uC778 \uD575\uC2EC \uC6D0\uC778\uC740 ${repeatedPatternLabels.joinToString(", ")}\uC774\uC5C8\uC2B5\uB2C8\uB2E4."
            )
        }
        repeatedLoadFocusLabel?.let {
            add("\uBD80\uB2F4\uC740 \uC8FC\uB85C $it \uCABD\uC5D0 \uBAB0\uB838\uC2B5\uB2C8\uB2E4.")
        }
        bestAttempt?.attemptNo?.let {
            add("\uCD5C\uACE0 \uAE30\uB85D\uC740 ${it}\uBC88\uC9F8 \uC2DC\uB3C4\uC5D0\uC11C \uB098\uC654\uC2B5\uB2C8\uB2E4.")
        }
    }.ifEmpty {
        listOf(
            "\uBC18\uBCF5 \uD328\uD134\uC744 \uD655\uC2E4\uD788 \uC9D1\uC5B4 \uB0B4\uAE30\uC5D0\uB294 \uB370\uC774\uD130\uAC00 \uBD80\uC871\uD574, \uC804\uCCB4 \uACBD\uD5A5 \uC704\uC8FC\uB85C \uD574\uC11D\uD588\uC2B5\uB2C8\uB2E4."
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
            "${repeatedPatternLabels.joinToString("\uACFC ")}\uC774 \uD568\uAED8 \uB098\uD0C0\uB09C \uAD6C\uAC04\uC785\uB2C8\uB2E4."
        }

        repeatedPatternLabels.size == 1 -> {
            "${repeatedPatternLabels.first()}\uC774 \uB450\uB4DC\uB7EC\uC9C4 \uAD6C\uAC04\uC785\uB2C8\uB2E4."
        }

        else -> null
    }

    return when {
        causeSentence != null && repeatedLoadFocusLabel != null -> {
            "$causeSentence \uD2B9\uD788 $repeatedLoadFocusLabel\uC5D0 \uBD80\uB2F4\uC774 \uCEE4\uC9C4 \uAD6C\uAC04\uC785\uB2C8\uB2E4."
        }

        causeSentence != null -> causeSentence
        repeatedLoadFocusLabel != null -> {
            "$repeatedLoadFocusLabel\uC5D0 \uBD80\uB2F4\uC774 \uCEE4\uC9C4 \uAD6C\uAC04\uC785\uB2C8\uB2E4."
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
