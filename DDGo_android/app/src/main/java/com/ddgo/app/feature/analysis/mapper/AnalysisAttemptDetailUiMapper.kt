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

internal object AnalysisAttemptDetailUiMapper {

    fun build(
        challenge: AnalysisChallengeSnapshot,
        attempt: AnalysisAttemptSnapshot
    ): AnalysisAttemptDetailUiModel {
        val maxHoldInChallenge = challenge.attempts.maxOfOrNull { it.maxHoldNo }?.coerceAtLeast(1) ?: 1
        val maxDangerEventsInChallenge = challenge.attempts
            .maxOfOrNull { it.dangerEventCount }
            ?.coerceAtLeast(1)
            ?: 1
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
            dangerEventScore = (attempt.dangerEventCount.toFloat() / maxDangerEventsInChallenge.toFloat()).coerceIn(0f, 1f),
            cruxFocusScore = ((attempt.cruxDurationMs ?: 0L).toFloat() / maxCruxDurationInChallenge.toFloat()).coerceIn(0f, 1f),
            stabilityValueLabel = AnalysisFormatters.formatPercent(attempt.centerStabilityRatio),
            reachValueLabel = "${attempt.maxHoldNo}번",
            dangerEventValueLabel = AnalysisFormatters.formatEventCount(attempt.dangerEventCount),
            cruxFocusValueLabel = AnalysisFormatters.formatDuration(attempt.cruxDurationMs ?: 0L),
            metricCards = listOf(
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptDurationLabel,
                    value = AnalysisFormatters.formatDuration(attempt.durationMs)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptMaxHoldLabel,
                    value = "${attempt.maxHoldNo}번"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptStabilityLabel,
                    value = AnalysisFormatters.formatPercent(attempt.centerStabilityRatio)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptCruxHoldLabel,
                    value = attempt.cruxHoldNo?.let { "${it}번" } ?: "-"
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptCruxTimeLabel,
                    value = AnalysisFormatters.formatDuration(attempt.cruxDurationMs ?: 0L)
                ),
                AnalysisOverviewStatUiModel(
                    label = AnalysisStrings.AttemptDangerEventsLabel,
                    value = AnalysisFormatters.formatEventCount(attempt.dangerEventCount)
                )
            ),
            timelineItems = buildTimelineItems(attempt),
            coachCards = buildCoachCards(attempt)
        )
    }

    private fun headlineFor(result: AnalysisChallengeResult): String {
        return when (result) {
            AnalysisChallengeResult.SUCCESS -> "완등으로 이어진 시도의 지표예요."
            AnalysisChallengeResult.FAIL -> "이 시도에서 막힌 구간과 위험 요소를 볼 수 있어요."
            AnalysisChallengeResult.UNKNOWN -> "저장된 시도 데이터를 기준으로 표시하고 있어요."
        }
    }

    private fun buildTimelineItems(attempt: AnalysisAttemptSnapshot): List<AnalysisTimelineItemUiModel> {
        return listOf(
            AnalysisTimelineItemUiModel(
                title = "중심 안정률",
                description = if (attempt.centerStabilityRatio >= 0.75f) {
                    "시도 내내 중심이 비교적 안정적으로 유지됐어요."
                } else {
                    "중심이 흔들린 구간이 있어 자세 보정이 필요해요."
                },
                tone = AnalysisBadgeTone.Accent
            ),
            AnalysisTimelineItemUiModel(
                title = "크럭스 구간",
                description = attempt.cruxHoldNo?.let { holdNo ->
                    "${holdNo}번 홀드 부근에서 ${AnalysisFormatters.formatDuration(attempt.cruxDurationMs ?: 0L)} 동안 가장 오래 머물렀어요."
                } ?: "이번 시도에서는 뚜렷한 크럭스 홀드가 기록되지 않았어요.",
                tone = AnalysisBadgeTone.Warning
            ),
            AnalysisTimelineItemUiModel(
                title = "위험 이벤트",
                description = if (attempt.dangerEventCount > 0) {
                    "위험 이벤트가 ${AnalysisFormatters.formatEventCount(attempt.dangerEventCount)} 기록됐어요."
                } else {
                    "위험 이벤트 없이 비교적 안정적인 흐름이었어요."
                },
                tone = if (attempt.dangerEventCount > 0) {
                    AnalysisBadgeTone.Danger
                } else {
                    AnalysisBadgeTone.Success
                }
            )
        )
    }

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
