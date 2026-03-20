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

    const val ScreenTitle = "\uD504\uB85C\uD544"
    const val Loading = "\uBD88\uB7EC\uC624\uB294 \uC911"
    const val LoadingAccount = "\uACC4\uC815 \uC815\uBCF4\uB97C \uBD88\uB7EC\uC624\uB294 \uC911"
    const val DefaultNickname = "\uC0AC\uC6A9\uC790"
    const val Dash = "-"

    const val AccountSectionTitle = "\uACC4\uC815"
    const val UsernameRowTitle = "\uC544\uC774\uB514"
    const val NicknameRowTitle = "\uB2C9\uB124\uC784"
    const val NicknameEmpty = "\uBBF8\uC124\uC815"

    const val ActionRegister = "\uB4F1\uB85D"
    const val ActionEdit = "\uC218\uC815"
    const val ActionSave = "\uC800\uC7A5"
    const val ActionInput = "\uC785\uB825"
    const val ActionCancel = "\uCDE8\uC18C"

    const val NicknameCreateTitle = "\uB2C9\uB124\uC784 \uB4F1\uB85D"
    const val NicknameUpdateTitle = "\uB2C9\uB124\uC784 \uBCC0\uACBD"
    const val NicknameCreateDescription =
        "\uD45C\uC2DC\uD560 \uC774\uB984\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694. \uB2C9\uB124\uC784\uC740 20\uC790 \uC774\uD558\uB85C \uC124\uC815\uD560 \uC218 \uC788\uC5B4\uC694."
    const val NicknameUpdateDescription =
        "\uBC14\uAFC0 \uB2C9\uB124\uC784\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694. \uB2C9\uB124\uC784\uC740 20\uC790 \uC774\uD558\uB85C \uC124\uC815\uD560 \uC218 \uC788\uC5B4\uC694."
    const val NicknameFieldLabel = "\uB2C9\uB124\uC784"

    const val BodyProfileSectionTitle = "\uC2E0\uCCB4 \uC815\uBCF4"
    const val SexRowTitle = "\uC131\uBCC4"
    const val HeightRowTitle = "\uD0A4"
    const val WeightRowTitle = "\uBAB8\uBB34\uAC8C"
    const val WingspanRowTitle = "\uD314 \uAE38\uC774"
    const val BodyProfileEditRowTitle = "\uC2E0\uCCB4 \uC815\uBCF4"
    const val BodyProfileMissing = "\uBBF8\uC785\uB825"

    const val BodyProfileCreateTitle = "\uC2E0\uCCB4 \uC815\uBCF4 \uC785\uB825"
    const val BodyProfileUpdateTitle = "\uC2E0\uCCB4 \uC815\uBCF4 \uC218\uC815"
    const val BodyProfileCreateDescription =
        "\uAE30\uBCF8 \uC2E0\uCCB4 \uC815\uBCF4\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val BodyProfileUpdateDescription =
        "\uBC14\uAFC0 \uC815\uBCF4\uB9CC \uC218\uC815\uD574 \uC8FC\uC138\uC694."
    const val BodyProfileFieldDescriptionLoading =
        "\uBD88\uB7EC\uC624\uB294 \uC911"
    const val BodyProfileFieldLabelHeight = "\uD0A4"
    const val BodyProfileFieldLabelWeight = "\uBAB8\uBB34\uAC8C"
    const val BodyProfileFieldLabelWingspan = "\uD314 \uAE38\uC774"
    const val BodyProfileSubmitCreate = "\uC785\uB825"
    const val BodyProfileSubmitUpdate = "\uC800\uC7A5"
    const val SexLabel = "\uC131\uBCC4"
    const val SexMale = "\uB0A8\uC131"
    const val SexFemale = "\uC5EC\uC131"

    const val SecuritySectionTitle = "\uBCF4\uC548"
    const val ChangePasswordRowTitle = "\uBE44\uBC00\uBC88\uD638 \uBCC0\uACBD"
    const val ChangePasswordDialogTitle = "\uBE44\uBC00\uBC88\uD638 \uBCC0\uACBD"
    const val ChangePasswordDialogDescription =
        "\uD604\uC7AC \uBE44\uBC00\uBC88\uD638\uB97C \uD655\uC778\uD55C \uB4A4 \uC0C8 \uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694. \uC0C8 \uBE44\uBC00\uBC88\uD638\uB294 8~64\uC790, 2\uC885 \uC870\uD569\uC73C\uB85C \uC124\uC815\uD574\uC57C \uD574\uC694."
    const val CurrentPasswordFieldLabel = "\uD604\uC7AC \uBE44\uBC00\uBC88\uD638"
    const val NewPasswordFieldLabel = "\uC0C8 \uBE44\uBC00\uBC88\uD638"
    const val ConfirmPasswordFieldLabel = "\uC0C8 \uBE44\uBC00\uBC88\uD638 \uD655\uC778"
    const val LogoutRowTitle = "\uB85C\uADF8\uC544\uC6C3"
    const val LogoutAction = "\uB85C\uADF8\uC544\uC6C3"

    const val DangerZoneSectionTitle = "\uD68C\uC6D0 \uD0C8\uD1F4"
    const val DangerZoneCardTitle = "\uACC4\uC815 \uC0AD\uC81C"
    const val DangerZoneCardSubtitle = ""
    const val DangerZoneAction = "\uD0C8\uD1F4\uD558\uAE30"

    const val LogoutDialogTitle = "\uB85C\uADF8\uC544\uC6C3"
    const val LogoutDialogMessage = "\uC774 \uAE30\uAE30\uC5D0\uC11C \uB85C\uADF8\uC544\uC6C3\uD560\uAE4C\uC694?"
    const val DeleteAccountDialogTitle = "\uD68C\uC6D0 \uD0C8\uD1F4"
    const val DeleteAccountDialogMessage =
        "\uD0C8\uD1F4 \uD6C4\uC5D0\uB294 \uACC4\uC815\uACFC \uAE30\uB85D\uC744 \uBCF5\uAD6C\uD560 \uC218 \uC5C6\uC5B4\uC694. \uACC4\uC18D\uD560\uAE4C\uC694?"

    const val ComingSoon = "\uC774 \uAE30\uB2A5\uC740 \uACE7 \uC0AC\uC6A9\uD560 \uC218 \uC788\uC5B4\uC694."
    const val LogoutFailed =
        "\uB85C\uADF8\uC544\uC6C3\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."
    const val DeleteAccountFailed =
        "\uD68C\uC6D0 \uD0C8\uD1F4\uB97C \uC644\uB8CC\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."
    const val LoadProfileFailed =
        "\uD504\uB85C\uD544 \uC815\uBCF4\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."
    const val NicknameCreated = "\uB2C9\uB124\uC784\uC744 \uB4F1\uB85D\uD588\uC5B4\uC694."
    const val NicknameUpdated = "\uB2C9\uB124\uC784\uC744 \uBCC0\uACBD\uD588\uC5B4\uC694."
    const val NicknameSaveFailed =
        "\uB2C9\uB124\uC784\uC744 \uC800\uC7A5\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."
    const val BodyProfileSaved = "\uC2E0\uCCB4 \uC815\uBCF4\uB97C \uC800\uC7A5\uD588\uC5B4\uC694."
    const val BodyProfileSaveFailed =
        "\uC2E0\uCCB4 \uC815\uBCF4\uB97C \uC800\uC7A5\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."
    const val PasswordChanged = "\uBE44\uBC00\uBC88\uD638\uB97C \uBCC0\uACBD\uD588\uC5B4\uC694."
    const val PasswordChangeFailed =
        "\uBE44\uBC00\uBC88\uD638\uB97C \uBCC0\uACBD\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD574 \uC8FC\uC138\uC694."

    const val NicknameRequired = "\uB2C9\uB124\uC784\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val NicknameTooLong = "\uB2C9\uB124\uC784\uC740 20\uC790 \uC774\uD558\uB85C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val NicknameSameAsCurrent = "\uD604\uC7AC \uB2C9\uB124\uC784\uACFC \uAC19\uC544\uC694."
    const val SexRequired = "\uC131\uBCC4\uC744 \uC120\uD0DD\uD574 \uC8FC\uC138\uC694."
    const val CurrentPasswordRequired = "\uD604\uC7AC \uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val NewPasswordRequired = "\uC0C8 \uBE44\uBC00\uBC88\uD638\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val ConfirmPasswordRequired =
        "\uC0C8 \uBE44\uBC00\uBC88\uD638 \uD655\uC778\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val NewPasswordSameAsCurrent =
        "\uC0C8 \uBE44\uBC00\uBC88\uD638\uB294 \uD604\uC7AC \uBE44\uBC00\uBC88\uD638\uC640 \uB2E4\uB974\uAC8C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    const val PasswordConfirmMismatch =
        "\uC0C8 \uBE44\uBC00\uBC88\uD638 \uD655\uC778\uC774 \uC77C\uCE58\uD558\uC9C0 \uC54A\uC544\uC694."

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
        return "$fieldLabel\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    }

    fun positiveNumberMessage(fieldLabel: String): String {
        return "$fieldLabel\uB294 0\uBCF4\uB2E4 \uD070 \uAC12\uC744 \uC785\uB825\uD574 \uC8FC\uC138\uC694."
    }
}
