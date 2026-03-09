package com.ddgo.app.di

import com.ddgo.app.BuildConfig
import com.ddgo.app.core.network.AuthInterceptor
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
}
