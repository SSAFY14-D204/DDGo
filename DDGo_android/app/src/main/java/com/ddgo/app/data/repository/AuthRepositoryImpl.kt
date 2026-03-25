package com.ddgo.app.data.repository

import android.util.Log
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.data.mapper.AuthMapper.toDomain
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.LoginRequestDto
import com.ddgo.app.data.remote.auth.PasswordResetConfirmRequestDto
import com.ddgo.app.data.remote.auth.PasswordResetMailRequestDto
import com.ddgo.app.data.remote.auth.RefreshTokenRequestDto
import com.ddgo.app.data.remote.auth.RegisterRequestDto
import com.ddgo.app.data.remote.auth.SocialLoginRequestDto
import com.ddgo.app.data.remote.auth.UpdateNicknameRequestDto
import com.ddgo.app.data.remote.auth.UpdatePasswordRequestDto
import com.ddgo.app.data.remote.auth.UpdateProfileRequestDto
import com.ddgo.app.data.remote.common.ApiErrorResponse
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val TAG = "AuthRepository"

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore,
    private val json: Json
) : AuthRepository {

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
                Result.failure(Exception(response.message.ifBlank { REGISTER_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, REGISTER_FAILED_MESSAGE), e))
        }
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> {
        return try {
            val response = authApi.checkUsernameAvailability(username = username)
            if (response.success && response.data != null) {
                Result.success(response.data.available)
            } else {
                Result.failure(Exception(response.message.ifBlank {
                    "아이디 중복 확인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
                }))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    resolveErrorMessage(
                        e,
                        "아이디 중복 확인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
                    ),
                    e
                )
            )
        }
    }

    override suspend fun checkNicknameAvailability(nickname: String): Result<Boolean> {
        return try {
            val response = authApi.checkNicknameAvailability(nickname = nickname)
            if (response.success && response.data != null) {
                Result.success(response.data.available)
            } else {
                Result.failure(Exception(response.message.ifBlank {
                    "닉네임 중복 확인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
                }))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    resolveErrorMessage(
                        e,
                        "닉네임 중복 확인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
                    ),
                    e
                )
            )
        }
    }

    override suspend fun login(
        username: String,
        password: String
    ): Result<AuthToken> {
        return try {
            val response = authApi.login(LoginRequestDto(username, password))
            if (response.success && response.data != null) {
                saveTokens(response.data.accessToken, response.data.refreshToken, "login")
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { LOGIN_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, LOGIN_FAILED_MESSAGE), e))
        }
    }

    override suspend fun socialLogin(
        provider: SocialLoginProvider,
        accessToken: String?,
        idToken: String?
    ): Result<AuthToken> {
        return try {
            val response = authApi.socialLogin(
                SocialLoginRequestDto(
                    provider = provider.name,
                    accessToken = accessToken,
                    idToken = idToken
                )
            )
            if (response.success && response.data != null) {
                saveTokens(response.data.accessToken, response.data.refreshToken, "socialLogin:${provider.name}")
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { SOCIAL_LOGIN_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, SOCIAL_LOGIN_FAILED_MESSAGE), e))
        }
    }

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            val response = authApi.requestPasswordReset(
                PasswordResetMailRequestDto(email = email)
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifBlank {
                    "비밀번호 재설정 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요."
                }))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    resolveErrorMessage(
                        e,
                        "비밀번호 재설정 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요."
                    ),
                    e
                )
            )
        }
    }

    override suspend fun confirmPasswordReset(
        token: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val response = authApi.confirmPasswordReset(
                PasswordResetConfirmRequestDto(
                    token = token,
                    newPassword = newPassword
                )
            )
            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifBlank {
                    "비밀번호 재설정을 완료하지 못했어요. 링크 또는 토큰을 다시 확인해 주세요."
                }))
            }
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    resolveErrorMessage(
                        e,
                        "비밀번호 재설정을 완료하지 못했어요. 링크 또는 토큰을 다시 확인해 주세요."
                    ),
                    e
                )
            )
        }
    }

    override suspend fun refreshToken(refreshToken: String): Result<AuthToken> {
        return try {
            val response = authApi.refresh(RefreshTokenRequestDto(refreshToken))
            if (response.success && response.data != null) {
                saveTokens(response.data.accessToken, response.data.refreshToken, "refreshToken")
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { REFRESH_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, REFRESH_FAILED_MESSAGE), e))
        }
    }

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
                                reason = response.message.takeIf { it.isNotBlank() }
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

    override suspend fun getMyInfo(): Result<User> {
        return try {
            val response = authApi.getMyInfo()
            if (response.success && response.data != null) {
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { LOAD_PROFILE_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, LOAD_PROFILE_FAILED_MESSAGE), e))
        }
    }

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
                Result.failure(Exception(response.message.ifBlank { UPDATE_PROFILE_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, UPDATE_PROFILE_FAILED_MESSAGE), e))
        }
    }

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
                Result.failure(Exception(response.message.ifBlank { UPDATE_NICKNAME_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, UPDATE_NICKNAME_FAILED_MESSAGE), e))
        }
    }

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
                Result.failure(Exception(response.message.ifBlank { UPDATE_PASSWORD_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, UPDATE_PASSWORD_FAILED_MESSAGE), e))
        }
    }

    override suspend fun deleteMe(): Result<Unit> {
        return try {
            val response = authApi.deleteMe()
            if (response.success) {
                tokenDataStore.clearTokens()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message.ifBlank { DELETE_ME_FAILED_MESSAGE }))
            }
        } catch (e: Exception) {
            Result.failure(Exception(resolveErrorMessage(e, DELETE_ME_FAILED_MESSAGE), e))
        }
    }

    private suspend fun saveTokens(accessToken: String, refreshToken: String, source: String) {
        tokenDataStore.saveTokens(accessToken, refreshToken)
        Log.d(
            TAG,
            "$source: tokens saved, accessTokenLength=${accessToken.length}, refreshTokenLength=${refreshToken.length}"
        )
    }

    private fun resolveErrorMessage(throwable: Exception, fallbackMessage: String): String {
        throwable.toUserFacingNetworkMessageOrNull()?.let { return it }

        if (throwable is HttpException) {
            val parsedMessage = throwable.response()
                ?.errorBody()
                ?.string()
                ?.takeIf { it.isNotBlank() }
                ?.let { body ->
                    runCatching {
                        json.decodeFromString(ApiErrorResponse.serializer(), body).message
                    }.getOrNull()
                }
                ?.takeIf { it.isNotBlank() }

            if (!parsedMessage.isNullOrBlank()) {
                return parsedMessage
            }
        }

        val rawMessage = throwable.message?.trim()
        return if (rawMessage.isNullOrBlank() || rawMessage.startsWith("HTTP ")) {
            fallbackMessage
        } else {
            rawMessage
        }
    }

    private companion object {
        const val REGISTER_FAILED_MESSAGE =
            "회원가입을 완료하지 못했어요. 입력한 정보를 확인해 주세요."
        const val LOGIN_FAILED_MESSAGE =
            "로그인에 실패했어요. 아이디와 비밀번호를 확인해 주세요."
        const val SOCIAL_LOGIN_FAILED_MESSAGE =
            "카카오 로그인을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val REFRESH_FAILED_MESSAGE =
            "세션을 갱신하지 못했어요. 다시 로그인해 주세요."
        const val LOAD_PROFILE_FAILED_MESSAGE =
            "프로필 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        const val UPDATE_PROFILE_FAILED_MESSAGE =
            "신체 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val UPDATE_NICKNAME_FAILED_MESSAGE =
            "닉네임을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val UPDATE_PASSWORD_FAILED_MESSAGE =
            "비밀번호를 변경하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val DELETE_ME_FAILED_MESSAGE =
            "회원 탈퇴를 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
}
