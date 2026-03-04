package com.ddgo.app.data.repository

import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.mapper.AuthMapper.toUser
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.LoginRequestDto
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * AuthRepository 인터페이스(domain)의 실제 구현체.
 *
 * domain 계층은 이 클래스를 직접 알지 못하며,
 * di/RepositoryModule에서 AuthRepository 인터페이스로 바인딩됩니다.
 */
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = authApi.login(LoginRequestDto(email, password))
            if (response.success && response.data != null) {
                // 토큰 저장
                tokenDataStore.saveTokens(
                    accessToken = response.data.accessToken,
                    refreshToken = response.data.refreshToken
                )
                // DTO → Domain Model 변환
                Result.success(response.data.toUser())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            authApi.logout()
            tokenDataStore.clearTokens()
            Result.success(Unit)
        } catch (e: Exception) {
            // 서버 로그아웃 실패해도 로컬 토큰은 삭제
            tokenDataStore.clearTokens()
            Result.success(Unit)
        }
    }
}
