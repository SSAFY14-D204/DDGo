package com.ddgo.app.feature.profile.state

import com.ddgo.app.domain.model.User
import com.ddgo.app.feature.profile.mapper.ProfileUiStateMapper
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfileBodyProfileSnapshot
import com.ddgo.app.feature.profile.model.ProfileFieldFeedback
import com.ddgo.app.feature.profile.model.ProfileNicknameEditorUiState
import com.ddgo.app.feature.profile.model.ProfilePasswordEditorUiState
import com.ddgo.app.feature.profile.model.ProfileUiState

/**
 * 프로필 feature 내부에서만 사용하는 원본 상태입니다.
 *
 * 역할:
 * - ViewModel이 직접 여러 개의 필드를 따로 관리하지 않도록 상태를 한 곳에 모읍니다.
 * - 닉네임/신체 정보 스냅샷, 다이얼로그 상태, 로딩 플래그를 한 번에 다룹니다.
 * - 최종 화면 상태 변환은 [toUiState]를 통해 mapper에 위임합니다.
 */
internal data class ProfileFeatureState(
    val currentUser: User? = null,
    val nicknameSnapshot: String? = null,
    val bodyProfileSnapshot: ProfileBodyProfileSnapshot? = null,
    val isLoadingProfile: Boolean = true,
    val isLoggingOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val nicknameEditor: ProfileNicknameEditorUiState? = null,
    val bodyProfileEditor: ProfileBodyProfileEditorUiState? = null,
    val passwordEditor: ProfilePasswordEditorUiState? = null
) {

    /** 현재 내부 상태를 화면 전용 UI 상태로 변환합니다. */
    fun toUiState(): ProfileUiState {
        return ProfileUiStateMapper.create(
            user = currentUser,
            nicknameSnapshot = nicknameSnapshot,
            bodyProfileSnapshot = bodyProfileSnapshot,
            isLoadingProfile = isLoadingProfile,
            isLoggingOut = isLoggingOut,
            isDeletingAccount = isDeletingAccount,
            nicknameEditor = nicknameEditor,
            bodyProfileEditor = bodyProfileEditor,
            passwordEditor = passwordEditor
        )
    }

    /** 현재 화면에 보여줘야 하는 닉네임 값을 계산합니다. */
    fun resolveNickname(): String? {
        return nicknameSnapshot?.takeUnless { it.isBlank() }
            ?: currentUser?.nickname?.takeUnless { it.isBlank() }
    }

    /** `/me` 재조회 성공 시 최신 사용자 정보를 반영합니다. */
    fun applyLoadedUser(user: User): ProfileFeatureState {
        return copy(
            currentUser = user,
            nicknameSnapshot = null,
            bodyProfileSnapshot = null
        )
    }

    /** 프로필 전체 로딩 상태를 갱신합니다. */
    fun withProfileLoading(isLoading: Boolean): ProfileFeatureState {
        return copy(isLoadingProfile = isLoading)
    }

    /** 로그아웃 진행 상태를 갱신합니다. */
    fun withLogoutLoading(isLoading: Boolean): ProfileFeatureState {
        return copy(isLoggingOut = isLoading)
    }

    /** 회원 탈퇴 진행 여부를 갱신합니다. */
    fun withDeleteAccountLoading(isLoading: Boolean): ProfileFeatureState {
        return copy(isDeletingAccount = isLoading)
    }

    /** 닉네임 편집 다이얼로그를 엽니다. */
    fun openNicknameEditor(): ProfileFeatureState {
        return copy(
            bodyProfileEditor = null,
            passwordEditor = null,
            nicknameEditor = ProfileUiStateMapper.createNicknameEditor(
                user = currentUser,
                nicknameSnapshot = nicknameSnapshot
            )
        )
    }

    /** 닉네임 편집 다이얼로그를 닫습니다. */
    fun closeNicknameEditor(): ProfileFeatureState {
        if (nicknameEditor?.isSaving == true) return this
        return copy(nicknameEditor = null)
    }

    /** 닉네임 입력값을 갱신합니다. */
    fun updateNicknameInput(input: String): ProfileFeatureState {
        val editor = nicknameEditor ?: return this
        if (editor.isSaving) return this

        return copy(
            nicknameEditor = editor.copy(
                nicknameInput = input,
                nicknameFeedback = null,
                isCheckingAvailability = false,
                isNicknameAvailable = false,
                errorMessage = null
            )
        )
    }

    fun updateNicknameFeedback(
        feedback: ProfileFieldFeedback?,
        isCheckingAvailability: Boolean,
        isNicknameAvailable: Boolean
    ): ProfileFeatureState {
        val editor = nicknameEditor ?: return this
        if (editor.isSaving) return this

        return copy(
            nicknameEditor = editor.copy(
                nicknameFeedback = feedback,
                isCheckingAvailability = isCheckingAvailability,
                isNicknameAvailable = isNicknameAvailable,
                errorMessage = null
            )
        )
    }

    /** 닉네임 저장 중 상태를 반영합니다. */
    fun markNicknameSaving(nickname: String): ProfileFeatureState {
        val editor = nicknameEditor ?: return this
        return copy(
            nicknameEditor = editor.copy(
                nicknameInput = nickname,
                nicknameFeedback = null,
                isCheckingAvailability = false,
                isNicknameAvailable = false,
                errorMessage = null,
                isSaving = true
            )
        )
    }

    /** 닉네임 저장 실패 후 편집 상태를 복구합니다. */
    fun restoreNicknameEditor(nickname: String): ProfileFeatureState {
        val editor = nicknameEditor ?: return this
        return copy(
            nicknameEditor = editor.copy(
                nicknameInput = nickname,
                nicknameFeedback = null,
                isCheckingAvailability = false,
                isNicknameAvailable = false,
                errorMessage = null,
                isSaving = false
            )
        )
    }

    /** 닉네임 편집 다이얼로그에 즉시 확인 가능한 오류 메시지를 표시합니다. */
    fun showNicknameEditorError(message: String): ProfileFeatureState {
        val editor = nicknameEditor ?: return this
        return copy(
            nicknameEditor = editor.copy(
                errorMessage = message,
                isSaving = false
            )
        )
    }

    /** 닉네임 저장 성공 값을 로컬 상태에 즉시 반영합니다. */
    fun applySavedNickname(nickname: String): ProfileFeatureState {
        val updatedUser = currentUser?.copy(nickname = nickname)
        return copy(
            currentUser = updatedUser,
            nicknameSnapshot = if (updatedUser == null) nickname else null,
            nicknameEditor = null
        )
    }

    /** 신체 정보 편집 다이얼로그를 엽니다. */
    fun openBodyProfileEditor(): ProfileFeatureState {
        return copy(
            nicknameEditor = null,
            passwordEditor = null,
            bodyProfileEditor = ProfileUiStateMapper.createBodyProfileEditor(
                user = currentUser,
                bodyProfileSnapshot = bodyProfileSnapshot
            )
        )
    }

    /** 신체 정보 편집 다이얼로그를 닫습니다. */
    fun closeBodyProfileEditor(): ProfileFeatureState {
        if (bodyProfileEditor?.isSaving == true) return this
        return copy(bodyProfileEditor = null)
    }

    /** 신체 정보 편집 상태를 수정합니다. */
    fun updateBodyProfileEditor(
        transform: (ProfileBodyProfileEditorUiState) -> ProfileBodyProfileEditorUiState
    ): ProfileFeatureState {
        val editor = bodyProfileEditor ?: return this
        if (editor.isSaving) return this

        return copy(
            bodyProfileEditor = transform(editor).copy(errorMessage = null)
        )
    }

    /** 신체 정보 저장 중 상태를 반영합니다. */
    fun markBodyProfileSaving(): ProfileFeatureState {
        val editor = bodyProfileEditor ?: return this
        return copy(
            bodyProfileEditor = editor.copy(
                errorMessage = null,
                isSaving = true
            )
        )
    }

    /** 신체 정보 저장 실패 후 편집 상태를 복구합니다. */
    fun restoreBodyProfileEditor(
        editor: ProfileBodyProfileEditorUiState
    ): ProfileFeatureState {
        return copy(
            bodyProfileEditor = editor.copy(
                errorMessage = null,
                isSaving = false
            )
        )
    }

    /** 신체 정보 편집 다이얼로그에 즉시 확인 가능한 오류 메시지를 표시합니다. */
    fun showBodyProfileEditorError(message: String): ProfileFeatureState {
        val editor = bodyProfileEditor ?: return this
        return copy(
            bodyProfileEditor = editor.copy(
                errorMessage = message,
                isSaving = false
            )
        )
    }

    /** 신체 정보 저장 성공 값을 로컬 상태에 즉시 반영합니다. */
    fun applySavedBodyProfile(
        sex: String,
        heightCm: Float,
        weightKg: Float,
        wingspanCm: Float
    ): ProfileFeatureState {
        val updatedUser = currentUser?.copy(
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            wingspanCm = wingspanCm
        )

        return copy(
            currentUser = updatedUser,
            bodyProfileSnapshot = if (updatedUser == null) {
                ProfileBodyProfileSnapshot(
                    sex = sex,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    wingspanCm = wingspanCm
                )
            } else {
                null
            },
            bodyProfileEditor = null
        )
    }

    /** 비밀번호 변경 다이얼로그를 엽니다. */
    fun openPasswordEditor(): ProfileFeatureState {
        return copy(
            nicknameEditor = null,
            bodyProfileEditor = null,
            passwordEditor = ProfileUiStateMapper.createPasswordEditor()
        )
    }

    /** 비밀번호 변경 다이얼로그를 닫습니다. */
    fun closePasswordEditor(): ProfileFeatureState {
        if (passwordEditor?.isSaving == true) return this
        return copy(passwordEditor = null)
    }

    /** 비밀번호 변경 다이얼로그 입력값을 갱신합니다. */
    fun updatePasswordEditor(
        transform: (ProfilePasswordEditorUiState) -> ProfilePasswordEditorUiState
    ): ProfileFeatureState {
        val editor = passwordEditor ?: return this
        if (editor.isSaving) return this

        return copy(
            passwordEditor = transform(editor).copy(errorMessage = null)
        )
    }

    fun updatePasswordFeedbacks(
        currentPasswordFeedback: ProfileFieldFeedback?,
        newPasswordFeedback: ProfileFieldFeedback?,
        confirmPasswordFeedback: ProfileFieldFeedback?,
        canSubmit: Boolean
    ): ProfileFeatureState {
        val editor = passwordEditor ?: return this
        if (editor.isSaving) return this

        return copy(
            passwordEditor = editor.copy(
                currentPasswordFeedback = currentPasswordFeedback,
                newPasswordFeedback = newPasswordFeedback,
                confirmPasswordFeedback = confirmPasswordFeedback,
                canSubmit = canSubmit,
                errorMessage = null
            )
        )
    }

    /** 비밀번호 저장 진행 중 상태로 전환합니다. */
    fun markPasswordSaving(): ProfileFeatureState {
        val editor = passwordEditor ?: return this
        return copy(
            passwordEditor = editor.copy(
                currentPasswordFeedback = null,
                newPasswordFeedback = null,
                confirmPasswordFeedback = null,
                canSubmit = false,
                errorMessage = null,
                isSaving = true
            )
        )
    }

    /** 비밀번호 저장 실패 후 편집 상태를 복원합니다. */
    fun restorePasswordEditor(
        editor: ProfilePasswordEditorUiState
    ): ProfileFeatureState {
        return copy(
            passwordEditor = editor.copy(
                errorMessage = null,
                isSaving = false
            )
        )
    }

    /** 비밀번호 변경 다이얼로그에 즉시 확인 가능한 오류 메시지를 표시합니다. */
    fun showPasswordEditorError(message: String): ProfileFeatureState {
        val editor = passwordEditor ?: return this
        return copy(
            passwordEditor = editor.copy(
                errorMessage = message,
                canSubmit = false,
                isSaving = false
            )
        )
    }

    /** 비밀번호 변경 성공 후 편집 상태를 정리합니다. */
    fun applySavedPassword(): ProfileFeatureState {
        return copy(passwordEditor = null)
    }
}
