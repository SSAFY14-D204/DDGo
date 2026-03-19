package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisAttemptDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisCoachCardUiModel
import com.ddgo.app.feature.analysis.model.AnalysisOverviewStatUiModel
import com.ddgo.app.feature.analysis.model.AnalysisTimelineItemUiModel

/**
 * 시도 상세 화면 전용 UI 모델을 조립하는 mapper입니다.
 *
 * 역할:
 * - 한 시도의 상세 수치, 타임라인, 코칭 카드만 담당해 상세 화면 계산을 분리합니다.
 * - 시도 상세가 복잡해져도 대시보드나 챌린지 상세 mapper까지 비대해지지 않게 막습니다.
 */
internal object AnalysisAttemptDetailUiMapper {

    fun build(
        challenge: AnalysisChallengeSnapshot,
        attempt: AnalysisAttemptSnapshot
    ): AnalysisAttemptDetailUiModel {
        val maxHoldInChallenge = challenge.attempts.maxOfOrNull { it.maxHoldNo }?.coerceAtLeast(1) ?: 1
        val maxCruxDurationInChallenge = challenge.attempts
            .maxOfOrNull { it.cruxDurationMs ?: 0L }
            ?.coerceAtLeast(1L)
            ?: 1L

        return AnalysisAttemptDetailUiModel(
            title = "${attempt.attemptNo}차 시도",
            subtitle = "${challenge.gymName} | ${AnalysisFormatters.challengeSubtitle(challenge)}",
            resultBadge = AnalysisBadgeUiModel(
                label = AnalysisFormatters.resultLabel(attempt.attemptResult),
                tone = AnalysisFormatters.resultTone(attempt.attemptResult)
            ),
            headline = headlineFor(attempt.attemptResult),
            stabilityScore = attempt.centerStabilityRatio.coerceIn(0f, 1f),
            reachScore = (attempt.maxHoldNo.toFloat() / maxHoldInChallenge.toFloat()).coerceIn(0f, 1f),
            riskControlScore = (1f - (attempt.dangerEventCount.toFloat() / 4f)).coerceIn(0f, 1f),
            cruxFocusScore = ((attempt.cruxDurationMs ?: 0L).toFloat() / maxCruxDurationInChallenge.toFloat()).coerceIn(0f, 1f),
            metricCards = listOf(
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptDurationLabel,
                    value = AnalysisFormatters.formatDuration(attempt.durationMs)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptMaxHoldLabel,
                    value = "${attempt.maxHoldNo}홀드"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptStabilityLabel,
                    value = AnalysisFormatters.formatPercent(attempt.centerStabilityRatio)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptCruxTimeLabel,
                    value = AnalysisFormatters.formatDuration(attempt.cruxDurationMs ?: 0L)
                )
            ),
            timelineItems = buildTimelineItems(attempt),
            coachCards = buildCoachCards(attempt)
        )
    }

    /** 시도 결과에 따라 상단 헤드라인을 다르게 조립합니다. */
    private fun headlineFor(result: AnalysisChallengeResult): String {
        return when (result) {
            AnalysisChallengeResult.SUCCESS -> "가장 안정적인 리듬으로 완등한 시도예요."
            AnalysisChallengeResult.FAIL -> "크럭스 전환에서 흐름이 잠시 끊긴 시도예요."
            AnalysisChallengeResult.UNKNOWN -> "결과는 보류됐지만 흐름 패턴은 확인할 수 있어요."
        }
    }

    /** 시도 흐름을 읽기 쉽게 세 단계 카드로 구성합니다. */
    private fun buildTimelineItems(attempt: AnalysisAttemptSnapshot): List<AnalysisTimelineItemUiModel> {
        return listOf(
            AnalysisTimelineItemUiModel(
                title = "초반 흐름",
                description = if (attempt.centerStabilityRatio >= 0.75f) {
                    "초반 중심 이동이 안정적으로 유지돼요."
                } else {
                    "초반에 상체가 먼저 열리며 중심 흔들림이 커졌어요."
                },
                tone = AnalysisBadgeTone.Accent
            ),
            AnalysisTimelineItemUiModel(
                title = "크럭스 구간",
                description = attempt.cruxHoldNo?.let { holdNo ->
                    "${holdNo}홀드 구간에서 ${AnalysisFormatters.formatDuration(attempt.cruxDurationMs ?: 0L)} 동안 가장 오래 머물렀어요."
                } ?: "이번 시도에서는 크럭스 구간 정보가 충분하지 않아요.",
                tone = AnalysisBadgeTone.Warning
            ),
            AnalysisTimelineItemUiModel(
                title = "마무리 패턴",
                description = when (attempt.attemptResult) {
                    AnalysisChallengeResult.SUCCESS -> "후반부 리듬이 무너지지 않고 자연스럽게 완등으로 이어졌어요."
                    AnalysisChallengeResult.FAIL -> "마무리 직전 힘 배분이 흔들리며 마지막 연결이 끊겼어요."
                    AnalysisChallengeResult.UNKNOWN -> "마무리 패턴은 확인됐지만 최종 결과는 확정되지 않았어요."
                },
                tone = if (attempt.attemptResult == AnalysisChallengeResult.SUCCESS) {
                    AnalysisBadgeTone.Success
                } else {
                    AnalysisBadgeTone.Danger
                }
            )
        )
    }

    /** 시도 상세 하단의 코칭 카드 세 개를 구성합니다. */
    private fun buildCoachCards(attempt: AnalysisAttemptSnapshot): List<AnalysisCoachCardUiModel> {
        return listOf(
            AnalysisCoachCardUiModel(
                title = AnalysisStrings.CoachFailureTitle,
                body = attempt.failureReason ?: AnalysisStrings.EmptyCoachMessage,
                tone = AnalysisBadgeTone.Warning
            ),
            AnalysisCoachCardUiModel(
                title = AnalysisStrings.CoachRiskTitle,
                body = attempt.riskAlert ?: AnalysisStrings.EmptyCoachMessage,
                tone = AnalysisBadgeTone.Danger
            ),
            AnalysisCoachCardUiModel(
                title = AnalysisStrings.CoachMissionTitle,
                body = attempt.nextMission ?: AnalysisStrings.EmptyCoachMessage,
                tone = AnalysisBadgeTone.Accent
            )
        )
    }
}
