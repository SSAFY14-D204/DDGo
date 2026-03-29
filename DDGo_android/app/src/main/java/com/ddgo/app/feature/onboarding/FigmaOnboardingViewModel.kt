package com.ddgo.app.feature.onboarding

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.datastore.MainEntryGuideStep
import com.ddgo.app.core.datastore.OnboardingPreferenceDataStore
import com.ddgo.app.core.datastore.PreferredGymPreferenceDataStore
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.core.validation.AuthInputPolicy
import com.ddgo.app.core.validation.ValidationResult
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.usecase.CheckNicknameAvailabilityUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import com.ddgo.app.domain.usecase.UpdateProfileUseCase
import com.ddgo.app.feature.profile.model.ProfileSexOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "FigmaOnboardingVm"
private const val DEFAULT_SEARCH_LATITUDE = 37.5665
private const val DEFAULT_SEARCH_LONGITUDE = 126.9780
private const val DEFAULT_HEIGHT = 165
private const val DEFAULT_WEIGHT_TENTHS = 650
private const val GYM_SEARCH_DEBOUNCE_MS = 250L
private const val NICKNAME_CHECK_DEBOUNCE_MS = 350L

@HiltViewModel
class FigmaOnboardingViewModel @Inject constructor(
    private val onboardingPreferenceDataStore: OnboardingPreferenceDataStore,
    private val preferredGymPreferenceDataStore: PreferredGymPreferenceDataStore,
    private val getMyInfoUseCase: GetMyInfoUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val searchNearbyClimbingGymsUseCase: SearchNearbyClimbingGymsUseCase,
    private val resolveGymUseCase: ResolveGymUseCase,
    private val checkNicknameAvailabilityUseCase: CheckNicknameAvailabilityUseCase,
    private val updateNicknameUseCase: UpdateNicknameUseCase
) : ViewModel() {

    private var hasPrepared = false
    private var gymSearchJob: Job? = null
    private var nicknameAvailabilityJob: Job? = null
    private var currentNickname: String? = null
    private var nicknameSeed: String = "ddgo"
    private var lastCheckedNickname: String? = null
    private var lastCheckedNicknameAvailable: Boolean = false
    private var hasCustomWingspan: Boolean = false

    var isPreparing by mutableStateOf(false)
        private set

    var isSavingProfile by mutableStateOf(false)
        private set

    var isSavingNickname by mutableStateOf(false)
        private set

    var isCompletingFlow by mutableStateOf(false)
        private set

    var sex by mutableStateOf<ProfileSexOption?>(null)
        private set

    var heightCm by mutableIntStateOf(DEFAULT_HEIGHT)
        private set

    var weightTenthsKg by mutableIntStateOf(DEFAULT_WEIGHT_TENTHS)
        private set

    var wingspanCm by mutableIntStateOf(DEFAULT_HEIGHT)
        private set

    var gymSearchQuery by mutableStateOf("")
        private set

    var gymSearchUiState by mutableStateOf<FigmaOnboardingGymSearchUiState>(
        FigmaOnboardingGymSearchUiState.Idle
    )
        private set

    var gymResolveUiState by mutableStateOf<FigmaOnboardingGymResolveUiState>(
        FigmaOnboardingGymResolveUiState.Idle
    )
        private set

    var selectedNearbyPlace by mutableStateOf<NearbyPlace?>(null)
        private set

    var selectedResolvedGym by mutableStateOf<ResolvedGym?>(null)
        private set

    var nicknameInput by mutableStateOf("")
        private set

    var recommendedNickname by mutableStateOf("포근한고양이")
        private set

    var nicknameFeedback by mutableStateOf<OnboardingFieldFeedback?>(null)
        private set

    var isCheckingNickname by mutableStateOf(false)
        private set

    var isNicknameAvailable by mutableStateOf(false)
        private set

    var profileErrorMessage by mutableStateOf<String?>(null)
        private set

    fun prepare() {
        if (hasPrepared) return

        viewModelScope.launch {
            isPreparing = true

            getMyInfoUseCase()
                .onSuccess { user ->
                    sex = ProfileSexOption.fromApiValue(user.sex)
                    heightCm = user.heightCm.toPickerValue(
                        defaultValue = DEFAULT_HEIGHT,
                        min = HEIGHT_RANGE.first,
                        max = HEIGHT_RANGE.last
                    )
                    weightTenthsKg = user.weightKg.toTenthsPickerValue(
                        defaultValue = DEFAULT_WEIGHT_TENTHS,
                        min = WEIGHT_TENTHS_RANGE.first,
                        max = WEIGHT_TENTHS_RANGE.last
                    )
                    wingspanCm = user.wingspanCm.toPickerValue(
                        defaultValue = heightCm,
                        min = WINGSPAN_RANGE.first,
                        max = WINGSPAN_RANGE.last
                    )
                    hasCustomWingspan = user.wingspanCm?.let { it > 0f } == true
                    currentNickname = user.nickname.takeIf { it.isNotBlank() }
                    nicknameSeed = "${user.id}:${user.username}"
                    recommendedNickname = buildRecommendedNickname(nicknameSeed, currentNickname)
                    nicknameInput = currentNickname.orEmpty()
                    if (nicknameInput.isNotBlank()) {
                        nicknameFeedback = OnboardingFieldFeedback(
                            message = "멋진 닉네임이네요!",
                            tone = OnboardingFieldFeedbackTone.Success
                        )
                        isNicknameAvailable = true
                    }
                }
                .onFailure { throwable ->
                    Log.w(TAG, "Failed to load onboarding defaults", throwable)
                    recommendedNickname = buildRecommendedNickname(nicknameSeed, fallback = null)
                }

            if (nicknameInput.isBlank()) {
                nicknameFeedback = null
                isNicknameAvailable = false
            }

            hasPrepared = true
            isPreparing = false
        }
    }

    fun selectSex(option: ProfileSexOption) {
        sex = option
        profileErrorMessage = null
    }

    fun setHeight(value: Int) {
        heightCm = value.coerceIn(HEIGHT_RANGE)
        if (!hasCustomWingspan) {
            wingspanCm = heightCm
        }
        profileErrorMessage = null
    }

    fun setWeight(value: Int) {
        weightTenthsKg = (value * 10).coerceIn(WEIGHT_TENTHS_RANGE)
        profileErrorMessage = null
    }

    fun setWeightTenths(value: Int) {
        weightTenthsKg = value.coerceIn(WEIGHT_TENTHS_RANGE)
        profileErrorMessage = null
    }

    fun setWingspan(value: Int) {
        wingspanCm = value.coerceIn(WINGSPAN_RANGE)
        hasCustomWingspan = true
        profileErrorMessage = null
    }

    fun applyHeightToWingspan() {
        wingspanCm = heightCm
        hasCustomWingspan = false
        profileErrorMessage = null
    }

    fun updateGymSearchQuery(input: String) {
        gymSearchQuery = input
        selectedNearbyPlace = null
        selectedResolvedGym = null
        gymResolveUiState = FigmaOnboardingGymResolveUiState.Idle
        scheduleGymSearch()
    }

    fun triggerGymSearch() {
        scheduleGymSearch(immediate = true)
    }

    fun selectGymPlace(place: NearbyPlace) {
        selectedNearbyPlace = place
        selectedResolvedGym = null
        gymSearchQuery = place.placeName
        gymResolveUiState = FigmaOnboardingGymResolveUiState.Loading

        viewModelScope.launch {
            resolveGymUseCase(place)
                .onSuccess { resolvedGym ->
                    selectedResolvedGym = resolvedGym
                    gymResolveUiState = FigmaOnboardingGymResolveUiState.Success(resolvedGym)
                }
                .onFailure { throwable ->
                    gymResolveUiState = FigmaOnboardingGymResolveUiState.Error(
                        throwable.orNetworkMessage("암장 정보를 확인하지 못했어요.")
                    )
                }
        }
    }

    fun updateNicknameInput(input: String) {
        nicknameInput = input.take(20)
        refreshNicknameAvailability()
    }

    fun applyRecommendedNickname() {
        nicknameInput = recommendedNickname
        refreshNicknameAvailability()
    }

    fun canContinueNickname(): Boolean {
        return nicknameInput.trim().isNotBlank() &&
            isNicknameAvailable &&
            !isCheckingNickname &&
            !isSavingNickname
    }

    fun saveBodyProfile(onSuccess: () -> Unit) {
        if (isSavingProfile) return

        val selectedSex = sex
        if (selectedSex == null) {
            profileErrorMessage = "성별을 선택해주세요."
            return
        }

        viewModelScope.launch {
            isSavingProfile = true
            profileErrorMessage = null

            updateProfileUseCase(
                sex = selectedSex.apiValue,
                heightCm = heightCm.toFloat(),
                weightKg = weightTenthsKg / 10f,
                wingspanCm = wingspanCm.toFloat()
            ).onSuccess {
                onSuccess()
            }.onFailure { throwable ->
                profileErrorMessage = throwable.orNetworkMessage("신체 정보를 저장하지 못했어요.")
            }

            isSavingProfile = false
        }
    }

    fun saveNickname(onSuccess: () -> Unit) {
        if (isSavingNickname) return

        val trimmedNickname = nicknameInput.trim()
        when (val validation = AuthInputPolicy.validateNickname(trimmedNickname)) {
            is ValidationResult.Invalid -> {
                nicknameFeedback = OnboardingFieldFeedback(
                    message = validation.message,
                    tone = OnboardingFieldFeedbackTone.Error
                )
                isNicknameAvailable = false
                return
            }

            is ValidationResult.Valid -> Unit
        }

        if (!currentNickname.isNullOrBlank() && trimmedNickname == currentNickname) {
            onSuccess()
            return
        }

        if (isCheckingNickname) {
            nicknameFeedback = OnboardingFieldFeedback(
                message = "닉네임을 확인하는 중이에요.",
                tone = OnboardingFieldFeedbackTone.Neutral
            )
            return
        }

        if (!isNicknameAvailable || lastCheckedNickname != trimmedNickname) {
            nicknameFeedback = OnboardingFieldFeedback(
                message = "사용 가능한 닉네임인지 확인해주세요.",
                tone = OnboardingFieldFeedbackTone.Error
            )
            return
        }

        viewModelScope.launch {
            isSavingNickname = true

            updateNicknameUseCase(trimmedNickname)
                .onSuccess {
                    currentNickname = trimmedNickname
                    nicknameFeedback = OnboardingFieldFeedback(
                        message = "멋진 닉네임이네요!",
                        tone = OnboardingFieldFeedbackTone.Success
                    )
                    isNicknameAvailable = true
                    onSuccess()
                }
                .onFailure { throwable ->
                    nicknameFeedback = OnboardingFieldFeedback(
                        message = throwable.orNetworkMessage("닉네임을 저장하지 못했어요."),
                        tone = OnboardingFieldFeedbackTone.Error
                    )
                    isNicknameAvailable = false
                }

            isSavingNickname = false
        }
    }

    fun completeOnboarding(
        showEntryGuide: Boolean,
        onSuccess: () -> Unit
    ) {
        if (isCompletingFlow) return

        viewModelScope.launch {
            isCompletingFlow = true

            persistSelectedGym()

            runCatching {
                onboardingPreferenceDataStore.setOnboardingCompleted(true)
                onboardingPreferenceDataStore.setMainEntryGuideStep(MainEntryGuideStep.DONE)
            }.onFailure { throwable ->
                Log.e(TAG, "Failed to persist onboarding completion", throwable)
            }

            isCompletingFlow = false
            onSuccess()
        }
    }

    fun selectedGymTitle(): String? {
        return selectedResolvedGym?.gym?.displayName?.normalizeGymDisplayName()
            ?: selectedNearbyPlace?.placeName
    }

    fun completionNickname(): String {
        return nicknameInput.trim()
            .ifBlank { currentNickname.orEmpty() }
            .ifBlank { recommendedNickname }
    }

    fun weightDisplayText(): String = formatWeightTenths(weightTenthsKg)

    fun weightSummaryText(): String = formatWeightTenths(weightTenthsKg).removeSuffix(".0")

    fun shouldShowRecommendedNickname(): Boolean {
        return recommendedNickname.isNotBlank()
    }

    private fun scheduleGymSearch(immediate: Boolean = false) {
        gymSearchJob?.cancel()

        val trimmedQuery = gymSearchQuery.trim()
        if (trimmedQuery.isBlank()) {
            gymSearchUiState = FigmaOnboardingGymSearchUiState.Idle
            return
        }

        gymSearchJob = viewModelScope.launch {
            if (!immediate) delay(GYM_SEARCH_DEBOUNCE_MS)
            gymSearchUiState = FigmaOnboardingGymSearchUiState.Loading

            searchNearbyClimbingGymsUseCase(
                latitude = DEFAULT_SEARCH_LATITUDE,
                longitude = DEFAULT_SEARCH_LONGITUDE,
                query = trimmedQuery
            ).onSuccess { places ->
                gymSearchUiState = if (places.isEmpty()) {
                    FigmaOnboardingGymSearchUiState.Error("\uCC3E\uC73C\uC2DC\uB294 \uC554\uC7A5\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.")
                } else {
                    FigmaOnboardingGymSearchUiState.Success(places)
                }
            }.onFailure { throwable ->
                gymSearchUiState = FigmaOnboardingGymSearchUiState.Error(
                    throwable.orNetworkMessage("암장을 찾지 못했어요.")
                )
            }
        }
    }

    private fun refreshNicknameAvailability() {
        nicknameAvailabilityJob?.cancel()
        val trimmedNickname = nicknameInput.trim()

        if (trimmedNickname.isBlank()) {
            nicknameFeedback = null
            isCheckingNickname = false
            isNicknameAvailable = false
            return
        }

        when (val validation = AuthInputPolicy.validateNickname(trimmedNickname)) {
            is ValidationResult.Invalid -> {
                nicknameFeedback = OnboardingFieldFeedback(
                    message = validation.message,
                    tone = OnboardingFieldFeedbackTone.Error
                )
                isCheckingNickname = false
                isNicknameAvailable = false
                return
            }

            is ValidationResult.Valid -> Unit
        }

        if (!currentNickname.isNullOrBlank() && trimmedNickname == currentNickname) {
            nicknameFeedback = OnboardingFieldFeedback(
                message = "멋진 닉네임이네요!",
                tone = OnboardingFieldFeedbackTone.Success
            )
            isCheckingNickname = false
            isNicknameAvailable = true
            return
        }

        if (lastCheckedNickname == trimmedNickname) {
            nicknameFeedback = OnboardingFieldFeedback(
                message = if (lastCheckedNicknameAvailable) {
                    "멋진 닉네임이네요!"
                } else {
                    "이미 사용 중인 닉네임이에요."
                },
                tone = if (lastCheckedNicknameAvailable) {
                    OnboardingFieldFeedbackTone.Success
                } else {
                    OnboardingFieldFeedbackTone.Error
                }
            )
            isCheckingNickname = false
            isNicknameAvailable = lastCheckedNicknameAvailable
            return
        }

        isCheckingNickname = true
        isNicknameAvailable = false
        nicknameFeedback = OnboardingFieldFeedback(
            message = "닉네임을 확인하는 중이에요.",
            tone = OnboardingFieldFeedbackTone.Neutral
        )

        nicknameAvailabilityJob = viewModelScope.launch {
            delay(NICKNAME_CHECK_DEBOUNCE_MS)

            checkNicknameAvailabilityUseCase(trimmedNickname)
                .onSuccess { available ->
                    if (nicknameInput.trim() != trimmedNickname) return@launch

                    lastCheckedNickname = trimmedNickname
                    lastCheckedNicknameAvailable = available
                    isCheckingNickname = false
                    isNicknameAvailable = available
                    nicknameFeedback = OnboardingFieldFeedback(
                        message = if (available) {
                            "멋진 닉네임이네요!"
                        } else {
                            "이미 사용 중인 닉네임이에요."
                        },
                        tone = if (available) {
                            OnboardingFieldFeedbackTone.Success
                        } else {
                            OnboardingFieldFeedbackTone.Error
                        }
                    )
                }
                .onFailure { throwable ->
                    if (nicknameInput.trim() != trimmedNickname) return@launch

                    lastCheckedNickname = null
                    lastCheckedNicknameAvailable = false
                    isCheckingNickname = false
                    isNicknameAvailable = false
                    nicknameFeedback = OnboardingFieldFeedback(
                        message = throwable.orNetworkMessage("닉네임 확인에 실패했어요."),
                        tone = OnboardingFieldFeedbackTone.Error
                    )
                }
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
            Log.w(TAG, "Failed to save preferred gym", throwable)
        }
    }

    private fun Throwable.orNetworkMessage(fallback: String): String {
        return toUserFacingNetworkMessageOrNull()
            ?: if (fallback.contains("?")) {
                "\uC694\uCCAD\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC5B4\uC694."
            } else {
                fallback
            }
    }
}

