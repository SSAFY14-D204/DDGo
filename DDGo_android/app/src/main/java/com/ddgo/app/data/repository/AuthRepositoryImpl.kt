package com.ddgo.app.data.repository

import android.util.Log
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.mapper.AuthMapper.toDomain
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.LoginRequestDto
import com.ddgo.app.data.remote.auth.RefreshTokenRequestDto
import com.ddgo.app.data.remote.auth.RegisterRequestDto
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

private const val TAG = "AuthRepository"

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

    // 1. 회원가입 구현
    override suspend fun register(
        username: String,
        password: String,
        nickname: String
    ): Result<Unit> {
        return try {
            val response = authApi.register(RegisterRequestDto(username, password, nickname))
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. 로그인 구현
    override suspend fun login(username: String, password: String): Result<AuthToken> {
        return try {
            val response = authApi.login(LoginRequestDto(username, password))
            if (response.success && response.data != null) {
                tokenDataStore.saveTokens(
                    response.data.accessToken,
                    response.data.refreshToken
                )
                Log.d(
                    TAG,
                    "login: tokens saved, accessTokenLength=${response.data.accessToken.length}, " +
                        "refreshTokenLength=${response.data.refreshToken.length}"
                )
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. 토큰 재발급 구현
    override suspend fun refreshToken(refreshToken: String): Result<AuthToken> {
        return try {
            val response = authApi.refresh(RefreshTokenRequestDto(refreshToken))
            if (response.success && response.data != null) {
                tokenDataStore.saveTokens(
                    response.data.accessToken,
                    response.data.refreshToken
                )
                Log.d(
                    TAG,
                    "refreshToken: tokens saved, accessTokenLength=${response.data.accessToken.length}, " +
                        "refreshTokenLength=${response.data.refreshToken.length}"
                )
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. 로그아웃 구현
    override suspend fun logout(): Result<Unit> {
        return try {
            val response = authApi.logout()
            // 서버 응답이 성공이든 실패든 상관없이 일단 로컬 토큰은 삭제합니다.
            // (이미 만료된 토큰이거나 서버 세션이 끊긴 경우에도 사용자는 로그아웃되어야 함)
            tokenDataStore.clearTokens()
            
            if (response.success) {
                Result.success(Unit)
            } else {
                // 서버에서 에러 메시지를 보낸 경우에도 로컬은 정리되었으므로 성공으로 간주하거나 
                // 에러를 반환하되 UI에서는 이동 처리
                Result.success(Unit) 
            }
        } catch (e: Exception) {
            // 403 Forbidden 등 네트워크 예외 발생 시에도 로컬 토큰을 강제로 삭제합니다.
            tokenDataStore.clearTokens()
            // 사용자에게는 성공한 것처럼 보여주어 흐름을 끊지 않습니다.
            Result.success(Unit)
        }
    }

    // 5. 내 정보 조회 구현
    override suspend fun getMyInfo(): Result<User> {
        return try {
            val response = authApi.getMyInfo()
            if (response.success && response.data != null) {
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 6. 회원 탈퇴 구현
    override suspend fun deleteMe(): Result<Unit> {
        return try {
            val response = authApi.deleteMe()
            // 탈퇴 성공 여부와 관계없이 사용자 데이터 정리가 필요할 수 있으나, 
            // 여기서는 성공 시에만 로컬 토큰을 삭제합니다.
            if (response.success) {
                tokenDataStore.clearTokens()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
