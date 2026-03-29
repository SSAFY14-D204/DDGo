package com.ddgo.app.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.core.network.JwtTokenParser
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.UserResponseDto
import com.ddgo.app.data.remote.common.ApiErrorResponse
import com.ddgo.app.domain.repository.AuthRepository
import com.ddgo.app.navigation.ScreenRoutes
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
    private val onboardingPreferenceDataStore: OnboardingPreferenceDataStore,
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

            val hasCompletedOnboarding = onboardingPreferenceDataStore.hasCompletedOnboarding.first()
            val hasAuthenticatedOnce = onboardingPreferenceDataStore.hasAuthenticatedOnce.first()
            val resolvedDestination = resolveAuthenticatedDestination()

            val navigationEvent = when (resolvedDestination) {
                ResolvedDestination.Auth -> {
                    if (hasAuthenticatedOnce) {
                        SplashNavigationEvent.NavigateToLoginEmail
                    } else {
                        SplashNavigationEvent.NavigateToWelcome
                    }
                }

                is ResolvedDestination.Main -> {
                    onboardingPreferenceDataStore.setHasAuthenticatedOnce()
                    when {
                        !hasCompletedOnboarding -> SplashNavigationEvent.NavigateToOnboarding(
                            nextRoute = ScreenRoutes.MainGraph.route,
                            showEntryGuide = true
                        )

                        resolvedDestination.requiresRecoveryOnboarding ->
                            SplashNavigationEvent.NavigateToOnboarding(
                                nextRoute = ScreenRoutes.MainGraph.route,
                                showEntryGuide = false
                            )

                        else -> SplashNavigationEvent.NavigateToMain
                    }
                }
            }

            _navigationEvent.emit(navigationEvent)
        }
    }

    private suspend fun resolveAuthenticatedDestination(): ResolvedDestination {
        val accessToken = tokenDataStore.accessToken.first()
        if (!accessToken.isNullOrEmpty() && !JwtTokenParser.isExpired(accessToken)) {
            return resolveServerValidatedDestination(accessToken)
        }

        val refreshToken = tokenDataStore.refreshToken.first()
        if (!refreshToken.isNullOrEmpty()) {
            val refreshSuccess = tryRefreshToken(refreshToken, maxRetries = 2)
            if (refreshSuccess) {
                val refreshedAccessToken = tokenDataStore.accessToken.first()
                if (!refreshedAccessToken.isNullOrEmpty()) {
                    return resolveServerValidatedDestination(refreshedAccessToken)
                }
            }
        }

        tokenDataStore.clearTokens()
        return ResolvedDestination.Auth
    }

    private suspend fun resolveServerValidatedDestination(accessToken: String): ResolvedDestination {
        return when (val validation = validateServerSession(accessToken)) {
            is ServerSessionValidation.Valid -> ResolvedDestination.Main(
                requiresRecoveryOnboarding = validationUserRequiresRecoveryOnboarding(
                    user = validation.user
                )
            )
            ServerSessionValidation.UnknownFailure -> ResolvedDestination.Main(
                requiresRecoveryOnboarding = false
            )

            ServerSessionValidation.InvalidSession -> {
                tokenDataStore.clearTokens()
                ResolvedDestination.Auth
            }
        }
    }

    private suspend fun validateServerSession(accessToken: String): ServerSessionValidation {
        return try {
            val response = splashAuthApi.getMyInfoWithAuthorization(
                authorization = AUTHORIZATION_PREFIX + accessToken
            )
            if (response.success && response.data != null) {
                ServerSessionValidation.Valid(response.data)
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

private sealed interface ServerSessionValidation {
    data class Valid(val user: UserResponseDto) : ServerSessionValidation
    data object InvalidSession : ServerSessionValidation
    data object UnknownFailure : ServerSessionValidation
}

private sealed interface ResolvedDestination {
    data object Auth : ResolvedDestination
    data class Main(val requiresRecoveryOnboarding: Boolean) : ResolvedDestination
}

private fun validationUserRequiresRecoveryOnboarding(user: UserResponseDto): Boolean {
    return user.sex.isNullOrBlank() &&
        user.heightCm.isMissingBodyMetric() &&
        user.weightKg.isMissingBodyMetric() &&
        user.wingspanCm.isMissingBodyMetric()
}

sealed class SplashNavigationEvent {
    data object NavigateToWelcome : SplashNavigationEvent()
    data object NavigateToLoginEmail : SplashNavigationEvent()
    data object NavigateToMain : SplashNavigationEvent()
    data class NavigateToOnboarding(
        val nextRoute: String,
        val showEntryGuide: Boolean
    ) : SplashNavigationEvent()
}

private fun Float?.isMissingBodyMetric(): Boolean = this == null || this <= 0f
