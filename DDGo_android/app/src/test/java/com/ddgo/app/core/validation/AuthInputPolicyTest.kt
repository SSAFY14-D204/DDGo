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
    fun validatePassword_rejectsEmailLocalPart() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "climber12!",
            normalizedUsername = "climber@example.com",
            nickname = "climber000001"
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validatePassword_rejectsSequentialPattern() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "Abcd1234!",
            normalizedUsername = "user@example.com",
            nickname = "user000001"
        )

        assertTrue(result is ValidationResult.Invalid)
    }

    @Test
    fun validatePassword_acceptsBackendCompatiblePassword() {
        val result = AuthInputPolicy.validatePassword(
            rawPassword = "WallMove!92",
            normalizedUsername = "user@example.com",
            nickname = "ddgo123456"
        )

        assertTrue(result is ValidationResult.Valid)
    }

    @Test
    fun buildProvisionalNickname_staysWithinBackendLimit() {
        repeat(20) {
            val nickname = AuthInputPolicy.buildProvisionalNickname("very.long.email.address@example.com")
            assertTrue(nickname.isNotBlank())
            assertTrue(nickname.length <= 20)
        }
    }
}
