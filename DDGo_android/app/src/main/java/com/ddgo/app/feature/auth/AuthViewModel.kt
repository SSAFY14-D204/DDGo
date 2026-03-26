package com.ddgo.app.feature.auth

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.core.validation.AuthInputPolicy
import com.ddgo.app.core.validation.ValidationResult
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.usecase.ConfirmPasswordResetUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LoginUseCase
import com.ddgo.app.domain.usecase.RegisterUseCase
import com.ddgo.app.domain.usecase.RequestPasswordResetUseCase
import com.ddgo.app.domain.usecase.SocialLoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AuthViewModel"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val onboardingPreferenceDataStore: OnboardingPreferenceDataStore,
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val socialLoginUseCase: SocialLoginUseCase,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val requestPasswordResetUseCase: RequestPasswordResetUseCase,
    private val confirmPasswordResetUseCase: ConfirmPasswordResetUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var username by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var passwordResetEmail by mutableStateOf("")
        private set

    var passwordResetTokenOrLink by mutableStateOf("")
        private set

    var passwordResetNewPassword by mutableStateOf("")
        private set

    var passwordResetConfirmPassword by mutableStateOf("")
        private set

    var lastPasswordResetRequestedEmail by mutableStateOf<String?>(null)
        private set

    var isRequestingPasswordReset by mutableStateOf(false)
        private set

    var isConfirmingPasswordReset by mutableStateOf(false)
        private set

    fun updateUsername(input: String) {
        username = input
        clearErrorState()
    }

    fun updatePassword(input: String) {
        password = input
        clearErrorState()
    }

    fun prepareLoginFlow() {
        username = ""
        password = ""
        clearPasswordResetState()
        resetUiState()
    }

    fun prepareRegisterFlow() {
        username = ""
        password = ""
        clearPasswordResetState()
        resetUiState()
    }

    fun markWelcomeSeen() {
        viewModelScope.launch {
            onboardingPreferenceDataStore.setWelcomeSeen()
        }
    }

    fun preparePasswordResetFlow() {
        preparePasswordResetFlow(tokenOrLink = null)
    }

    fun preparePasswordResetFlow(tokenOrLink: String?) {
        val prefilledEmail = username.trim()
        if (prefilledEmail.isNotBlank() && passwordResetEmail.isBlank()) {
            passwordResetEmail = prefilledEmail
        }

        passwordResetTokenOrLink = tokenOrLink?.trim().orEmpty()
        passwordResetNewPassword = ""
        passwordResetConfirmPassword = ""
        lastPasswordResetRequestedEmail = null
        isRequestingPasswordReset = false
        isConfirmingPasswordReset = false
        resetUiState()
    }

    fun updatePasswordResetEmail(input: String) {
        passwordResetEmail = input

        val normalizedInput = normalizeNullableEmail(input)
        if (lastPasswordResetRequestedEmail != null && normalizedInput != lastPasswordResetRequestedEmail) {
            lastPasswordResetRequestedEmail = null
            passwordResetTokenOrLink = ""
            passwordResetNewPassword = ""
            passwordResetConfirmPassword = ""
        }

        clearErrorState()
    }

    fun updatePasswordResetTokenOrLink(input: String) {
        passwordResetTokenOrLink = input
        clearErrorState()
    }

    fun updatePasswordResetNewPassword(input: String) {
        passwordResetNewPassword = input
        clearErrorState()
    }

    fun updatePasswordResetConfirmPassword(input: String) {
        passwordResetConfirmPassword = input
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
                .onSuccess { authToken ->
                    clearErrorState()
                    _uiState.value = AuthUiState.Success(resolveSuccessDestination(authToken))
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
                .onSuccess { authToken ->
                    Log.d(TAG, "Kakao social login succeeded")
                    clearErrorState()
                    _uiState.value = AuthUiState.Success(resolveSuccessDestination(authToken))
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Kakao social login failed: ${throwable.message}", throwable)
                    setError(throwable.message ?: AuthStrings.KakaoLoginFailed)
                }
        }
    }

    fun loginWithGoogleIdToken(idToken: String) {
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
                .onSuccess { authToken ->
                    Log.d(TAG, "Google social login succeeded")
                    clearErrorState()
                    _uiState.value = AuthUiState.Success(resolveSuccessDestination(authToken))
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

        when (
            val validation = AuthInputPolicy.validatePassword(
                rawPassword = password,
                normalizedUsername = normalizedUsername,
                nickname = null
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

            registerUseCase(normalizedUsername, password)
                .onSuccess {
                    loginUseCase(normalizedUsername, password)
                        .onSuccess { authToken ->
                            clearErrorState()
                            _uiState.value = AuthUiState.Success(
                                resolveSuccessDestination(
                                    authToken = authToken,
                                    forceProfileOnboarding = true
                                )
                            )
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

    fun requestPasswordReset() {
        val normalizedEmail = when (val validation = AuthInputPolicy.validateUsername(passwordResetEmail)) {
            is ValidationResult.Invalid -> {
                setError(validation.message)
                return
            }

            is ValidationResult.Valid -> validation.value
        }
        passwordResetEmail = normalizedEmail

        viewModelScope.launch {
            clearErrorState()
            isRequestingPasswordReset = true

            requestPasswordResetUseCase(normalizedEmail)
                .onSuccess {
                    username = normalizedEmail
                    lastPasswordResetRequestedEmail = normalizedEmail
                    passwordResetTokenOrLink = ""
                    passwordResetNewPassword = ""
                    passwordResetConfirmPassword = ""
                    clearErrorState()
                }
                .onFailure { throwable ->
                    setError(throwable.message ?: AuthStrings.PasswordResetRequestFailed)
                }

            isRequestingPasswordReset = false
        }
    }

    fun confirmPasswordReset() {
        val normalizedEmail = normalizeNullableEmail(passwordResetEmail)
        if (normalizedEmail != null) {
            passwordResetEmail = normalizedEmail
        }

        val resolvedToken = extractPasswordResetToken(passwordResetTokenOrLink)
        if (resolvedToken.isBlank()) {
            setError(AuthStrings.PasswordResetTokenRequired)
            return
        }

        when (
            val validation = AuthInputPolicy.validatePassword(
                rawPassword = passwordResetNewPassword,
                normalizedUsername = normalizedEmail.orEmpty(),
                nickname = null
            )
        ) {
            is ValidationResult.Invalid -> {
                setError(validation.message)
                return
            }

            is ValidationResult.Valid -> Unit
        }

        if (passwordResetConfirmPassword.isBlank()) {
            setError(AuthStrings.PasswordResetConfirmRequired)
            return
        }

        if (passwordResetNewPassword != passwordResetConfirmPassword) {
            setError(AuthStrings.PasswordResetPasswordMismatch)
            return
        }

        viewModelScope.launch {
            clearErrorState()
            isConfirmingPasswordReset = true

            confirmPasswordResetUseCase(
                token = resolvedToken,
                newPassword = passwordResetNewPassword
            ).onSuccess {
                normalizedEmail?.let { username = it }
                password = ""
                passwordResetTokenOrLink = ""
                passwordResetNewPassword = ""
                passwordResetConfirmPassword = ""
                clearErrorState()
                _uiState.value = AuthUiState.PasswordResetCompleted
            }.onFailure { throwable ->
                setError(throwable.message ?: AuthStrings.PasswordResetConfirmFailed)
            }

            isConfirmingPasswordReset = false
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

    fun consumePasswordResetCompletion() {
        lastPasswordResetRequestedEmail = null
        resetUiState()
    }

    private fun clearPasswordResetState() {
        passwordResetEmail = ""
        passwordResetTokenOrLink = ""
        passwordResetNewPassword = ""
        passwordResetConfirmPassword = ""
        lastPasswordResetRequestedEmail = null
        isRequestingPasswordReset = false
        isConfirmingPasswordReset = false
    }

    private fun setError(message: String) {
        errorMessage = message
        _uiState.value = AuthUiState.Error(message)
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun normalizeNullableEmail(email: String): String? {
        return email.trim()
            .takeIf { it.isNotBlank() }
            ?.let(::normalizeEmail)
    }

    private fun extractPasswordResetToken(rawInput: String): String {
        val trimmedInput = rawInput.trim()
        if (trimmedInput.isBlank()) {
            return ""
        }

        return runCatching {
            Uri.parse(trimmedInput).getQueryParameter("token")
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: trimmedInput
    }

    private suspend fun resolveSuccessDestination(
        authToken: AuthToken,
        forceProfileOnboarding: Boolean = false
    ): AuthSuccessDestination {
        val hasCompletedIntroOnboarding = onboardingPreferenceDataStore.hasCompletedOnboarding.first()
        val shouldOpenOnboarding = forceProfileOnboarding ||
            authToken.needsOnboarding == true ||
            authToken.isNewUser == true

        if (shouldOpenOnboarding) {
            return if (hasCompletedIntroOnboarding) {
                AuthSuccessDestination.ProfileOnboarding
            } else {
                AuthSuccessDestination.FullOnboarding
            }
        }

        val currentUser = getMyInfoUseCase().getOrNull()
        return when {
            !hasCompletedIntroOnboarding -> AuthSuccessDestination.FullOnboarding
            currentUser != null && currentUser.requiresProfileOnboarding() ->
                AuthSuccessDestination.ProfileOnboarding
            else -> AuthSuccessDestination.Main
        }
    }
}

private fun User.requiresProfileOnboarding(): Boolean {
    return sex.isNullOrBlank() ||
        heightCm == null ||
        heightCm <= 0f ||
        weightKg == null ||
        weightKg <= 0f ||
        wingspanCm == null ||
        wingspanCm <= 0f
}

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Success(val destination: AuthSuccessDestination) : AuthUiState()
    data object PasswordResetCompleted : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

enum class AuthSuccessDestination {
    Main,
    ProfileOnboarding,
    FullOnboarding
}
