package com.ddgo.app.di

import com.ddgo.app.BuildConfig
import com.ddgo.app.core.network.AuthInterceptor
import com.ddgo.app.core.network.KakaoLocalAuthInterceptor
import com.ddgo.app.core.network.TokenAuthenticator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 네트워크 관련 의존성을 제공하는 Hilt 모듈.
 *
 * ## OkHttpClient 종류
 * - 기본 OkHttpClient: AuthInterceptor(AT 주입) + TokenAuthenticator(401 시 RT 재발급) 포함
 * - @Named("AuthOkHttpClient") OkHttpClient: TokenAuthenticator 전용. 인터셉터 없음.
 *   → TokenAuthenticator가 Retrofit을 직접 참조하면 순환 의존성이 생기므로
 *     별도 클라이언트를 사용하는 AuthApi를 주입해 이를 방지합니다.
 *
 * 새 API를 추가할 때:
 *   1. data/remote/{기능}/YourApi.kt 인터페이스를 만드세요.
 *   2. 이 모듈에 @Provides fun provideYourApi(retrofit: Retrofit): YourApi 함수를 추가하세요.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true  // 서버 필드가 추가돼도 앱이 깨지지 않음
        coerceInputValues = true  // null을 기본값으로 처리
    }

    // ──────────────────────────────────────────────────────────────
    // [1] 순환 의존성 방지용: 인터셉터 없는 순수 OkHttpClient
    //     TokenAuthenticator에서 refresh API를 호출할 때만 사용
    // ──────────────────────────────────────────────────────────────
    @Provides
    @Singleton
    @Named("AuthOkHttpClient")
    fun provideAuthOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // ──────────────────────────────────────────────────────────────
    // [1-1] AI 서버 전용 순수 OkHttpClient
    //     FastAPI AI 서버는 인증 인터셉터 없이 호출합니다.
    // ──────────────────────────────────────────────────────────────
    @Provides
    @Singleton
    @Named("AiServerOkHttpClient")
    fun provideAiServerOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .build()
    }

    // ──────────────────────────────────────────────────────────────
    // [2] 순환 의존성 방지용: AuthOkHttpClient를 사용하는 별도 Retrofit
    // ──────────────────────────────────────────────────────────────
    @Provides
    @Singleton
    @Named("AuthOkHttpClient")
    fun provideAuthRetrofit(
        @Named("AuthOkHttpClient") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @Named("AuthOkHttpClient")
    fun provideAuthApiForAuthenticator(
        @Named("AuthOkHttpClient") retrofit: Retrofit
    ): com.ddgo.app.data.remote.auth.AuthApi {
        return retrofit.create(com.ddgo.app.data.remote.auth.AuthApi::class.java)
    }

    // ──────────────────────────────────────────────────────────────
    // [2-1] AI 서버 전용 Retrofit
    // ──────────────────────────────────────────────────────────────
    @Provides
    @Singleton
    @Named("AiServerRetrofit")
    fun provideAiServerRetrofit(
        @Named("AiServerOkHttpClient") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.AI_SERVER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // ──────────────────────────────────────────────────────────────
    // [3] 메인 OkHttpClient: AuthInterceptor + TokenAuthenticator 포함
    // ──────────────────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)       // 모든 요청에 AT 헤더 주입
            .authenticator(tokenAuthenticator)     // 401 시 RT로 재발급 후 재시도
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): com.ddgo.app.data.remote.auth.AuthApi {
        return retrofit.create(com.ddgo.app.data.remote.auth.AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideReportApi(retrofit: Retrofit): com.ddgo.app.data.remote.report.ReportApi {
        return retrofit.create(com.ddgo.app.data.remote.report.ReportApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUploadApi(retrofit: Retrofit): com.ddgo.app.data.remote.upload.UploadApi {
        return retrofit.create(com.ddgo.app.data.remote.upload.UploadApi::class.java)
    }

    @Provides
    @Singleton
    fun provideChallengeApi(
        retrofit: Retrofit
    ): com.ddgo.app.data.remote.challenge.ChallengeApi {
        return retrofit.create(com.ddgo.app.data.remote.challenge.ChallengeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCommunityApi(
        retrofit: Retrofit
    ): com.ddgo.app.data.remote.community.CommunityApi {
        return retrofit.create(com.ddgo.app.data.remote.community.CommunityApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAttemptApi(
        retrofit: Retrofit
    ): com.ddgo.app.data.remote.attempt.AttemptApi {
        return retrofit.create(com.ddgo.app.data.remote.attempt.AttemptApi::class.java)
    }

    /**
     * AI 분석 API 구현체를 제공합니다.
     */
    @Provides
    @Singleton
    fun provideAiAnalysisApi(
        @Named("AiServerRetrofit") retrofit: Retrofit
    ): com.ddgo.app.data.remote.ai.AiAnalysisApi {
        return retrofit.create(com.ddgo.app.data.remote.ai.AiAnalysisApi::class.java)
    }

    /**
     * presigned 직접 업로드 전용 순수 OkHttpClient를 제공합니다.
     *
     * 규칙:
     * - presigned URL 업로드에는 인증 헤더가 섞이면 안 됩니다.
     * - 그래서 이 클라이언트는 AuthInterceptor와 TokenAuthenticator를 의도적으로 제외합니다.
     */
    @Provides
    @Singleton
    @Named("DirectUploadOkHttpClient")
    fun provideDirectUploadOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Kakao Local API 전용 OkHttpClient를 제공합니다.
     *
     * 역할:
     * - Kakao 인증 헤더 추가
     * - 디버그 환경에서 HTTP 로그 출력
     */
    @Provides
    @Singleton
    @Named("KakaoLocalOkHttpClient")
    fun provideKakaoLocalOkHttpClient(
        kakaoLocalAuthInterceptor: KakaoLocalAuthInterceptor
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(kakaoLocalAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Kakao Local API 전용 Retrofit을 제공합니다.
     *
     * 주의:
     * - DDGo backend Retrofit과 baseUrl이 다르므로 별도 Named Retrofit을 사용합니다.
     */
    @Provides
    @Singleton
    @Named("KakaoLocalRetrofit")
    fun provideKakaoLocalRetrofit(
        @Named("KakaoLocalOkHttpClient") okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.KAKAO_LOCAL_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    /**
     * KakaoLocalApi 구현체를 제공합니다.
     */
    @Provides
    @Singleton
    fun provideKakaoLocalApi(
        @Named("KakaoLocalRetrofit") retrofit: Retrofit
    ): com.ddgo.app.data.remote.kakao.KakaoLocalApi {
        return retrofit.create(com.ddgo.app.data.remote.kakao.KakaoLocalApi::class.java)
    }

    /**
     * DDGo backend의 GymApi 구현체를 제공합니다.
     */
    @Provides
    @Singleton
    fun provideGymApi(retrofit: Retrofit): com.ddgo.app.data.remote.gym.GymApi {
        return retrofit.create(com.ddgo.app.data.remote.gym.GymApi::class.java)
    }
}
