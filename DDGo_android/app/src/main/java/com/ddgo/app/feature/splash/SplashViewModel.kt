package com.ddgo.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.core.network.JwtTokenParser
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.common.ApiErrorResponse
import com.ddgo.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import retrofit2.HttpException

private const val SPLASH_DELAY_MS = 1_500L
private const val REFRESH_RETRY_DELAY_MS = 500L
private const val AUTHORIZATION_PREFIX = "Bearer "

private val invalidSessionStatusCodes = setOf(401, 403)
private val invalidSessionErrorCodes = setOf("A001", "A002", "A003", "U001")

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    private val authRepository: AuthRepository,
    @Named("AuthOkHttpClient") private val splashAuthApi: AuthApi,
    private val json: Json
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<SplashNavigationEvent>()
    val navigationEvent: SharedFlow<SplashNavigationEvent> = _navigationEvent.asSharedFlow()

    init {
        checkLoginStatus()
    }

    private fun checkLoginStatus() {
        viewModelScope.launch {
            delay(SPLASH_DELAY_MS)

            val accessToken = tokenDataStore.accessToken.first()
            if (!accessToken.isNullOrEmpty() && !JwtTokenParser.isExpired(accessToken)) {
                validateSessionAndNavigate(accessToken)
                return@launch
            }

            val refreshToken = tokenDataStore.refreshToken.first()
            if (!refreshToken.isNullOrEmpty()) {
                val refreshSuccess = tryRefreshToken(refreshToken, maxRetries = 2)
                if (refreshSuccess) {
                    val refreshedAccessToken = tokenDataStore.accessToken.first()
                    if (!refreshedAccessToken.isNullOrEmpty()) {
                        validateSessionAndNavigate(refreshedAccessToken)
                        return@launch
                    }
                }
            }

            tokenDataStore.clearTokens()
            _navigationEvent.emit(SplashNavigationEvent.NavigateToAuth)
        }
    }

    private suspend fun validateSessionAndNavigate(accessToken: String) {
        when (validateServerSession(accessToken)) {
            ServerSessionValidation.Valid,
            ServerSessionValidation.UnknownFailure -> {
                _navigationEvent.emit(SplashNavigationEvent.NavigateToMain)
            }

            ServerSessionValidation.InvalidSession -> {
                tokenDataStore.clearTokens()
                _navigationEvent.emit(SplashNavigationEvent.NavigateToAuth)
            }
        }
    }

    private suspend fun validateServerSession(accessToken: String): ServerSessionValidation {
        return try {
            val response = splashAuthApi.getMyInfoWithAuthorization(
                authorization = AUTHORIZATION_PREFIX + accessToken
            )
            if (response.success && response.data != null) {
                ServerSessionValidation.Valid
            } else {
                ServerSessionValidation.UnknownFailure
            }
        } catch (throwable: Throwable) {
            if (throwable.isInvalidSessionValidationFailure(json)) {
                ServerSessionValidation.InvalidSession
            } else {
                ServerSessionValidation.UnknownFailure
            }
        }
    }

    /**
     * refreshToken으로 토큰 재발급을 시도합니다. 최대 [maxRetries]번 재시도합니다.
     * @return 성공 시 true, 모두 실패 시 false
     */
    private suspend fun tryRefreshToken(refreshToken: String, maxRetries: Int): Boolean {
        repeat(maxRetries) { attempt ->
            val result = authRepository.refreshToken(refreshToken)
            if (result.isSuccess) return true
            if (attempt < maxRetries - 1) delay(REFRESH_RETRY_DELAY_MS)
        }
        return false
    }
}

internal fun Throwable.isInvalidSessionValidationFailure(json: Json): Boolean {
    val httpException = findHttpException() ?: return false

    if (httpException.code() in invalidSessionStatusCodes) {
        return true
    }

    val errorCode = runCatching {
        val rawBody = httpException.response()
            ?.errorBody()
            ?.string()
            ?.takeIf { it.isNotBlank() }
            ?: return@runCatching null
        json.decodeFromString(ApiErrorResponse.serializer(), rawBody).code
    }.getOrNull()

    return errorCode in invalidSessionErrorCodes
}

internal fun Throwable.findHttpException(): HttpException? {
    var current: Throwable? = this
    while (current != null) {
        if (current is HttpException) {
            return current
        }
        current = current.cause
    }
    return null
}

private enum class ServerSessionValidation {
    Valid,
    InvalidSession,
    UnknownFailure
}

sealed class SplashNavigationEvent {
    object NavigateToAuth : SplashNavigationEvent()
    object NavigateToMain : SplashNavigationEvent()
}
