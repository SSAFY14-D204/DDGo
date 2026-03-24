package com.ddgo.app.feature.auth

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.validation.AuthInputPolicy
import com.ddgo.app.core.validation.ValidationResult
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.usecase.CheckUsernameAvailabilityUseCase
import com.ddgo.app.domain.usecase.ConfirmPasswordResetUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LoginUseCase
import com.ddgo.app.domain.usecase.RegisterUseCase
import com.ddgo.app.domain.usecase.RequestPasswordResetUseCase
import com.ddgo.app.domain.usecase.SocialLoginUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NICKNAME_KEYWORD = "닉네임"
private const val USERNAME_KEYWORD = "아이디"
private const val EMAIL_KEYWORD = "이메일"
private const val TAG = "AuthViewModel"
private const val LOGIN_USERNAME_CHECK_DEBOUNCE_MS = 120L
private const val USERNAME_CHECK_DEBOUNCE_MS = 350L

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
    private val socialLoginUseCase: SocialLoginUseCase,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val requestPasswordResetUseCase: RequestPasswordResetUseCase,
    private val confirmPasswordResetUseCase: ConfirmPasswordResetUseCase,
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase
) : ViewModel() {

    private companion object {
        const val PROVISIONAL_NICKNAME_PREFIX = "DDGoUser"
        const val KAKAO_USERNAME_PREFIX = "kakao_"
        const val GOOGLE_USERNAME_PREFIX = "google_"
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private var loginUsernameCheckJob: Job? = null
    private var registerUsernameCheckJob: Job? = null
    private var lastCheckedLoginUsername: String? = null
    private var isLoginUsernameVerified: Boolean = false
    private var lastCheckedRegisterUsername: String? = null

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var username by mutableStateOf("")
        private set

    var password by mutableStateOf("")
        private set

    var loginUsernameFeedback by mutableStateOf<AuthFieldFeedback?>(null)
        private set

    var loginPasswordFeedback by mutableStateOf<AuthFieldFeedback?>(null)
        private set

    var isCheckingLoginUsername by mutableStateOf(false)
        private set

    var registerUsernameFeedback by mutableStateOf<AuthFieldFeedback?>(null)
        private set

    var registerPasswordFeedback by mutableStateOf<AuthFieldFeedback?>(null)
        private set

    var isCheckingRegisterUsername by mutableStateOf(false)
        private set

    var isRegisterUsernameAvailable by mutableStateOf(false)
        private set

    var isRegisterPasswordValid by mutableStateOf(false)
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

    fun updateUsername(input: String) = updateLoginUsername(input)

    fun updatePassword(input: String) = updateLoginPassword(input)

    fun updateLoginUsername(input: String) {
        cancelLoginUsernameCheck()
        username = input
        lastCheckedLoginUsername = null
        isLoginUsernameVerified = false
        isCheckingLoginUsername = false
        loginUsernameFeedback = buildLoginUsernameFeedback(
            rawInput = input,
            showRequired = false
        )
        loginPasswordFeedback = null
        clearErrorState()
    }

    fun updateLoginPassword(input: String) {
        password = input
        loginPasswordFeedback = null
        clearErrorState()
    }

    fun updateRegisterUsername(input: String) {
        username = input
        clearErrorState()
        evaluateRegisterUsernameFeedback(showRequired = false)
        refreshRegisterPasswordFeedback(showRequired = false)
    }

    fun updateRegisterPassword(input: String) {
        password = input
        clearErrorState()
        evaluateRegisterPasswordFeedback(showRequired = false)
    }

    fun refreshLoginUsernameFeedback(showRequired: Boolean = false) {
        loginUsernameFeedback = buildLoginUsernameFeedback(
            rawInput = username,
            showRequired = showRequired
        )
    }

    fun refreshRegisterUsernameFeedback(showRequired: Boolean = false) {
        evaluateRegisterUsernameFeedback(showRequired = showRequired)
    }

    fun refreshRegisterPasswordFeedback(showRequired: Boolean = false) {
        evaluateRegisterPasswordFeedback(showRequired = showRequired)
    }

    fun prepareLoginFlow() {
        resetCredentialInputs()
        clearPasswordResetState()
        clearAuthFieldFeedbackState()
        resetUiState()
    }

    fun prepareRegisterFlow() {
        resetCredentialInputs()
        clearPasswordResetState()
        clearAuthFieldFeedbackState()
        resetUiState()
    }

    fun preparePasswordResetFlow() {
        preparePasswordResetFlow(tokenOrLink = null)
    }

    fun preparePasswordResetFlow(tokenOrLink: String?) {
        val prefilledEmail = username.trim()
        if (prefilledEmail.isNotBlank() && passwordResetEmail.isBlank()) {
            passwordResetEmail = prefilledEmail
        }

        clearAuthFieldFeedbackState()
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

    fun validateUsernameStep(): String? = validateLoginUsernameStep()

    fun validateLoginUsernameStep(): String? {
        val feedback = buildLoginUsernameFeedback(
            rawInput = username,
            showRequired = true
        )
        loginUsernameFeedback = feedback

        return if (feedback == null) {
            username = AuthInputPolicy.normalizeUsername(username)
            null
        } else {
            feedback.message
        }
    }

    fun submitLoginUsername(onVerified: () -> Unit) {
        val localValidationMessage = validateLoginUsernameStep()
        if (localValidationMessage != null) {
            return
        }

        val normalizedUsername = AuthInputPolicy.normalizeUsername(username)
        username = normalizedUsername

        if (lastCheckedLoginUsername == normalizedUsername && isLoginUsernameVerified) {
            clearErrorState()
            onVerified()
            return
        }

        cancelLoginUsernameCheck()
        isCheckingLoginUsername = true
        loginUsernameFeedback = neutralFeedback(AuthStrings.LoginUsernameChecking)

        loginUsernameCheckJob = viewModelScope.launch {
            delay(LOGIN_USERNAME_CHECK_DEBOUNCE_MS)

            checkUsernameAvailabilityUseCase(normalizedUsername)
                .onSuccess { available ->
                    if (normalizeNullableEmail(username) != normalizedUsername) return@launch

                    isCheckingLoginUsername = false

                    // check-username API는 "회원가입 가능 여부"를 주므로,
                    // 로그인 단계에서는 available=true 를 "가입된 아이디 없음"으로 해석한다.
                    if (available) {
                        lastCheckedLoginUsername = null
                        isLoginUsernameVerified = false
                        loginUsernameFeedback = errorFeedback(AuthStrings.LoginUsernameNotFound)
                        return@launch
                    }

                    lastCheckedLoginUsername = normalizedUsername
                    isLoginUsernameVerified = true
                    loginUsernameFeedback = null
                    clearErrorState()
                    onVerified()
                }
                .onFailure { throwable ->
                    if (normalizeNullableEmail(username) != normalizedUsername) return@launch

                    lastCheckedLoginUsername = null
                    isLoginUsernameVerified = false
                    isCheckingLoginUsername = false
                    loginUsernameFeedback = errorFeedback(
                        throwable.message ?: AuthStrings.LoginUsernameCheckFailed
                    )
                }
        }
    }

    fun validateRegisterUsernameStep(): String? {
        val validation = AuthInputPolicy.validateUsername(username)
        if (validation is ValidationResult.Invalid) {
            cancelRegisterUsernameCheck()
            isRegisterUsernameAvailable = false
            registerUsernameFeedback = errorFeedback(validation.message)
            return validation.message
        }

        val normalizedUsername = (validation as ValidationResult.Valid).value
        username = normalizedUsername

        if (isCheckingRegisterUsername) {
            registerUsernameFeedback = neutralFeedback(AuthStrings.UsernameAvailabilityChecking)
            return AuthStrings.UsernameAvailabilityChecking
        }

        if (!isRegisterUsernameAvailable || lastCheckedRegisterUsername != normalizedUsername) {
            val message = when {
                registerUsernameFeedback?.tone == AuthFieldFeedbackTone.Error ->
                    registerUsernameFeedback?.message
                else -> AuthStrings.UsernameAvailabilityPending
            } ?: AuthStrings.UsernameAvailabilityPending

            registerUsernameFeedback = errorFeedback(message)
            return message
        }

        clearErrorState()
        return null
    }

    fun canProceedWithUsername(): Boolean = canProceedWithLoginUsername()

    fun canProceedWithLoginUsername(): Boolean {
        return AuthInputPolicy.validateUsername(username) is ValidationResult.Valid
    }

    fun canProceedWithRegisterUsername(): Boolean {
        return AuthInputPolicy.validateUsername(username) is ValidationResult.Valid &&
            isRegisterUsernameAvailable &&
            !isCheckingRegisterUsername
    }

    fun canSubmitWithPassword(): Boolean = canSubmitLoginWithPassword()

    fun canSubmitLoginWithPassword(): Boolean = password.isNotBlank()

    fun canSubmitRegistration(): Boolean {
        return canProceedWithRegisterUsername() && isRegisterPasswordValid
    }

    fun login() {
        val usernameMessage = validateLoginUsernameStep()
        val passwordMessage = validateLoginPassword(showRequired = true)
        if (usernameMessage != null || passwordMessage != null) {
            return
        }

        val normalizedUsername = AuthInputPolicy.normalizeUsername(username)
        username = normalizedUsername

        viewModelScope.launch {
            clearErrorState()
            loginPasswordFeedback = null
            _uiState.value = AuthUiState.Loading

            loginUseCase(normalizedUsername, password)
                .onSuccess {
                    clearAuthFieldFeedbackState()
                    clearErrorState()
                    _uiState.value = AuthUiState.Success
                }
                .onFailure { throwable ->
                    _uiState.value = AuthUiState.Idle
                    loginPasswordFeedback = errorFeedback(
                        throwable.message ?: AuthStrings.LoginFailed
                    )
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
                    clearAuthFieldFeedbackState()
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
                    clearAuthFieldFeedbackState()
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
        val usernameMessage = validateRegisterUsernameStep()
        val passwordMessage = evaluateRegisterPasswordFeedback(showRequired = true)
        if (usernameMessage != null || passwordMessage != null) {
            return
        }

        val normalizedUsername = AuthInputPolicy.normalizeUsername(username)
        username = normalizedUsername

        viewModelScope.launch {
            clearErrorState()
            _uiState.value = AuthUiState.Loading

            registerWithGeneratedNickname(normalizedUsername, password)
                .onSuccess {
                    loginUseCase(normalizedUsername, password)
                        .onSuccess {
                            clearAuthFieldFeedbackState()
                            clearErrorState()
                            _uiState.value = AuthUiState.Success
                        }
                        .onFailure { throwable ->
                            _uiState.value = AuthUiState.Idle
                            registerPasswordFeedback = errorFeedback(
                                throwable.message ?: AuthStrings.RegisterAutoLoginFailed
                            )
                        }
                }
                .onFailure { throwable ->
                    _uiState.value = AuthUiState.Idle
                    applyRegisterFailureFeedback(
                        throwable.message ?: AuthStrings.RegisterFailed
                    )
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

    private fun validateLoginPassword(showRequired: Boolean): String? {
        if (password.isBlank()) {
            loginPasswordFeedback = if (showRequired) {
                errorFeedback(AuthStrings.PasswordRequired)
            } else {
                null
            }
            return loginPasswordFeedback?.message
        }

        loginPasswordFeedback = null
        return null
    }

    // 회원가입 이메일은 입력이 잠시 멈췄을 때만 서버에 물어보도록 디바운스를 둡니다.
    private fun evaluateRegisterUsernameFeedback(showRequired: Boolean) {
        cancelRegisterUsernameCheck()

        if (username.isBlank()) {
            registerUsernameFeedback = if (showRequired) {
                errorFeedback(AuthStrings.UsernameRequired)
            } else {
                null
            }
            isRegisterUsernameAvailable = false
            lastCheckedRegisterUsername = null
            return
        }

        when (val validation = AuthInputPolicy.validateUsername(username)) {
            is ValidationResult.Invalid -> {
                registerUsernameFeedback = errorFeedback(validation.message)
                isRegisterUsernameAvailable = false
                lastCheckedRegisterUsername = null
            }

            is ValidationResult.Valid -> {
                val normalizedUsername = validation.value
                val canUseCachedResult =
                    lastCheckedRegisterUsername == normalizedUsername &&
                        registerUsernameFeedback?.tone != AuthFieldFeedbackTone.Neutral

                if (canUseCachedResult) {
                    isRegisterUsernameAvailable =
                        registerUsernameFeedback?.tone == AuthFieldFeedbackTone.Success
                    return
                }

                registerUsernameFeedback =
                    neutralFeedback(AuthStrings.UsernameAvailabilityChecking)
                isRegisterUsernameAvailable = false
                isCheckingRegisterUsername = true

                registerUsernameCheckJob = viewModelScope.launch {
                    delay(USERNAME_CHECK_DEBOUNCE_MS)

                    checkUsernameAvailabilityUseCase(normalizedUsername)
                        .onSuccess { available ->
                            // 오래된 응답이 현재 입력값을 덮어쓰지 않도록 마지막 입력과 비교합니다.
                            if (normalizeNullableEmail(username) != normalizedUsername) return@launch

                            lastCheckedRegisterUsername = normalizedUsername
                            isCheckingRegisterUsername = false
                            isRegisterUsernameAvailable = available
                            registerUsernameFeedback = if (available) {
                                successFeedback(AuthStrings.UsernameAvailable)
                            } else {
                                errorFeedback(AuthStrings.UsernameUnavailable)
                            }
                        }
                        .onFailure { throwable ->
                            if (normalizeNullableEmail(username) != normalizedUsername) return@launch

                            lastCheckedRegisterUsername = null
                            isCheckingRegisterUsername = false
                            isRegisterUsernameAvailable = false
                            registerUsernameFeedback = errorFeedback(
                                throwable.message ?: AuthStrings.UsernameAvailabilityCheckFailed
                            )
                        }
                }
            }
        }
    }

    private fun evaluateRegisterPasswordFeedback(showRequired: Boolean): String? {
        if (password.isBlank()) {
            isRegisterPasswordValid = false
            registerPasswordFeedback = if (showRequired) {
                errorFeedback(AuthStrings.PasswordRequired)
            } else {
                null
            }
            return registerPasswordFeedback?.message
        }

        val normalizedUsername = normalizeNullableEmail(username).orEmpty()
        val provisionalNickname = if (normalizedUsername.isBlank()) {
            null
        } else {
            AuthInputPolicy.buildProvisionalNickname(normalizedUsername)
        }

        return when (
            val validation = AuthInputPolicy.validatePassword(
                rawPassword = password,
                normalizedUsername = normalizedUsername,
                nickname = provisionalNickname
            )
        ) {
            is ValidationResult.Invalid -> {
                isRegisterPasswordValid = false
                registerPasswordFeedback = errorFeedback(validation.message)
                validation.message
            }

            is ValidationResult.Valid -> {
                isRegisterPasswordValid = true
                registerPasswordFeedback = successFeedback(AuthStrings.RegisterPasswordValid)
                null
            }
        }
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

    // 회원가입 실패 메시지를 어느 입력칸에 보여줄지 분리합니다.
    private fun applyRegisterFailureFeedback(message: String) {
        when {
            message.contains(USERNAME_KEYWORD) || message.contains(EMAIL_KEYWORD) -> {
                cancelRegisterUsernameCheck()
                isRegisterUsernameAvailable = false
                lastCheckedRegisterUsername = null
                registerUsernameFeedback = errorFeedback(message)
            }

            else -> {
                registerPasswordFeedback = errorFeedback(message)
            }
        }
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

    private fun setError(message: String) {
        errorMessage = message
        _uiState.value = AuthUiState.Error(message)
    }

    private fun cancelRegisterUsernameCheck() {
        registerUsernameCheckJob?.cancel()
        registerUsernameCheckJob = null
        isCheckingRegisterUsername = false
    }

    private fun cancelLoginUsernameCheck() {
        loginUsernameCheckJob?.cancel()
        loginUsernameCheckJob = null
        isCheckingLoginUsername = false
    }

    private fun clearAuthFieldFeedbackState() {
        cancelRegisterUsernameCheck()
        clearLoginFieldFeedbackState()
        clearRegisterFieldFeedbackState()
    }

    private fun clearLoginFieldFeedbackState() {
        cancelLoginUsernameCheck()
        loginUsernameFeedback = null
        loginPasswordFeedback = null
        lastCheckedLoginUsername = null
        isLoginUsernameVerified = false
    }

    private fun clearRegisterFieldFeedbackState() {
        registerUsernameFeedback = null
        registerPasswordFeedback = null
        isRegisterUsernameAvailable = false
        isRegisterPasswordValid = false
        lastCheckedRegisterUsername = null
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

    private fun resetCredentialInputs() {
        username = ""
        password = ""
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

    private fun buildLoginUsernameFeedback(
        rawInput: String,
        showRequired: Boolean
    ): AuthFieldFeedback? {
        if (rawInput.isBlank()) {
            return if (showRequired) {
                errorFeedback(AuthStrings.UsernameRequired)
            } else {
                null
            }
        }

        return when (val validation = AuthInputPolicy.validateUsername(rawInput)) {
            is ValidationResult.Invalid -> errorFeedback(validation.message)
            is ValidationResult.Valid -> null
        }
    }

    private fun neutralFeedback(message: String): AuthFieldFeedback {
        return AuthFieldFeedback(
            message = message,
            tone = AuthFieldFeedbackTone.Neutral
        )
    }

    private fun successFeedback(message: String): AuthFieldFeedback {
        return AuthFieldFeedback(
            message = message,
            tone = AuthFieldFeedbackTone.Success
        )
    }

    private fun errorFeedback(message: String): AuthFieldFeedback {
        return AuthFieldFeedback(
            message = message,
            tone = AuthFieldFeedbackTone.Error
        )
    }
}

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data object Success : AuthUiState()
    data object PasswordResetCompleted : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
