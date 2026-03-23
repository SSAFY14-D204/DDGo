package com.ddgo.app.feature.auth

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.validation.AuthInputPolicy
import com.ddgo.app.core.validation.ValidationResult
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LoginUseCase
import com.ddgo.app.domain.usecase.RegisterUseCase
import com.ddgo.app.domain.usecase.SocialLoginUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NICKNAME_KEYWORD = "\uB2C9\uB124\uC784"
private const val TAG = "AuthViewModel"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val socialLoginUseCase: SocialLoginUseCase,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase
) : ViewModel() {

    private companion object {
        const val PROVISIONAL_NICKNAME_PREFIX = "DDGoUser"
        const val KAKAO_USERNAME_PREFIX = "kakao_"
        const val GOOGLE_USERNAME_PREFIX = "google_"
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var username by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    fun updateUsername(input: String) {
        username = input
        clearErrorState()
    }

    fun updatePassword(input: String) {
        password = input
        clearErrorState()
    }

    fun validateUsernameStep(): String? {
        return when (val validation = AuthInputPolicy.validateUsername(username)) {
            is ValidationResult.Invalid -> {
                setError(validation.message)
                validation.message
            }

            is ValidationResult.Valid -> {
                username = validation.value
                clearErrorState()
                null
            }
        }
    }

    fun canProceedWithUsername(): Boolean = username.trim().isNotBlank()

    fun canSubmitWithPassword(): Boolean = password.isNotBlank()

    fun login() {
        val normalizedUsername = when (val validation = AuthInputPolicy.validateUsername(username)) {
            is ValidationResult.Invalid -> {
                setError(validation.message)
                return
            }

            is ValidationResult.Valid -> validation.value
        }
        username = normalizedUsername

        if (AuthInputPolicy.validatePasswordPresence(password) is ValidationResult.Invalid) {
            setError(AuthStrings.PasswordRequired)
            return
        }

        viewModelScope.launch {
            clearErrorState()
            _uiState.value = AuthUiState.Loading

            loginUseCase(normalizedUsername, password)
                .onSuccess {
                    clearErrorState()
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { throwable ->
                    setError(throwable.message ?: AuthStrings.LoginFailed)
                }
        }
    }

    fun loginWithKakaoAccessToken(accessToken: String) {
        if (accessToken.isBlank()) {
            setError(AuthStrings.KakaoLoginFailed)
            return
        }

        viewModelScope.launch {
            clearErrorState()
            _uiState.value = AuthUiState.Loading

            socialLoginUseCase(
                provider = SocialLoginProvider.KAKAO,
                accessToken = accessToken
            )
                .onSuccess {
                    Log.d(TAG, "Kakao social login succeeded")
                    syncKakaoNicknameIfNeeded()
                    clearErrorState()
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Kakao social login failed: ${throwable.message}", throwable)
                    setError(throwable.message ?: AuthStrings.KakaoLoginFailed)
                }
        }
    }

    fun loginWithGoogleIdToken(idToken: String, displayName: String?) {
        if (idToken.isBlank()) {
            setError(AuthStrings.GoogleLoginFailed)
            return
        }

        viewModelScope.launch {
            clearErrorState()
            _uiState.value = AuthUiState.Loading

            socialLoginUseCase(
                provider = SocialLoginProvider.GOOGLE,
                idToken = idToken
            )
                .onSuccess {
                    Log.d(TAG, "Google social login succeeded")
                    syncGoogleNicknameIfNeeded(displayName)
                    clearErrorState()
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Google social login failed: ${throwable.message}", throwable)
                    setError(throwable.message ?: AuthStrings.GoogleLoginFailed)
                }
        }
    }

    fun register() {
        val normalizedUsername = when (val validation = AuthInputPolicy.validateUsername(username)) {
            is ValidationResult.Invalid -> {
                setError(validation.message)
                return
            }

            is ValidationResult.Valid -> validation.value
        }
        username = normalizedUsername

        val provisionalNickname = AuthInputPolicy.buildProvisionalNickname(normalizedUsername)
        when (
            val validation = AuthInputPolicy.validatePassword(
                rawPassword = password,
                normalizedUsername = normalizedUsername,
                nickname = provisionalNickname
            )
        ) {
            is ValidationResult.Invalid -> {
                setError(validation.message)
                return
            }

            is ValidationResult.Valid -> Unit
        }

        viewModelScope.launch {
            clearErrorState()
            _uiState.value = AuthUiState.Loading

            registerWithGeneratedNickname(normalizedUsername, password)
                .onSuccess {
                    loginUseCase(normalizedUsername, password)
                        .onSuccess {
                            clearErrorState()
                            _uiState.value = AuthUiState.Success
                        }
                        .onFailure { throwable ->
                            setError(throwable.message ?: AuthStrings.RegisterAutoLoginFailed)
                        }
                }
                .onFailure { throwable ->
                    setError(throwable.message ?: AuthStrings.RegisterFailed)
                }
        }
    }

    fun reportExternalLoginError(message: String) {
        setError(message)
    }

    fun resetUiState() {
        _uiState.value = AuthUiState.Idle
        errorMessage = null
    }

    fun clearErrorState() {
        errorMessage = null
        if (_uiState.value is AuthUiState.Error) {
            _uiState.value = AuthUiState.Idle
        }
    }

    private fun setError(message: String) {
        errorMessage = message
        _uiState.value = AuthUiState.Error(message)
    }

    private suspend fun registerWithGeneratedNickname(
        normalizedUsername: String,
        rawPassword: String
    ): Result<Unit> {
        repeat(3) {
            val provisionalNickname = AuthInputPolicy.buildProvisionalNickname(normalizedUsername)
            val result = registerUseCase(
                username = normalizedUsername,
                password = rawPassword,
                nickname = provisionalNickname
            )

            if (result.isSuccess) {
                return result
            }

            val message = result.exceptionOrNull()?.message.orEmpty()
            if (!message.contains(NICKNAME_KEYWORD)) {
                return result
            }
        }

        return Result.failure(Exception(AuthStrings.RegisterFailed))
    }

    private suspend fun syncKakaoNicknameIfNeeded() {
        val currentUser = getMyInfoUseCase().getOrNull() ?: return
        if (!shouldSyncKakaoNickname(currentUser)) return

        val kakaoNickname = loadKakaoProfile()
            .getOrNull()
            ?.nickname
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?: return

        updateNicknameUseCase(kakaoNickname)
    }

    private suspend fun syncGoogleNicknameIfNeeded(displayName: String?) {
        val googleNickname = displayName
            ?.trim()
            ?.takeUnless { it.isBlank() }
            ?: return

        val currentUser = getMyInfoUseCase().getOrNull() ?: return
        if (!shouldSyncGoogleNickname(currentUser)) return

        updateNicknameUseCase(googleNickname)
    }

    private fun shouldSyncKakaoNickname(user: User): Boolean {
        return user.username.startsWith(KAKAO_USERNAME_PREFIX) &&
            user.nickname.startsWith(PROVISIONAL_NICKNAME_PREFIX)
    }

    private fun shouldSyncGoogleNickname(user: User): Boolean {
        return user.username.startsWith(GOOGLE_USERNAME_PREFIX) &&
            user.nickname.startsWith(PROVISIONAL_NICKNAME_PREFIX)
    }
}

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
