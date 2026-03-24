package com.ddgo.app.feature.auth

internal object AuthStrings {

    const val WelcomeEyebrow = ""
    const val WelcomeTitle = "디디고"
    const val WelcomeDescription = "클라이밍을 데이터로 즐기다"
    const val WelcomeRegister = "시작하기"
    const val WelcomeLoginQuestion = "이미 계정이 있나요?"
    const val WelcomeLoginAction = "로그인"
    const val LoginToRegisterPrefix = "처음 오셨나요?"

    const val UsernameLabel = "아이디"
    const val UsernamePlaceholder = "가입한 이메일을 입력해 주세요"
    const val PasswordLabel = "비밀번호"
    const val PasswordPlaceholder = "비밀번호를 입력해 주세요"
    const val NextAction = "다음"
    const val LoginAction = "로그인"
    const val RegisterAction = "회원가입"
    const val StartNowAction = "지금 시작하기"

    const val LoginUsernameTitle = "가입한 이메일을 입력해 주세요"
    const val LoginUsernameDescription =
        "로그인에 사용할 이메일을 먼저 확인할게요."
    const val LoginUsernameHelper =
        "이메일을 먼저 확인한 뒤 비밀번호 입력 단계로 이어집니다."
    const val LoginPasswordTitle = "비밀번호를 입력해 주세요"
    const val LoginPasswordDescriptionPrefix = "입력한"
    const val LoginPasswordDescriptionSuffix = "계정으로 로그인합니다."
    const val LoginPasswordHelper =
        "비밀번호가 기억나지 않으면 아래에서 재설정을 진행할 수 있어요."
    const val ForgotPasswordAction = "비밀번호를 잊으셨나요?"

    const val PasswordResetTitle = "비밀번호 재설정"
    const val PasswordResetDescription =
        "가입한 이메일로 재설정 메일을 보내드릴게요. 메일의 링크나 토큰으로 새 비밀번호를 설정할 수 있어요."
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
    const val PasswordResetTokenRequired = "메일에서 받은 링크나 토큰을 입력해 주세요."
    const val PasswordResetConfirmRequired = "새 비밀번호 확인을 입력해 주세요."
    const val PasswordResetPasswordMismatch = "새 비밀번호가 서로 일치하지 않아요."
    const val PasswordResetRequestFailed =
        "비밀번호 재설정 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요."
    const val PasswordResetConfirmFailed =
        "비밀번호 재설정에 실패했어요. 링크를 다시 확인한 뒤 시도해 주세요."

    const val RegisterUsernameTitle = "회원가입에 사용할 이메일을 입력해 주세요"
    const val RegisterUsernameDescription =
        "이메일은 로그인 아이디로 사용되며, 중복 여부를 바로 확인해 드려요."
    const val RegisterUsernameHelper =
        "이메일 형식이 맞고 이미 가입된 계정이 아니어야 다음 단계로 넘어갈 수 있어요."
    const val RegisterPasswordTitle = "비밀번호를 설정해 주세요"
    const val RegisterPasswordDescription =
        "안전하게 사용할 수 있도록 비밀번호 규칙을 바로 확인해 드릴게요."
    const val RegisterPasswordHelper =
        "입력하면서 바로 피드백을 보고 조건을 맞출 수 있어요."
    const val RegisterPasswordRule =
        "영문, 숫자, 특수문자 중 2종 이상을 포함하고 8~64자로 입력해 주세요. 1234, qwer, aaa 같은 쉬운 패턴은 사용할 수 없어요."
    const val RegisterPasswordValid = "사용 가능한 비밀번호예요."

    const val UsernameRequired = "아이디를 입력해 주세요."
    const val UsernameInvalidFormat = "아이디는 이메일 형식으로 입력해 주세요."
    const val UsernameTooLong = "아이디는 255자 이하로 입력해 주세요."
    const val UsernameAvailabilityChecking = "사용 가능한 아이디인지 확인하고 있어요."
    const val UsernameAvailable = "사용 가능한 아이디예요."
    const val UsernameUnavailable = "이미 사용 중인 아이디예요."
    const val UsernameAvailabilityCheckFailed =
        "아이디 중복 확인에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val UsernameAvailabilityPending =
        "아이디 확인이 아직 끝나지 않았어요. 잠시만 기다려 주세요."
    const val LoginUsernameChecking = "가입된 아이디인지 확인하고 있어요."
    const val LoginUsernameNotFound = "아이디가 틀렸어요. 가입한 이메일인지 확인해 주세요."
    const val LoginUsernameCheckFailed =
        "아이디 확인에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val PasswordRequired = "비밀번호를 입력해 주세요."
    const val LoginFailed =
        "로그인에 실패했어요. 아이디와 비밀번호를 다시 확인해 주세요."
    const val KakaoLoginFailed =
        "카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val KakaoTalkRequired =
        "카카오톡 설치가 필요해요. 카카오톡을 설치한 뒤 다시 시도해 주세요."
    const val KakaoTalkLoginFailed =
        "카카오톡 로그인에 실패했어요. 권한과 계정 상태를 확인한 뒤 다시 시도해 주세요."
    const val KakaoConsentFailed =
        "카카오 권한 동의에 실패했어요. 다시 시도해 주세요."
    const val KakaoNotConfigured =
        "카카오 설정이 아직 완료되지 않았어요. local.properties의 kakao.native.app.key를 확인해 주세요."
    const val GoogleLoginFailed =
        "Google 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
    const val GoogleAccountReauthFailed =
        "Google 계정 확인에 실패했어요. 다시 시도해 주세요."
    const val GoogleNotConfigured =
        "Google Web Client ID 설정이 필요해요. local.properties의 google.web.client.id를 확인해 주세요."
    const val RegisterFailed =
        "회원가입에 실패했어요. 입력한 정보를 다시 확인해 주세요."
    const val RegisterAutoLoginFailed =
        "회원가입은 완료됐지만 자동 로그인에 실패했어요. 로그인 화면에서 다시 시도해 주세요."
    const val RegisterCompleted =
        "회원가입이 완료됐어요. 바로 시작해 볼까요?"

    fun loginPasswordDescription(username: String): String {
        return "${LoginPasswordDescriptionPrefix} ${username.ifBlank { UsernamePlaceholder }} ${LoginPasswordDescriptionSuffix}"
    }

    fun passwordResetSentDescription(email: String): String {
        return "$email 주소로 재설정 메일을 보냈어요. 메일의 링크 전체를 붙여 넣거나 token 값만 입력해서 이어서 진행해 주세요."
    }
}
