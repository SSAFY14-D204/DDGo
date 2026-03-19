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

/**
 * 대시보드 화면에 필요한 UI 모델을 조립하는 mapper입니다.
 *
 * 역할:
 * - 전체 성장 요약과 챌린지 목록처럼 대시보드 영역에 필요한 계산만 담당합니다.
 * - 챌린지 상세/시도 상세 계산과 분리해 mapper 책임을 작게 유지합니다.
 */
internal object AnalysisDashboardUiMapper {

    fun buildGrowthSummary(challenges: List<AnalysisChallengeSnapshot>): AnalysisGrowthSummaryUiModel {
        if (challenges.isEmpty()) return AnalysisGrowthSummaryUiModel.empty()

        val attempts = challenges.flatMap { it.attempts }
        val totalChallenges = challenges.size
        val totalAttempts = attempts.size
        val successChallenges = challenges.count { it.challengeResult == AnalysisChallengeResult.SUCCESS }
        val averageStability = attempts.map { it.centerStabilityRatio }.average().toFloat()
        val averageDanger = attempts.map { it.dangerEventCount }.average().toFloat()
        val completionScore = if (totalChallenges > 0) {
            successChallenges.toFloat() / totalChallenges.toFloat()
        } else {
            0f
        }
        val riskControlScore = (1f - (averageDanger / 4f)).coerceIn(0f, 1f)

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
            stabilityDeltaPercent > 0 -> "최근 챌린지에서 안정률이 ${stabilityDeltaPercent}% 좋아졌어요."
            stabilityDeltaPercent < 0 -> "최근 챌린지에서 안정률 변동이 ${abs(stabilityDeltaPercent)}% 있었어요."
            else -> "최근 챌린지 흐름이 비슷한 수준으로 유지되고 있어요."
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
                    label = "리스크 관리 ${AnalysisFormatters.formatPercent(riskControlScore)}",
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
                )
            ),
            trendPoints = trendPoints,
            stabilityScore = averageStability.coerceIn(0f, 1f),
            completionScore = completionScore,
            riskControlScore = riskControlScore
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
