package com.ddgo.app.feature.splash

import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.UserResponseDto
import com.ddgo.app.data.remote.common.ApiResponse
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.repository.AuthRepository
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import com.ddgo.app.navigation.ScreenRoutes
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
    fun `valid access token and complete onboarding routes to main`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = true,
            hasAuthenticatedOnce = true
        )
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } returns successfulMyInfoResponse()

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToMain, navigation.await())
        coVerify(exactly = 1) { onboardingPreferenceDataStore.setHasAuthenticatedOnce() }
        coVerify(exactly = 0) { authRepository.refreshToken(any()) }
    }

    @Test
    fun `no session and no previous auth routes to welcome`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = false,
            hasAuthenticatedOnce = false
        )
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val splashAuthApi = mockk<AuthApi>(relaxed = true)

        every { tokenDataStore.accessToken } returns flowOf(null)
        every { tokenDataStore.refreshToken } returns flowOf(null)
        coEvery { tokenDataStore.clearTokens() } just Runs

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToWelcome, navigation.await())
    }

    @Test
    fun `invalid server session routes to login when user authenticated before`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = true,
            hasAuthenticatedOnce = true
        )
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
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToLoginEmail, navigation.await())
        coVerify(exactly = 1) { tokenDataStore.clearTokens() }
    }

    @Test
    fun `not completed onboarding routes to full onboarding with guide`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = false,
            hasAuthenticatedOnce = true
        )
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } returns successfulMyInfoResponse()

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(
            SplashNavigationEvent.NavigateToOnboarding(
                nextRoute = ScreenRoutes.MainGraph.route,
                showEntryGuide = true
            ),
            navigation.await()
        )
    }

    @Test
    fun `all body metrics missing after onboarding routes to recovery onboarding without guide`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = true,
            hasAuthenticatedOnce = true
        )
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } returns successfulMyInfoResponse(
            sex = null,
            heightCm = null,
            weightKg = null,
            wingspanCm = null
        )

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(
            SplashNavigationEvent.NavigateToOnboarding(
                nextRoute = ScreenRoutes.MainGraph.route,
                showEntryGuide = false
            ),
            navigation.await()
        )
    }

    @Test
    fun `partially missing body profile still routes to main`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = true,
            hasAuthenticatedOnce = true
        )
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val accessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returns flowOf(accessToken)
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
        coEvery {
            splashAuthApi.getMyInfoWithAuthorization("Bearer $accessToken")
        } returns successfulMyInfoResponse(
            sex = "M",
            heightCm = 175f,
            weightKg = null,
            wingspanCm = 180f
        )

        val viewModel = createViewModel(
            tokenDataStore = tokenDataStore,
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToMain, navigation.await())
    }

    @Test
    fun `refresh token success validates refreshed session`() = runTest {
        val tokenDataStore = mockk<TokenDataStore>()
        val onboardingPreferenceDataStore = onboardingStore(
            hasCompletedOnboarding = true,
            hasAuthenticatedOnce = true
        )
        val authRepository = mockk<AuthRepository>()
        val splashAuthApi = mockk<AuthApi>()
        val expiredAccessToken = jwtToken(expiresAtEpochSeconds = 946684800L)
        val refreshedAccessToken = jwtToken(expiresAtEpochSeconds = 4_102_444_800L)

        every { tokenDataStore.accessToken } returnsMany listOf(
            flowOf(expiredAccessToken),
            flowOf(refreshedAccessToken)
        )
        every { tokenDataStore.refreshToken } returns flowOf("refresh-token")
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
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            authRepository = authRepository,
            splashAuthApi = splashAuthApi
        )
        val navigation = backgroundScope.async { viewModel.navigationEvent.first() }

        advanceSplash()

        assertEquals(SplashNavigationEvent.NavigateToMain, navigation.await())
        coVerify(exactly = 1) { authRepository.refreshToken("refresh-token") }
    }

    private fun onboardingStore(
        hasCompletedOnboarding: Boolean,
        hasAuthenticatedOnce: Boolean
    ): OnboardingPreferenceDataStore {
        return mockk(relaxed = true) {
            every { this@mockk.hasCompletedOnboarding } returns flowOf(hasCompletedOnboarding)
            every { this@mockk.hasAuthenticatedOnce } returns flowOf(hasAuthenticatedOnce)
            coEvery { this@mockk.setHasAuthenticatedOnce(any()) } just Runs
        }
    }

    private fun createViewModel(
        tokenDataStore: TokenDataStore,
        onboardingPreferenceDataStore: OnboardingPreferenceDataStore,
        authRepository: AuthRepository,
        splashAuthApi: AuthApi
    ): SplashViewModel = SplashViewModel(
        tokenDataStore = tokenDataStore,
        onboardingPreferenceDataStore = onboardingPreferenceDataStore,
        authRepository = authRepository,
        splashAuthApi = splashAuthApi,
        json = json
    )

    private fun successfulMyInfoResponse(
        sex: String? = "M",
        heightCm: Float? = 175f,
        weightKg: Float? = 68f,
        wingspanCm: Float? = 178f
    ): ApiResponse<UserResponseDto> = ApiResponse(
        success = true,
        message = "success",
        data = UserResponseDto(
            id = 1L,
            username = "tester",
            nickname = "Tester",
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            wingspanCm = wingspanCm
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

    private fun jwtToken(expiresAtEpochSeconds: Long): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"none","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"exp":$expiresAtEpochSeconds}""".toByteArray())
        return "$header.$payload.signature"
    }
}
