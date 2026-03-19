package com.ddgo.app.feature.analysis

import com.ddgo.app.domain.mock.AnalysisMockFixtures
import com.ddgo.app.feature.analysis.mapper.AnalysisUiStateMapper
import com.ddgo.app.feature.analysis.model.AnalysisScreenState
import com.ddgo.app.feature.analysis.model.AnalysisUiState

/**
 * Compose Preview에서 사용할 분석 화면 기본 상태를 만들어주는 도우미입니다.
 *
 * 역할:
 * - 실제 런타임 ViewModel은 UseCase를 사용하고, Preview만 별도로 같은 fixture를 재사용합니다.
 * - 미리보기와 런타임이 같은 mapper를 쓰도록 해 화면 계약이 따로 놀지 않게 합니다.
 */
internal object AnalysisPreviewData {

    /** 대시보드 프리뷰에 사용할 기본 UI 상태를 생성합니다. */
    fun defaultUiState(): AnalysisUiState {
        val challenges = AnalysisMockFixtures.challengeSnapshots
        val challenge = challenges.first()
        val attempt = challenge.attempts.last()
        return AnalysisUiStateMapper.create(
            challenges = challenges,
            selectedChallengeId = challenge.id,
            selectedAttemptNo = attempt.attemptNo,
            currentScreen = AnalysisScreenState.Dashboard
        )
    }
}
