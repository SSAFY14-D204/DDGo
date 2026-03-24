package com.ddgo.app.feature.onboarding

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.core.datastore.PreferredGymPreferenceDataStore
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.UpdateProfileUseCase
import com.ddgo.app.feature.profile.ProfileStrings
import com.ddgo.app.feature.profile.model.ProfileBodyProfileEditorUiState
import com.ddgo.app.feature.profile.model.ProfileSexOption
import com.ddgo.app.feature.profile.state.ProfileInputValidator
import com.ddgo.app.feature.profile.state.ProfileValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

private const val TAG = "OnboardingViewModel"
private const val DEFAULT_SEARCH_LATITUDE = 37.5665
private const val DEFAULT_SEARCH_LONGITUDE = 126.9780

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val onboardingPreferenceDataStore: OnboardingPreferenceDataStore,
    private val preferredGymPreferenceDataStore: PreferredGymPreferenceDataStore,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val searchNearbyClimbingGymsUseCase: SearchNearbyClimbingGymsUseCase,
    private val resolveGymUseCase: ResolveGymUseCase
) : ViewModel() {

    private var preparedMode: OnboardingMode? = null
    private var hasLoadedProfileDefaults = false

    var isCompletingFlow by mutableStateOf(false)
        private set

    var isLoadingProfileDefaults by mutableStateOf(false)
        private set

    var isSubmittingProfile by mutableStateOf(false)
        private set

    var gymSearchQuery by mutableStateOf("")
        private set

    var gymSearchUiState by mutableStateOf<OnboardingGymSearchUiState>(OnboardingGymSearchUiState.Idle)
        private set

    var gymResolveUiState by mutableStateOf<OnboardingGymResolveUiState>(OnboardingGymResolveUiState.Idle)
        private set

    var selectedNearbyPlace by mutableStateOf<NearbyPlace?>(null)
        private set

    var selectedResolvedGym by mutableStateOf<ResolvedGym?>(null)
        private set

    var lastSearchLatitude by mutableStateOf<Double?>(null)
        private set

    var lastSearchLongitude by mutableStateOf<Double?>(null)
        private set

    var climbingLevel by mutableStateOf<OnboardingClimbingLevel?>(null)
        private set

    var climbingStyle by mutableStateOf<OnboardingClimbingStyle?>(null)
        private set

    var onboardingGoal by mutableStateOf<OnboardingGoal?>(null)
        private set

    var sex by mutableStateOf<ProfileSexOption?>(null)
        private set

    var heightCmInput by mutableStateOf("")
        private set

    var weightKgInput by mutableStateOf("")
        private set

    var wingspanCmInput by mutableStateOf("")
        private set

    var profileErrorMessage by mutableStateOf<String?>(null)
        private set

    fun prepare(mode: OnboardingMode) {
        if (preparedMode == mode && (!mode.includesProfileSetup || hasLoadedProfileDefaults)) {
            return
        }

        preparedMode = mode

        if (mode.includesProfileSetup && !hasLoadedProfileDefaults) {
            loadProfileDefaults()
        }
    }

    fun updateGymSearchQuery(input: String) {
        gymSearchQuery = input
    }

    fun searchGyms(
        latitude: Double? = null,
        longitude: Double? = null,
        query: String = gymSearchQuery,
        nearbyOnly: Boolean = false
    ) {
        val normalizedQuery = query.trim()
        gymSearchQuery = normalizedQuery

        val resolvedLatitude = latitude ?: lastSearchLatitude ?: DEFAULT_SEARCH_LATITUDE
        val resolvedLongitude = longitude ?: lastSearchLongitude ?: DEFAULT_SEARCH_LONGITUDE

        if (latitude != null && longitude != null) {
            lastSearchLatitude = latitude
            lastSearchLongitude = longitude
        }

        selectedNearbyPlace = null
        selectedResolvedGym = null
        gymResolveUiState = OnboardingGymResolveUiState.Idle

        viewModelScope.launch {
            gymSearchUiState = OnboardingGymSearchUiState.Loading

            searchNearbyClimbingGymsUseCase(
                latitude = resolvedLatitude,
                longitude = resolvedLongitude,
                query = normalizedQuery,
                nearbyOnly = nearbyOnly
            ).onSuccess { places ->
                gymSearchUiState = OnboardingGymSearchUiState.Success(places)
            }.onFailure { throwable ->
                gymSearchUiState = OnboardingGymSearchUiState.Error(
                    throwable.message ?: "암장 검색에 실패했어요."
                )
            }
        }
    }

    fun resolveSelectedPlace(place: NearbyPlace) {
        selectedNearbyPlace = place
        selectedResolvedGym = null
        gymResolveUiState = OnboardingGymResolveUiState.Loading

        viewModelScope.launch {
            resolveGymUseCase(place)
                .onSuccess { resolvedGym ->
                    selectedResolvedGym = resolvedGym
                    gymResolveUiState = OnboardingGymResolveUiState.Success(resolvedGym)
                }
                .onFailure { throwable ->
                    gymResolveUiState = OnboardingGymResolveUiState.Error(
                        throwable.message ?: "선택한 암장 정보를 확인하지 못했어요."
                    )
                }
        }
    }

    fun updateClimbingLevel(level: OnboardingClimbingLevel) {
        climbingLevel = level
    }

    fun updateClimbingStyle(style: OnboardingClimbingStyle) {
        climbingStyle = style
    }

    fun updateGoal(goal: OnboardingGoal) {
        onboardingGoal = goal
    }

    fun updateSex(option: ProfileSexOption) {
        sex = option
        clearProfileError()
    }

    fun updateHeight(input: String) {
        heightCmInput = ProfileInputValidator.sanitizeNumberInput(input)
        clearProfileError()
    }

    fun updateWeight(input: String) {
        weightKgInput = ProfileInputValidator.sanitizeNumberInput(input)
        clearProfileError()
    }

    fun updateWingspan(input: String) {
        wingspanCmInput = ProfileInputValidator.sanitizeNumberInput(input)
        clearProfileError()
    }

    fun applyHeightToWingspan() {
        if (heightCmInput.isNotBlank()) {
            wingspanCmInput = heightCmInput
            clearProfileError()
        }
    }

    fun canContinueBodyProfile(): Boolean {
        return sex != null &&
            heightCmInput.isNotBlank() &&
            weightKgInput.isNotBlank()
    }

    fun saveBodyProfile(onCompleted: () -> Unit) {
        if (isSubmittingProfile) return

        val resolvedWingspanInput = wingspanCmInput.ifBlank { heightCmInput }
        if (wingspanCmInput.isBlank() && heightCmInput.isNotBlank()) {
            wingspanCmInput = heightCmInput
        }

        val editor = ProfileBodyProfileEditorUiState(
            title = "",
            description = "",
            submitLabel = "",
            sex = sex,
            heightCmInput = heightCmInput,
            weightKgInput = weightKgInput,
            wingspanCmInput = resolvedWingspanInput
        )

        when (val validation = ProfileInputValidator.validateBodyProfile(editor)) {
            is ProfileValidation.Invalid -> {
                profileErrorMessage = validation.message
            }

            is ProfileValidation.Valid -> {
                viewModelScope.launch {
                    isSubmittingProfile = true
                    profileErrorMessage = null

                    updateProfileUseCase(
                        sex = validation.value.sex.apiValue,
                        heightCm = validation.value.heightCm,
                        weightKg = validation.value.weightKg,
                        wingspanCm = validation.value.wingspanCm
                    ).onSuccess {
                        onCompleted()
                    }.onFailure { throwable ->
                        profileErrorMessage = throwable.message ?: "신체 정보를 저장하지 못했어요."
                    }

                    isSubmittingProfile = false
                }
            }
        }
    }

    fun completeFlow(onCompleted: () -> Unit) {
        if (isCompletingFlow) return

        viewModelScope.launch {
            isCompletingFlow = true
            persistSelectedGym()
            persistOnboardingCompletion()
            isCompletingFlow = false
            onCompleted()
        }
    }

    fun selectedGymTitle(): String? {
        return selectedResolvedGym?.gym?.displayName?.normalizeGymDisplayName()
            ?: selectedNearbyPlace?.placeName
    }

    fun selectedGymGradeCount(): Int? = selectedResolvedGym?.grades?.size

    fun resolvedWingspanPreview(): String {
        return wingspanCmInput.ifBlank { heightCmInput }
    }

    private fun clearProfileError() {
        profileErrorMessage = null
    }

    private fun loadProfileDefaults() {
        viewModelScope.launch {
            isLoadingProfileDefaults = true

            getMyInfoUseCase()
                .onSuccess { user ->
                    sex = ProfileSexOption.fromApiValue(user.sex)
                    heightCmInput = user.heightCm.toEditableNumber()
                    weightKgInput = user.weightKg.toEditableNumber()
                    wingspanCmInput = user.wingspanCm.toEditableNumber()
                    hasLoadedProfileDefaults = true
                }
                .onFailure { throwable ->
                    Log.w(TAG, "Failed to load profile defaults", throwable)
                }

            isLoadingProfileDefaults = false
        }
    }

    private suspend fun persistSelectedGym() {
        val resolvedGym = selectedResolvedGym ?: return

        runCatching {
            preferredGymPreferenceDataStore.setPreferredGym(
                gymId = resolvedGym.gym.id,
                gymName = resolvedGym.gym.displayName.normalizeGymDisplayName()
            )
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to persist preferred gym", throwable)
        }
    }

    private suspend fun persistOnboardingCompletion() {
        runCatching {
            onboardingPreferenceDataStore.setOnboardingCompleted()
        }.onFailure { throwable ->
            Log.e(TAG, "Failed to persist onboarding completion", throwable)
        }
    }
}

