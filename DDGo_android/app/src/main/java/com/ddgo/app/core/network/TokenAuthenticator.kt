package com.ddgo.app.core.network

import android.util.Log
import com.ddgo.app.BuildConfig
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.RefreshTokenRequestDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Named

private const val TAG = "TokenAuthenticator"

/**
 * 401 Unauthorized 응답을 받았을 때 자동으로 토큰을 재발급하는 OkHttp Authenticator.
 *
 * ## 동작 흐름
 * 1. 401 수신 → DataStore에서 refreshToken 읽기
 * 2. refreshToken 없음 → null 반환 (재시도 없이 그대로 401 전파)
 * 3. POST /v1/users/refresh 호출
 * 4. 성공 → 새 AT/RT를 DataStore에 저장 후 원래 요청을 새 AT로 재시도
 * 5. 실패 → DataStore 토큰 삭제(강제 로그아웃) 후 null 반환
 *
 * ## 순환 의존성 방지
 * Retrofit → OkHttpClient → Authenticator → Retrofit 순환이 생기기 때문에
 * `@Named("AuthOkHttpClient")`로 인터셉터가 없는 별도 OkHttpClient를 사용하는
 * AuthApi를 주입받습니다. (NetworkModule 참고)
 */
class TokenAuthenticator @Inject constructor(
    @Named("AuthOkHttpClient") private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "authenticate: code=${response.code}, path=${response.request.url.encodedPath}"
            )
        }

        // 연속 재시도 방지: 이미 한 번 재시도한 경우 포기
        if (response.priorResponse?.code == 401) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "authenticate: prior 401 exists, skip retry")
            }
            return null
        }

        val refreshToken = runBlocking { tokenDataStore.refreshToken.first() }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "authenticate: hasRefreshToken=${!refreshToken.isNullOrEmpty()}, " +
                    "refreshTokenLength=${refreshToken?.length ?: 0}"
            )
        }

        // RefreshToken 없으면 로그인 화면으로 보내야 함 (재시도 없음)
        if (refreshToken.isNullOrEmpty()) return null

        return runBlocking {
            try {
                val refreshResponse = authApi.refresh(RefreshTokenRequestDto(refreshToken))
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "authenticate: refresh success=${refreshResponse.success}, " +
                            "hasData=${refreshResponse.data != null}"
                    )
                }

                if (refreshResponse.success && refreshResponse.data != null) {
                    // 새 토큰 저장
                    tokenDataStore.saveTokens(
                        refreshResponse.data.accessToken,
                        refreshResponse.data.refreshToken
                    )
                    // 원래 요청에 새 AT를 붙여서 재시도
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${refreshResponse.data.accessToken}")
                        .build()
                } else {
                    // 리프레시 실패 → 강제 로그아웃 처리
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "authenticate: refresh failed, clear tokens")
                    }
                    tokenDataStore.clearTokens()
                    null
                }
            } catch (e: Exception) {
                // 네트워크 오류 등 예외 발생 → 강제 로그아웃 처리
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "authenticate: refresh exception", e)
                }
                tokenDataStore.clearTokens()
                null
            }
        }
    }
}
