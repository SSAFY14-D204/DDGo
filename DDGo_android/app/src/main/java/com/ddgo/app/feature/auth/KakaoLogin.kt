package com.ddgo.app.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.ddgo.app.BuildConfig
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

private const val TAG = "KakaoLogin"

internal fun startKakaoLogin(
    context: Context,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        onError(AuthStrings.KakaoNotConfigured)
        return
    }

    val activity = context.findActivity()
    if (activity == null) {
        onError(AuthStrings.KakaoLoginFailed)
        return
    }

    val accountLoginCallback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
        handleKakaoLoginResult(
            activity = activity,
            token = token,
            error = error,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    if (UserApiClient.instance.isKakaoTalkLoginAvailable(activity)) {
        UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
            when {
                error is ClientError && error.reason == ClientErrorCause.Cancelled -> {
                    Log.w(TAG, "KakaoTalk login cancelled by user")
                }
                error != null -> {
                    Log.w(TAG, "KakaoTalk login failed, fallback to Kakao account: ${error.message}", error)
                    UserApiClient.instance.loginWithKakaoAccount(
                        activity,
                        callback = accountLoginCallback
                    )
                }
                else -> handleKakaoLoginResult(
                    activity = activity,
                    token = token,
                    error = error,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        }
        return
    }

    UserApiClient.instance.loginWithKakaoAccount(activity, callback = accountLoginCallback)
}

private fun handleKakaoLoginResult(
    activity: Activity,
    token: OAuthToken?,
    error: Throwable?,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    when {
        error != null -> {
            Log.e(TAG, "Kakao account login failed: ${error.message}", error)
            onError(AuthStrings.KakaoLoginFailed)
        }
        token != null -> {
            Log.d(TAG, "Kakao login token received")
            ensureRequiredKakaoScopes(
                activity = activity,
                accessToken = token.accessToken,
                onSuccess = onSuccess,
                onError = onError
            )
        }
        else -> {
            Log.e(TAG, "Kakao login finished without token and without error")
            onError(AuthStrings.KakaoLoginFailed)
        }
    }
}

private fun ensureRequiredKakaoScopes(
    activity: Activity,
    accessToken: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    UserApiClient.instance.me { user, error ->
        if (error != null || user == null) {
            if (error != null) {
                Log.w(TAG, "Failed to inspect Kakao scopes, continuing with current token: ${error.message}", error)
            }
            onSuccess(accessToken)
            return@me
        }

        val profileNeedsAgreement = user.kakaoAccount?.profileNeedsAgreement == true
        val emailNeedsAgreement = user.kakaoAccount?.emailNeedsAgreement == true

        if (emailNeedsAgreement) {
            Log.d(TAG, "Kakao email scope available but skipped because nickname-only sync is enabled")
        }

        if (!profileNeedsAgreement) {
            Log.d(TAG, "No additional Kakao nickname scopes required")
            onSuccess(accessToken)
            return@me
        }

        val scopes = listOf("profile")
        Log.d(TAG, "Requesting additional Kakao scopes for nickname sync: $scopes")
        UserApiClient.instance.loginWithNewScopes(activity, scopes) { token, scopeError ->
            when {
                scopeError is ClientError && scopeError.reason == ClientErrorCause.Cancelled -> {
                    Log.w(TAG, "Additional Kakao scope request cancelled by user")
                }
                scopeError != null -> {
                    Log.e(TAG, "Additional Kakao scope request failed: ${scopeError.message}", scopeError)
                    onError(AuthStrings.KakaoConsentFailed)
                }
                token != null -> {
                    Log.d(TAG, "Additional Kakao scopes granted")
                    onSuccess(token.accessToken)
                }
                else -> {
                    Log.e(TAG, "Additional Kakao scope request finished without token and without error")
                    onError(AuthStrings.KakaoConsentFailed)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
