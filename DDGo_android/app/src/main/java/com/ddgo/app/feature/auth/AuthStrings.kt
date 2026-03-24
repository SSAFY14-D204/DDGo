package com.ddgo.app.feature.auth

internal object AuthStrings {

    const val WelcomeEyebrow = "기록이 남는 클라이밍 루틴"
    const val WelcomeTitle = "디디고로 도전 기록을 이어가세요"
    const val WelcomeDescription =
        "아이디로 간단히 가입하고 시도, 챌린지, 분석 기록을 계속 쌓을 수 있어요."
    const val WelcomeRegister = "회원가입"
    const val WelcomeLoginQuestion = "이미 계정이 있나요?"
    const val WelcomeLoginAction = "로그인"
    const val LoginToRegisterPrefix = "처음이라면"

    const val UsernameLabel = "아이디"
    const val UsernamePlaceholder = "로그인에 사용할 아이디"
    const val PasswordLabel = "비밀번호"
    const val PasswordPlaceholder = "비밀번호 입력"
    const val NextAction = "다음"
    const val LoginAction = "로그인"
    const val RegisterAction = "가입하기"
    const val StartNowAction = "가입하고 시작하기"

    const val LoginUsernameTitle = "로그인할 아이디를 입력해 주세요"
    const val LoginUsernameDescription =
        "가입한 아이디로 디디고에 다시 들어갈 수 있어요."
    const val LoginUsernameHelper =
        "아직 계정이 없다면 아래에서 회원가입을 진행해 주세요."
    const val LoginPasswordTitle = "비밀번호를 입력해 주세요"
    const val LoginPasswordDescriptionPrefix = "입력한 계정"
    const val LoginPasswordDescriptionSuffix = "로 로그인할게요."
    const val LoginPasswordHelper =
        "비밀번호가 맞지 않으면 로그인되지 않아요."
    const val ForgotPasswordAction = "비밀번호를 잊으셨나요?"
    const val PasswordResetTitle = "비밀번호 재설정"
    const val PasswordResetDescription =
        "가입한 이메일로 재설정 메일을 보내고, 메일에서 받은 링크나 토큰으로 새 비밀번호를 설정할 수 있어요."
    const val PasswordResetEmailLabel = "이메일"
    const val PasswordResetSendAction = "재설정 메일 보내기"
    const val PasswordResetResendAction = "메일 다시 보내기"
    const val PasswordResetSentTitle = "메일을 보냈어요"
    const val PasswordResetTokenLabel = "재설정 링크 또는 토큰"
    const val PasswordResetTokenPlaceholder = "메일에서 받은 링크 또는 token 입력"
    const val PasswordResetLinkHint = "메일의 링크 전체를 붙여 넣어도 token 값을 자동으로 읽어와요."
    const val PasswordResetNewPasswordLabel = "새 비밀번호"
    const val PasswordResetConfirmPasswordLabel = "새 비밀번호 확인"
    const val PasswordResetCompleteAction = "새 비밀번호로 변경"
    const val PasswordResetTokenRequired = "재설정 링크 또는 토큰을 입력해 주세요."
    const val PasswordResetConfirmRequired = "새 비밀번호 확인을 입력해 주세요."
    const val PasswordResetPasswordMismatch = "새 비밀번호가 서로 일치하지 않습니다."
    const val PasswordResetRequestFailed =
        "비밀번호 재설정 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요."
    const val PasswordResetConfirmFailed =
        "비밀번호 재설정을 완료하지 못했어요. 링크 또는 토큰을 다시 확인해 주세요."

    const val RegisterUsernameTitle = "로그인에 사용할 아이디를 만들어 주세요"
    const val RegisterUsernameDescription =
        "아이디는 회원가입 후 로그인에 계속 사용돼요."
    const val RegisterUsernameHelper =
        "표시 이름과 신체 정보는 가입 후 프로필에서 이어서 설정할 수 있어요."
    const val RegisterPasswordTitle = "비밀번호를 설정해 주세요"
    const val RegisterPasswordDescription =
        "가입이 완료되면 바로 로그인되고 프로필에서 정보를 이어서 정리할 수 있어요."
    const val RegisterPasswordHelper =
        "현재 가입 단계에서는 아이디와 비밀번호만 설정해요."
    const val RegisterPasswordRule =
        "영문/숫자/특수문자 중 2종 이상, 8~64자. 이메일·닉네임, 1234/qwer, aaa 같은 패턴은 사용할 수 없어요."

    const val UsernameRequired = "아이디를 입력해 주세요."
    const val UsernameInvalidFormat = "아이디는 이메일 형식으로 입력해 주세요."
    const val UsernameTooLong = "아이디는 255자 이하로 입력해 주세요."
    const val PasswordRequired = "비밀번호를 입력해 주세요."
    const val LoginFailed =
        "로그인에 실패했어요. 아이디와 비밀번호를 확인해 주세요."
    const val KakaoLoginFailed =
        "카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val KakaoTalkRequired =
        "카카오톡 앱이 설치되어 있어야 카카오톡으로 로그인할 수 있어요."
    const val KakaoTalkLoginFailed =
        "카카오톡 앱으로 로그인하지 못했어요. 카카오톡 상태를 확인한 뒤 다시 시도해 주세요."
    const val KakaoConsentFailed =
        "카카오 프로필 정보 동의를 완료하지 못했어요. 다시 시도해 주세요."
    const val KakaoNotConfigured =
        "카카오 네이티브 앱 키가 설정되지 않았어요. local.properties에 kakao.native.app.key를 추가해 주세요."
    const val GoogleLoginFailed =
        "구글 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val GoogleAccountReauthFailed =
        "구글 계정 재인증을 완료하지 못했어요. 기기에서 구글 계정 상태를 확인한 뒤 다시 시도해 주세요."
    const val GoogleNotConfigured =
        "구글 Web Client ID가 설정되지 않았어요. local.properties에 google.web.client.id를 추가해 주세요."
    const val RegisterFailed =
        "회원가입을 완료하지 못했어요. 입력한 정보를 확인해 주세요."
    const val RegisterAutoLoginFailed =
        "회원가입은 완료됐지만 자동 로그인에 실패했어요. 다시 로그인해 주세요."
    const val RegisterCompleted =
        "가입이 완료됐어요. 닉네임은 프로필에서 바꿀 수 있어요."

    fun loginPasswordDescription(username: String): String {
        return "${LoginPasswordDescriptionPrefix} ${username.ifBlank { UsernamePlaceholder }} ${LoginPasswordDescriptionSuffix}"
    }

    fun passwordResetSentDescription(email: String): String {
        return "$email 주소로 안내 메일을 보냈어요. 메일에 있는 링크 전체를 붙여 넣어도 token 값을 자동으로 읽어올게요."
    }
}
