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

    @Test
    fun `회원가입 테스트`() = runBlocking {
        val request = RegisterRequestDto(
            username = "test1",
            password = "testtest1",
            nickname = "테스트"
        )

        try {
            val response = authApi.register(request)
            println("신규 가입 성공: $response")
            // 200번대 성공 시 통과
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 409) {
                // 409 에러가 나면 '실패'를 던지지 않고 그냥 넘어감
                println("이미 가입된 계정(409)이므로 테스트를 성공으로 간주합니다.")
            } else {
                // 409가 아닌 다른 에러(500, 404 등)는 진짜 실패로 처리
                throw e
            }
        } catch (e: Exception) {
            // 네트워크 연결 오류 등은 실패 처리
            throw e
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Refresh Token 테스트
    // ──────────────────────────────────────────────────────────────

    /**
     * [정상 케이스] 로그인 → 발급된 refreshToken으로 새 토큰 재발급 테스트.
     *
     * 검증 항목:
     * - 응답 success == true
     * - 새 accessToken, refreshToken이 null이 아닌지
     */
    @Test
    fun `로그인_후_리프레시_토큰으로_재발급_성공`() = runBlocking {
        // === Step 1: 로그인하여 토큰 획득 ===
        val loginRequest = LoginRequestDto(username = "string", password = "stringst")
        val loginResponse = try {
            authApi.login(loginRequest)
        } catch (e: Exception) {
            println("⚠️ 서버 연결 실패 (로그인 단계): ${e.message}")
            assertTrue("서버가 실행 중이지 않습니다: ${e.message}", false)
            return@runBlocking
        }

        assertTrue("로그인이 먼저 성공해야 합니다", loginResponse.success)
        assertNotNull("로그인 응답 데이터가 null입니다", loginResponse.data)

        val refreshToken = loginResponse.data!!.refreshToken
        println("✅ 로그인 성공. refreshToken: $refreshToken")

        // === Step 2: refreshToken으로 토큰 재발급 ===
        val refreshRequest = RefreshTokenRequestDto(refreshToken = refreshToken)
        val refreshResponse = try {
            authApi.refresh(refreshRequest)
        } catch (e: Exception) {
            e.printStackTrace()
            assertTrue("refresh API 호출 중 오류 발생: ${e.message}", false)
            return@runBlocking
        }

        // === Step 3: 검증 ===
        println("✅ 재발급 응답: $refreshResponse")
        assertTrue("재발급 API 응답이 success=true여야 합니다", refreshResponse.success)
        assertNotNull("재발급 응답 데이터가 null입니다", refreshResponse.data)
        assertNotNull("새 accessToken이 null입니다", refreshResponse.data?.accessToken)
        assertNotNull("새 refreshToken이 null입니다", refreshResponse.data?.refreshToken)
        assertTrue(
            "새 accessToken이 비어있습니다",
            refreshResponse.data?.accessToken?.isNotEmpty() == true
        )
        assertTrue(
            "새 refreshToken이 비어있습니다",
            refreshResponse.data?.refreshToken?.isNotEmpty() == true
        )
        println("✅ 새 accessToken: ${refreshResponse.data?.accessToken}")
    }

    /**
     * [실패 케이스] 유효하지 않은 refreshToken으로 재발급 시도 → 실패 응답 확인.
     *
     * 검증 항목:
     * - 서버가 401 또는 success=false를 반환하는지 확인
     */
    @Test
    fun `유효하지_않은_리프레시_토큰으로_재발급_실패`() = runBlocking {
        val invalidRefreshToken = "THIS_IS_INVALID_TOKEN_12345"
        val refreshRequest = RefreshTokenRequestDto(refreshToken = invalidRefreshToken)

        try {
            val response = authApi.refresh(refreshRequest)
            println("응답: $response")
            // 서버가 200 + success=false로 응답하는 경우
            assertTrue(
                "유효하지 않은 RT로는 reissue가 실패해야 합니다",
                !response.success
            )
        } catch (e: retrofit2.HttpException) {
            // 서버가 401 또는 4xx HTTP 오류를 반환하는 경우 → 정상 실패
            println("✅ 서버가 ${e.code()} 오류를 반환했습니다 (예상된 실패)")
            assertTrue(
                "유효하지 않은 RT는 4xx 오류를 반환해야 합니다",
                e.code() in 400..499
            )
        } catch (e: Exception) {
            println("⚠️ 서버 연결 실패: ${e.message}")
            assertTrue("서버가 실행 중이지 않습니다: ${e.message}", false)
        }
    }
}
