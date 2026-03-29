package com.ddgo.app.feature.auth

import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.usecase.ConfirmPasswordResetUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LoginUseCase
import com.ddgo.app.domain.usecase.RegisterUseCase
import com.ddgo.app.domain.usecase.RequestPasswordResetUseCase
import com.ddgo.app.domain.usecase.SocialLoginUseCase
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `google social login routes to main when onboarding already complete`() = runTest {
        val onboardingPreferenceDataStore = onboardingStore(hasCompletedOnboarding = true)
        val loginUseCase = mockk<LoginUseCase>(relaxed = true)
        val registerUseCase = mockk<RegisterUseCase>(relaxed = true)
        val socialLoginUseCase = mockk<SocialLoginUseCase>()
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>()
        val requestPasswordResetUseCase = mockk<RequestPasswordResetUseCase>(relaxed = true)
        val confirmPasswordResetUseCase = mockk<ConfirmPasswordResetUseCase>(relaxed = true)

        coEvery {
            socialLoginUseCase(
                provider = SocialLoginProvider.GOOGLE,
                accessToken = null,
                idToken = "google-id-token"
            )
        } returns Result.success(
            AuthToken(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )
        coEvery { getMyInfoUseCase() } returns Result.success(completeUser())

        val viewModel = AuthViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            loginUseCase = loginUseCase,
            registerUseCase = registerUseCase,
            socialLoginUseCase = socialLoginUseCase,
            getMyInfoUseCase = getMyInfoUseCase,
            requestPasswordResetUseCase = requestPasswordResetUseCase,
            confirmPasswordResetUseCase = confirmPasswordResetUseCase
        )

        viewModel.loginWithGoogleIdToken("google-id-token")
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            AuthUiState.Success(AuthSuccessDestination.Main),
            viewModel.uiState.value
        )
        coVerify(exactly = 1) { getMyInfoUseCase() }
    }

    @Test
    fun `register forces onboarding with entry guide`() = runTest {
        val onboardingPreferenceDataStore = onboardingStore(hasCompletedOnboarding = true)
        val loginUseCase = mockk<LoginUseCase>()
        val registerUseCase = mockk<RegisterUseCase>()
        val socialLoginUseCase = mockk<SocialLoginUseCase>(relaxed = true)
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>(relaxed = true)
        val requestPasswordResetUseCase = mockk<RequestPasswordResetUseCase>(relaxed = true)
        val confirmPasswordResetUseCase = mockk<ConfirmPasswordResetUseCase>(relaxed = true)

        coEvery { registerUseCase("user@example.com", "Password!12") } returns Result.success(Unit)
        coEvery { loginUseCase("user@example.com", "Password!12") } returns Result.success(
            AuthToken(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )

        val viewModel = AuthViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            loginUseCase = loginUseCase,
            registerUseCase = registerUseCase,
            socialLoginUseCase = socialLoginUseCase,
            getMyInfoUseCase = getMyInfoUseCase,
            requestPasswordResetUseCase = requestPasswordResetUseCase,
            confirmPasswordResetUseCase = confirmPasswordResetUseCase
        )

        viewModel.updateUsername("user@example.com")
        viewModel.updatePassword("Password!12")
        viewModel.register()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            AuthUiState.Success(AuthSuccessDestination.Onboarding(showEntryGuide = true)),
            viewModel.uiState.value
        )
    }

    @Test
    fun `all body metrics missing routes to recovery onboarding without guide`() = runTest {
        val onboardingPreferenceDataStore = onboardingStore(hasCompletedOnboarding = true)
        val loginUseCase = mockk<LoginUseCase>()
        val registerUseCase = mockk<RegisterUseCase>(relaxed = true)
        val socialLoginUseCase = mockk<SocialLoginUseCase>(relaxed = true)
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>()
        val requestPasswordResetUseCase = mockk<RequestPasswordResetUseCase>(relaxed = true)
        val confirmPasswordResetUseCase = mockk<ConfirmPasswordResetUseCase>(relaxed = true)

        coEvery { loginUseCase("user@example.com", "Password!12") } returns Result.success(
            AuthToken(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )
        coEvery { getMyInfoUseCase() } returns Result.success(
            completeUser(
                sex = null,
                heightCm = null,
                weightKg = null,
                wingspanCm = null
            )
        )

        val viewModel = AuthViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            loginUseCase = loginUseCase,
            registerUseCase = registerUseCase,
            socialLoginUseCase = socialLoginUseCase,
            getMyInfoUseCase = getMyInfoUseCase,
            requestPasswordResetUseCase = requestPasswordResetUseCase,
            confirmPasswordResetUseCase = confirmPasswordResetUseCase
        )

        viewModel.updateUsername("user@example.com")
        viewModel.updatePassword("Password!12")
        viewModel.login()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            AuthUiState.Success(AuthSuccessDestination.Onboarding(showEntryGuide = false)),
            viewModel.uiState.value
        )
    }

    @Test
    fun `partially missing body profile still routes to main`() = runTest {
        val onboardingPreferenceDataStore = onboardingStore(hasCompletedOnboarding = true)
        val loginUseCase = mockk<LoginUseCase>()
        val registerUseCase = mockk<RegisterUseCase>(relaxed = true)
        val socialLoginUseCase = mockk<SocialLoginUseCase>(relaxed = true)
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>()
        val requestPasswordResetUseCase = mockk<RequestPasswordResetUseCase>(relaxed = true)
        val confirmPasswordResetUseCase = mockk<ConfirmPasswordResetUseCase>(relaxed = true)

        coEvery { loginUseCase("user@example.com", "Password!12") } returns Result.success(
            AuthToken(
                accessToken = "access-token",
                refreshToken = "refresh-token"
            )
        )
        coEvery { getMyInfoUseCase() } returns Result.success(
            completeUser(
                sex = "M",
                heightCm = 180f,
                weightKg = null,
                wingspanCm = 181f
            )
        )

        val viewModel = AuthViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            loginUseCase = loginUseCase,
            registerUseCase = registerUseCase,
            socialLoginUseCase = socialLoginUseCase,
            getMyInfoUseCase = getMyInfoUseCase,
            requestPasswordResetUseCase = requestPasswordResetUseCase,
            confirmPasswordResetUseCase = confirmPasswordResetUseCase
        )

        viewModel.updateUsername("user@example.com")
        viewModel.updatePassword("Password!12")
        viewModel.login()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            AuthUiState.Success(AuthSuccessDestination.Main),
            viewModel.uiState.value
        )
    }

    private fun onboardingStore(hasCompletedOnboarding: Boolean): OnboardingPreferenceDataStore {
        return mockk(relaxed = true) {
            every { this@mockk.hasCompletedOnboarding } returns flowOf(hasCompletedOnboarding)
            coEvery { this@mockk.setHasAuthenticatedOnce(any()) } just Runs
        }
    }

    private fun completeUser(
        sex: String? = "M",
        heightCm: Float? = 180f,
        weightKg: Float? = 70f,
        wingspanCm: Float? = 182f
    ): User {
        return User(
            id = 1L,
            username = "google_social_user",
            email = "social@example.com",
            nickname = "차분한클라이머",
            sex = sex,
            heightCm = heightCm,
            weightKg = weightKg,
            wingspanCm = wingspanCm
        )
    }
}
