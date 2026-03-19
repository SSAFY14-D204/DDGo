package com.ddgo.app.data.repository

import android.util.Log
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.mapper.AuthMapper.toDomain
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.LoginRequestDto
import com.ddgo.app.data.remote.auth.RefreshTokenRequestDto
import com.ddgo.app.data.remote.auth.RegisterRequestDto
import com.ddgo.app.data.remote.auth.UpdateNicknameRequestDto
import com.ddgo.app.data.remote.auth.UpdatePasswordRequestDto
import com.ddgo.app.data.remote.auth.UpdateProfileRequestDto
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

private const val TAG = "AuthRepository"

/**
 * [AuthRepository]의 실제 구현체입니다.
 *
 * 역할:
 * - AuthApi 호출 결과를 domain 계층에서 다루기 쉬운 `Result` 형태로 변환합니다.
 * - 토큰 저장/삭제처럼 로컬 인증 상태 정리도 함께 담당합니다.
 */
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore
) : AuthRepository {

    /** 회원가입 요청을 수행합니다. */
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

    /** 로그인 요청을 수행하고 토큰을 저장합니다. */
    override suspend fun login(
        username: String,
        password: String
    ): Result<AuthToken> {
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

    /** Refresh Token으로 토큰을 재발급하고 저장합니다. */
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

    /**
     * 로그아웃 요청을 시도하고 로컬 토큰을 정리합니다.
     *
     * 서버 응답이 실패해도 로컬 토큰 정리는 수행해 앱 상태는 확실히 로그아웃되도록 합니다.
     */
    override suspend fun logout(): Result<LogoutResult> {
        return try {
            val serverResult = runCatching { authApi.logout() }
            tokenDataStore.clearTokens()

            serverResult.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Result.success(LogoutResult.ServerConfirmed)
                    } else {
                        Result.success(
                            LogoutResult.LocalOnly(
                                reason = response.message?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                },
                onFailure = { throwable ->
                    Result.success(
                        LogoutResult.LocalOnly(
                            reason = throwable.message
                        )
                    )
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 로그인한 사용자의 정보를 조회합니다. */
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

    /** 신체 정보 수정 요청을 수행합니다. */
    override suspend fun updateProfile(
        sex: String,
        heightCm: Float,
        weightKg: Float,
        wingspanCm: Float
    ): Result<Unit> {
        return try {
            val response = authApi.updateProfile(
                UpdateProfileRequestDto(
                    sex = sex,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    wingspanCm = wingspanCm
                )
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 닉네임 등록 또는 변경 요청을 수행합니다. */
    override suspend fun updateNickname(nickname: String): Result<Unit> {
        return try {
            val response = authApi.updateNickname(
                UpdateNicknameRequestDto(
                    nickname = nickname
                )
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 기존 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. */
    override suspend fun updatePassword(
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val response = authApi.updatePassword(
                UpdatePasswordRequestDto(
                    oldPassword = oldPassword,
                    newPassword = newPassword
                )
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 회원 탈퇴 요청 후 로컬 토큰을 정리합니다. */
    override suspend fun deleteMe(): Result<Unit> {
        return try {
            val response = authApi.deleteMe()
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
