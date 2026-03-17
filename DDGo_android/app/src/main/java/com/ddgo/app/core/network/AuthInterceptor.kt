package com.ddgo.app.core.network

import android.util.Log
import com.ddgo.app.BuildConfig
import com.ddgo.app.core.datastore.TokenDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private const val TAG = "AuthInterceptor"

/**
 * 모든 API 요청에 자동으로 Authorization 헤더를 주입하는 OkHttp 인터셉터.
 *
 * TokenDataStore에서 accessToken을 읽어 "Bearer {token}" 형태로 헤더에 추가합니다.
 * 토큰이 없는 경우(로그인 전)에는 헤더를 추가하지 않습니다.
 */
class AuthInterceptor @Inject constructor(
    private val tokenDataStore: TokenDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenDataStore.accessToken.first() }
        val hasToken = !token.isNullOrEmpty()

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "intercept: method=${chain.request().method}, path=${chain.request().url.encodedPath}, " +
                    "hasToken=$hasToken, tokenLength=${token?.length ?: 0}"
            )
        }

        val request = if (hasToken) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "intercept: authorizationHeaderAttached=${request.header("Authorization") != null}"
            )
        }

        return chain.proceed(request)
    }
}
