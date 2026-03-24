package com.ddgo.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.validation.AuthInputPolicy
import com.ddgo.app.core.validation.ValidationResult
import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.usecase.CheckNicknameAvailabilityUseCase
import com.ddgo.app.domain.usecase.DeleteMeUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LogoutUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import com.ddgo.app.domain.usecase.UpdatePasswordUseCase
import com.ddgo.app.domain.usecase.UpdateProfileUseCase
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfileFieldFeedback
import com.ddgo.app.feature.profile.model.ProfileFieldFeedbackTone
import com.ddgo.app.feature.profile.model.ProfilePasswordEditorUiState
import com.ddgo.app.feature.profile.model.ProfileSexOption
import com.ddgo.app.feature.profile.model.ProfileUiEvent
import com.ddgo.app.feature.profile.model.ProfileUiState
import com.ddgo.app.feature.profile.state.ProfileFeatureState
import com.ddgo.app.feature.profile.state.ProfileInputValidator
import com.ddgo.app.feature.profile.state.ProfileValidation
import com.ddgo.app.feature.profile.state.ValidatedBodyProfile
import com.ddgo.app.feature.profile.state.ValidatedPasswordChange
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val NICKNAME_CHECK_DEBOUNCE_MS = 350L

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val updatePasswordUseCase: UpdatePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deleteMeUseCase: DeleteMeUseCase,
    private val checkNicknameAvailabilityUseCase: CheckNicknameAvailabilityUseCase
) : ViewModel() {

    private var featureState = ProfileFeatureState()
    private val _uiState = MutableStateFlow(featureState.toUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<ProfileUiEvent>()
    val uiEvent: SharedFlow<ProfileUiEvent> = _uiEvent.asSharedFlow()

    private var nicknameAvailabilityJob: Job? = null
    private var lastCheckedNickname: String? = null
    private var lastCheckedNicknameAvailable: Boolean = false

    init {
        loadMyInfo()
    }

    fun onActionClick(actionType: ProfileActionType) {
        when (actionType) {
            ProfileActionType.EditNickname -> openNicknameEditor()
            ProfileActionType.EditBodyProfile -> openBodyProfileEditor()
            ProfileActionType.ChangePassword -> openPasswordEditor()
            ProfileActionType.Logout -> logout()
            ProfileActionType.DeleteAccount -> deleteAccount()
        }
    }

    fun openNicknameEditor() {
        resetNicknameAvailabilityState()
        updateState { it.openNicknameEditor() }
        refreshNicknameFeedback()
    }

    fun dismissNicknameEditor() {
        cancelNicknameAvailabilityCheck()
        updateState { it.closeNicknameEditor() }
    }

    fun updateNicknameInput(input: String) {
        updateState { it.updateNicknameInput(input) }
        refreshNicknameFeedback()
    }

    fun submitNickname() {
        val editor = featureState.nicknameEditor ?: return
        if (editor.isSaving) return

        val validation = ProfileInputValidator.validateNickname(
            rawInput = editor.nicknameInput,
            currentNickname = featureState.resolveNickname()
        )

        when (validation) {
            is ProfileValidation.Invalid -> {
                resetNicknameAvailabilityState()
                updateState {
                    it.updateNicknameFeedback(
                        feedback = errorFeedback(validation.message),
                        isCheckingAvailability = false,
                        isNicknameAvailable = false
                    )
                }
            }

            is ProfileValidation.Valid -> {
                val nickname = validation.value

                if (editor.isCheckingAvailability) {
                    updateState {
                        it.updateNicknameFeedback(
                            feedback = neutralFeedback(ProfileStrings.NicknameChecking),
                            isCheckingAvailability = true,
                            isNicknameAvailable = false
                        )
                    }
                    return
                }

                if (!editor.isNicknameAvailable || lastCheckedNickname != nickname) {
                    updateState {
                        it.updateNicknameFeedback(
                            feedback = errorFeedback(ProfileStrings.NicknameUnavailable),
                            isCheckingAvailability = false,
                            isNicknameAvailable = false
                        )
                    }
                    return
                }

                submitNicknameInternal(nickname)
            }
        }
    }

    fun openBodyProfileEditor() {
        updateState { it.openBodyProfileEditor() }
    }

    fun dismissBodyProfileEditor() {
        updateState { it.closeBodyProfileEditor() }
    }

    fun updateBodyProfileSex(option: ProfileSexOption) {
        updateState { state ->
            state.updateBodyProfileEditor { editor -> editor.copy(sex = option) }
        }
    }

    fun updateHeightInput(input: String) {
        updateBodyProfileInput { editor ->
            editor.copy(heightCmInput = ProfileInputValidator.sanitizeNumberInput(input))
        }
    }

    fun updateWeightInput(input: String) {
        updateBodyProfileInput { editor ->
            editor.copy(weightKgInput = ProfileInputValidator.sanitizeNumberInput(input))
        }
    }

    fun updateWingspanInput(input: String) {
        updateBodyProfileInput { editor ->
            editor.copy(wingspanCmInput = ProfileInputValidator.sanitizeNumberInput(input))
        }
    }

    fun submitBodyProfile() {
        val editor = featureState.bodyProfileEditor ?: return
        if (editor.isSaving) return

        when (val validation = ProfileInputValidator.validateBodyProfile(editor)) {
            is ProfileValidation.Invalid -> updateState {
                it.showBodyProfileEditorError(validation.message)
            }

            is ProfileValidation.Valid -> submitBodyProfileInternal(
                originalEditor = editor,
                validated = validation.value
            )
        }
    }

    fun openPasswordEditor() {
        updateState { it.openPasswordEditor() }
        refreshPasswordFeedbacks()
    }

    fun dismissPasswordEditor() {
        updateState { it.closePasswordEditor() }
    }

    fun updateCurrentPasswordInput(input: String) {
        updatePasswordInput { editor -> editor.copy(currentPasswordInput = input) }
        refreshPasswordFeedbacks()
    }

    fun updateNewPasswordInput(input: String) {
        updatePasswordInput { editor -> editor.copy(newPasswordInput = input) }
        refreshPasswordFeedbacks()
    }

    fun updateConfirmPasswordInput(input: String) {
        updatePasswordInput { editor -> editor.copy(confirmPasswordInput = input) }
        refreshPasswordFeedbacks()
    }

    fun submitPasswordChange() {
        val editor = featureState.passwordEditor ?: return
        if (editor.isSaving) return

        when (
            val validation = ProfileInputValidator.validatePasswordChange(
                editor = editor,
                currentUsername = featureState.currentUser?.username,
                currentNickname = featureState.resolveNickname()
            )
        ) {
            is ProfileValidation.Invalid -> {
                refreshPasswordFeedbacks(showRequired = true)
            }

            is ProfileValidation.Valid -> submitPasswordChangeInternal(
                originalEditor = editor,
                validated = validation.value
            )
        }
    }

    private fun refreshNicknameFeedback(showRequired: Boolean = false) {
        cancelNicknameAvailabilityCheck()

        val editor = featureState.nicknameEditor ?: return
        val rawInput = editor.nicknameInput

        if (rawInput.isBlank()) {
            resetNicknameAvailabilityState()
            updateState {
                it.updateNicknameFeedback(
                    feedback = if (showRequired) {
                        errorFeedback(ProfileStrings.NicknameRequired)
                    } else {
                        null
                    },
                    isCheckingAvailability = false,
                    isNicknameAvailable = false
                )
            }
            return
        }

        when (
            val validation = ProfileInputValidator.validateNickname(
                rawInput = rawInput,
                currentNickname = featureState.resolveNickname()
            )
        ) {
            is ProfileValidation.Invalid -> {
                resetNicknameAvailabilityState()
                updateState {
                    it.updateNicknameFeedback(
                        feedback = errorFeedback(validation.message),
                        isCheckingAvailability = false,
                        isNicknameAvailable = false
                    )
                }
            }

            is ProfileValidation.Valid -> {
                val nickname = validation.value

                if (lastCheckedNickname == nickname) {
                    updateState {
                        it.updateNicknameFeedback(
                            feedback = if (lastCheckedNicknameAvailable) {
                                successFeedback(ProfileStrings.NicknameAvailable)
                            } else {
                                errorFeedback(ProfileStrings.NicknameUnavailable)
                            },
                            isCheckingAvailability = false,
                            isNicknameAvailable = lastCheckedNicknameAvailable
                        )
                    }
                    return
                }

                updateState {
                    it.updateNicknameFeedback(
                        feedback = neutralFeedback(ProfileStrings.NicknameChecking),
                        isCheckingAvailability = true,
                        isNicknameAvailable = false
                    )
                }

                // 입력이 잠시 멈췄을 때만 서버에 중복 확인을 보내서 타이핑 중 호출을 줄입니다.
                nicknameAvailabilityJob = viewModelScope.launch {
                    delay(NICKNAME_CHECK_DEBOUNCE_MS)

                    checkNicknameAvailabilityUseCase(nickname)
                        .onSuccess { available ->
                            // 이전 요청 응답이 늦게 도착해도 현재 입력값과 다르면 화면을 덮어쓰지 않습니다.
                            val currentInput = featureState.nicknameEditor?.nicknameInput?.trim()
                            if (currentInput != nickname) return@launch

                            lastCheckedNickname = nickname
                            lastCheckedNicknameAvailable = available
                            updateState {
                                it.updateNicknameFeedback(
                                    feedback = if (available) {
                                        successFeedback(ProfileStrings.NicknameAvailable)
                                    } else {
                                        errorFeedback(ProfileStrings.NicknameUnavailable)
                                    },
                                    isCheckingAvailability = false,
                                    isNicknameAvailable = available
                                )
                            }
                        }
                        .onFailure { throwable ->
                            val currentInput = featureState.nicknameEditor?.nicknameInput?.trim()
                            if (currentInput != nickname) return@launch

                            resetNicknameAvailabilityState()
                            updateState {
                                it.updateNicknameFeedback(
                                    feedback = errorFeedback(
                                        throwable.message ?: ProfileStrings.NicknameCheckFailed
                                    ),
                                    isCheckingAvailability = false,
                                    isNicknameAvailable = false
                                )
                            }
                        }
                }
            }
        }
    }

    private fun refreshPasswordFeedbacks(showRequired: Boolean = false) {
        val editor = featureState.passwordEditor ?: return

        val currentPasswordFeedback = when {
            editor.currentPasswordInput.isBlank() && showRequired ->
                errorFeedback(ProfileStrings.CurrentPasswordRequired)

            editor.currentPasswordInput.isBlank() -> null
            else -> null
        }

        val newPasswordFeedback = when {
            editor.newPasswordInput.isBlank() && showRequired ->
                errorFeedback(ProfileStrings.NewPasswordRequired)

            editor.newPasswordInput.isBlank() -> null
            editor.currentPasswordInput.isNotBlank() &&
                editor.currentPasswordInput == editor.newPasswordInput ->
                errorFeedback(ProfileStrings.NewPasswordSameAsCurrent)

            else -> {
                when (
                    val validation = AuthInputPolicy.validatePassword(
                        rawPassword = editor.newPasswordInput,
                        normalizedUsername = featureState.currentUser?.username
                            ?.let(AuthInputPolicy::normalizeUsername)
                            .orEmpty(),
                        nickname = featureState.resolveNickname()
                    )
                ) {
                    is ValidationResult.Invalid -> errorFeedback(validation.message)
                    is ValidationResult.Valid -> successFeedback(ProfileStrings.NewPasswordValid)
                }
            }
        }

        val confirmPasswordFeedback = when {
            editor.confirmPasswordInput.isBlank() && showRequired ->
                errorFeedback(ProfileStrings.ConfirmPasswordRequired)

            editor.confirmPasswordInput.isBlank() -> null
            editor.newPasswordInput.isBlank() -> null
            editor.confirmPasswordInput != editor.newPasswordInput ->
                errorFeedback(ProfileStrings.PasswordConfirmMismatch)

            else -> successFeedback(ProfileStrings.ConfirmPasswordReady)
        }

        val canSubmit = editor.currentPasswordInput.isNotBlank() &&
            newPasswordFeedback?.tone == ProfileFieldFeedbackTone.Success &&
            confirmPasswordFeedback?.tone == ProfileFieldFeedbackTone.Success

        updateState {
            it.updatePasswordFeedbacks(
                currentPasswordFeedback = currentPasswordFeedback,
                newPasswordFeedback = newPasswordFeedback,
                confirmPasswordFeedback = confirmPasswordFeedback,
                canSubmit = canSubmit
            )
        }
    }

    private fun logout() {
        if (featureState.isLoggingOut || featureState.isDeletingAccount) return

        viewModelScope.launch {
            updateState { it.withLogoutLoading(true) }

            val logoutResult = logoutUseCase()
            if (logoutResult.isSuccess) {
                handleLogoutSuccess(logoutResult.getOrThrow())
            } else {
                emitMessage(
                    logoutResult.exceptionOrNull()?.message ?: ProfileStrings.LogoutFailed
                )
            }

            updateState { it.withLogoutLoading(false) }
        }
    }

    private fun deleteAccount() {
        if (featureState.isDeletingAccount || featureState.isLoggingOut) return

        viewModelScope.launch {
            updateState { it.withDeleteAccountLoading(true) }

            deleteMeUseCase()
                .onSuccess {
                    _uiEvent.emit(ProfileUiEvent.NavigateToAuth)
                }
                .onFailure { throwable ->
                    emitMessage(
                        throwable.message ?: ProfileStrings.DeleteAccountFailed
                    )
                }

            updateState { it.withDeleteAccountLoading(false) }
        }
    }

    private fun loadMyInfo() {
        viewModelScope.launch {
            reloadMyInfo(showLoading = true, suppressErrorMessage = false)
        }
    }

    private suspend fun reloadMyInfo(
        showLoading: Boolean,
        suppressErrorMessage: Boolean
    ) {
        if (showLoading) {
            updateState { it.withProfileLoading(true) }
        }

        getMyInfoUseCase()
            .onSuccess { user ->
                featureState = featureState.applyLoadedUser(user)
            }
            .onFailure { throwable ->
                if (!suppressErrorMessage) {
                    emitMessage(throwable.message ?: ProfileStrings.LoadProfileFailed)
                }
            }

        featureState = featureState.withProfileLoading(false)
        publishState()
    }

    private fun submitNicknameInternal(nickname: String) {
        val hadNickname = !featureState.resolveNickname().isNullOrBlank()

        viewModelScope.launch {
            featureState = featureState.markNicknameSaving(nickname)
            publishState()

            updateNicknameUseCase(nickname)
                .onSuccess {
                    resetNicknameAvailabilityState()
                    featureState = featureState.applySavedNickname(nickname)
                    publishState()

                    reloadMyInfo(showLoading = false, suppressErrorMessage = true)
                    emitMessage(ProfileStrings.nicknameSavedMessage(hadNickname))
                }
                .onFailure { throwable ->
                    featureState = featureState.restoreNicknameEditor(nickname)
                        .showNicknameEditorError(
                            throwable.message ?: ProfileStrings.NicknameSaveFailed
                        )
                    publishState()
                    refreshNicknameFeedback()
                }
        }
    }

    private fun submitBodyProfileInternal(
        originalEditor: ProfileBodyProfileEditorUiState,
        validated: ValidatedBodyProfile
    ) {
        viewModelScope.launch {
            featureState = featureState.markBodyProfileSaving()
            publishState()

            updateProfileUseCase(
                sex = validated.sex.apiValue,
                heightCm = validated.heightCm,
                weightKg = validated.weightKg,
                wingspanCm = validated.wingspanCm
            ).onSuccess {
                featureState = featureState.applySavedBodyProfile(
                    sex = validated.sex.apiValue,
                    heightCm = validated.heightCm,
                    weightKg = validated.weightKg,
                    wingspanCm = validated.wingspanCm
                )
                publishState()

                reloadMyInfo(showLoading = false, suppressErrorMessage = true)
                emitMessage(ProfileStrings.BodyProfileSaved)
            }.onFailure { throwable ->
                featureState = featureState.restoreBodyProfileEditor(originalEditor)
                    .showBodyProfileEditorError(
                        throwable.message ?: ProfileStrings.BodyProfileSaveFailed
                    )
                publishState()
            }
        }
    }

    private fun submitPasswordChangeInternal(
        originalEditor: ProfilePasswordEditorUiState,
        validated: ValidatedPasswordChange
    ) {
        viewModelScope.launch {
            featureState = featureState.markPasswordSaving()
            publishState()

            updatePasswordUseCase(
                oldPassword = validated.oldPassword,
                newPassword = validated.newPassword
            ).onSuccess {
                featureState = featureState.applySavedPassword()
                publishState()
                emitMessage(ProfileStrings.PasswordChanged)
            }.onFailure { throwable ->
                featureState = featureState.restorePasswordEditor(originalEditor)
                    .showPasswordEditorError(
                        throwable.message ?: ProfileStrings.PasswordChangeFailed
                    )
                publishState()
                refreshPasswordFeedbacks(showRequired = true)
            }
        }
    }

    private suspend fun handleLogoutSuccess(result: LogoutResult) {
        when (result) {
            LogoutResult.ServerConfirmed,
            is LogoutResult.LocalOnly -> _uiEvent.emit(ProfileUiEvent.NavigateToAuth)
        }
    }

    private fun updateBodyProfileInput(
        transform: (ProfileBodyProfileEditorUiState) -> ProfileBodyProfileEditorUiState
    ) {
        updateState { state -> state.updateBodyProfileEditor(transform) }
    }

    private fun updatePasswordInput(
        transform: (ProfilePasswordEditorUiState) -> ProfilePasswordEditorUiState
    ) {
        updateState { state -> state.updatePasswordEditor(transform) }
    }

    private fun updateState(
        transform: (ProfileFeatureState) -> ProfileFeatureState
    ) {
        featureState = transform(featureState)
        publishState()
    }

    private fun publishState() {
        _uiState.value = featureState.toUiState()
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _uiEvent.emit(ProfileUiEvent.ShowMessage(message))
        }
    }

    private fun cancelNicknameAvailabilityCheck() {
        nicknameAvailabilityJob?.cancel()
        nicknameAvailabilityJob = null
    }

    private fun resetNicknameAvailabilityState() {
        cancelNicknameAvailabilityCheck()
        lastCheckedNickname = null
        lastCheckedNicknameAvailable = false
    }

    private fun neutralFeedback(message: String): ProfileFieldFeedback {
        return ProfileFieldFeedback(
            message = message,
            tone = ProfileFieldFeedbackTone.Neutral
        )
    }

    private fun successFeedback(message: String): ProfileFieldFeedback {
        return ProfileFieldFeedback(
            message = message,
            tone = ProfileFieldFeedbackTone.Success
        )
    }

    private fun errorFeedback(message: String): ProfileFieldFeedback {
        return ProfileFieldFeedback(
            message = message,
            tone = ProfileFieldFeedbackTone.Error
        )
    }
}
