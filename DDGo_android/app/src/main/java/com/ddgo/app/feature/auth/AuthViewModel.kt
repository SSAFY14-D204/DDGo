package com.ddgo.app.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
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
}

sealed class AuthUiState{
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}