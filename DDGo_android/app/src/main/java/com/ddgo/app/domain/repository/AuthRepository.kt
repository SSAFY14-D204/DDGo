package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.model.User

interface AuthRepository {

    suspend fun register(
        username: String,
        password: String,
        nickname: String
    ): Result<Unit>

    suspend fun checkUsernameAvailability(username: String): Result<Boolean>

    suspend fun checkNicknameAvailability(nickname: String): Result<Boolean>

    suspend fun login(
        username: String,
        password: String
    ): Result<AuthToken>

    suspend fun socialLogin(
        provider: SocialLoginProvider,
        accessToken: String? = null,
        idToken: String? = null
    ): Result<AuthToken>

    suspend fun requestPasswordReset(email: String): Result<Unit>

    suspend fun confirmPasswordReset(
        token: String,
        newPassword: String
    ): Result<Unit>

    suspend fun refreshToken(refreshToken: String): Result<AuthToken>

    suspend fun logout(): Result<LogoutResult>

    suspend fun getMyInfo(): Result<User>

    suspend fun updateProfile(
        sex: String,
        heightCm: Float,
        weightKg: Float,
        wingspanCm: Float
    ): Result<Unit>

    suspend fun updateNickname(nickname: String): Result<Unit>

    suspend fun updatePassword(
        oldPassword: String,
        newPassword: String
    ): Result<Unit>

    suspend fun deleteMe(): Result<Unit>
}
