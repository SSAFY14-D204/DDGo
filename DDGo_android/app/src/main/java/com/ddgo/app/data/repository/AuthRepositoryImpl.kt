package com.ddgo.app.data.repository

import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.mapper.AuthMapper.toDomain
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.LoginRequestDto
import com.ddgo.app.domain.model.AuthToken
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
    override suspend fun login(username: String, password: String): Result<AuthToken> {
        return try {
            val response = authApi.login(LoginRequestDto(username, password))
            if (response.success && response.data != null) {
                tokenDataStore.saveTokens(
                    response.data.accessToken,
                    response.data.refreshToken
                )
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}