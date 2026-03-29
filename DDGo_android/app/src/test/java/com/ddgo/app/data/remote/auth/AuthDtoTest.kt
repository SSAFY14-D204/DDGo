package com.ddgo.app.data.remote.auth

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthDtoTest {

    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun `register request serializes only username and password`() {
        val payload = json.encodeToString(
            RegisterRequestDto(
                username = "user@example.com",
                password = "Password!12"
            )
        )

        assertTrue(payload.contains("\"username\""))
        assertTrue(payload.contains("\"password\""))
        assertFalse(payload.contains("\"nickname\""))
    }
}
