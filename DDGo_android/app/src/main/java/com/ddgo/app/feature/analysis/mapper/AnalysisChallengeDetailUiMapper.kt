package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.feature.analysis.model.AnalysisAttemptFlowItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptGrowthPointUiModel
import com.ddgo.app.feature.analysis.model.AnalysisAttemptListItemUiModel
import com.ddgo.app.feature.analysis.model.AnalysisBadgeTone
import com.ddgo.app.feature.analysis.model.AnalysisBadgeUiModel
import com.ddgo.app.feature.analysis.model.AnalysisChallengeDetailUiModel
import com.ddgo.app.feature.analysis.model.AnalysisChallengeSummaryUiModel
import kotlin.math.roundToInt

internal object AnalysisChallengeDetailUiMapper {

    fun build(challenge: AnalysisChallengeSnapshot): AnalysisChallengeDetailUiModel {
        val attempts = challenge.attempts.sortedBy { it.attemptNo }

        return AnalysisChallengeDetailUiModel(
            title = challenge.gymName,
            subtitle = "${AnalysisFormatters.challengeSubtitle(challenge)} | " +
                AnalysisFormatters.formatFullDate(challenge.startedAt),
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
                    lowerBodyDriveScore = attempt.lowerBodyDriveScore ?: 0,
                    tone = AnalysisFormatters.resultTone(attempt.attemptResult)
                )
            },
            summary = buildSummary(challenge, attempts),
            attempts = attempts.map { attempt ->
                buildAttemptListItem(
                    attempt = attempt,
                    maxHoldInChallenge = attempts.maxOfOrNull { it.maxHoldNo }?.coerceAtLeast(1) ?: 1
                )
            }
        )
    }

    private fun buildSummary(
        challenge: AnalysisChallengeSnapshot,
        attempts: List<AnalysisAttemptSnapshot>
    ): AnalysisChallengeSummaryUiModel {
        val overallScores = attempts.mapNotNull { it.overallMovementScore }
        val recoveryScores = attempts.mapNotNull { it.stabilityRecoveryScore }
        val lowerBodyScores = attempts.mapNotNull { it.lowerBodyDriveScore }
        val stableContactScores = attempts.mapNotNull { it.stableContactRatio?.times(100f)?.roundToInt() }
        val averageStabilityScore = attempts
            .map { (it.centerStabilityRatio * 100f).roundToInt() }
            .average()
            .roundToInt()
        val reachedHold = attempts.maxOfOrNull { it.maxHoldNo } ?: 0
        val maxHoldInChallenge = attempts.maxOfOrNull { it.maxHoldNo }?.coerceAtLeast(1) ?: 1
        val repeatedCruxHold = attempts
            .mapNotNull { it.cruxHoldNo }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: attempts.maxByOrNull { it.cruxDurationMs ?: 0L }?.cruxHoldNo
        val repeatedLoadFocus = attempts
            .mapNotNull { it.loadFocusLabel?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        val defaultHeadline = when (challenge.challengeResult) {
            AnalysisChallengeResult.SUCCESS ->
                "완등까지 이어진 흐름을 다시 보며, 강했던 패턴과 유지한 리듬을 함께 확인해보세요."
            AnalysisChallengeResult.FAIL ->
                "마지막까지 이어지지 않은 구간을 중심으로, 다음 시도에서 보완할 핵심 흐름을 확인해보세요."
            AnalysisChallengeResult.UNKNOWN ->
                "시도별 결과를 함께 비교하며, 어떤 흐름이 반복되었는지 확인해보세요."
        }

        return AnalysisChallengeSummaryUiModel(
            title = "챌린지 결과",
            headline = challenge.finalComment.takeIf { it.isNotBlank() } ?: defaultHeadline,
            attemptCount = attempts.size,
            overallScore = overallScores.averageOrNull(),
            overallSuccess = challenge.challengeResult == AnalysisChallengeResult.SUCCESS,
            reachedHoldLabel = "${reachedHold}번",
            reachedHoldSuffix = "/${maxHoldInChallenge}번",
            cruxHoldLabel = repeatedCruxHold?.let { "${it}번 홀드" } ?: "-",
            averageStabilityScore = averageStabilityScore,
            averageRecoveryScore = recoveryScores.averageOrNull(),
            averageLowerBodyDriveScore = lowerBodyScores.averageOrNull(),
            averageStableContactScore = stableContactScores.averageOrNull(),
            repeatedLoadFocusLabel = repeatedLoadFocus,
            strengths = buildStrengths(challenge, averageStabilityScore, recoveryScores.averageOrNull(), lowerBodyScores.averageOrNull()),
            improvements = buildImprovements(attempts, averageStabilityScore, recoveryScores.averageOrNull(), lowerBodyScores.averageOrNull(), repeatedLoadFocus)
        )
    }

    private fun buildAttemptListItem(
        attempt: AnalysisAttemptSnapshot,
        maxHoldInChallenge: Int
    ): AnalysisAttemptListItemUiModel {
        val stabilityScore = (attempt.centerStabilityRatio * 100f).roundToInt()
        val summaryLine = attempt.nextMission
            ?.takeIf { it.isNotBlank() }
            ?: attempt.failureReason?.takeIf { it.isNotBlank() }
            ?: attempt.riskAlert?.takeIf { it.isNotBlank() }
            ?: when (attempt.attemptResult) {
                AnalysisChallengeResult.SUCCESS -> "완등까지 연결한 흐름과 강했던 동작 패턴을 다시 확인해보세요."
                AnalysisChallengeResult.FAIL -> "흐름이 끊긴 구간과 다음 시도에서 먼저 보완할 지점을 확인해보세요."
                AnalysisChallengeResult.UNKNOWN -> "이번 시도의 흐름과 도달 결과를 다시 정리해보세요."
            }

        return AnalysisAttemptListItemUiModel(
            attemptNo = attempt.attemptNo,
            title = "${attempt.attemptNo}차 시도",
            subtitle = AnalysisFormatters.formatDuration(attempt.durationMs),
            holdLabel = "${attempt.maxHoldNo}번",
            holdSuffix = "/${maxHoldInChallenge}번",
            cruxLabel = attempt.cruxHoldNo?.let { "${it}번 홀드" } ?: "-",
            stabilityScore = stabilityScore,
            stabilityLabel = AnalysisFormatters.formatPercent(attempt.centerStabilityRatio),
            recoveryScore = attempt.stabilityRecoveryScore,
            recoveryLabel = attempt.stabilityRecoveryScore?.let { "${it}점" } ?: "-",
            lowerBodyDriveScore = attempt.lowerBodyDriveScore,
            lowerBodyDriveLabel = attempt.lowerBodyDriveScore?.let { "${it}점" } ?: "-",
            stableContactScore = attempt.stableContactRatio?.times(100f)?.roundToInt(),
            stableContactLabel = attempt.stableContactRatio?.let { AnalysisFormatters.formatPercent(it) } ?: "-",
            overallMovementScore = attempt.overallMovementScore,
            loadFocusLabel = attempt.loadFocusLabel,
            summaryLine = summaryLine,
            resultBadge = AnalysisBadgeUiModel(
                label = AnalysisFormatters.resultLabel(attempt.attemptResult),
                tone = AnalysisFormatters.resultTone(attempt.attemptResult)
            )
        )
    }

    private fun buildStrengths(
        challenge: AnalysisChallengeSnapshot,
        averageStabilityScore: Int,
        averageRecoveryScore: Int?,
        averageLowerBodyDriveScore: Int?
    ): List<String> {
        return buildList {
            if (challenge.challengeResult == AnalysisChallengeResult.SUCCESS) {
                add("여러 시도 끝에 완등까지 연결하며 전체 흐름을 끝까지 이어갔어요.")
            }
            if (averageStabilityScore >= 70) {
                add("시도 전반에서 중심을 비교적 안정적으로 유지했어요.")
            }
            if ((averageRecoveryScore ?: 0) >= 65) {
                add("흔들린 뒤에도 다시 자세를 회복하는 흐름이 좋았어요.")
            }
            if ((averageLowerBodyDriveScore ?: 0) >= 65) {
                add("다리로 밀어 올리는 사용 흐름이 비교적 안정적이었어요.")
            }
            if (isEmpty()) {
                add("시도마다 도달 구간이 넓어지며 문제 이해도가 조금씩 올라갔어요.")
            }
        }.take(2)
    }

    private fun buildImprovements(
        attempts: List<AnalysisAttemptSnapshot>,
        averageStabilityScore: Int,
        averageRecoveryScore: Int?,
        averageLowerBodyDriveScore: Int?,
        repeatedLoadFocus: String?
    ): List<String> {
        val failureReasons = attempts.mapNotNull { it.failureReason?.takeIf(String::isNotBlank) }
        val riskAlerts = attempts.mapNotNull { it.riskAlert?.takeIf(String::isNotBlank) }

        return buildList {
            if (averageStabilityScore < 65) {
                add("크럭스 구간에서는 발 위치를 먼저 고정해 중심이 무너지지 않도록 해보세요.")
            }
            if ((averageRecoveryScore ?: 100) < 60) {
                add("흔들린 뒤에는 쉬운 홀드에서 짧게 자세를 정리해 회복 시간을 확보해보세요.")
            }
            if ((averageLowerBodyDriveScore ?: 100) < 60) {
                add("팔로 버티기보다 다리로 밀어 올리는 타이밍을 더 분명하게 가져가보세요.")
            }
            if (repeatedLoadFocus?.contains("팔") == true || repeatedLoadFocus?.contains("손") == true) {
                add("팔과 손에 부담이 몰린 만큼, 쉬운 구간에서는 하체 지지와 곧은 팔 사용을 늘려보세요.")
            }
            if (failureReasons.any { it.contains("발") }) {
                add("발을 더 정확하게 올리는 연습으로 상지 부담을 줄여보세요.")
            }
            if (riskAlerts.any { it.contains("중심") || it.contains("흔들") }) {
                add("동작 전에 힙과 코어를 먼저 고정해 흔들림을 줄여보세요.")
            }
            if (isEmpty()) {
                add("다음 시도에서는 지금 유지한 흐름을 살리면서 크럭스 진입 타이밍만 더 다듬어보세요.")
            }
        }.take(4)
    }

    private fun List<Int>.averageOrNull(): Int? {
        return if (isEmpty()) null else average().roundToInt()
    }
}
