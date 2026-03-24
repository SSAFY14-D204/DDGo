package com.ddgo.app.feature.profile

/**
 * 프로필 feature에서 사용하는 사용자 노출 문구 모음입니다.
 *
 * 역할:
 * - 프로필 화면에서 반복해서 사용하는 문구를 한 곳에서 관리합니다.
 * - mapper, dialog, view model이 같은 카피를 재사용해 워딩 드리프트를 막습니다.
 * - 문자열 인코딩 이슈가 다시 생기지 않도록 사용자 노출 문구는 안전한 형태로 모아둡니다.
 */
internal object ProfileStrings {

    const val ScreenTitle = "프로필"
    const val Loading = "불러오는 중"
    const val LoadingAccount = "계정 정보를 불러오는 중"
    const val DefaultNickname = "사용자"
    const val Dash = "-"

    const val AccountSectionTitle = "계정"
    const val AccountIdRowTitle = "계정"
    const val UsernameRowTitle = "아이디"
    const val NicknameRowTitle = "닉네임"
    const val NicknameEmpty = "미설정"
    const val KakaoAccountLabel = "카카오 로그인 계정"
    const val GoogleAccountLabel = "구글 로그인 계정"
    const val KakaoDefaultNickname = "카카오 사용자"
    const val GoogleDefaultNickname = "구글 사용자"

    const val ActionRegister = "등록"
    const val ActionEdit = "수정"
    const val ActionSave = "저장"
    const val ActionInput = "입력"
    const val ActionCancel = "취소"

    const val NicknameCreateTitle = "닉네임 등록"
    const val NicknameUpdateTitle = "닉네임 변경"
    const val NicknameCreateDescription =
        "표시할 이름을 입력해 주세요. 닉네임은 20자 이하로 설정할 수 있어요."
    const val NicknameUpdateDescription =
        "바꿀 닉네임을 입력해 주세요. 닉네임은 20자 이하로 설정할 수 있어요."
    const val NicknameFieldLabel = "닉네임"

    const val BodyProfileSectionTitle = "신체 정보"
    const val SexRowTitle = "성별"
    const val HeightRowTitle = "키"
    const val WeightRowTitle = "몸무게"
    const val WingspanRowTitle = "팔 길이"
    const val BodyProfileEditRowTitle = "신체 정보"
    const val BodyProfileMissing = "미입력"

    const val BodyProfileCreateTitle = "신체 정보 입력"
    const val BodyProfileUpdateTitle = "신체 정보 수정"
    const val BodyProfileCreateDescription =
        "기본 신체 정보를 입력해 주세요."
    const val BodyProfileUpdateDescription =
        "바꿀 정보만 수정해 주세요."
    const val BodyProfileFieldDescriptionLoading =
        "불러오는 중"
    const val BodyProfileFieldLabelHeight = "키"
    const val BodyProfileFieldLabelWeight = "몸무게"
    const val BodyProfileFieldLabelWingspan = "팔 길이"
    const val BodyProfileSubmitCreate = "입력"
    const val BodyProfileSubmitUpdate = "저장"
    const val SexLabel = "성별"
    const val SexMale = "남성"
    const val SexFemale = "여성"

    const val SecuritySectionTitle = "보안"
    const val ChangePasswordRowTitle = "비밀번호 변경"
    const val ChangePasswordDialogTitle = "비밀번호 변경"
    const val ChangePasswordDialogDescription =
        "현재 비밀번호를 확인한 뒤 새 비밀번호를 입력해 주세요. 새 비밀번호는 8~64자, 2종 조합으로 설정해야 해요."
    const val CurrentPasswordFieldLabel = "현재 비밀번호"
    const val NewPasswordFieldLabel = "새 비밀번호"
    const val ConfirmPasswordFieldLabel = "새 비밀번호 확인"
    const val LogoutRowTitle = "로그아웃"
    const val LogoutAction = "로그아웃"

    const val DangerZoneSectionTitle = "회원 탈퇴"
    const val DangerZoneCardTitle = "계정 삭제"
    const val DangerZoneCardSubtitle = ""
    const val DangerZoneAction = "탈퇴하기"

    const val LogoutDialogTitle = "로그아웃"
    const val LogoutDialogMessage = "이 기기에서 로그아웃할까요?"
    const val DeleteAccountDialogTitle = "회원 탈퇴"
    const val DeleteAccountDialogMessage =
        "탈퇴 후에는 계정과 기록을 복구할 수 없어요. 계속할까요?"

    const val ComingSoon = "이 기능은 곧 사용할 수 있어요."
    const val LogoutFailed =
        "로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val DeleteAccountFailed =
        "회원 탈퇴를 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val LoadProfileFailed =
        "프로필 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
    const val NicknameCreated = "닉네임을 등록했어요."
    const val NicknameUpdated = "닉네임을 변경했어요."
    const val NicknameSaveFailed =
        "닉네임을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val BodyProfileSaved = "신체 정보를 저장했어요."
    const val BodyProfileSaveFailed =
        "신체 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val PasswordChanged = "비밀번호를 변경했어요."
    const val PasswordChangeFailed =
        "비밀번호를 변경하지 못했어요. 잠시 후 다시 시도해 주세요."

    const val NicknameRequired = "닉네임을 입력해 주세요."
    const val NicknameTooLong = "닉네임은 20자 이하로 입력해 주세요."
    const val NicknameSameAsCurrent = "현재 닉네임과 같아요."
    const val SexRequired = "성별을 선택해 주세요."
    const val CurrentPasswordRequired = "현재 비밀번호를 입력해 주세요."
    const val NewPasswordRequired = "새 비밀번호를 입력해 주세요."
    const val ConfirmPasswordRequired =
        "새 비밀번호 확인을 입력해 주세요."
    const val NewPasswordSameAsCurrent =
        "새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요."
    const val PasswordConfirmMismatch =
        "새 비밀번호 확인이 일치하지 않아요."

    fun nicknameActionLabel(hasNickname: Boolean): String {
        return if (hasNickname) ActionEdit else ActionRegister
    }

    fun nicknameEditorTitle(hasNickname: Boolean): String {
        return if (hasNickname) NicknameUpdateTitle else NicknameCreateTitle
    }

    fun nicknameEditorDescription(hasNickname: Boolean): String {
        return if (hasNickname) NicknameUpdateDescription else NicknameCreateDescription
    }

    fun nicknameSavedMessage(hadNickname: Boolean): String {
        return if (hadNickname) NicknameUpdated else NicknameCreated
    }

    fun bodyProfileEditorTitle(hasBodyProfile: Boolean): String {
        return if (hasBodyProfile) BodyProfileUpdateTitle else BodyProfileCreateTitle
    }

    fun bodyProfileEditorDescription(hasBodyProfile: Boolean): String {
        return if (hasBodyProfile) BodyProfileUpdateDescription else BodyProfileCreateDescription
    }

    fun bodyProfileActionLabel(hasBodyProfile: Boolean): String {
        return if (hasBodyProfile) ActionEdit else ActionInput
    }

    fun bodyProfileSubmitLabel(hasBodyProfile: Boolean): String {
        return if (hasBodyProfile) BodyProfileSubmitUpdate else BodyProfileSubmitCreate
    }

    fun requiredNumberMessage(fieldLabel: String): String {
        return "${fieldLabel}를 입력해 주세요."
    }

    fun positiveNumberMessage(fieldLabel: String): String {
        return "${fieldLabel}는 0보다 큰 값을 입력해 주세요."
    }
}
