package com.ddgo.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.usecase.DeleteMeUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LogoutUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import com.ddgo.app.domain.usecase.UpdatePasswordUseCase
import com.ddgo.app.domain.usecase.UpdateProfileUseCase
import com.ddgo.app.feature.profile.model.ProfileActionType
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 프로필 화면의 상태와 액션을 조율하는 ViewModel입니다.
 *
 * 역할:
 * - 프로필 조회, 닉네임 수정, 신체 정보 수정, 비밀번호 변경, 로그아웃, 회원 탈퇴를 조율합니다.
 * - 화면에 필요한 원본 상태는 [ProfileFeatureState]로 유지하고, 노출용 상태는 `uiState`로만 전달합니다.
 * - 일회성 메시지와 화면 전환은 [ProfileUiEvent]로 분리해 Compose 재구성의 영향을 줄입니다.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase,
    private val updatePasswordUseCase: UpdatePasswordUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val deleteMeUseCase: DeleteMeUseCase
) : ViewModel() {

    /** 프로필 feature 내부에서만 관리하는 원본 상태입니다. */
    private var featureState = ProfileFeatureState()

    /** Compose 화면이 구독하는 최종 UI 상태입니다. */
    private val _uiState = MutableStateFlow(featureState.toUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** 스낵바, 인증 화면 이동처럼 한 번만 소비해야 하는 이벤트입니다. */
    private val _uiEvent = MutableSharedFlow<ProfileUiEvent>()
    val uiEvent: SharedFlow<ProfileUiEvent> = _uiEvent.asSharedFlow()

    init {
        loadMyInfo()
    }

    /**
     * 화면에서 들어온 액션을 종류에 맞게 분기합니다.
     *
     * 확인 다이얼로그가 필요한 액션은 Screen에서 한 번 더 감싼 뒤,
     * 최종 확인이 끝났을 때 이 메서드가 다시 호출됩니다.
     */
    fun onActionClick(actionType: ProfileActionType) {
        when (actionType) {
            ProfileActionType.EditNickname -> openNicknameEditor()
            ProfileActionType.EditBodyProfile -> openBodyProfileEditor()
            ProfileActionType.ChangePassword -> openPasswordEditor()
            ProfileActionType.Logout -> logout()
            ProfileActionType.DeleteAccount -> deleteAccount()
        }
    }

    /** 닉네임 편집 다이얼로그를 엽니다. */
    fun openNicknameEditor() {
        updateState { it.openNicknameEditor() }
    }

    /** 닉네임 편집 다이얼로그를 닫습니다. */
    fun dismissNicknameEditor() {
        updateState { it.closeNicknameEditor() }
    }

    /** 닉네임 입력값을 상태에 반영합니다. */
    fun updateNicknameInput(input: String) {
        updateState { it.updateNicknameInput(input) }
    }

    /** 닉네임 저장을 시작합니다. */
    fun submitNickname() {
        val editor = featureState.nicknameEditor ?: return
        if (editor.isSaving) return

        when (
            val validation = ProfileInputValidator.validateNickname(
                rawInput = editor.nicknameInput,
                currentNickname = featureState.resolveNickname()
            )
        ) {
            is ProfileValidation.Invalid -> emitMessage(validation.message)
            is ProfileValidation.Valid -> submitNicknameInternal(validation.value)
        }
    }

    /** 신체 정보 편집 다이얼로그를 엽니다. */
    fun openBodyProfileEditor() {
        updateState { it.openBodyProfileEditor() }
    }

    /** 신체 정보 편집 다이얼로그를 닫습니다. */
    fun dismissBodyProfileEditor() {
        updateState { it.closeBodyProfileEditor() }
    }

    /** 성별 선택값을 상태에 반영합니다. */
    fun updateBodyProfileSex(option: ProfileSexOption) {
        updateState { state ->
            state.updateBodyProfileEditor { editor -> editor.copy(sex = option) }
        }
    }

    /** 키 입력값을 상태에 반영합니다. */
    fun updateHeightInput(input: String) {
        updateBodyProfileInput { editor ->
            editor.copy(
                heightCmInput = ProfileInputValidator.sanitizeNumberInput(input)
            )
        }
    }

    /** 몸무게 입력값을 상태에 반영합니다. */
    fun updateWeightInput(input: String) {
        updateBodyProfileInput { editor ->
            editor.copy(
                weightKgInput = ProfileInputValidator.sanitizeNumberInput(input)
            )
        }
    }

    /** 팔 길이 입력값을 상태에 반영합니다. */
    fun updateWingspanInput(input: String) {
        updateBodyProfileInput { editor ->
            editor.copy(
                wingspanCmInput = ProfileInputValidator.sanitizeNumberInput(input)
            )
        }
    }

    /** 신체 정보 저장을 시작합니다. */
    fun submitBodyProfile() {
        val editor = featureState.bodyProfileEditor ?: return
        if (editor.isSaving) return

        when (val validation = ProfileInputValidator.validateBodyProfile(editor)) {
            is ProfileValidation.Invalid -> emitMessage(validation.message)
            is ProfileValidation.Valid -> submitBodyProfileInternal(
                originalEditor = editor,
                validated = validation.value
            )
        }
    }

    /** 비밀번호 편집 다이얼로그를 엽니다. */
    fun openPasswordEditor() {
        updateState { it.openPasswordEditor() }
    }

    /** 비밀번호 편집 다이얼로그를 닫습니다. */
    fun dismissPasswordEditor() {
        updateState { it.closePasswordEditor() }
    }

    /** 현재 비밀번호 입력값을 상태에 반영합니다. */
    fun updateCurrentPasswordInput(input: String) {
        updatePasswordInput { editor -> editor.copy(currentPasswordInput = input) }
    }

    /** 새 비밀번호 입력값을 상태에 반영합니다. */
    fun updateNewPasswordInput(input: String) {
        updatePasswordInput { editor -> editor.copy(newPasswordInput = input) }
    }

    /** 새 비밀번호 확인 입력값을 상태에 반영합니다. */
    fun updateConfirmPasswordInput(input: String) {
        updatePasswordInput { editor -> editor.copy(confirmPasswordInput = input) }
    }

    /** 비밀번호 변경 요청을 시작합니다. */
    fun submitPasswordChange() {
        val editor = featureState.passwordEditor ?: return
        if (editor.isSaving) return

        when (val validation = ProfileInputValidator.validatePasswordChange(editor)) {
            is ProfileValidation.Invalid -> emitMessage(validation.message)
            is ProfileValidation.Valid -> submitPasswordChangeInternal(
                originalEditor = editor,
                validated = validation.value
            )
        }
    }

    /**
     * 로그아웃을 처리합니다.
     *
     * 회원 탈퇴와 동시에 실행되지 않도록 가드하고,
     * 성공 시 인증 화면으로 복귀시킵니다.
     */
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

    /**
     * 회원 탈퇴를 처리합니다.
     *
     * 역할:
     * - 진행 중에는 중복 호출을 막습니다.
     * - 성공 시 인증 화면으로 복귀합니다.
     * - 실패 시 현재 화면에 남아 오류만 안내합니다.
     */
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

    /** 화면 진입 시 내 정보를 불러옵니다. */
    private fun loadMyInfo() {
        viewModelScope.launch {
            reloadMyInfo(showLoading = true, suppressErrorMessage = false)
        }
    }

    /**
     * `/me` 응답으로 프로필 상태를 다시 맞춥니다.
     *
     * 저장 직후의 재조회에서는 전체 로딩을 다시 띄우지 않기 위해
     * [showLoading]을 `false`로 넘길 수 있습니다.
     */
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

    /** 닉네임 저장 API를 호출하고 결과를 상태에 반영합니다. */
    private fun submitNicknameInternal(nickname: String) {
        val hadNickname = !featureState.resolveNickname().isNullOrBlank()

        viewModelScope.launch {
            featureState = featureState.markNicknameSaving(nickname)
            publishState()

            updateNicknameUseCase(nickname)
                .onSuccess {
                    featureState = featureState.applySavedNickname(nickname)
                    publishState()

                    reloadMyInfo(showLoading = false, suppressErrorMessage = true)
                    emitMessage(ProfileStrings.nicknameSavedMessage(hadNickname))
                }
                .onFailure { throwable ->
                    featureState = featureState.restoreNicknameEditor(nickname)
                    publishState()
                    emitMessage(throwable.message ?: ProfileStrings.NicknameSaveFailed)
                }
        }
    }

    /** 신체 정보 저장 API를 호출하고 결과를 상태에 반영합니다. */
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
                publishState()
                emitMessage(throwable.message ?: ProfileStrings.BodyProfileSaveFailed)
            }
        }
    }

    /** 비밀번호 변경 API를 호출하고 결과를 상태에 반영합니다. */
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
                publishState()
                emitMessage(throwable.message ?: ProfileStrings.PasswordChangeFailed)
            }
        }
    }

    /** 로그아웃 결과를 화면 전환 이벤트로 바꿉니다. */
    private suspend fun handleLogoutSuccess(result: LogoutResult) {
        when (result) {
            LogoutResult.ServerConfirmed,
            is LogoutResult.LocalOnly -> _uiEvent.emit(ProfileUiEvent.NavigateToAuth)
        }
    }

    /** 신체 정보 편집기의 숫자 입력값을 공통 방식으로 갱신합니다. */
    private fun updateBodyProfileInput(
        transform: (ProfileBodyProfileEditorUiState) -> ProfileBodyProfileEditorUiState
    ) {
        updateState { state -> state.updateBodyProfileEditor(transform) }
    }

    /** 비밀번호 편집기의 입력값을 공통 방식으로 갱신합니다. */
    private fun updatePasswordInput(
        transform: (ProfilePasswordEditorUiState) -> ProfilePasswordEditorUiState
    ) {
        updateState { state -> state.updatePasswordEditor(transform) }
    }

    /** feature 상태를 갱신하고 즉시 UI 상태로 반영합니다. */
    private fun updateState(
        transform: (ProfileFeatureState) -> ProfileFeatureState
    ) {
        featureState = transform(featureState)
        publishState()
    }

    /** 현재 feature 상태를 화면에 전달할 최종 UI 상태로 발행합니다. */
    private fun publishState() {
        _uiState.value = featureState.toUiState()
    }

    /** 화면 하단 스낵바에서 사용할 메시지를 이벤트로 보냅니다. */
    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _uiEvent.emit(ProfileUiEvent.ShowMessage(message))
        }
    }
}
