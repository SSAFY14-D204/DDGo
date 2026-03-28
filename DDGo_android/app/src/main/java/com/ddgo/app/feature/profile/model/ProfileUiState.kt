package com.ddgo.app.feature.profile.model

/**
 * 프로필 화면을 그리기 위한 최종 UI 상태입니다.
 *
 * 역할:
 * - 상단 헤더, 목록형 섹션, 회원 탈퇴 영역을 한 번에 담습니다.
 * - 닉네임 편집 다이얼로그와 신체 정보 편집 다이얼로그 상태도 함께 보관합니다.
 * - 화면에서 직접 원본 도메인 모델을 조합하지 않도록, 렌더링에 필요한 값만 노출합니다.
 */
data class ProfileUiState(
    val title: String,
    val header: ProfileHeaderUiModel,
    val infoSections: List<ProfileInfoSectionUiModel>,
    val dangerZone: ProfileDangerZoneUiModel,
    val isLoadingProfile: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val nicknameEditor: ProfileNicknameEditorUiState? = null,
    val bodyProfileEditor: ProfileBodyProfileEditorUiState? = null,
    val passwordEditor: ProfilePasswordEditorUiState? = null
)

/** 상단 히어로 카드에 노출할 핵심 계정 정보입니다. */
data class ProfileHeaderUiModel(
    val nickname: String,
    val accountId: String
)

/** 계정, 신체 정보, 보안처럼 화면에 반복되는 목록형 섹션 모델입니다. */
data class ProfileInfoSectionUiModel(
    val title: String,
    val rows: List<ProfileInfoRowUiModel>,
    val headerAction: ProfileSectionActionUiModel? = null
)

/**
 * 프로필 화면의 한 줄(row)을 표현합니다.
 *
 * value:
 * - 현재 설정값 또는 안내 문구입니다.
 *
 * actionType:
 * - 탭했을 때 ViewModel로 전달할 액션입니다.
 *
 * trailing:
 * - 액션 캡슐, disclosure 아이콘, 또는 없음 중 하나입니다.
 */
data class ProfileInfoRowUiModel(
    val icon: ProfileRowIcon,
    val title: String,
    val value: String? = null,
    val actionType: ProfileActionType? = null,
    val trailing: ProfileRowTrailing = ProfileRowTrailing.None
)

/** 회원 탈퇴 영역에 필요한 문구와 액션 정보를 담습니다. */
data class ProfileDangerZoneUiModel(
    val title: String,
    val subtitle: String? = null,
    val actionLabel: String,
    val actionType: ProfileActionType
)

/** 섹션 제목 우측에 붙는 액션 칩 UI 모델입니다. */
data class ProfileSectionActionUiModel(
    val label: String,
    val actionType: ProfileActionType,
    val tone: ProfileActionTone = ProfileActionTone.Accent
)

/** 목록 행의 액션 강조 톤입니다. */
enum class ProfileActionTone {
    Normal,
    Accent,
    Danger
}

/**
 * 목록 행 우측에 붙는 trailing 표현입니다.
 *
 * - None: 추가 표시가 없습니다.
 * - Disclosure: 다음 화면으로 이동하는 `>` 아이콘입니다.
 * - Action: 즉시 실행 성격의 캡슐 버튼입니다.
 */
sealed class ProfileRowTrailing {
    object None : ProfileRowTrailing()
    object Disclosure : ProfileRowTrailing()

    data class Action(
        val label: String,
        val tone: ProfileActionTone = ProfileActionTone.Normal
    ) : ProfileRowTrailing()
}

enum class ProfileRowIcon {
    Account,
    Nickname,
    Sex,
    Height,
    Weight,
    Wingspan,
    BodyProfile,
    Password,
    Logout
}

/** 프로필 화면에서 처리하는 액션 종류입니다. */
enum class ProfileActionType {
    EditNickname,
    EditBodyProfile,
    ChangePassword,
    Logout,
    DeleteAccount
}
