package com.ddgo.app.core.validation

import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputPolicyTest {

    @Test
    fun normalizeUsername_trimsAndLowercasesEmail() {
        val normalized = AuthInputPolicy.normalizeUsername("  User@Example.COM  ")

        assertTrue(normalized == "user@example.com")
    }

    @Test
    fun validateUsername_rejectsNonEmailInput() {
        val result = AuthInputPolicy.validateUsername("not-an-email")

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validateNickname_trimsInput() {
        val result = AuthInputPolicy.validateNickname("  차분한바다  ")

        assertTrue(result == ValidationResult.Valid("차분한바다"))
    }

    @Test
    fun validateNickname_rejectsBlankInput() {
        val result = AuthInputPolicy.validateNickname("   ")

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validatePassword_rejectsEmailLocalPart() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "climber12!",
            normalizedUsername = "climber@example.com",
            nickname = null
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validatePassword_rejectsSequentialPattern() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "Abcd1234!",
            normalizedUsername = "user@example.com",
            nickname = null
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validatePassword_acceptsBackendCompatiblePassword() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "WallMove!92",
            normalizedUsername = "user@example.com",
            nickname = null
        )

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun validatePassword_rejectsCurrentNickname_caseInsensitively() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "coolnickname!1",
            normalizedUsername = "user@example.com",
            nickname = "CoolNickName"
        )

        assertTrue(result is ValidationResult.Invalid)
    }
}
