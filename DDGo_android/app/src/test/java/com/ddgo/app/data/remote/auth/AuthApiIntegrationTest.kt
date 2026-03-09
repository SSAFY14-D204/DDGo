package com.ddgo.app.data.remote.auth

import com.ddgo.app.data.remote.common.ApiResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 실서버(localhost:8080)와의 통신을 테스트하는 통합 테스트 코드입니다.
 * 실제 서버가 실행 중이어야 테스트가 통과합니다.
 */
class AuthApiIntegrationTest {

    private lateinit var authApi: AuthApi
    private val baseUrl = "http://localhost:8080/"

    @Before
    fun setUp() {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        val okHttpClient = OkHttpClient.Builder().build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApi = retrofit.create(AuthApi::class.java)
    }

    @Test
    fun `실제_서버_로그인_테스트`() = runBlocking {
        // Given: 실제 서버에 존재하는 계정 정보 (상황에 맞게 수정 필요)
        val request = LoginRequestDto(
            username = "string",
            password = "stringst"
        )

        try {
            // When
            val response: ApiResponse<LoginResponseDto> = authApi.login(request)

            // Then
            println("Response: $response")
            assertNotNull(response)
            // 서버 스펙에 따라 success 여부 확인
            // assertTrue("API 요청은 성공해야 합니다", response.success)
            
        } catch (e: Exception) {
            e.printStackTrace()
            assertTrue("서버가 실행 중이지 않거나 연결 오류가 발생했습니다: ${e.message}", false)
        }
    }
}
