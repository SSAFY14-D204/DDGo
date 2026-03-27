package com.ddgo.app.feature.main

import com.ddgo.app.core.datastore.MainEntryGuideStep
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
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
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainGuideViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `fab guide activation advances to menu`() = runTest {
        val onboardingPreferenceDataStore = mockk<OnboardingPreferenceDataStore>()
        every { onboardingPreferenceDataStore.mainEntryGuideStep } returns flowOf(MainEntryGuideStep.FAB)
        coEvery { onboardingPreferenceDataStore.setMainEntryGuideStep(any()) } just Runs

        val viewModel = MainGuideViewModel(onboardingPreferenceDataStore)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onFabGuideActivated()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.MENU)
        }
    }

    @Test
    fun `menu guide dismissal advances to done`() = runTest {
        val onboardingPreferenceDataStore = mockk<OnboardingPreferenceDataStore>()
        every { onboardingPreferenceDataStore.mainEntryGuideStep } returns flowOf(MainEntryGuideStep.MENU)
        coEvery { onboardingPreferenceDataStore.setMainEntryGuideStep(any()) } just Runs

        val viewModel = MainGuideViewModel(onboardingPreferenceDataStore)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissMenuGuide()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.DONE)
        }
    }
}
