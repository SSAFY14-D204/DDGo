package com.ddgo.app.feature.profile.mapper

import com.ddgo.app.domain.model.User
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileUiStateMapperTest {

    @Test
    fun `social nickname displays as normal nickname and editor keeps value`() {
        val user = user(
            username = "google_social-user",
            nickname = "차분한바다"
        )

        val state = ProfileUiStateMapper.create(
            user = user,
            nicknameSnapshot = null,
            bodyProfileSnapshot = null,
            isLoadingProfile = false,
            isLoggingOut = false,
            isDeletingAccount = false,
            nicknameEditor = null,
            bodyProfileEditor = null,
            passwordEditor = null
        )
        val editor = ProfileUiStateMapper.createNicknameEditor(user, nicknameSnapshot = null)

        assertEquals("차분한바다", state.header.nickname)
        assertEquals("차분한바다", editor.nicknameInput)
    }

    @Test
    fun `local nickname displays as normal nickname`() {
        val user = user(
            username = "local@example.com",
            nickname = "차분한바다"
        )

        val state = ProfileUiStateMapper.create(
            user = user,
            nicknameSnapshot = null,
            bodyProfileSnapshot = null,
            isLoadingProfile = false,
            isLoggingOut = false,
            isDeletingAccount = false,
            nicknameEditor = null,
            bodyProfileEditor = null,
            passwordEditor = null
        )
        val editor = ProfileUiStateMapper.createNicknameEditor(user, nicknameSnapshot = null)

        assertEquals("차분한바다", state.header.nickname)
        assertEquals("차분한바다", editor.nicknameInput)
    }

    @Test
    fun `nickname snapshot overrides stored nickname`() {
        val user = user(
            username = "kakao_social-user",
            nickname = "느린파도"
        )

        val state = ProfileUiStateMapper.create(
            user = user,
            nicknameSnapshot = "새닉네임",
            bodyProfileSnapshot = null,
            isLoadingProfile = false,
            isLoggingOut = false,
            isDeletingAccount = false,
            nicknameEditor = null,
            bodyProfileEditor = null,
            passwordEditor = null
        )
        val editor = ProfileUiStateMapper.createNicknameEditor(user, nicknameSnapshot = "새닉네임")

        assertEquals("새닉네임", state.header.nickname)
        assertEquals("새닉네임", editor.nicknameInput)
    }

    private fun user(
        username: String,
        nickname: String
    ): User {
        return User(
            id = 1L,
            username = username,
            email = "user@example.com",
            nickname = nickname
        )
    }
}
