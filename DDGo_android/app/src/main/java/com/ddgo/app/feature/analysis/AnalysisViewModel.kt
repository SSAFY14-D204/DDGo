package com.ddgo.app.feature.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.usecase.GetAnalysisSnapshotsUseCase
import com.ddgo.app.feature.analysis.mapper.AnalysisUiStateMapper
import com.ddgo.app.feature.analysis.model.AnalysisScreenState
import com.ddgo.app.feature.analysis.model.AnalysisUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 분석 화면의 상태를 관리하는 ViewModel입니다.
 *
 * 역할:
 * - UseCase를 통해 원본 분석 데이터를 가져오고, 화면 전환 상태와 함께 UI 모델로 변환합니다.
 * - feature가 Preview 데이터나 하드코딩 fixture를 직접 들고 있지 않도록 의존 경계를 지킵니다.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val getAnalysisSnapshotsUseCase: GetAnalysisSnapshotsUseCase
) : ViewModel() {

    private val challengeSnapshots = MutableStateFlow<List<AnalysisChallengeSnapshot>>(emptyList())
    private val selectedChallengeId = MutableStateFlow(0L)
    private val selectedAttemptNo = MutableStateFlow(0)
    private val currentScreen = MutableStateFlow(AnalysisScreenState.Dashboard)

    val uiState: StateFlow<AnalysisUiState> = combine(
        challengeSnapshots,
        selectedChallengeId,
        selectedAttemptNo,
        currentScreen
    ) { challenges, challengeId, attemptNo, screen ->
        if (challenges.isEmpty()) {
            AnalysisUiState.empty()
        } else {
            AnalysisUiStateMapper.create(
                challenges = challenges,
                selectedChallengeId = challengeId,
                selectedAttemptNo = attemptNo,
                currentScreen = screen
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AnalysisUiState.empty()
    )

    init {
        loadAnalysisSnapshots()
    }

    /** 선택한 챌린지 상세 화면으로 이동합니다. */
    fun openChallengeDetail(challengeId: Long) {
        val challenge = challengeSnapshots.value.firstOrNull { it.id == challengeId } ?: return
        selectedChallengeId.value = challenge.id
        selectedAttemptNo.value = challenge.attempts.lastOrNull()?.attemptNo ?: 0
        currentScreen.value = AnalysisScreenState.ChallengeDetail
    }

    /** 현재 선택된 챌린지 안에서 시도 상세 화면으로 이동합니다. */
    fun openAttemptDetail(attemptNo: Int) {
        val challenge = challengeSnapshots.value.firstOrNull { it.id == selectedChallengeId.value } ?: return
        if (challenge.attempts.none { it.attemptNo == attemptNo }) return
        selectedAttemptNo.value = attemptNo
        currentScreen.value = AnalysisScreenState.AttemptDetail
    }

    /** 시도 상세 화면을 닫고 챌린지 상세로 돌아갑니다. */
    fun closeAttemptDetail() {
        currentScreen.value = AnalysisScreenState.ChallengeDetail
    }

    /** 챌린지 상세 화면을 닫고 대시보드로 돌아갑니다. */
    fun closeChallengeDetail() {
        currentScreen.value = AnalysisScreenState.Dashboard
    }

    /**
     * 분석 원본 데이터를 불러옵니다.
     *
     * 역할:
     * - 첫 진입 시 기본 선택 챌린지와 기본 선택 시도를 함께 설정합니다.
     * - 현재는 mock 데이터지만, 이후 API 연동 시에도 같은 흐름을 그대로 사용할 수 있습니다.
     */
    private fun loadAnalysisSnapshots() {
        viewModelScope.launch {
            getAnalysisSnapshotsUseCase().onSuccess { snapshots ->
                challengeSnapshots.value = snapshots
                if (selectedChallengeId.value == 0L) {
                    snapshots.firstOrNull()?.let { challenge ->
                        selectedChallengeId.value = challenge.id
                        selectedAttemptNo.value = challenge.attempts.lastOrNull()?.attemptNo ?: 0
                    }
                }
            }
        }
    }
}
