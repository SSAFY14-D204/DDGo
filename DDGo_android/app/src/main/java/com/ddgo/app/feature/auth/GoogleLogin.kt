package com.ddgo.app.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CustomCredential
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.ddgo.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.SecureRandom
import android.util.Base64

private const val TAG = "GoogleLogin"

internal data class GoogleLoginResult(
    val idToken: String,
    val displayName: String?
)

internal suspend fun startGoogleLogin(
    context: Context
): Result<GoogleLoginResult> {
    if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
        return Result.failure(IllegalStateException(AuthStrings.GoogleNotConfigured))
    }

    val activity = context.findActivity()
        ?: return Result.failure(IllegalStateException(AuthStrings.GoogleLoginFailed))

    val credentialManager = CredentialManager.create(context)
    val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
        serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    )
        .setNonce(generateSecureRandomNonce())
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInWithGoogleOption)
        .build()

    return try {
        val result = credentialManager.getCredential(
            request = request,
            context = activity
        )
        Result.success(result.toGoogleLoginResult())
    } catch (exception: GetCredentialCancellationException) {
        val message = exception.message.orEmpty()
        if (message.contains("reauth failed", ignoreCase = true)) {
            Log.e(TAG, "Google account reauth failed: $message", exception)
            Result.failure(IllegalStateException(AuthStrings.GoogleAccountReauthFailed, exception))
        } else {
            Log.w(TAG, "Google login cancelled: $message", exception)
            Result.failure(IllegalStateException(AuthStrings.GoogleLoginFailed, exception))
        }
    } catch (exception: GetCredentialException) {
        Log.e(TAG, "Google login credential error: ${exception.javaClass.simpleName}: ${exception.message}", exception)
        Result.failure(IllegalStateException(AuthStrings.GoogleLoginFailed, exception))
    }
}

private fun GetCredentialResponse.toGoogleLoginResult(): GoogleLoginResult {
    val credential = credential
    val isGoogleIdCredential = credential is CustomCredential && (
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
        )
    if (!isGoogleIdCredential) {
        Log.e(TAG, "Unexpected credential type: ${credential.javaClass.simpleName}/${credential.type}")
        throw IllegalStateException(AuthStrings.GoogleLoginFailed)
    }

    return try {
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        GoogleLoginResult(
            idToken = googleCredential.idToken,
            displayName = googleCredential.displayName
        )
    } catch (exception: GoogleIdTokenParsingException) {
        Log.e(TAG, "Failed to parse Google ID token credential: ${exception.message}", exception)
        throw IllegalStateException(AuthStrings.GoogleLoginFailed, exception)
    }
}

private fun generateSecureRandomNonce(byteLength: Int = 32): String {
    val randomBytes = ByteArray(byteLength)
    SecureRandom().nextBytes(randomBytes)
    return Base64.encodeToString(
        randomBytes,
        Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
    )
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
