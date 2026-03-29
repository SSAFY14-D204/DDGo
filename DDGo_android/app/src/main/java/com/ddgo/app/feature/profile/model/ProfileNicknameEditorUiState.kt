package com.ddgo.app.feature.profile.model

/**
 * 닉네임 등록/변경 다이얼로그의 화면 상태입니다.
 *
 * 역할:
 * - 초기 회원의 닉네임 등록과 기존 회원의 닉네임 변경을 같은 UI 모델로 표현합니다.
 * - 입력값과 저장 진행 여부를 함께 보관해 ViewModel과 Screen의 책임을 단순화합니다.
 */
data class ProfileNicknameEditorUiState(
    val title: String,
    val description: String,
    val submitLabel: String,
    val nicknameInput: String = "",
    val nicknameFeedback: ProfileFieldFeedback? = null,
    val isCheckingAvailability: Boolean = false,
    val isNicknameAvailable: Boolean = false,
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)
