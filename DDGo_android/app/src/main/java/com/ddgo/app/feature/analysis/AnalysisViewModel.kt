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

    fun openChallengeDetailIfAvailable(challengeId: Long): Boolean {
        val challenge = challengeSnapshots.value.firstOrNull { it.id == challengeId } ?: return false
        selectedChallengeId.value = challenge.id
        selectedAttemptNo.value = challenge.attempts.lastOrNull()?.attemptNo ?: 0
        currentScreen.value = AnalysisScreenState.ChallengeDetail
        return true
    }

    fun openChallengeDetail(challengeId: Long) {
        openChallengeDetailIfAvailable(challengeId)
    }

    fun openAttemptDetail(attemptNo: Int) {
        val challenge = challengeSnapshots.value.firstOrNull { it.id == selectedChallengeId.value } ?: return
        if (challenge.attempts.none { it.attemptNo == attemptNo }) return
        selectedAttemptNo.value = attemptNo
        currentScreen.value = AnalysisScreenState.AttemptDetail
    }

    fun closeAttemptDetail() {
        currentScreen.value = AnalysisScreenState.ChallengeDetail
    }

    fun closeChallengeDetail() {
        currentScreen.value = AnalysisScreenState.Dashboard
    }

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
