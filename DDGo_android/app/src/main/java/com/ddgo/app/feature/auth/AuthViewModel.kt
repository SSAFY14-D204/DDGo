package com.ddgo.app.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.usecase.LoginUseCase
import com.ddgo.app.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase
):ViewModel(){
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    var username by mutableStateOf("")
    var password by mutableStateOf("")

    fun login() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            loginUseCase(username, password).onSuccess {
                _uiState.value = AuthUiState.Success
            }.onFailure {
                _uiState.value = AuthUiState.Error(it.message ?: "로그인 실패")
            }
        }
    }

    fun register() {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            // 개발 단계 편의를 위해 @ 검증/파싱 없이 그대로 닉네임으로 사용
            val nickname = username
            registerUseCase(username, password, nickname).onSuccess {
                // 회원가입 성공 후 즉시 로그인 시도
                loginUseCase(username, password).onSuccess {
                    _uiState.value = AuthUiState.Success
                }.onFailure {
                    _uiState.value = AuthUiState.Error(it.message ?: "회원가입은 성공했으나 로그인에 실패했습니다.")
                }
            }.onFailure {
                _uiState.value = AuthUiState.Error(it.message ?: "회원가입 실패")
            }
        }
    }

    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
    }
}

sealed class AuthUiState{
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}