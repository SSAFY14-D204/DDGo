package com.ddgo.app.core.network

import android.util.Log
import com.ddgo.app.BuildConfig
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.RefreshTokenRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "AuthInterceptor"
private const val AUTH_RETRY_HEADER = "X-Auth-Retry"

/**
 * 모든 API 요청에 자동으로 Authorization 헤더를 주입하는 OkHttp 인터셉터.
 *
 * TokenDataStore에서 accessToken을 읽어 "Bearer {token}" 형태로 헤더에 추가합니다.
 * 토큰이 없는 경우(로그인 전)에는 헤더를 추가하지 않습니다.
 *
 * 추가 규칙:
 * - 현재 백엔드는 만료 토큰에 대해 401 대신 403을 반환하는 경우가 있습니다.
 * - 그래서 403 응답을 받으면 refreshToken으로 한 번 더 토큰 재발급을 시도합니다.
 */
class AuthInterceptor @Inject constructor(
    private val tokenDataStore: TokenDataStore,
    @Named("AuthOkHttpClient") private val authApi: AuthApi
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath
        val token = runBlocking { tokenDataStore.accessToken.first() }
        val hasToken = !token.isNullOrEmpty()

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "intercept: method=${originalRequest.method}, path=$path, " +
                    "hasToken=$hasToken, tokenLength=${token?.length ?: 0}"
            )
        }

        val request = if (hasToken && !isAuthExcludedPath(path)) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "intercept: authorizationHeaderAttached=${request.header("Authorization") != null}"
            )
        }

        var response = chain.proceed(request)
        if (shouldRetryWithRefresh(request, response.code)) {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "intercept: received 403, trying token refresh for path=${request.url.encodedPath}"
                )
            }

            val refreshedAccessToken = runBlocking { refreshAccessToken() }
            if (!refreshedAccessToken.isNullOrEmpty()) {
                response.close()
                val retriedRequest = request.newBuilder()
                    .header("Authorization", "Bearer $refreshedAccessToken")
                    .header(AUTH_RETRY_HEADER, "true")
                    .build()

                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "intercept: retry request with refreshed access token, " +
                            "path=${retriedRequest.url.encodedPath}"
                    )
                }

                response = chain.proceed(retriedRequest)
            }
        }

        return response
    }

    private fun shouldRetryWithRefresh(request: Request, responseCode: Int): Boolean {
        if (responseCode != 403) return false
        if (request.header(AUTH_RETRY_HEADER) != null) return false
        if (request.header("Authorization").isNullOrEmpty()) return false

        return !isAuthExcludedPath(request.url.encodedPath)
    }

    private fun isAuthExcludedPath(path: String): Boolean {
        return path == "/v1/users/login" ||
            path == "/v1/users/register" ||
            path == "/v1/users/refresh"
    }

    private suspend fun refreshAccessToken(): String? {
        val refreshToken = tokenDataStore.refreshToken.first()
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "refreshAccessToken: hasRefreshToken=${!refreshToken.isNullOrEmpty()}, " +
                    "refreshTokenLength=${refreshToken?.length ?: 0}"
            )
        }

        if (refreshToken.isNullOrEmpty()) {
            tokenDataStore.clearTokensBySessionExpiry()
            return null
        }

        return try {
            val refreshResponse = authApi.refresh(RefreshTokenRequestDto(refreshToken))
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "refreshAccessToken: success=${refreshResponse.success}, " +
                        "hasData=${refreshResponse.data != null}"
                )
            }

            if (refreshResponse.success && refreshResponse.data != null) {
                tokenDataStore.saveTokens(
                    refreshResponse.data.accessToken,
                    refreshResponse.data.refreshToken
                )
                refreshResponse.data.accessToken
            } else {
                tokenDataStore.clearTokensBySessionExpiry()
                null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "refreshAccessToken: refresh failed", e)
            }
            tokenDataStore.clearTokensBySessionExpiry()
            null
        }
    }
}
