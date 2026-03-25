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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
    fun `google social login keeps success flow without nickname sync`() = runTest {
        val onboardingPreferenceDataStore = mockk<OnboardingPreferenceDataStore>()
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
        every { onboardingPreferenceDataStore.hasCompletedOnboarding } returns flowOf(true)

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
    fun `register submits username and password then forces profile onboarding`() = runTest {
        val onboardingPreferenceDataStore = mockk<OnboardingPreferenceDataStore>()
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
        every { onboardingPreferenceDataStore.hasCompletedOnboarding } returns flowOf(true)

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

        coVerify(exactly = 1) { registerUseCase("user@example.com", "Password!12") }
        coVerify(exactly = 1) { loginUseCase("user@example.com", "Password!12") }
        assertEquals(
            AuthUiState.Success(AuthSuccessDestination.ProfileOnboarding),
            viewModel.uiState.value
        )
    }

    private fun completeUser(): User {
        return User(
            id = 1L,
            username = "google_social-user",
            email = "social@example.com",
            nickname = "차분한바다",
            sex = "M",
            heightCm = 180f,
            weightKg = 70f,
            wingspanCm = 182f
        )
    }
}
