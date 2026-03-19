package com.ddgo.app.feature.profile.state

import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfilePasswordEditorUiState
import com.ddgo.app.feature.profile.model.ProfileSexOption

/**
 * 프로필 편집 화면에서 사용하는 입력 검증 도우미입니다.
 *
 * 역할:
 * - ViewModel에서 반복되는 입력 검증 로직을 분리합니다.
 * - 닉네임과 신체 정보 검증 기준을 한 곳에서 유지합니다.
 */
internal object ProfileInputValidator {

    /** 닉네임 입력값을 저장 가능한 형태로 검증합니다. */
    fun validateNickname(
        rawInput: String,
        currentNickname: String?
    ): ProfileValidation<String> {
        val nickname = rawInput.trim()

        return when {
            nickname.isBlank() -> ProfileValidation.Invalid(ProfileStrings.NicknameRequired)
            !currentNickname.isNullOrBlank() && nickname == currentNickname ->
                ProfileValidation.Invalid(ProfileStrings.NicknameSameAsCurrent)
            else -> ProfileValidation.Valid(nickname)
        }
    }

    /** 신체 정보 입력값을 API 요청에 바로 사용할 수 있는 값으로 검증합니다. */
    fun validateBodyProfile(
        editor: ProfileBodyProfileEditorUiState
    ): ProfileValidation<ValidatedBodyProfile> {
        val sex = editor.sex ?: return ProfileValidation.Invalid(ProfileStrings.SexRequired)
        val heightCm = parsePositiveNumber(
            rawValue = editor.heightCmInput,
            fieldLabel = ProfileStrings.BodyProfileFieldLabelHeight
        )
        val weightKg = parsePositiveNumber(
            rawValue = editor.weightKgInput,
            fieldLabel = ProfileStrings.BodyProfileFieldLabelWeight
        )
        val wingspanCm = parsePositiveNumber(
            rawValue = editor.wingspanCmInput,
            fieldLabel = ProfileStrings.BodyProfileFieldLabelWingspan
        )

        if (heightCm is ProfileValidation.Invalid) return heightCm
        if (weightKg is ProfileValidation.Invalid) return weightKg
        if (wingspanCm is ProfileValidation.Invalid) return wingspanCm

        return ProfileValidation.Valid(
            ValidatedBodyProfile(
                sex = sex,
                heightCm = (heightCm as ProfileValidation.Valid).value,
                weightKg = (weightKg as ProfileValidation.Valid).value,
                wingspanCm = (wingspanCm as ProfileValidation.Valid).value
            )
        )
    }

    /** 비밀번호 변경 입력값을 검증합니다. */
    fun validatePasswordChange(
        editor: ProfilePasswordEditorUiState
    ): ProfileValidation<ValidatedPasswordChange> {
        val currentPassword = editor.currentPasswordInput
        val newPassword = editor.newPasswordInput
        val confirmPassword = editor.confirmPasswordInput

        return when {
            currentPassword.isBlank() -> ProfileValidation.Invalid(ProfileStrings.CurrentPasswordRequired)
            newPassword.isBlank() -> ProfileValidation.Invalid(ProfileStrings.NewPasswordRequired)
            confirmPassword.isBlank() -> ProfileValidation.Invalid(ProfileStrings.ConfirmPasswordRequired)
            currentPassword == newPassword ->
                ProfileValidation.Invalid(ProfileStrings.NewPasswordSameAsCurrent)
            newPassword != confirmPassword ->
                ProfileValidation.Invalid(ProfileStrings.PasswordConfirmMismatch)
            else -> ProfileValidation.Valid(
                ValidatedPasswordChange(
                    oldPassword = currentPassword,
                    newPassword = newPassword
                )
            )
        }
    }

    /** 숫자 입력 칸에는 숫자와 소수점만 남기도록 정리합니다. */
    fun sanitizeNumberInput(input: String): String {
        val filtered = input.filter { it.isDigit() || it == '.' }
        val dotIndex = filtered.indexOf('.')
        if (dotIndex == -1) return filtered

        val beforeDot = filtered.substring(0, dotIndex + 1)
        val afterDot = filtered.substring(dotIndex + 1).replace(".", "")
        return beforeDot + afterDot
    }

    /** 0보다 큰 숫자만 허용되는 입력값을 검증합니다. */
    private fun parsePositiveNumber(
        rawValue: String,
        fieldLabel: String
    ): ProfileValidation<Float> {
        if (rawValue.isBlank()) {
            return ProfileValidation.Invalid(
                ProfileStrings.requiredNumberMessage(fieldLabel)
            )
        }

        val parsed = rawValue.toFloatOrNull()
        return if (parsed == null || parsed <= 0f) {
            ProfileValidation.Invalid(
                ProfileStrings.positiveNumberMessage(fieldLabel)
            )
        } else {
            ProfileValidation.Valid(parsed)
        }
    }
}

/** 입력 검증 결과를 표현하는 공통 타입입니다. */
internal sealed interface ProfileValidation<out T> {
    data class Valid<T>(val value: T) : ProfileValidation<T>
    data class Invalid(val message: String) : ProfileValidation<Nothing>
}

/** 검증을 통과한 신체 정보 입력값입니다. */
internal data class ValidatedBodyProfile(
    val sex: ProfileSexOption,
    val heightCm: Float,
    val weightKg: Float,
    val wingspanCm: Float
)

/** 검증을 통과한 비밀번호 변경 입력값입니다. */
internal data class ValidatedPasswordChange(
    val oldPassword: String,
    val newPassword: String
)