enum class OnboardingClimbingLevel(
    val title: String,
    val subtitle: String,
    val summaryLabel: String
) {
    Beginner(
        title = "입문 막 시작",
        subtitle = "첫 볼더링과 리드 경험을 쌓는 단계예요",
        summaryLabel = "입문 루트부터 안정적으로"
    ),
    Easy(
        title = "V0-V2 위주",
        subtitle = "쉬운 문제를 꾸준히 완등하고 있어요",
        summaryLabel = "기본 동작 정확도 끌어올리기"
    ),
    Intermediate(
        title = "V3-V5 도전",
        subtitle = "중급 난이도에서 프로젝트를 자주 잡아요",
        summaryLabel = "성장 체감이 빠른 구간"
    ),
    Project(
        title = "프로젝트 중심",
        subtitle = "한 문제를 오래 붙으며 크럭스를 연구해요",
        summaryLabel = "퍼포먼스 기록 최적화"
    )
}

enum class OnboardingClimbingStyle(
    val title: String,
    val subtitle: String
) {
    Bouldering(
        title = "볼더링",
        subtitle = "짧고 강한 문제 해결이 익숙해요"
    ),
    Rope(
        title = "리드/탑로프",
        subtitle = "지구력과 페이스 조절이 중요해요"
    ),
    Both(
        title = "둘 다",
        subtitle = "상황에 따라 스타일을 오가며 타요"
    )
}

