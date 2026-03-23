package com.ddgo.app.feature.climbing.upload

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class UploadChallengeDelegate(
    private val searchNearbyClimbingGymsUseCase: SearchNearbyClimbingGymsUseCase,
    private val resolveGymUseCase: ResolveGymUseCase,
    private val createChallengeUseCase: CreateChallengeUseCase
) {

    var gymId by mutableStateOf<Int?>(null)
    var gymName by mutableStateOf("")
    var difficultyLevel by mutableStateOf("")
    var holdColor by mutableStateOf("")
    var selectedHoldColorKey by mutableStateOf<String?>(null)
    var selectedLevelSortOrder by mutableStateOf<Int?>(null)
    var selectedGymGradeId by mutableStateOf<Long?>(null)
    var selectedGymGrade by mutableStateOf<GymGrade?>(null)
    var createdChallenge by mutableStateOf<ChallengeSession?>(null)
    var challengeId by mutableStateOf<Long?>(null)

    private val _challengeCreationUiState =
        MutableStateFlow<ChallengeCreationUiState>(ChallengeCreationUiState.Idle)
    val challengeCreationUiState = _challengeCreationUiState.asStateFlow()

    private val _gymSearchUiState = MutableStateFlow<GymSearchUiState>(GymSearchUiState.Idle)
    val gymSearchUiState = _gymSearchUiState.asStateFlow()

    private val _gymResolveUiState = MutableStateFlow<GymResolveUiState>(GymResolveUiState.Idle)
    val gymResolveUiState = _gymResolveUiState.asStateFlow()

    var nearbyPlaces by mutableStateOf<List<NearbyPlace>>(emptyList())
    var selectedNearbyPlace by mutableStateOf<NearbyPlace?>(null)
    var resolvedGym by mutableStateOf<ResolvedGym?>(null)
    var resolvedGymGrades by mutableStateOf<List<GymGrade>>(emptyList())
    var lastSearchLatitude by mutableStateOf<Double?>(null)
    var lastSearchLongitude by mutableStateOf<Double?>(null)
    var nearbyPlaceSortMode by mutableStateOf(NearbyPlaceSortMode.DistanceAscending)

    val sortedNearbyPlaces: List<NearbyPlace>
        get() = nearbyPlaces.sortedWith(nearbyPlaceComparator(nearbyPlaceSortMode))

    fun updateGymInfo(
        id: Int,
        name: String,
        onChallengeFlowCleared: () -> Unit
    ) {
        gymId = id
        gymName = name
        onChallengeFlowCleared()
    }

    suspend fun searchNearbyPlaces(
        latitude: Double,
        longitude: Double,
        query: String,
        nearbyOnly: Boolean,
        onChallengeFlowCleared: () -> Unit
    ) {
        lastSearchLatitude = latitude
        lastSearchLongitude = longitude

        val normalizedQuery = query.trim()
        Log.d(
            TAG,
            "searchNearbyPlaces: latitude=$latitude, longitude=$longitude, query=$normalizedQuery, nearbyOnly=$nearbyOnly"
        )

        selectedNearbyPlace = null
        resolvedGym = null
        resolvedGymGrades = emptyList()
        gymId = null
        gymName = ""
        onChallengeFlowCleared()
        _gymResolveUiState.value = GymResolveUiState.Idle
        _gymSearchUiState.value = GymSearchUiState.Loading

        searchNearbyClimbingGymsUseCase(
            latitude = latitude,
            longitude = longitude,
            query = normalizedQuery,
            nearbyOnly = nearbyOnly
        )
            .onSuccess { places ->
                nearbyPlaces = places
                Log.d(TAG, "searchNearbyPlaces: success, placeCount=${places.size}")
                _gymSearchUiState.value = GymSearchUiState.Success(places)
            }
            .onFailure { throwable ->
                nearbyPlaces = emptyList()
                Log.e(TAG, "searchNearbyPlaces: failed", throwable)
                _gymSearchUiState.value = GymSearchUiState.Error(
                    throwable.message ?: "Failed to search nearby gyms."
                )
            }
    }

    suspend fun resolveSelectedPlace(
        place: NearbyPlace,
        onChallengeFlowCleared: () -> Unit
    ) {
        selectedNearbyPlace = place
        Log.d(
            TAG,
            "resolveSelectedPlace: name=${place.placeName}, " +
                "address=${place.roadAddressName ?: place.addressName}, " +
                "lat=${place.latitude}, lng=${place.longitude}, " +
                "externalPlaceId=${place.externalPlaceId}"
        )

        _gymResolveUiState.value = GymResolveUiState.Loading

        resolveGymUseCase(place)
            .onSuccess { resolved ->
                resolvedGym = resolved
                resolvedGymGrades = resolved.grades
                gymId = resolved.gymId
                gymName = resolved.gym.displayName
                onChallengeFlowCleared()
                Log.d(
                    TAG,
                    "resolveSelectedPlace: success, gymId=${resolved.gymId}, " +
                        "displayName=${resolved.gym.displayName}, gradeCount=${resolved.grades.size}, " +
                        "matched=${resolved.matched}, matchStatus=${resolved.matchStatus}, " +
                        "gradeSource=${resolved.gradeSource}"
                )
                _gymResolveUiState.value = GymResolveUiState.Success(resolved)
            }
            .onFailure { throwable ->
                resolvedGym = null
                resolvedGymGrades = emptyList()
                gymId = null
                gymName = ""
                onChallengeFlowCleared()
                Log.e(TAG, "resolveSelectedPlace: failed", throwable)
                _gymResolveUiState.value = GymResolveUiState.Error(
                    throwable.message ?: "Failed to resolve gym."
                )
            }
    }

    fun selectGymLevel(
        sortOrder: Int,
        formatSelectedLevelLabel: (GymGrade) -> String,
        onCreatedChallengeCleared: () -> Unit
    ) {
        selectedLevelSortOrder = sortOrder

        val matchingGrades = resolvedGymGrades.filter { it.sortOrder == sortOrder }
        difficultyLevel = matchingGrades.firstOrNull()
            ?.let(formatSelectedLevelLabel)
            ?: "V$sortOrder"

        val nextSelectedGrade = selectedGymGrade
            ?.takeIf { it.sortOrder == sortOrder }
            ?: matchingGrades.firstOrNull()

        if (nextSelectedGrade != null) {
            selectedGymGrade = nextSelectedGrade
            selectedGymGradeId = nextSelectedGrade.gymGradeId.toLong()
        } else {
            selectedGymGrade = null
            selectedGymGradeId = null
        }

        onCreatedChallengeCleared()
    }

    fun updateHoldColor(
        colorKey: String,
        resolveHoldColorDisplayName: (String) -> String
    ) {
        selectedHoldColorKey = colorKey.takeIf { it.isNotBlank() }
        holdColor = resolveHoldColorDisplayName(colorKey)
    }

    fun selectGymGrade(
        grade: GymGrade,
        formatSelectedLevelLabel: (GymGrade) -> String,
        onCreatedChallengeCleared: () -> Unit
    ) {
        selectedLevelSortOrder = grade.sortOrder
        selectedGymGrade = grade
        selectedGymGradeId = grade.gymGradeId.toLong()
        difficultyLevel = formatSelectedLevelLabel(grade)
        onCreatedChallengeCleared()
    }

    suspend fun createChallengeFromSelection(
        startedAt: String,
        resolveDefaultHoldColorKey: (String) -> String?,
        resolveHoldColorDisplayName: (String) -> String
    ): ChallengeSession? {
        val currentGymId = gymId?.toLong()
        val currentGymGradeId = selectedGymGradeId

        if (currentGymId == null || currentGymId <= 0L) {
            _challengeCreationUiState.value =
                ChallengeCreationUiState.Error("암장 선택이 필요합니다.")
            return null
        }

        if (currentGymGradeId == null || currentGymGradeId <= 0L) {
            _challengeCreationUiState.value =
                ChallengeCreationUiState.Error("난이도 선택이 필요합니다.")
            return null
        }

        val existingChallenge = createdChallenge
        if (
            existingChallenge != null &&
            existingChallenge.gymId == currentGymId &&
            existingChallenge.gymGradeId == currentGymGradeId
        ) {
            challengeId = existingChallenge.challengeId
            _challengeCreationUiState.value = ChallengeCreationUiState.Success(existingChallenge)
            return existingChallenge
        }

        _challengeCreationUiState.value = ChallengeCreationUiState.Loading

        return createChallengeUseCase(
            gymId = currentGymId,
            gymGradeId = currentGymGradeId,
            startedAt = startedAt
        )
            .onSuccess { challenge ->
                createdChallenge = challenge
                challengeId = challenge.challengeId
                difficultyLevel =
                    challenge.gradeLabel ?: (selectedGymGrade?.gradeLabel ?: challenge.problemColor)
                if (selectedHoldColorKey == null) {
                    resolveDefaultHoldColorKey(challenge.problemColor)
                        ?.let { defaultColorKey ->
                            selectedHoldColorKey = defaultColorKey
                            holdColor = resolveHoldColorDisplayName(defaultColorKey)
                        }
                }
                _challengeCreationUiState.value = ChallengeCreationUiState.Success(challenge)
                Log.d(
                    TAG,
                    "createChallengeFromSelection: success, challengeId=${challenge.challengeId}, " +
                        "gymId=${challenge.gymId}, gymGradeId=${challenge.gymGradeId}, " +
                        "problemColor=${challenge.problemColor}"
                )
            }
            .onFailure { throwable ->
                createdChallenge = null
                challengeId = null
                Log.e(TAG, "createChallengeFromSelection: failed", throwable)
                _challengeCreationUiState.value = ChallengeCreationUiState.Error(
                    throwable.message ?: "Failed to create challenge."
                )
            }
            .getOrNull()
    }

    fun consumeChallengeCreationResult() {
        if (_challengeCreationUiState.value is ChallengeCreationUiState.Success) {
            _challengeCreationUiState.value = ChallengeCreationUiState.Idle
        }
    }

    fun resetChallengeCreationUiState() {
        _challengeCreationUiState.value = ChallengeCreationUiState.Idle
    }

    fun clearSelectionState() {
        selectedLevelSortOrder = null
        selectedGymGradeId = null
        selectedGymGrade = null
        difficultyLevel = ""
        selectedHoldColorKey = null
        holdColor = ""
    }

    fun clearCreatedChallengeState() {
        createdChallenge = null
        challengeId = null
        _challengeCreationUiState.value = ChallengeCreationUiState.Idle
    }

    fun resetSearchState() {
        nearbyPlaces = emptyList()
        selectedNearbyPlace = null
        resolvedGym = null
        resolvedGymGrades = emptyList()
        gymId = null
        gymName = ""
        lastSearchLatitude = null
        lastSearchLongitude = null
        nearbyPlaceSortMode = NearbyPlaceSortMode.DistanceAscending
        _gymSearchUiState.value = GymSearchUiState.Idle
        _gymResolveUiState.value = GymResolveUiState.Idle
    }

    fun resetAll() {
        resetSearchState()
        clearSelectionState()
        clearCreatedChallengeState()
    }

    fun updateNearbyPlaceSortMode(sortMode: NearbyPlaceSortMode) {
        nearbyPlaceSortMode = sortMode
    }

    fun applyExistingChallenge(
        challenge: ChallengeSession,
        resolveHoldColorKey: (String) -> String?,
        resolveHoldColorDisplayName: (String) -> String
    ) {
        createdChallenge = challenge
        challengeId = challenge.challengeId
        gymId = challenge.gymId.toInt()
        gymName = challenge.gymName
        selectedGymGradeId = challenge.gymGradeId
        difficultyLevel = challenge.gradeLabel ?: challenge.problemColor
        selectedHoldColorKey = resolveHoldColorKey(challenge.problemColor)
        holdColor = resolveHoldColorDisplayName(challenge.problemColor)
        _challengeCreationUiState.value = ChallengeCreationUiState.Success(challenge)
    }

    companion object {
        private const val TAG = "UploadChallengeDelegate"

        private fun nearbyPlaceComparator(sortMode: NearbyPlaceSortMode) =
            when (sortMode) {
                NearbyPlaceSortMode.DistanceAscending -> compareBy<NearbyPlace> {
                    it.distanceMeters ?: Int.MAX_VALUE
                }.thenBy {
                    it.placeName.lowercase()
                }

                NearbyPlaceSortMode.NameAscending -> compareBy<NearbyPlace> {
                    it.placeName.lowercase()
                }.thenBy {
                    it.distanceMeters ?: Int.MAX_VALUE
                }
            }
    }
}
