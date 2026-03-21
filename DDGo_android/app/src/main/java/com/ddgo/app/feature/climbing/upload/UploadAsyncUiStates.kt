package com.ddgo.app.feature.climbing.upload

import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.model.UploadedAttemptVideo

sealed class UploadUiState {
    object Idle : UploadUiState()
    object Loading : UploadUiState()
    object Success : UploadUiState()
    data class Error(val message: String) : UploadUiState()
}

sealed class GymSearchUiState {
    object Idle : GymSearchUiState()
    object Loading : GymSearchUiState()
    data class Success(val places: List<NearbyPlace>) : GymSearchUiState()
    data class Error(val message: String) : GymSearchUiState()
}

sealed class GymResolveUiState {
    object Idle : GymResolveUiState()
    object Loading : GymResolveUiState()
    data class Success(val resolvedGym: ResolvedGym) : GymResolveUiState()
    data class Error(val message: String) : GymResolveUiState()
}

sealed class ChallengeCreationUiState {
    object Idle : ChallengeCreationUiState()
    object Loading : ChallengeCreationUiState()
    data class Success(val challenge: ChallengeSession) : ChallengeCreationUiState()
    data class Error(val message: String) : ChallengeCreationUiState()
}

sealed class UploadSubmissionUiState {
    object Idle : UploadSubmissionUiState()
    data class Loading(val message: String) : UploadSubmissionUiState()
    data class Success(val uploadedAttempts: List<UploadedAttemptVideo>) : UploadSubmissionUiState()
    data class Error(val message: String) : UploadSubmissionUiState()
}
