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

        val lowerBodyDriveScores = attempts.mapNotNull { it.lowerBodyDriveScore }
        val averageLowerBodyDrive = if (lowerBodyDriveScores.isNotEmpty()) {
            lowerBodyDriveScores.average().toFloat()
        } else {
            0f
        }

        val completionScore = if (totalChallenges > 0) {
            successChallenges.toFloat() / totalChallenges.toFloat()
        } else {
            0f
        }
        val lowerBodyDriveProgress = (averageLowerBodyDrive / 100f).coerceIn(0f, 1f)

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
                "최근 기록에서\n평균 안정률이 ${stabilityDeltaPercent}% 좋아졌어요."

            stabilityDeltaPercent < 0 ->
                "최근 기록에서\n평균 안정률이 ${abs(stabilityDeltaPercent)}% 낮아졌어요."

            else ->
                "최근 기록에서\n평균 안정률이 비슷한 흐름을 유지하고 있어요."
        }

        return AnalysisGrowthSummaryUiModel(
            title = AnalysisStrings.GrowthSection,
            headline = growthHeadline,
            trendBadges = listOf(
                AnalysisBadgeUiModel(
                    label = "완등 ${successChallenges}회",
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
                    label = if (lowerBodyDriveScores.isNotEmpty()) {
                        "평균 하체 주도성 ${averageLowerBodyDrive.roundToInt()}점"
                    } else {
                        "하체 주도성 데이터 없음"
                    },
                    tone = if (averageLowerBodyDrive >= 65f) {
                        AnalysisBadgeTone.Success
                    } else {
                        AnalysisBadgeTone.Warning
                    }
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
                    label = "평균 하체 주도성",
                    value = if (lowerBodyDriveScores.isNotEmpty()) {
                        "${averageLowerBodyDrive.roundToInt()}점"
                    } else {
                        "-"
                    }
                )
            ),
            trendPoints = trendPoints,
            stabilityScore = averageStability.coerceIn(0f, 1f),
            completionScore = completionScore,
            averageLowerBodyDriveScore = averageLowerBodyDrive,
            lowerBodyDriveProgress = lowerBodyDriveProgress
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
