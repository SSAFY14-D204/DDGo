package com.ddgo.app.feature.analysis.mapper

import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.feature.analysis.AnalysisStrings
import com.ddgo.app.feature.analysis.model.AnalysisScreenState
import com.ddgo.app.feature.analysis.model.AnalysisUiState

/**
 * 분석 화면 전체 UI 상태를 조립하는 얇은 coordinator mapper입니다.
 *
 * 역할:
 * - 실제 계산은 대시보드/챌린지 상세/시도 상세 mapper에 위임하고, 여기서는 화면 조합만 담당합니다.
 * - 한 파일에 모든 분석 계산이 몰리지 않도록 진입점만 남겨 구조를 단순하게 유지합니다.
 */
internal object AnalysisUiStateMapper {

    fun create(
        challenges: List<AnalysisChallengeSnapshot>,
        selectedChallengeId: Long,
        selectedAttemptNo: Int,
        currentScreen: AnalysisScreenState
    ): AnalysisUiState {
        if (challenges.isEmpty()) return AnalysisUiState.empty()

        val orderedChallenges = challenges.sortedByDescending { it.startedAt }
        val selectedChallenge = orderedChallenges.firstOrNull { it.id == selectedChallengeId } ?: orderedChallenges.first()
        val selectedAttempt = selectedChallenge.attempts
            .firstOrNull { it.attemptNo == selectedAttemptNo }
            ?: selectedChallenge.attempts.last()

        return AnalysisUiState(
            title = AnalysisStrings.ScreenTitle,
            currentScreen = currentScreen,
            growthSummary = AnalysisDashboardUiMapper.buildGrowthSummary(orderedChallenges),
            challenges = AnalysisDashboardUiMapper.buildChallengeList(orderedChallenges),
            challengeDetail = if (
                currentScreen == AnalysisScreenState.ChallengeDetail ||
                currentScreen == AnalysisScreenState.AttemptDetail
            ) {
                AnalysisChallengeDetailUiMapper.build(selectedChallenge)
            } else {
                null
            },
            attemptDetail = if (currentScreen == AnalysisScreenState.AttemptDetail) {
                AnalysisAttemptDetailUiMapper.build(
                    challenge = selectedChallenge,
                    attempt = selectedAttempt
                )
            } else {
                null
            }
        )
    }
}
