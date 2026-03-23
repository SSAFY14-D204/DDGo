package com.ddgo.app.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.ddgo.app.BuildConfig
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient

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
                error is ClientError && error.reason == ClientErrorCause.Cancelled -> Unit
                error != null -> UserApiClient.instance.loginWithKakaoAccount(
                    activity,
                    callback = accountLoginCallback
                )
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
        error != null -> onError(AuthStrings.KakaoLoginFailed)
        token != null -> ensureRequiredKakaoScopes(
            activity = activity,
            accessToken = token.accessToken,
            onSuccess = onSuccess,
            onError = onError
        )
        else -> onError(AuthStrings.KakaoLoginFailed)
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
            onSuccess(accessToken)
            return@me
        }

        val scopes = buildList {
            if (user.kakaoAccount?.profileNeedsAgreement == true) {
                add("profile")
            }
            if (user.kakaoAccount?.emailNeedsAgreement == true) {
                add("account_email")
            }
        }

        if (scopes.isEmpty()) {
            onSuccess(accessToken)
            return@me
        }

        UserApiClient.instance.loginWithNewScopes(activity, scopes) { token, scopeError ->
            when {
                scopeError is ClientError && scopeError.reason == ClientErrorCause.Cancelled -> Unit
                scopeError != null -> onError(AuthStrings.KakaoConsentFailed)
                token != null -> onSuccess(token.accessToken)
                else -> onError(AuthStrings.KakaoConsentFailed)
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
