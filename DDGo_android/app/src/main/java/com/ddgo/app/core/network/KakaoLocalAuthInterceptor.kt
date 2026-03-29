package com.ddgo.app.core.network

import com.ddgo.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Kakao Local API 전용 인증 인터셉터.
 *
 * 역할:
 * - 모든 Kakao Local API 요청에 Authorization 헤더를 자동으로 추가합니다.
 *
 * 주의:
 * - Kakao Local API는 "KakaoAK {REST_API_KEY}" 형식의 헤더를 요구합니다.
 * - 이 인터셉터는 외부 API 전용이므로 core/network에 두는 것이 구조상 자연스럽습니다.
 */
class KakaoLocalAuthInterceptor @Inject constructor() : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
            .build()

        return chain.proceed(request)
    }
}
