package com.ddgo.app.feature.report

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Report 화면의 상태 관리 ViewModel.
 */
@HiltViewModel
class ReportViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    // TODO: ReportRepository 주입 후 실제 구현
    // fun loadReport(climbId: String) { ... }
}

sealed class ReportUiState {
    object Loading : ReportUiState()
    object Empty : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}
