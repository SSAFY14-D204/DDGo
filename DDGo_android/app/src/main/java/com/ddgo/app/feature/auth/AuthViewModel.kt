package com.ddgo.app.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.usecase.LoginUseCase
import com.ddgo.app.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 로그인/회원가입 화면에서 사용하는 인증 ViewModel입니다.
 *
 * 역할:
 * - 아이디/비밀번호 입력값을 보관합니다.
 * - 로그인과 회원가입 요청을 실행하고 결과를 [AuthUiState]로 전달합니다.
 * - 현재 회원가입 화면이 닉네임을 따로 받지 않는 제약을 ViewModel에서 한 번만 처리합니다.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    /** 로그인과 회원가입에 공통으로 사용하는 아이디 입력값입니다. */
    var username by mutableStateOf("")
        private set

    /** 로그인과 회원가입에 공통으로 사용하는 비밀번호 입력값입니다. */
    var password by mutableStateOf("")
        private set

    /** 아이디 입력값을 갱신합니다. */
    fun updateUsername(input: String) {
        username = input
    }

    /** 비밀번호 입력값을 갱신합니다. */
    fun updatePassword(input: String) {
        password = input
    }

    /** 현재 아이디 단계에서 다음으로 진행 가능한지 반환합니다. */
    fun canProceedWithUsername(): Boolean = username.trim().isNotBlank()

    /** 현재 비밀번호 단계에서 제출 가능한지 반환합니다. */
    fun canSubmitWithPassword(): Boolean = password.isNotBlank()

    /** 로그인 요청을 실행합니다. */
    fun login() {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank()) {
            _uiState.value = AuthUiState.Error(AuthStrings.UsernameRequired)
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error(AuthStrings.PasswordRequired)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            loginUseCase(normalizedUsername, password)
                .onSuccess {
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { throwable ->
                    _uiState.value = AuthUiState.Error(
                        throwable.message ?: AuthStrings.LoginFailed
                    )
                }
        }
    }

    /**
     * 회원가입을 실행합니다.
     *
     * 현재 백엔드는 닉네임 필드를 요구하지만,
     * 프론트 회원가입 단계에서는 별도 닉네임 입력을 받지 않으므로
     * 우선 아이디를 임시 닉네임으로 사용한 뒤 프로필에서 변경하도록 유도합니다.
     */
    fun register() {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isBlank()) {
            _uiState.value = AuthUiState.Error(AuthStrings.UsernameRequired)
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error(AuthStrings.PasswordRequired)
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading

            val provisionalNickname = normalizedUsername
            registerUseCase(normalizedUsername, password, provisionalNickname)
                .onSuccess {
                    loginUseCase(normalizedUsername, password)
                        .onSuccess {
                            _uiState.value = AuthUiState.Success
                        }
                        .onFailure { throwable ->
                            _uiState.value = AuthUiState.Error(
                                throwable.message ?: AuthStrings.RegisterAutoLoginFailed
                            )
                        }
                }
                .onFailure { throwable ->
                    _uiState.value = AuthUiState.Error(
                        throwable.message ?: AuthStrings.RegisterFailed
                    )
                }
        }
    }

    /** 화면에서 에러/성공 처리를 마친 뒤 상태를 초기화합니다. */
    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
    }
}

/** 인증 화면의 단순 비동기 상태입니다. */
sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
