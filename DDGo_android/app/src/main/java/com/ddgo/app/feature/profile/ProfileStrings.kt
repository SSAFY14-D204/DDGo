package com.ddgo.app.feature.profile

/**
 * 프로필 화면 전반에서 사용하는 문자열 모음이다.
 *
 * 화면, 다이얼로그, 검증 메시지가 서로 다른 파일에 흩어지지 않도록
 * 한곳에서 관리해 문구 톤과 용어를 일관되게 유지한다.
 */
internal object ProfileStrings {

    const val ScreenTitle = "프로필"
    const val Loading = "불러오는 중..."
    const val LoadingAccount = "계정 정보를 불러오는 중..."
    const val DefaultNickname = "사용자"
    const val Dash = "-"

    const val AccountSectionTitle = "계정"
    const val AccountIdRowTitle = "계정"
    const val UsernameRowTitle = "아이디"
    const val NicknameRowTitle = "닉네임"
    const val NicknameEmpty = "설정 안 됨"
    const val KakaoAccountLabel = "카카오 로그인 계정"
    const val GoogleAccountLabel = "Google 로그인 계정"
    const val KakaoDefaultNickname = "카카오 사용자"
    const val GoogleDefaultNickname = "Google 사용자"

    const val ActionRegister = "등록"
    const val ActionEdit = "수정"
    const val ActionSave = "저장"
    const val ActionInput = "입력"
    const val ActionCancel = "취소"

    const val NicknameCreateTitle = "닉네임 등록"
    const val NicknameUpdateTitle = "닉네임 변경"
    const val NicknameCreateDescription =
        "사용할 닉네임을 입력해 주세요. 닉네임은 20자 이하로 설정할 수 있어요."
    const val NicknameUpdateDescription =
        "바꿀 닉네임을 입력해 주세요. 닉네임은 20자 이하로 설정할 수 있어요."
    const val NicknameFieldLabel = "닉네임"
    const val NicknameChecking = "닉네임 사용 가능 여부를 확인하고 있어요."
    const val NicknameAvailable = "사용 가능한 닉네임이에요."
    const val NicknameUnavailable = "이미 사용 중인 닉네임이에요."
    const val NicknameCheckFailed =
        "닉네임 중복 확인에 실패했어요. 잠시 후 다시 시도해 주세요."

    const val BodyProfileSectionTitle = "신체 정보"
    const val SexRowTitle = "성별"
    const val HeightRowTitle = "키"
    const val WeightRowTitle = "몸무게"
    const val WingspanRowTitle = "윙스팬"
    const val BodyProfileEditRowTitle = "신체 정보"
    const val BodyProfileMissing = "설정 안 됨"

    const val BodyProfileCreateTitle = "신체 정보 입력"
    const val BodyProfileUpdateTitle = "신체 정보 수정"
    const val BodyProfileCreateDescription =
        "기록에 사용할 신체 정보를 입력해 주세요."
    const val BodyProfileUpdateDescription =
        "변경할 신체 정보를 수정해 주세요."
    const val BodyProfileFieldDescriptionLoading =
        "불러오는 중..."
    const val BodyProfileFieldLabelHeight = "키"
    const val BodyProfileFieldLabelWeight = "몸무게"
    const val BodyProfileFieldLabelWingspan = "윙스팬"
    const val BodyProfileSubmitCreate = "입력"
    const val BodyProfileSubmitUpdate = "저장"
    const val SexLabel = "성별"
    const val SexMale = "남성"
    const val SexFemale = "여성"

    const val SecuritySectionTitle = "보안"
    const val ChangePasswordRowTitle = "비밀번호 변경"
    const val ChangePasswordDialogTitle = "비밀번호 변경"
    const val ChangePasswordDialogDescription =
        "현재 비밀번호를 확인한 뒤 새 비밀번호를 입력해 주세요. 새 비밀번호는 8~64자, 2종 이상 조합 규칙을 만족해야 해요."
    const val CurrentPasswordFieldLabel = "현재 비밀번호"
    const val NewPasswordFieldLabel = "새 비밀번호"
    const val ConfirmPasswordFieldLabel = "새 비밀번호 확인"
    const val LogoutRowTitle = "로그아웃"
    const val LogoutAction = "로그아웃"

    const val DangerZoneSectionTitle = "계정 관리"
    const val DangerZoneCardTitle = "계정 탈퇴"
    const val DangerZoneCardSubtitle = ""
    const val DangerZoneAction = "탈퇴하기"

    const val LogoutDialogTitle = "로그아웃"
    const val LogoutDialogMessage = "지금 로그아웃할까요?"
    const val DeleteAccountDialogTitle = "계정 탈퇴"
    const val DeleteAccountDialogMessage =
        "탈퇴하면 계정과 클라이밍 기록을 되돌릴 수 없어요. 정말로 진행할까요?"

    const val ComingSoon = "이 기능은 곧 지원될 예정이에요."
    const val LogoutFailed =
        "로그아웃에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val DeleteAccountFailed =
        "계정 탈퇴에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val LoadProfileFailed =
        "프로필 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
    const val NicknameCreated = "닉네임이 등록됐어요."
    const val NicknameUpdated = "닉네임이 변경됐어요."
    const val NicknameSaveFailed =
        "닉네임을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val BodyProfileSaved = "신체 정보가 저장됐어요."
    const val BodyProfileSaveFailed =
        "신체 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val PasswordChanged = "비밀번호가 변경됐어요."
    const val PasswordChangeFailed =
        "비밀번호를 변경하지 못했어요. 잠시 후 다시 시도해 주세요."
    const val CurrentPasswordReady = "현재 비밀번호 입력이 확인됐어요."
    const val NewPasswordValid = "새 비밀번호를 사용해도 좋아요."
    const val ConfirmPasswordReady = "새 비밀번호 확인이 완료됐어요."

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
        return "${fieldLabel}는 0보다 큰 숫자로 입력해 주세요."
    }
}
