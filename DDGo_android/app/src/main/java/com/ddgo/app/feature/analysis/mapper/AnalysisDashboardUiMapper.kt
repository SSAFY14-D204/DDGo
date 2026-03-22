package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisChallengeListItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisGrowthSummaryUiModel
import com.ddgo.app.feature.analysis.model.AnalysisOverviewStatUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTrendPointUiModel
import kotlin.math.abs
import kotlin.math.roundToInt

internal object AnalysisDashboardUiMapper {

    fun buildGrowthSummary(challenges: List<AnalysisChallengeSnapshot>): AnalysisGrowthSummaryUiModel {
        if (challenges.isEmpty()) return AnalysisGrowthSummaryUiModel.empty()

        val attempts = challenges.flatMap { it.attempts }
        val totalChallenges = challenges.size
        val totalAttempts = attempts.size
        val successChallenges = challenges.count { it.challengeResult == AnalysisChallengeResult.SUCCESS }
        val averageStability = attempts.map { it.centerStabilityRatio }.average().toFloat()
        val averageDangerEvents = attempts.map { it.dangerEventCount }.average().toFloat()
        val totalDangerEvents = attempts.sumOf { it.dangerEventCount }
        val completionScore = if (totalChallenges > 0) {
            successChallenges.toFloat() / totalChallenges.toFloat()
        } else {
            0f
        }
        val dangerEventProgress = (averageDangerEvents / 4f).coerceIn(0f, 1f)

        val recentChallenges = challenges
            .sortedBy { it.startedAt }
            .takeLast(5)

        val trendPoints = recentChallenges.mapIndexed { index, challenge ->
            AnalysisTrendPointUiModel(
                label = AnalysisFormatters.formatDate(challenge.startedAt),
                value = challenge.attempts.map { it.centerStabilityRatio }.average().toFloat(),
                highlight = index == recentChallenges.lastIndex
            )
        }

        val stabilityDeltaPercent = if (trendPoints.size >= 2) {
            ((trendPoints.last().value - trendPoints.first().value) * 100f).roundToInt()
        } else {
            0
        }

        val growthHeadline = when {
            stabilityDeltaPercent > 0 ->
                "최근 기록에서 평균 안정률이 ${stabilityDeltaPercent}% 좋아졌어요."
            stabilityDeltaPercent < 0 ->
                "최근 기록에서 평균 안정률이 ${abs(stabilityDeltaPercent)}% 낮아졌어요."
            else ->
                "최근 기록의 평균 안정률이 비슷한 흐름을 유지하고 있어요."
        }

        return AnalysisGrowthSummaryUiModel(
            title = AnalysisStrings.GrowthSection,
            headline = growthHeadline,
            trendBadges = listOf(
                AnalysisBadgeUiModel(
                    label = "완등 ${successChallenges}개",
                    tone = AnalysisBadgeTone.Success
                ),
                AnalysisBadgeUiModel(
                    label = if (stabilityDeltaPercent >= 0) {
                        "안정률 +${stabilityDeltaPercent}%"
                    } else {
                        "안정률 ${stabilityDeltaPercent}%"
                    },
                    tone = if (stabilityDeltaPercent >= 0) {
                        AnalysisBadgeTone.Accent
                    } else {
                        AnalysisBadgeTone.Warning
                    }
                ),
                AnalysisBadgeUiModel(
                    label = "평균 위험 이벤트 ${AnalysisFormatters.formatAverageEventCount(averageDangerEvents)}",
                    tone = AnalysisBadgeTone.Warning
                )
            ),
            metrics = listOf(
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.TotalChallengesLabel,
                    value = "${totalChallenges}개"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.TotalAttemptsLabel,
                    value = "${totalAttempts}회"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AverageStabilityLabel,
                    value = AnalysisFormatters.formatPercent(averageStability)
                ),
                AnalysisOverviewStatUiModel(
                    label = "위험 이벤트",
                    value = AnalysisFormatters.formatEventCount(totalDangerEvents)
                )
            ),
            trendPoints = trendPoints,
            stabilityScore = averageStability.coerceIn(0f, 1f),
            completionScore = completionScore,
            averageDangerEvents = averageDangerEvents,
            dangerEventProgress = dangerEventProgress
        )
    }

    fun buildChallengeList(challenges: List<AnalysisChallengeSnapshot>): List<AnalysisChallengeListItemUiModel> {
        return challenges.mapIndexed { index, challenge ->
            AnalysisChallengeListItemUiModel(
                challengeId = challenge.id,
                title = challenge.gymName,
                subtitle = AnalysisFormatters.challengeSubtitle(challenge),
                meta = "${AnalysisFormatters.formatDate(challenge.startedAt)} | ${challenge.attempts.size}회 시도",
                resultBadge = AnalysisBadgeUiModel(
                    label = AnalysisFormatters.resultLabel(challenge.challengeResult),
                    tone = AnalysisFormatters.resultTone(challenge.challengeResult)
                ),
                isRecent = index == 0
            )
        }
    }
}
