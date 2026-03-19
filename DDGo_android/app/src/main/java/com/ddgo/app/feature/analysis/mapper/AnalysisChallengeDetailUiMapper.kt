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

/**
 * 챌린지 상세 화면 전용 UI 모델을 조립하는 mapper입니다.
 *
 * 역할:
 * - 챌린지 요약, 시도 흐름, 시도 목록 등 챌린지 상세 화면 구성만 담당합니다.
 * - 대시보드/시도 상세 계산과 분리해 변경 영향 범위를 챌린지 화면으로 한정합니다.
 */
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

    /** 챌린지 종합 분석 카드에 들어갈 헤드라인과 지표를 조립합니다. */
    private fun buildSummary(challenge: AnalysisChallengeSnapshot): AnalysisChallengeSummaryUiModel {
        val attempts = challenge.attempts
        val averageStability = attempts.map { it.centerStabilityRatio }.average().toFloat()
        val totalDanger = attempts.sumOf { it.dangerEventCount }
        val cruxHold = attempts.maxByOrNull { it.cruxDurationMs ?: 0L }?.cruxHoldNo

        val headline = when (challenge.challengeResult) {
            AnalysisChallengeResult.SUCCESS -> "시도마다 흔들림이 줄면서 완등까지 자연스럽게 연결됐어요."
            AnalysisChallengeResult.FAIL -> "도달 지점은 늘었지만 크럭스 전환이 아직 가장 큰 과제예요."
            AnalysisChallengeResult.UNKNOWN -> "이번 챌린지는 결과보다 흐름 확인이 먼저 필요한 상태예요."
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
                    label = AnalysisStrings.ChallengeCruxLabel,
                    value = cruxHold?.let { "${it}홀드" } ?: "-"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.ChallengeDangerLabel,
                    value = "${totalDanger}회"
                )
            )
        )
    }

    /** 챌린지 안의 시도 목록 카드용 값을 조립합니다. */
    private fun buildAttemptListItem(attempt: AnalysisAttemptSnapshot): AnalysisAttemptListItemUiModel {
        return AnalysisAttemptListItemUiModel(
            attemptNo = attempt.attemptNo,
            title = "${attempt.attemptNo}차 시도",
            subtitle = "${AnalysisFormatters.formatDuration(attempt.durationMs)} | 위험 ${attempt.dangerEventCount}회",
            holdLabel = "최대 ${attempt.maxHoldNo}홀드",
            stabilityLabel = "안정 ${AnalysisFormatters.formatPercent(attempt.centerStabilityRatio)}",
            resultBadge = AnalysisBadgeUiModel(
                label = AnalysisFormatters.resultLabel(attempt.attemptResult),
                tone = AnalysisFormatters.resultTone(attempt.attemptResult)
            )
        )
    }
}
