package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptFlowItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptGrowthPointUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptListItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisChallengeDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisChallengeSummaryUiModel
import com.ddgo.app.feature.analysis.model.AnalysisOverviewStatUiModel

internal object AnalysisChallengeDetailUiMapper {

    fun build(challenge: AnalysisChallengeSnapshot): AnalysisChallengeDetailUiModel {
        val attempts = challenge.attempts.sortedBy { it.attemptNo }

        return AnalysisChallengeDetailUiModel(
            title = challenge.gymName,
            subtitle = "${AnalysisFormatters.challengeSubtitle(challenge)} | ${AnalysisFormatters.formatFullDate(challenge.startedAt)}",
            badges = listOf(
                AnalysisBadgeUiModel(
                    label = AnalysisFormatters.statusLabel(challenge.challengeStatus),
                    tone = AnalysisFormatters.statusTone(challenge.challengeStatus)
                ),
                AnalysisBadgeUiModel(
                    label = AnalysisFormatters.resultLabel(challenge.challengeResult),
                    tone = AnalysisFormatters.resultTone(challenge.challengeResult)
                )
            ),
            attemptFlow = attempts.map { attempt ->
                AnalysisAttemptFlowItemUiModel(
                    attemptNo = attempt.attemptNo,
                    tone = AnalysisFormatters.resultTone(attempt.attemptResult),
                    isLatest = attempt.attemptNo == attempts.lastOrNull()?.attemptNo
                )
            },
            growthPoints = attempts.map { attempt ->
                AnalysisAttemptGrowthPointUiModel(
                    label = attempt.attemptNo.toString(),
                    stabilityScore = attempt.centerStabilityRatio.coerceIn(0f, 1f),
                    maxHoldNo = attempt.maxHoldNo,
                    riskEventCount = attempt.dangerEventCount,
                    tone = AnalysisFormatters.resultTone(attempt.attemptResult)
                )
            },
            summary = buildSummary(challenge),
            attempts = attempts.map(::buildAttemptListItem)
        )
    }

    private fun buildSummary(challenge: AnalysisChallengeSnapshot): AnalysisChallengeSummaryUiModel {
        val attempts = challenge.attempts
        val averageStability = attempts.map { it.centerStabilityRatio }.average().toFloat()
        val totalDangerEvents = attempts.sumOf { it.dangerEventCount }
        val representativeCruxHold = attempts
            .maxByOrNull { it.cruxDurationMs ?: 0L }
            ?.cruxHoldNo

        val headline = when (challenge.challengeResult) {
            AnalysisChallengeResult.SUCCESS ->
                "완등까지 이어진 흐름을 다시 볼 수 있어요."
            AnalysisChallengeResult.FAIL ->
                "반복된 시도에서 어느 구간이 막혔는지 확인할 수 있어요."
            AnalysisChallengeResult.UNKNOWN ->
                "현재 저장된 시도 데이터를 기준으로 분석했어요."
        }

        return AnalysisChallengeSummaryUiModel(
            title = AnalysisStrings.ChallengeSummarySection,
            headline = headline,
            stats = listOf(
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.ChallengeAttemptCountLabel,
                    value = "${attempts.size}회"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.ChallengeStabilityLabel,
                    value = AnalysisFormatters.formatPercent(averageStability)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.ChallengeDangerLabel,
                    value = AnalysisFormatters.formatEventCount(totalDangerEvents)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.ChallengeCruxLabel,
                    value = representativeCruxHold?.let { "${it}번" } ?: "-"
                )
            )
        )
    }

    private fun buildAttemptListItem(attempt: AnalysisAttemptSnapshot): AnalysisAttemptListItemUiModel {
        return AnalysisAttemptListItemUiModel(
            attemptNo = attempt.attemptNo,
            title = "${attempt.attemptNo}차 시도",
            subtitle = "${AnalysisFormatters.formatDuration(attempt.durationMs)} | 위험 ${AnalysisFormatters.formatEventCount(attempt.dangerEventCount)}",
            holdLabel = "최대 ${attempt.maxHoldNo}번 홀드",
            stabilityLabel = "안정률 ${AnalysisFormatters.formatPercent(attempt.centerStabilityRatio)}",
            resultBadge = AnalysisBadgeUiModel(
                label = AnalysisFormatters.resultLabel(attempt.attemptResult),
                tone = AnalysisFormatters.resultTone(attempt.attemptResult)
            )
        )
    }
}
