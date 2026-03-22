package com.ddgo.app.data.remote.auth

import com.ddgo.app.BuildConfig
import com.ddgo.app.data.remote.common.ApiResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Integration tests that call the same backend base URL the app uses.
 *
 * Login-based tests require dedicated test credentials.
 * Set one of the following before running them:
 * - Gradle/JVM properties: auth.test.username, auth.test.password
 * - Environment variables: AUTH_TEST_USERNAME, AUTH_TEST_PASSWORD
 */
class AuthApiIntegrationTest {

    private lateinit var authApi: AuthApi
    private val baseUrl = BuildConfig.BASE_URL.ensureTrailingSlash()
    private val loginUsername = System.getProperty(TEST_USERNAME_PROPERTY)
        ?: System.getenv(TEST_USERNAME_ENV)
    private val loginPassword = System.getProperty(TEST_PASSWORD_PROPERTY)
        ?: System.getenv(TEST_PASSWORD_ENV)

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
    fun `login endpoint responds with configured dev account`() = runBlocking {
        requireLoginCredentials()

        val request = LoginRequestDto(
            username = loginUsername.orEmpty(),
            password = loginPassword.orEmpty()
        )

        try {
            val response: ApiResponse<LoginResponseDto> = authApi.login(request)
            println("Response: $response")
            assertNotNull(response)
            assertTrue("Login should succeed with configured dev credentials.", response.success)
            assertNotNull("Login response data should not be null.", response.data)
        } catch (e: Exception) {
            e.printStackTrace()
            assertTrue("Server connection or login failed: ${e.message}", false)
        }
    }

    @Test
    fun `register endpoint responds from app server`() = runBlocking {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val request = RegisterRequestDto(
            username = "itest$suffix@example.com",
            password = "testtest1",
            nickname = "itest$suffix"
        )

        try {
            val response = authApi.register(request)
            println("Register success: $response")
            assertNotNull(response)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 409) {
                println("Account already exists, treat as acceptable integration response.")
            } else {
                throw e
            }
        }
    }

    @Test
    fun `refresh succeeds with valid refresh token`() = runBlocking {
        requireLoginCredentials()

        val loginRequest = LoginRequestDto(
            username = loginUsername.orEmpty(),
            password = loginPassword.orEmpty()
        )
        val loginResponse = try {
            authApi.login(loginRequest)
        } catch (e: Exception) {
            println("Server connection failed during login step: ${e.message}")
            assertTrue("Server connection failed during login step: ${e.message}", false)
            return@runBlocking
        }

        assertTrue("Login should succeed before refresh test.", loginResponse.success)
        assertNotNull("Login response data should not be null.", loginResponse.data)

        val refreshToken = loginResponse.data!!.refreshToken
        val refreshRequest = RefreshTokenRequestDto(refreshToken = refreshToken)
        val refreshResponse = try {
            authApi.refresh(refreshRequest)
        } catch (e: Exception) {
            e.printStackTrace()
            assertTrue("Refresh request failed: ${e.message}", false)
            return@runBlocking
        }

        println("Refresh response: $refreshResponse")
        assertTrue("Refresh should succeed with a valid refresh token.", refreshResponse.success)
        assertNotNull("Refresh response data should not be null.", refreshResponse.data)
        assertTrue(
            "Access token should not be blank.",
            refreshResponse.data?.accessToken?.isNotBlank() == true
        )
        assertTrue(
            "Refresh token should not be blank.",
            refreshResponse.data?.refreshToken?.isNotBlank() == true
        )
    }

    @Test
    fun `refresh fails with invalid refresh token`() = runBlocking {
        val invalidRefreshToken = "THIS_IS_INVALID_TOKEN_12345"
        val refreshRequest = RefreshTokenRequestDto(refreshToken = invalidRefreshToken)

        try {
            val response = authApi.refresh(refreshRequest)
            println("Response: $response")
            assertTrue(
                "Invalid refresh token should not produce success=true.",
                !response.success
            )
        } catch (e: retrofit2.HttpException) {
            println("Server returned ${e.code()} for invalid refresh token.")
            assertTrue(
                "Invalid refresh token should return a 4xx response.",
                e.code() in 400..499
            )
        } catch (e: Exception) {
            println("Server connection failed: ${e.message}")
            assertTrue("Server connection failed: ${e.message}", false)
        }
    }

    private fun requireLoginCredentials() {
        assumeTrue(
            "Set auth.test.username/auth.test.password or AUTH_TEST_USERNAME/AUTH_TEST_PASSWORD to run login-based integration tests.",
            !loginUsername.isNullOrBlank() && !loginPassword.isNullOrBlank()
        )
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }

    private companion object {
        const val TEST_USERNAME_PROPERTY = "auth.test.username"
        const val TEST_PASSWORD_PROPERTY = "auth.test.password"
        const val TEST_USERNAME_ENV = "AUTH_TEST_USERNAME"
        const val TEST_PASSWORD_ENV = "AUTH_TEST_PASSWORD"
    }
}
