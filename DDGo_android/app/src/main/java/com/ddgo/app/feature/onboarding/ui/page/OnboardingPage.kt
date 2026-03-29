package com.ddgo.app.feature.onboarding.ui.page

import androidx.compose.runtime.Composable
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.feature.onboarding.OnboardingClimbingLevel
import com.ddgo.app.feature.onboarding.OnboardingClimbingStyle
import com.ddgo.app.feature.onboarding.OnboardingGoal
import com.ddgo.app.feature.onboarding.OnboardingGymResolveUiState
import com.ddgo.app.feature.onboarding.OnboardingGymSearchUiState
import com.ddgo.app.feature.onboarding.OnboardingMode
import com.ddgo.app.feature.onboarding.OnboardingStage
import com.ddgo.app.feature.onboarding.ui.organism.BodyProfileStageSection
import com.ddgo.app.feature.onboarding.ui.organism.ClimbingProfileStageSection
import com.ddgo.app.feature.onboarding.ui.organism.GoalStageSection
import com.ddgo.app.feature.onboarding.ui.organism.GymStageSection
import com.ddgo.app.feature.onboarding.ui.organism.HeroStageSection
import com.ddgo.app.feature.onboarding.ui.organism.OnboardingStageScaffold
import com.ddgo.app.feature.onboarding.ui.organism.SummaryStageSection
import com.ddgo.app.feature.profile.model.ProfileSexOption

data class OnboardingPageState(
    val mode: OnboardingMode,
    val currentStage: OnboardingStage,
    val currentStageIndex: Int,
    val trackedStages: List<OnboardingStage>,
    val locationMessage: String?,
    val isResolvingLocation: Boolean,
    val canContinue: Boolean,
    val buttonLabel: String,
    val helperText: String,
    val isLoading: Boolean,
    val gymSearchQuery: String,
    val gymSearchUiState: OnboardingGymSearchUiState,
    val gymResolveUiState: OnboardingGymResolveUiState,
    val selectedNearbyPlaceId: String?,
    val selectedGymTitle: String?,
    val selectedGymGradeCount: Int?,
    val climbingLevel: OnboardingClimbingLevel?,
    val climbingStyle: OnboardingClimbingStyle?,
    val onboardingGoal: OnboardingGoal?,
    val sex: ProfileSexOption?,
    val heightCmInput: String,
    val weightKgInput: String,
    val wingspanCmInput: String,
    val profileErrorMessage: String?,
    val isLoadingProfileDefaults: Boolean,
    val isSubmittingProfile: Boolean
)

data class OnboardingPageCallbacks(
    val onBack: () -> Unit,
    val onSkipGym: () -> Unit,
    val onSearch: () -> Unit,
    val onCurrentLocationSearch: () -> Unit,
    val onContinue: () -> Unit,
    val onGymSearchQueryChange: (String) -> Unit,
    val onSelectPlace: (NearbyPlace) -> Unit,
    val onSelectClimbingLevel: (OnboardingClimbingLevel) -> Unit,
    val onSelectClimbingStyle: (OnboardingClimbingStyle) -> Unit,
    val onSelectGoal: (OnboardingGoal) -> Unit,
    val onSelectSex: (ProfileSexOption) -> Unit,
    val onHeightChange: (String) -> Unit,
    val onWeightChange: (String) -> Unit,
    val onWingspanChange: (String) -> Unit,
    val onApplyHeightToWingspan: () -> Unit
)

@Composable
fun OnboardingPage(
    state: OnboardingPageState,
    callbacks: OnboardingPageCallbacks
) {
    OnboardingStageScaffold(
        currentStage = state.currentStage,
        trackedStages = state.trackedStages,
        currentStageIndex = state.currentStageIndex,
        canContinue = state.canContinue,
        buttonLabel = state.buttonLabel,
        helperText = state.helperText,
        isLoading = state.isLoading,
        onContinue = callbacks.onContinue,
        canGoBack = state.currentStageIndex > 0,
        onBack = callbacks.onBack,
        showSkip = state.currentStage == OnboardingStage.Gym,
        onSkip = callbacks.onSkipGym
    ) {
        when (state.currentStage) {
            OnboardingStage.Hero -> HeroStageSection()
            OnboardingStage.Gym -> GymStageSection(
                gymSearchQuery = state.gymSearchQuery,
                gymSearchUiState = state.gymSearchUiState,
                gymResolveUiState = state.gymResolveUiState,
                selectedNearbyPlaceId = state.selectedNearbyPlaceId,
                locationMessage = state.locationMessage,
                isResolvingLocation = state.isResolvingLocation,
                onGymSearchQueryChange = callbacks.onGymSearchQueryChange,
                onSearch = callbacks.onSearch,
                onCurrentLocationSearch = callbacks.onCurrentLocationSearch,
                onSelectPlace = callbacks.onSelectPlace
            )

            OnboardingStage.ClimbingProfile -> ClimbingProfileStageSection(
                climbingLevel = state.climbingLevel,
                climbingStyle = state.climbingStyle,
                onSelectClimbingLevel = callbacks.onSelectClimbingLevel,
                onSelectClimbingStyle = callbacks.onSelectClimbingStyle
            )

            OnboardingStage.Goal -> GoalStageSection(
                selectedGoal = state.onboardingGoal,
                onSelectGoal = callbacks.onSelectGoal
            )

            OnboardingStage.BodyProfile -> BodyProfileStageSection(
                sex = state.sex,
                heightCmInput = state.heightCmInput,
                weightKgInput = state.weightKgInput,
                wingspanCmInput = state.wingspanCmInput,
                profileErrorMessage = state.profileErrorMessage,
                isLoadingProfileDefaults = state.isLoadingProfileDefaults,
                isSubmittingProfile = state.isSubmittingProfile,
                onSelectSex = callbacks.onSelectSex,
                onHeightChange = callbacks.onHeightChange,
                onWeightChange = callbacks.onWeightChange,
                onWingspanChange = callbacks.onWingspanChange,
                onApplyHeightToWingspan = callbacks.onApplyHeightToWingspan
            )

            OnboardingStage.Summary -> SummaryStageSection(
                mode = state.mode,
                selectedGymTitle = state.selectedGymTitle,
                selectedGymGradeCount = state.selectedGymGradeCount,
                onboardingGoal = state.onboardingGoal,
                climbingLevel = state.climbingLevel,
                heightCmInput = state.heightCmInput,
                wingspanCmInput = state.wingspanCmInput
            )
        }
    }
}
