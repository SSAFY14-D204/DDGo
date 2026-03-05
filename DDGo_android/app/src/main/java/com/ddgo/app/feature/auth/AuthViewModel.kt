package com.ddgo.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auth 화면의 상태 관리 ViewModel.
 *
 * UI 상태(UiState) 패턴:
 * - sealed class로 화면 상태를 명확히 정의합니다
 * - _uiState(내부 변경가능) / uiState(외부 읽기전용) 패턴을 사용합니다
 *
 * @HiltViewModel: Hilt가 ViewModel에 의존성을 주입합니다.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            authRepository.login(email, password)
                .onSuccess { _uiState.value = AuthUiState.Success }
                .onFailure { e -> _uiState.value = AuthUiState.Error(e.message ?: "로그인 실패") }
        }
    }
}

/** Auth 화면의 UI 상태 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
