package com.ddgo.app.feature.onboarding

import com.ddgo.app.core.datastore.MainEntryGuideStep
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.core.datastore.PreferredGymPreferenceDataStore
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.usecase.CheckNicknameAvailabilityUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import com.ddgo.app.domain.usecase.UpdateProfileUseCase
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import com.ddgo.app.feature.profile.model.ProfileSexOption
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FigmaOnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `apply height to wingspan mirrors height`() = runTest {
        val viewModel = createViewModel()

        viewModel.setHeight(171)
        viewModel.applyHeightToWingspan()

        assertEquals(171, viewModel.wingspanCm)
    }

    @Test
    fun `save body profile uses selected metrics`() = runTest {
        val updateProfileUseCase = mockk<UpdateProfileUseCase>()
        coEvery {
            updateProfileUseCase(
                sex = "F",
                heightCm = 165f,
                weightKg = 58f,
                wingspanCm = 167f
            )
        } returns Result.success(Unit)

        val viewModel = createViewModel(updateProfileUseCase = updateProfileUseCase)
        viewModel.selectSex(ProfileSexOption.Female)
        viewModel.setHeight(165)
        viewModel.setWeight(58)
        viewModel.setWingspan(167)

        viewModel.saveBodyProfile {}
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            updateProfileUseCase(
                sex = "F",
                heightCm = 165f,
                weightKg = 58f,
                wingspanCm = 167f
            )
        }
    }

    @Test
    fun `complete onboarding with guide stores fab step`() = runTest {
        val onboardingPreferenceDataStore = mockk<OnboardingPreferenceDataStore>(relaxed = true)
        coEvery { onboardingPreferenceDataStore.setOnboardingCompleted(any()) } just Runs
        coEvery { onboardingPreferenceDataStore.setMainEntryGuideStep(any()) } just Runs

        val viewModel = createViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore
        )

        viewModel.completeOnboarding(showEntryGuide = true) {}
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { onboardingPreferenceDataStore.setOnboardingCompleted(true) }
        coVerify(exactly = 1) {
            onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.FAB)
        }
    }

    @Test
    fun `complete onboarding without guide stores done step`() = runTest {
        val onboardingPreferenceDataStore = mockk<OnboardingPreferenceDataStore>(relaxed = true)
        coEvery { onboardingPreferenceDataStore.setOnboardingCompleted(any()) } just Runs
        coEvery { onboardingPreferenceDataStore.setMainEntryGuideStep(any()) } just Runs

        val viewModel = createViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore
        )

        viewModel.completeOnboarding(showEntryGuide = false) {}
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.DONE)
        }
    }

    private fun createViewModel(
        onboardingPreferenceDataStore: OnboardingPreferenceDataStore = mockk(relaxed = true),
        preferredGymPreferenceDataStore: PreferredGymPreferenceDataStore = mockk(relaxed = true),
        getMyInfoUseCase: GetMyInfoUseCase = mockGetMyInfoUseCase(),
        updateProfileUseCase: UpdateProfileUseCase = mockk(relaxed = true),
        searchNearbyClimbingGymsUseCase: SearchNearbyClimbingGymsUseCase = mockk(relaxed = true),
        resolveGymUseCase: ResolveGymUseCase = mockk(relaxed = true),
        checkNicknameAvailabilityUseCase: CheckNicknameAvailabilityUseCase = mockk(relaxed = true),
        updateNicknameUseCase: UpdateNicknameUseCase = mockk(relaxed = true)
    ): FigmaOnboardingViewModel {
        return FigmaOnboardingViewModel(
            onboardingPreferenceDataStore = onboardingPreferenceDataStore,
            preferredGymPreferenceDataStore = preferredGymPreferenceDataStore,
            getMyInfoUseCase = getMyInfoUseCase,
            updateProfileUseCase = updateProfileUseCase,
            searchNearbyClimbingGymsUseCase = searchNearbyClimbingGymsUseCase,
            resolveGymUseCase = resolveGymUseCase,
            checkNicknameAvailabilityUseCase = checkNicknameAvailabilityUseCase,
            updateNicknameUseCase = updateNicknameUseCase
        )
    }

    private fun mockGetMyInfoUseCase(): GetMyInfoUseCase {
        val useCase = mockk<GetMyInfoUseCase>()
        coEvery { useCase() } returns Result.success(
            User(
                id = 1L,
                username = "tester",
                nickname = "포근한고양이",
                sex = "M",
                heightCm = 170f,
                weightKg = 60f,
                wingspanCm = 171f
            )
        )
        return useCase
    }
}
