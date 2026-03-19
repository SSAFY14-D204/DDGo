package com.ddgo.app.feature.profile.model

/**
 * 비밀번호 변경 다이얼로그에 필요한 UI 상태입니다.
 *
 * 역할:
 * - 현재 비밀번호, 새 비밀번호, 새 비밀번호 확인 입력값을 함께 보관합니다.
 * - 저장 중 상태를 포함해 다이얼로그 동작을 안정적으로 제어합니다.
 */
data class ProfilePasswordEditorUiState(
    val title: String,
    val description: String,
    val submitLabel: String,
    val currentPasswordInput: String = "",
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val isSaving: Boolean = false
)
