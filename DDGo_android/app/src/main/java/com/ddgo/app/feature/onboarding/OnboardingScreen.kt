package com.ddgo.app.feature.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.feature.onboarding.ui.page.OnboardingPage
import com.ddgo.app.feature.onboarding.ui.page.OnboardingPageCallbacks
import com.ddgo.app.feature.onboarding.ui.page.OnboardingPageState
import com.ddgo.app.feature.onboarding.ui.shared.tokens.OnboardingTokens

enum class OnboardingStage {
    Hero,
    Gym,
    ClimbingProfile,
    Goal,
    BodyProfile,
    Summary
}

@Composable
fun OnboardingScreen(
    mode: OnboardingMode,
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    LaunchedEffect(mode) {
        viewModel.prepare(mode)
    }

    val context = LocalContext.current
    val stages = remember(mode) { buildOnboardingStages(mode) }
    val trackedStages = remember(stages) { stages.filterNot { it == OnboardingStage.Summary } }
    var currentStageIndex by rememberSaveable(mode.name) { mutableIntStateOf(0) }
    val currentStage = stages[currentStageIndex]

    var locationMessage by remember { mutableStateOf<String?>(null) }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var hasShownCachedLocationResult by remember { mutableStateOf(false) }

    val startLocationSearch = {
        hasShownCachedLocationResult = false
        isResolvingLocation = true
        loadCurrentLocationIncrementally(
            context = context,
            onCachedLocation = { latitude, longitude ->
                hasShownCachedLocationResult = true
                locationMessage = null
                viewModel.searchGyms(
                    latitude = latitude,
                    longitude = longitude,
                    query = viewModel.gymSearchQuery,
                    nearbyOnly = viewModel.gymSearchQuery.isBlank()
                )
            },
            onFreshLocation = { latitude, longitude, isSameAsCached ->
                isResolvingLocation = false
                locationMessage = null
                if (!isSameAsCached) {
                    viewModel.searchGyms(
                        latitude = latitude,
                        longitude = longitude,
                        query = viewModel.gymSearchQuery,
                        nearbyOnly = viewModel.gymSearchQuery.isBlank()
                    )
                }
            },
            onError = { message ->
                isResolvingLocation = false
                locationMessage = if (hasShownCachedLocationResult) {
                    "최근 위치 기준 결과를 먼저 보여드리고 있어요."
                } else {
                    message
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            startLocationSearch()
        } else {
            isResolvingLocation = false
            locationMessage = "현재 위치 기반으로 암장을 찾으려면 위치 권한이 필요해요."
        }
    }

    val requestCurrentLocationSearch = {
        if (hasLocationPermission(context)) {
            startLocationSearch()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val canContinue = when (currentStage) {
        OnboardingStage.Hero -> true
        OnboardingStage.Gym -> !isResolvingLocation
        OnboardingStage.ClimbingProfile -> viewModel.climbingLevel != null

        OnboardingStage.Goal -> viewModel.onboardingGoal != null
        OnboardingStage.BodyProfile ->
            viewModel.canContinueBodyProfile() &&
                !viewModel.isLoadingProfileDefaults &&
                !viewModel.isSubmittingProfile

        OnboardingStage.Summary -> !viewModel.isCompletingFlow
    }

    val buttonLabel = primaryButtonLabel(currentStage, mode, viewModel)
    val helperText = helperTextForStage(currentStage, mode, viewModel)
    val isLoading = when (currentStage) {
        OnboardingStage.BodyProfile -> viewModel.isSubmittingProfile
        OnboardingStage.Summary -> viewModel.isCompletingFlow
        else -> false
    }

    fun moveToNextStage() {
        if (currentStageIndex < stages.lastIndex) {
            currentStageIndex += 1
        }
    }

    fun handleContinue() {
        when (currentStage) {
            OnboardingStage.BodyProfile -> {
                viewModel.saveBodyProfile(onCompleted = ::moveToNextStage)
            }

            OnboardingStage.Summary -> {
                viewModel.completeFlow(onCompleted = onFinish)
            }

            else -> moveToNextStage()
        }
    }

    val pageState = OnboardingPageState(
        mode = mode,
        currentStage = currentStage,
        currentStageIndex = currentStageIndex,
        trackedStages = trackedStages,
        locationMessage = locationMessage,
        isResolvingLocation = isResolvingLocation,
        canContinue = canContinue,
        buttonLabel = buttonLabel,
        helperText = helperText,
        isLoading = isLoading,
        gymSearchQuery = viewModel.gymSearchQuery,
        gymSearchUiState = viewModel.gymSearchUiState,
        gymResolveUiState = viewModel.gymResolveUiState,
        selectedNearbyPlaceId = viewModel.selectedNearbyPlace?.externalPlaceId,
        selectedGymTitle = viewModel.selectedGymTitle(),
        selectedGymGradeCount = viewModel.selectedGymGradeCount(),
        climbingLevel = viewModel.climbingLevel,
        climbingStyle = viewModel.climbingStyle,
        onboardingGoal = viewModel.onboardingGoal,
        sex = viewModel.sex,
        heightCmInput = viewModel.heightCmInput,
        weightKgInput = viewModel.weightKgInput,
        wingspanCmInput = viewModel.wingspanCmInput,
        profileErrorMessage = viewModel.profileErrorMessage,
        isLoadingProfileDefaults = viewModel.isLoadingProfileDefaults,
        isSubmittingProfile = viewModel.isSubmittingProfile
    )

    val callbacks = OnboardingPageCallbacks(
        onBack = { currentStageIndex -= 1 },
        onSkipGym = ::moveToNextStage,
        onSearch = { viewModel.searchGyms(query = viewModel.gymSearchQuery) },
        onCurrentLocationSearch = requestCurrentLocationSearch,
        onContinue = ::handleContinue,
        onGymSearchQueryChange = viewModel::updateGymSearchQuery,
        onSelectPlace = viewModel::resolveSelectedPlace,
        onSelectClimbingLevel = viewModel::updateClimbingLevel,
        onSelectClimbingStyle = viewModel::updateClimbingStyle,
        onSelectGoal = viewModel::updateGoal,
        onSelectSex = viewModel::updateSex,
        onHeightChange = viewModel::updateHeight,
        onWeightChange = viewModel::updateWeight,
        onWingspanChange = viewModel::updateWingspan,
        onApplyHeightToWingspan = viewModel::applyHeightToWingspan
    )

    SafeAreaScreen(
        modifier = Modifier.background(OnboardingTokens.Background),
        applyBottomInset = false
    ) {
        OnboardingPage(
            state = pageState,
            callbacks = callbacks
        )
    }
}

internal fun buildOnboardingStages(mode: OnboardingMode): List<OnboardingStage> {
    return buildList {
        if (mode.includesIntro) {
            add(OnboardingStage.Hero)
            add(OnboardingStage.Gym)
            add(OnboardingStage.ClimbingProfile)
            add(OnboardingStage.Goal)
        }
        if (mode.includesProfileSetup) {
            add(OnboardingStage.BodyProfile)
        }
        add(OnboardingStage.Summary)
    }
}

internal fun primaryButtonLabel(
    stage: OnboardingStage,
    mode: OnboardingMode,
    viewModel: OnboardingViewModel
): String {
    return when (stage) {
        OnboardingStage.Hero -> "내 등반 시작하기"
        OnboardingStage.Gym -> if (viewModel.selectedGymTitle() != null) {
            "이 암장에서 계속"
        } else {
            "나중에 선택하고 계속"
        }

        OnboardingStage.ClimbingProfile -> "목표 고르기"
        OnboardingStage.Goal -> if (mode.includesProfileSetup) "내 기준 더 정교하게" else "결과 보기"
        OnboardingStage.BodyProfile -> "내 기준 저장하기"
        OnboardingStage.Summary -> "디디고 시작하기"
    }
}

internal fun helperTextForStage(
    stage: OnboardingStage,
    mode: OnboardingMode,
    viewModel: OnboardingViewModel
): String {
    return when (stage) {
        OnboardingStage.Hero -> "디디고는 첫 등반 경험을 빠르게 개인화하는 데 집중해요."
        OnboardingStage.Gym -> "대표 암장은 지금 정하지 않아도, 나중에 프로필에서 다시 바꿀 수 있어요."
        OnboardingStage.ClimbingProfile -> "초보자도 편하게 고를 수 있게, 지금 가장 가까운 단계만 묻고 있어요."
        OnboardingStage.Goal -> "선택한 목표는 홈 화면 추천과 첫 기록 흐름에 바로 반영돼요."
        OnboardingStage.BodyProfile -> "체중과 윙스팬은 분석 정확도에 도움을 주고, 윙스팬은 비워두면 키 기준으로 먼저 시작해요."
        OnboardingStage.Summary -> {
            if (mode.includesProfileSetup) {
                "첫 기록을 남기면 디디고가 내 성장 흐름을 더 정교하게 이어갈 수 있어요."
            } else if (viewModel.selectedGymTitle() != null) {
                "대표 암장은 저장되고, 로그인 이후에도 같은 기준으로 이어서 시작할 수 있어요."
            } else {
                "지금은 가볍게 시작하고, 자세한 설정은 디디고 안에서 천천히 이어가도 괜찮아요."
            }
        }
    }
}
