package com.ddgo.app.di

import com.ddgo.app.BuildConfig
import com.ddgo.app.core.network.AuthInterceptor
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
import javax.inject.Singleton

/**
 * 네트워크 관련 의존성을 제공하는 Hilt 모듈.
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

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY  // 디버그: 전체 로그
            } else {
                HttpLoggingInterceptor.Level.NONE  // 릴리즈: 로그 없음
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)   // 토큰 헤더 자동 주입
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
