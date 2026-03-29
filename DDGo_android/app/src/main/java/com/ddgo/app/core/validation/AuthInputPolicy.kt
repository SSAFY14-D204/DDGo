package com.ddgo.app.core.validation

import java.util.Locale

object AuthInputPolicy {

    private const val USERNAME_MAX_LENGTH = 255
    private const val NICKNAME_MAX_LENGTH = 20
    private val emailRegex =
        Regex("^[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")
    private val weakPasswords = setOf(
        "12345678",
        "password",
        "qwer1234",
        "abcd1234",
        "admin123",
        "qwerty",
        "123456789",
        "12341234",
        "password123",
        "11111111",
        "00000000",
        "qazwsx",
        "1234567",
        "asdfgh",
        "zxcvbnm",
        "password!",
        "admin1234",
        "123123123",
        "manager",
        "test1234",
        "welcome1",
        "1234567890"
    )

    fun normalizeUsername(input: String): String = input.trim().lowercase(Locale.ROOT)

    fun validateUsername(rawInput: String): ValidationResult<String> {
        val normalized = normalizeUsername(rawInput)

        return when {
            normalized.isBlank() -> ValidationResult.Invalid("아이디를 입력해 주세요.")
            normalized.length > USERNAME_MAX_LENGTH ->
                ValidationResult.Invalid("아이디는 255자 이하로 입력해 주세요.")
            !emailRegex.matches(normalized) ->
                ValidationResult.Invalid("아이디는 이메일 형식으로 입력해 주세요.")
            else -> ValidationResult.Valid(normalized)
        }
    }

    fun validateNickname(rawInput: String): ValidationResult<String> {
        val nickname = rawInput.trim()

        return when {
            nickname.isBlank() -> ValidationResult.Invalid("닉네임을 입력해 주세요.")
            nickname.length > NICKNAME_MAX_LENGTH ->
                ValidationResult.Invalid("닉네임은 20자 이하로 입력해 주세요.")
            else -> ValidationResult.Valid(nickname)
        }
    }

    fun validatePasswordPresence(password: String): ValidationResult<String> {
        return if (password.isBlank()) {
            ValidationResult.Invalid("비밀번호를 입력해 주세요.")
        } else {
            ValidationResult.Valid(password)
        }
    }

    fun validatePassword(
        rawPassword: String,
        normalizedUsername: String,
        nickname: String?
    ): ValidationResult<String> {
        if (rawPassword.isBlank()) {
            return ValidationResult.Invalid("비밀번호를 입력해 주세요.")
        }

        if (!isAsciiPrintablePassword(rawPassword) || countCharacterTypes(rawPassword) < 2) {
            return ValidationResult.Invalid(PASSWORD_POLICY_MESSAGE)
        }

        val lowerPassword = rawPassword.lowercase(Locale.ROOT)

        if (normalizedUsername.isNotBlank() && lowerPassword.contains(normalizedUsername)) {
            return ValidationResult.Invalid("아이디를 비밀번호에 포함할 수 없습니다.")
        }

        val localPart = normalizedUsername.substringBefore('@', "")
        if (localPart.length >= 3 && lowerPassword.contains(localPart)) {
            return ValidationResult.Invalid("이메일 앞부분을 비밀번호에 포함할 수 없습니다.")
        }

        if (!nickname.isNullOrBlank()) {
            val loweredNickname = nickname.trim().lowercase(Locale.ROOT)
            if (loweredNickname.isNotBlank() && lowerPassword.contains(loweredNickname)) {
                return ValidationResult.Invalid("닉네임을 비밀번호에 포함할 수 없습니다.")
            }
        }

        if (rawPassword.contains(Regex("(.)\\1{2,}"))) {
            return ValidationResult.Invalid("동일한 문자를 3회 이상 연속으로 사용할 수 없습니다.")
        }

        if (hasConsecutiveSequence(lowerPassword)) {
            return ValidationResult.Invalid("연속된 패턴(예: 1234, abcd, qwer)을 4자 이상 사용할 수 없습니다.")
        }

        if (weakPasswords.contains(lowerPassword)) {
            return ValidationResult.Invalid("너무 쉬운 비밀번호(password, 12345678 등)는 사용할 수 없습니다.")
        }

        return ValidationResult.Valid(rawPassword)
    }

    fun buildRegisterPasswordGuide(): String {
        return "영문/숫자/특수문자 중 2종 이상, 8~64자. 이메일·닉네임, 1234/qwer, aaa 같은 패턴은 사용할 수 없어요."
    }

    fun buildChangePasswordGuide(): String {
        return "새 비밀번호는 8~64자이며, 영문/숫자/특수문자 중 2종 이상을 포함해야 해요. 이메일·닉네임·연속 패턴은 사용할 수 없어요."
    }

    private fun isAsciiPrintablePassword(password: String): Boolean {
        return password.length in 8..64 && password.all { it.code in 0x21..0x7E }
    }

    private fun countCharacterTypes(password: String): Int {
        var typeCount = 0
        if (password.any { it.isLetter() }) typeCount += 1
        if (password.any { it.isDigit() }) typeCount += 1
        if (password.any { it in '!'..'/' || it in ':'..'@' || it in '['..'`' || it in '{'..'~' }) {
            typeCount += 1
        }
        return typeCount
    }

    private fun hasConsecutiveSequence(password: String): Boolean {
        if (password.contains("qwer") || password.contains("rewq")) {
            return true
        }

        for (index in 0..password.length - 4) {
            val first = password[index]
            val second = password[index + 1]
            val third = password[index + 2]
            val fourth = password[index + 3]

            val isIncreasing = first + 1 == second && second + 1 == third && third + 1 == fourth
            val isDecreasing = first - 1 == second && second - 1 == third && third - 1 == fourth

            if ((isIncreasing || isDecreasing) && first.isLetterOrDigit()) {
                return true
            }
        }

        return false
    }

    private const val PASSWORD_POLICY_MESSAGE =
        "비밀번호는 8~64자이며, 영문/숫자/특수문자 중 2종 이상을 포함해야 합니다."
}

sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val message: String) : ValidationResult<Nothing>
}
