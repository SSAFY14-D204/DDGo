package com.ddgo.app.feature.auth

/**
 * 인증 화면에서 사용하는 문구를 한곳에 모아둔 객체입니다.
 *
 * 역할:
 * - 로그인, 회원가입, 후속 안내 문구를 화면마다 중복하지 않게 합니다.
 * - 백엔드 실제 지원 범위에 맞는 안내만 남겨 잘못된 기대를 줄입니다.
 */
internal object AuthStrings {

    const val WelcomeEyebrow = "\uAE30\uB85D\uC774 \uB0A8\uB294 \uD074\uB77C\uC774\uBC0D \uB8E8\uD2F4"
    const val WelcomeTitle = "\uB514\uB514\uACE0\uB85C \uB3C4\uC804 \uAE30\uB85D\uC744 \uC774\uC5B4\uAC00\uC138\uC694"
    const val WelcomeDescription =
        "\uC544\uC774\uB514\uB85C \uAC04\uB2E8\uD788 \uAC00\uC785\uD558\uACE0 \uC2DC\uB3C4, \uCC4C\uB9B0\uC9C0, \uBD84\uC11D \uAE30\uB85D\uC744 \uACC4\uC18D \uC313\uC744 \uC218 \uC788\uC5B4\uC694."
    const val WelcomeRegister = "\uD68C\uC6D0\uAC00\uC785"
    const val WelcomeLoginQuestion = "\uC774\uBBF8 \uACC4\uC815\uC774 \uC788\uB098\uC694?"
    const val WelcomeLoginAction = "\uB85C\uADF8\uC778"
    const val LoginToRegisterPrefix = "\uCC98\uC74C\uC774\uB77C\uBA74"

    const val UsernameLabel = "\uC544\uC774\uB514"
    const val UsernamePlaceholder = "\uB85C\uADF8\uC778\uC5D0 \uC0AC\uC6A9\uD560 \uC544\uC774\uB514"
    const val PasswordLabel = "\uBE44\uBC00\uBC88\uD638"
    const val PasswordPlaceholder = "\uBE44\uBC00\uBC88\uD638 \uC785\uB825"
    const val NextAction = "\uB2E4\uC74C"
    const val LoginAction = "\uB85C\uADF8\uC778"
    const val RegisterAction = "\uAC00\uC785\uD558\uAE30"
    const val StartNowAction = "\uAC00\uC785\uD558\uACE0 \uC2DC\uC791\uD558\uAE30"

    const val LoginUsernameTitle = "\uB85C\uADF8\uC778\uD560 \uC544\uC774\uB514\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694"
    const val LoginUsernameDescription =
        "\uAC00\uC785\uD55C \uC544\uC774\uB514\uB85C \uB514\uB514\uACE0\uC5D0 \uB2E4\uC2DC \uB4E4\uC5B4\uAC08 \uC218 \uC788\uC5B4\uC694."
    const val LoginUsernameHelper =
        "\uC544\uC9C1 \uACC4\uC815\uC774 \uC5C6\uB2E4\uBA74 \uC544\uB798\uC5D0\uC11C \uD68C\uC6D0\uAC00\uC785\uC744 \uC9C4\uD589\uD574 \uC8FC\uC138\uC694."
    const val LoginPasswordTitle = "\uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694"
    const val LoginPasswordDescriptionPrefix = "\uC785\uB825\uD55C \uACC4\uC815"
    const val LoginPasswordDescriptionSuffix = "\uB85C \uB85C\uADF8\uC778\uD560\uAC8C\uC694."
    const val LoginPasswordHelper =
        "\uBE44\uBC00\uBC88\uD638\uAC00 \uB9DE\uC9C0 \uC54A\uC73C\uBA74 \uB85C\uADF8\uC778\uB418\uC9C0 \uC54A\uC544\uC694."

    const val RegisterUsernameTitle = "\uB85C\uADF8\uC778\uC5D0 \uC0AC\uC6A9\uD560 \uC544\uC774\uB514\uB97C \uB9CC\uB4E4\uC5B4 \uC8FC\uC138\uC694"
    const val RegisterUsernameDescription =
        "\uC544\uC774\uB514\uB294 \uD68C\uC6D0\uAC00\uC785 \uD6C4 \uB85C\uADF8\uC778\uC5D0 \uACC4\uC18D \uC0AC\uC6A9\uB3FC\uC694."
    const val RegisterUsernameHelper =
        "\uD45C\uC2DC \uC774\uB984\uACFC \uC2E0\uCCB4 \uC815\uBCF4\uB294 \uAC00\uC785 \uD6C4 \uD504\uB85C\uD544\uC5D0\uC11C \uC774\uC5B4\uC11C \uC124\uC815\uD560 \uC218 \uC788\uC5B4\uC694."
    const val RegisterPasswordTitle = "\uBE44\uBC00\uBC88\uD638\uB97C \uC124\uC815\uD574 \uC8FC\uC138\uC694"
    const val RegisterPasswordDescription =
        "\uAC00\uC785\uC774 \uC644\uB8CC\uB418\uBA74 \uBC14\uB85C \uB85C\uADF8\uC778\uB418\uACE0 \uD504\uB85C\uD544\uC5D0\uC11C \uC815\uBCF4\uB97C \uC774\uC5B4\uC11C \uC815\uB9AC\uD560 \uC218 \uC788\uC5B4\uC694."
    const val RegisterPasswordHelper =
        "\uD604\uC7AC \uAC00\uC785 \uB2E8\uACC4\uC5D0\uC11C\uB294 \uC544\uC774\uB514\uC640 \uBE44\uBC00\uBC88\uD638\uB9CC \uC124\uC815\uD574\uC694."
    const val RegisterPasswordRule =
        "\uBE44\uBC00\uBC88\uD638\uB294 \uC774\uD6C4 \uD504\uB85C\uD544 \uD654\uBA74\uC5D0\uC11C \uB2E4\uC2DC \uBCC0\uACBD\uD560 \uC218 \uC788\uC5B4\uC694."

    const val UsernameRequired = "\uC544\uC774\uB514\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val PasswordRequired = "\uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val LoginFailed =
        "\uB85C\uADF8\uC778\uC5D0 \uC2E4\uD328\uD588\uC5B4\uC694. \uC544\uC774\uB514\uC640 \uBE44\uBC00\uBC88\uD638\uB97C \uD655\uC778\uD574 \uC8FC\uC138\uC694."
    const val RegisterFailed =
        "\uD68C\uC6D0\uAC00\uC785\uC744 \uC644\uB8CC\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC785\uB825\uD55C \uC815\uBCF4\uB97C \uD655\uC778\uD574 \uC8FC\uC138\uC694."
    const val RegisterAutoLoginFailed =
        "\uD68C\uC6D0\uAC00\uC785\uC740 \uC644\uB8CC\uB410\uC9C0\uB9CC \uC790\uB3D9 \uB85C\uADF8\uC778\uC5D0 \uC2E4\uD328\uD588\uC5B4\uC694. \uB2E4\uC2DC \uB85C\uADF8\uC778\uD574 \uC8FC\uC138\uC694."

    fun loginPasswordDescription(username: String): String {
        return "${LoginPasswordDescriptionPrefix} ${username.ifBlank { UsernamePlaceholder }} ${LoginPasswordDescriptionSuffix}"
    }
}