sealed interface FigmaOnboardingGymSearchUiState {
    data object Idle : FigmaOnboardingGymSearchUiState
    data object Loading : FigmaOnboardingGymSearchUiState
    data class Success(val places: List<NearbyPlace>) : FigmaOnboardingGymSearchUiState
    data class Error(val message: String) : FigmaOnboardingGymSearchUiState
}

sealed interface FigmaOnboardingGymResolveUiState {
    data object Idle : FigmaOnboardingGymResolveUiState
    data object Loading : FigmaOnboardingGymResolveUiState
    data class Success(val resolvedGym: ResolvedGym) : FigmaOnboardingGymResolveUiState
    data class Error(val message: String) : FigmaOnboardingGymResolveUiState
}

enum class OnboardingFieldFeedbackTone {
    Neutral,
    Success,
    Error
}

data class OnboardingFieldFeedback(
    val message: String,
    val tone: OnboardingFieldFeedbackTone
)

val HEIGHT_RANGE: IntRange = 140..220
val WEIGHT_RANGE: IntRange = 30..150
val WEIGHT_TENTHS_RANGE: IntRange = 300..1500
val WINGSPAN_RANGE: IntRange = 140..220

private fun Float?.toPickerValue(
    defaultValue: Int,
    min: Int,
    max: Int
): Int {
    val numericValue = this?.takeIf { it > 0f }?.toInt() ?: defaultValue
    return numericValue.coerceIn(min, max)
}

private fun Float?.toTenthsPickerValue(
    defaultValue: Int,
    min: Int,
    max: Int
): Int {
    val numericValue = this
        ?.takeIf { it > 0f }
        ?.times(10f)
        ?.toInt()
        ?: defaultValue
    return numericValue.coerceIn(min, max)
}

private fun formatWeightTenths(value: Int): String {
    val whole = value / 10
    val decimal = kotlin.math.abs(value % 10)
    return "$whole.$decimal"
}

private fun buildRecommendedNickname(seed: String, fallback: String?): String {
    if (!fallback.isNullOrBlank()) return fallback

    val adjectives = listOf("포근한", "반짝이는", "단단한", "유연한", "차분한", "민첩한")
    val nouns = listOf("고양이", "다람쥐", "호랑이", "클라이머", "여우", "산새")
    val hash = seed.hashCode()
    val adjective = adjectives[kotlin.math.abs(hash) % adjectives.size]
    val noun = nouns[kotlin.math.abs(hash / 7) % nouns.size]
    return adjective + noun
}

private fun String.normalizeGymDisplayName(): String {
    return replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
}