enum class OnboardingGoal(
    val title: String,
    val subtitle: String,
    val focusLabel: String
) {
    TopRate(
        title = "완등률 올리기",
        subtitle = "실패 구간을 줄이고 안정적으로 완등하고 싶어요",
        focusLabel = "완등률 개선"
    ),
    Reach(
        title = "다이노 · 리치 개선",
        subtitle = "리치와 폭발적인 움직임을 더 잘 쓰고 싶어요",
        focusLabel = "리치 활용"
    ),
    Endurance(
        title = "지구력 키우기",
        subtitle = "후반에도 힘이 남는 등반을 만들고 싶어요",
        focusLabel = "지구력 강화"
    ),
    Safe(
        title = "부상 없이 오래 타기",
        subtitle = "회복과 무리 없는 루틴이 더 중요해요",
        focusLabel = "안전한 루틴"
    ),
    Routine(
        title = "기록 습관 만들기",
        subtitle = "등반을 꾸준히 남기고 성장 흐름을 보고 싶어요",
        focusLabel = "기록 습관"
    )
}

sealed interface OnboardingGymSearchUiState {
    data object Idle : OnboardingGymSearchUiState
    data object Loading : OnboardingGymSearchUiState
    data class Success(val places: List<NearbyPlace>) : OnboardingGymSearchUiState
    data class Error(val message: String) : OnboardingGymSearchUiState
}

sealed interface OnboardingGymResolveUiState {
    data object Idle : OnboardingGymResolveUiState
    data object Loading : OnboardingGymResolveUiState
    data class Success(val resolvedGym: ResolvedGym) : OnboardingGymResolveUiState
    data class Error(val message: String) : OnboardingGymResolveUiState
}

private fun Float?.toEditableNumber(): String {
    val value = this ?: return ""
    return if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

private fun String.normalizeGymDisplayName(): String {
    return replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
}
