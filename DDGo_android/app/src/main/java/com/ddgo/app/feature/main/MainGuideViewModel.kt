package com.ddgo.app.feature.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.MainEntryGuideStep
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MainGuideViewModel @Inject constructor(
    private val onboardingPreferenceDataStore: OnboardingPreferenceDataStore
) : ViewModel() {

    val guideStep: StateFlow<MainEntryGuideStep> = onboardingPreferenceDataStore.mainEntryGuideStep
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MainEntryGuideStep.NONE
        )

    fun onFabGuideActivated() {
        if (guideStep.value != MainEntryGuideStep.FAB) return

        viewModelScope.launch {
            onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.MENU)
        }
    }

    fun dismissMenuGuide() {
        if (guideStep.value != MainEntryGuideStep.MENU) return

        viewModelScope.launch {
            onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.DONE)
        }
    }
}
