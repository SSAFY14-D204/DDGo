package com.ddgo.app.feature.climbing.upload

import androidx.annotation.ColorInt
import com.ddgo.app.core.datastore.UploadRecoveryEntryIntent
import com.ddgo.app.core.ui.tokens.DdgoHoldColorPalette
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.model.UploadedAttemptVideo

sealed class UploadUiState {
    data object Idle : UploadUiState()
    data object Loading : UploadUiState()
    data object Success : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}

sealed interface UploadEntryPreparationResult {
    data object NoRecovery : UploadEntryPreparationResult
    data class Recovered(
        val target: UploadRecoveryResumeTarget
    ) : UploadEntryPreparationResult
    data object Blocked : UploadEntryPreparationResult
}

data class UploadRecoveryResumeTarget(
    val route: UploadRecoveryRoute,
    val createStep: ChallengeCreateEntryStep? = null
)

enum class UploadRecoveryPromptType {
    ClosedResult,
    RetryRequired,
    RestartRequired
}

data class UploadRecoveryPrompt(
    val type: UploadRecoveryPromptType,
    val challengeResult: String? = null,
    val reason: String? = null,
    val entryIntent: UploadRecoveryEntryIntent? = null
)

sealed class GymSearchUiState {
    data object Idle : GymSearchUiState()
    data object Loading : GymSearchUiState()
    data class Success(val places: List<NearbyPlace>) : GymSearchUiState()
    data class Error(val message: String) : GymSearchUiState()
}

sealed class GymResolveUiState {
    data object Idle : GymResolveUiState()
    data object Loading : GymResolveUiState()
    data class Success(val resolvedGym: ResolvedGym) : GymResolveUiState()
    data class Error(val message: String) : GymResolveUiState()
}

sealed class ChallengeCreationUiState {
    data object Idle : ChallengeCreationUiState()
    data object Loading : ChallengeCreationUiState()
    data class Success(val challenge: ChallengeSession) : ChallengeCreationUiState()
    data class Error(val message: String) : ChallengeCreationUiState()
}

sealed class UploadSubmissionUiState {
    data object Idle : UploadSubmissionUiState()
    data class Loading(val message: String) : UploadSubmissionUiState()
    data class Success(val uploadedAttempts: List<UploadedAttemptVideo>) : UploadSubmissionUiState()
    data class Error(val message: String) : UploadSubmissionUiState()
}

sealed class RealtimeAttemptActionState {
    data object Idle : RealtimeAttemptActionState()
    data object ShowingOptions : RealtimeAttemptActionState()
    data object RetakeRequested : RealtimeAttemptActionState()
    data object FinalAnalysisRequested : RealtimeAttemptActionState()
}

enum class RealtimeSetupStep {
    GymPrompt,
    GymList,
    ChallengeCreate,
    Ready
}

enum class NearbyPlaceSortMode {
    DistanceAscending,
    NameAscending
}

data class RealtimeHoldColorOption(
    val key: String,
    val label: String,
    @ColorInt val colorInt: Int,
    @ColorInt val borderColorInt: Int? = null
)

data class UploadRealtimeOverlayUiState(
    val setupStep: RealtimeSetupStep = RealtimeSetupStep.GymPrompt,
    val gymId: Int? = null,
    val gymName: String = "",
    val searchQuery: String = "",
    val nearbyPlaces: List<NearbyPlace> = emptyList(),
    val selectedNearbyPlace: NearbyPlace? = null,
    val nearbyPlaceSortMode: NearbyPlaceSortMode = NearbyPlaceSortMode.DistanceAscending,
    val gymSearchUiState: GymSearchUiState = GymSearchUiState.Idle,
    val gymResolveUiState: GymResolveUiState = GymResolveUiState.Idle,
    val resolvedGym: ResolvedGym? = null,
    val resolvedGymGrades: List<GymGrade> = emptyList(),
    val selectedLevelSortOrder: Int? = null,
    val selectedGymGrade: GymGrade? = null,
    val difficultyLabel: String = "",
    val selectedHoldColorKey: String? = null,
    val holdColor: String = "",
    val holdColorOptions: List<RealtimeHoldColorOption> = emptyList(),
    val challengeCreationUiState: ChallengeCreationUiState = ChallengeCreationUiState.Idle,
    val isHoldColorSheetVisible: Boolean = false,
    val isSetupVisible: Boolean = true,
    val isChallengeReady: Boolean = false,
    val isRetakePrepared: Boolean = false,
    val canFinishChallenge: Boolean = false,
    val canRetakeAttempt: Boolean = false,
    val lastSearchLatitude: Double? = null,
    val lastSearchLongitude: Double? = null
)

internal val realtimeHoldColorOptions = DdgoHoldColorPalette.all.map { token ->
    RealtimeHoldColorOption(
        key = token.key,
        label = token.label,
        colorInt = token.colorInt,
        borderColorInt = token.borderColorInt
    )
}

enum class AnalysisLoadingPhase {
    AttemptResultPreparation,
    FinalAnalysisPreparation
}

sealed class FinalAnalysisPreparationUiState {
    data object Idle : FinalAnalysisPreparationUiState()
    data object Loading : FinalAnalysisPreparationUiState()
    data object Success : FinalAnalysisPreparationUiState()
    data class Error(val message: String) : FinalAnalysisPreparationUiState()
}

enum class BackgroundUploadState {
    Idle,
    Running,
    Ready,
    Failed
}

data class BackgroundUploadNotice(
    val id: Long,
    val message: String,
    val actionLabel: String? = null
)
