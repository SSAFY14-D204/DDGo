package com.ddgo.app.feature.profile.state

import com.ddgo.app.core.validation.AuthInputPolicy
import com.ddgo.app.core.validation.ValidationResult
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfilePasswordEditorUiState
import com.ddgo.app.feature.profile.model.ProfileSexOption

internal object ProfileInputValidator {

    fun validateNickname(
        rawInput: String,
        currentNickname: String?
    ): ProfileValidation<String> {
        val nickname = when (val validation = AuthInputPolicy.validateNickname(rawInput)) {
            is ValidationResult.Invalid -> return ProfileValidation.Invalid(validation.message)
            is ValidationResult.Valid -> validation.value
        }

        return when {
            !currentNickname.isNullOrBlank() && nickname == currentNickname ->
                ProfileValidation.Invalid(ProfileStrings.NicknameSameAsCurrent)
            else -> ProfileValidation.Valid(nickname)
        }
    }

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

    fun validatePasswordChange(
        editor: ProfilePasswordEditorUiState,
        currentUsername: String?,
        currentNickname: String?
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
            else -> {
                when (
                    val validation = AuthInputPolicy.validatePassword(
                        rawPassword = newPassword,
                        normalizedUsername = currentUsername?.let(AuthInputPolicy::normalizeUsername).orEmpty(),
                        nickname = currentNickname
                    )
                ) {
                    is ValidationResult.Invalid -> ProfileValidation.Invalid(validation.message)
                    is ValidationResult.Valid -> ProfileValidation.Valid(
                        ValidatedPasswordChange(
                            oldPassword = currentPassword,
                            newPassword = validation.value
                        )
                    )
                }
            }
        }
    }

    fun sanitizeNumberInput(input: String): String {
        val filtered = input.filter { it.isDigit() || it == '.' }
        val dotIndex = filtered.indexOf('.')
        if (dotIndex == -1) return filtered

        val beforeDot = filtered.substring(0, dotIndex + 1)
        val afterDot = filtered.substring(dotIndex + 1).replace(".", "")
        return beforeDot + afterDot
    }

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

internal sealed interface ProfileValidation<out T> {
    data class Valid<T>(val value: T) : ProfileValidation<T>
    data class Invalid(val message: String) : ProfileValidation<Nothing>
}

internal data class ValidatedBodyProfile(
    val sex: ProfileSexOption,
    val heightCm: Float,
    val weightKg: Float,
    val wingspanCm: Float
)

internal data class ValidatedPasswordChange(
    val oldPassword: String,
    val newPassword: String
)
