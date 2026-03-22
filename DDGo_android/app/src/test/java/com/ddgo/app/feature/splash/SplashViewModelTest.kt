package com.ddgo.app.feature.splash

import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.UserResponseDto
import com.ddgo.app.data.remote.common.ApiResponse
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.repository.AuthRepository
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SplashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `valid access token and successful user me routes to main`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery { tokenDataStore.clearTokens() } just Runs
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } returns successfulMyInfoResponse()

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToMain, navigation.await())
        coVerify(exactly = 1) {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        }
        coVerify(exactly = 0) { tokenDataStore.clearTokens() }
        coVerify(exactly = 0) { authRepository.refreshToken(any()) }
    }

    @Test
    fun `user me 403 clears tokens and routes to auth`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery { tokenDataStore.clearTokens() } just Runs
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } throws httpException(403)

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToAuth, navigation.await())
        coVerify(exactly = 1) { tokenDataStore.clearTokens() }
    }

    @Test
    fun `user me 404 with U001 clears tokens and routes to auth`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery { tokenDataStore.clearTokens() } just Runs
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } throws httpException(
            code = 404,
            body = apiErrorBody(code = "U001", message = "User Not Found")
        )

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToAuth, navigation.await())
        coVerify(exactly = 1) { tokenDataStore.clearTokens() }
    }

    @Test
    fun `user me 500 keeps session and routes to main`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery { tokenDataStore.clearTokens() } just Runs
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } throws httpException(
            code = 500,
            body = apiErrorBody(code = "C003", message = "Server Error")
        )

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToMain, navigation.await())
        coVerify(exactly = 0) { tokenDataStore.clearTokens() }
    }

    @Test
    fun `expired access token refresh success then user me success routes to main`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val expiredAccessToken = jwtToken(expiresAtEpochSeconds = 946684800L)
        val refreshedAccessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returnsMany listOf(
            flowOf(expiredAccessToken),
            flowOf(refreshedAccessToken)
        )
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery { tokenDataStore.clearTokens() } just Runs
        coEvery { authRepository.refreshToken("refresh-token") } returns Result.success(
            AuthToken(
                accessToken = refreshedAccessToken,
                refreshToken = "new-refresh-token"
            )
        )
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $refreshedAccessToken")
        } returns successfulMyInfoResponse()

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToMain, navigation.await())
        coVerify(exactly = 1) { authRepository.refreshToken("refresh-token") }
        coVerify(exactly = 0) { tokenDataStore.clearTokens() }
    }

    @Test
    fun `refresh failure clears tokens and routes to auth`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>(relaxed = true)
        val expiredAccessToken = jwtToken(expiresAtEpochSeconds = 946684800L)

        every { tokenDataStore.accessToken } returns flowOf(expiredAccessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery { tokenDataStore.clearTokens() } just Runs
        coEvery { authRepository.refreshToken("refresh-token") } returns Result.failure(
            IllegalStateException("refresh failed")
        )

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToAuth, navigation.await())
        coVerify(exactly = 2) { authRepository.refreshToken("refresh-token") }
        coVerify(exactly = 1) { tokenDataStore.clearTokens() }
        coVerify(exactly = 0) { splashAuthApi.getMyInfoWithAuthorization(any()) }
    }

    @Test
    fun `invalid session helper matches status codes and backend error codes`() {
        assertTrue(httpException(401).isInvalidSessionValidationFailure(json))
        assertTrue(httpException(403).isInvalidSessionValidationFailure(json))
        assertTrue(
            httpException(
                code = 400,
                body = apiErrorBody(code = "A001", message = "Unauthorized")
            ).isInvalidSessionValidationFailure(json)
        )
        assertTrue(
            httpException(
                code = 400,
                body = apiErrorBody(code = "A002", message = "Invalid Token")
            ).isInvalidSessionValidationFailure(json)
        )
        assertTrue(
            httpException(
                code = 400,
                body = apiErrorBody(code = "A003", message = "Expired Token")
            ).isInvalidSessionValidationFailure(json)
        )
        assertTrue(
            httpException(
                code = 404,
                body = apiErrorBody(code = "U001", message = "User Not Found")
            ).isInvalidSessionValidationFailure(json)
        )
        assertTrue(
            Exception("wrapped", httpException(401)).isInvalidSessionValidationFailure(json)
        )
    }

    @Test
    fun `invalid session helper ignores non auth failures`() {
        assertFalse(
            httpException(
                code = 500,
                body = apiErrorBody(code = "C003", message = "Server Error")
            ).isInvalidSessionValidationFailure(json)
        )
        assertFalse(IOException("network down").isInvalidSessionValidationFailure(json))
        assertFalse(httpException(code = 404, body = "not-json").isInvalidSessionValidationFailure(json))
    }

    private fun createViewModel(
        tokenDataStore: TokenDataStore,
        authRepository: AuthRepository,
        splashAuthApi: AuthApi
    ): SplashViewModel = SplashViewModel(
        tokenDataStore = tokenDataStore,
        authRepository = authRepository,
        splashAuthApi = splashAuthApi,
        json = json
    )

    private fun successfulMyInfoResponse(): ApiResponse<UserResponseDto> = ApiResponse(
        success = true,
        message = "success",
        data = UserResponseDto(
            id = 1L,
            username = "tester",
            nickname = "Tester"
        )
    )

    private fun advanceSplash() {
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_500L)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    private fun httpException(code: Int, body: String = ""): HttpException {
        return HttpException(
            Response.error<Any>(
                code,
                body.toResponseBody("application/json".toMediaType())
            )
        )
    }

    private fun apiErrorBody(code: String, message: String): String {
        return """{"success":false,"code":"$code","message":"$message"}"""
    }

    private fun jwtToken(expiresAtEpochSeconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"exp":$expiresAtEpochSeconds}""".toByteArray())
        return "$header.$payload.signature"
    }
}
