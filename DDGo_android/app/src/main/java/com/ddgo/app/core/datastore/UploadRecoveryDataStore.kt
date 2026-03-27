package com.ddgo.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import com.ddgo.app.feature.climbing.upload.UploadRecoveryAnalysisPhase
import com.ddgo.app.feature.climbing.upload.UploadRecoveryBoundingBox
import com.ddgo.app.feature.climbing.upload.UploadRecoveryCreateStep
import com.ddgo.app.feature.climbing.upload.UploadRecoveryHoldSelectionPhase
import com.ddgo.app.feature.climbing.upload.UploadRecoveryNearbyPlaceDto
import com.ddgo.app.feature.climbing.upload.UploadRecoveryPublishedAttemptResultSessionDto
import com.ddgo.app.feature.climbing.upload.UploadRecoveryResolvedGymDto
import com.ddgo.app.feature.climbing.upload.UploadRecoveryRoute
import com.ddgo.app.feature.climbing.upload.UploadRecoverySnapshotPayload
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class UploadRecoveryEntryIntent {
    UPLOAD,
    REALTIME
}

data class UploadRecoverySnapshot(
    val userId: Long,
    val challengeId: Long = 0L,
    val gymId: Long = 0L,
    val gymGradeId: Long = 0L,
    val gymName: String = "",
    val problemColor: String = "",
    val gradeLabel: String? = null,
    val startedAt: String = "",
    val createdAt: String = "",
    val entryIntent: UploadRecoveryEntryIntent,
    val recoveredAt: Long,
    val lastKnownDoneAttemptCount: Int = 0,
    val lastRoute: UploadRecoveryRoute = UploadRecoveryRoute.ATTEMPT_UPLOAD,
    val managedPrimaryPlaybackUri: String? = null,
    val managedAdditionalPlaybackUris: List<String> = emptyList(),
    val managedAttemptOnlyPlaybackUris: List<String> = emptyList(),
    val selectedHoldColorKey: String? = null,
    val searchQuery: String? = null,
    val createStep: UploadRecoveryCreateStep? = null,
    val realtimeSetupStep: String? = null,
    val analysisLoadingPhase: UploadRecoveryAnalysisPhase? = null,
    val holdSelectionPhase: UploadRecoveryHoldSelectionPhase? = null,
    val selectedStartHoldBoundingBox: UploadRecoveryBoundingBox? = null,
    val selectedEndHoldBoundingBox: UploadRecoveryBoundingBox? = null,
    val currentAttemptIndex: Int = 0,
    val isAttemptOnlyMode: Boolean = false,
    val selectedNearbyPlace: UploadRecoveryNearbyPlaceDto? = null,
    val resolvedGym: UploadRecoveryResolvedGymDto? = null,
    val lastSearchLatitude: Double? = null,
    val lastSearchLongitude: Double? = null,
    val publishedAttemptResultSession: UploadRecoveryPublishedAttemptResultSessionDto? = null
) {
    val challengeIdOrNull: Long?
        get() = challengeId.takeIf { it > 0L }

    val gymIdOrNull: Long?
        get() = gymId.takeIf { it > 0L }

    val gymGradeIdOrNull: Long?
        get() = gymGradeId.takeIf { it > 0L }
}

class UploadRecoveryDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_UPLOAD_RECOVERY_SNAPSHOT =
            stringPreferencesKey("upload_recovery_snapshot_json")

        val recoveryJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    val uploadRecoverySnapshot: Flow<UploadRecoverySnapshot?> =
        context.ddgoPreferencesDataStore.data.map { preferences ->
            val encoded = preferences[KEY_UPLOAD_RECOVERY_SNAPSHOT] ?: return@map null
            runCatching {
                recoveryJson.decodeFromString<UploadRecoverySnapshotPayload>(encoded)
                    .toSnapshot()
            }.getOrNull()
        }

    suspend fun getUploadRecoverySnapshot(): UploadRecoverySnapshot? {
        return uploadRecoverySnapshot.first()
    }

    suspend fun saveUploadRecoverySnapshot(snapshot: UploadRecoverySnapshot) {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences[KEY_UPLOAD_RECOVERY_SNAPSHOT] =
                recoveryJson.encodeToString(snapshot.toPayload())
        }
    }

    suspend fun clearUploadRecoverySnapshot() {
        context.ddgoPreferencesDataStore.edit { preferences ->
            preferences.remove(KEY_UPLOAD_RECOVERY_SNAPSHOT)
        }
    }

    suspend fun clearAll() {
        clearUploadRecoverySnapshot()
    }
}

private fun UploadRecoverySnapshotPayload.toSnapshot(): UploadRecoverySnapshot {
    val parsedEntryIntent = runCatching {
        UploadRecoveryEntryIntent.valueOf(entryIntent)
    }.getOrDefault(UploadRecoveryEntryIntent.UPLOAD)

    return UploadRecoverySnapshot(
        userId = userId,
        challengeId = challengeId ?: 0L,
        gymId = gymId?.toLong() ?: 0L,
        gymGradeId = gymGradeId ?: 0L,
        gymName = gymName,
        problemColor = problemColor,
        gradeLabel = gradeLabel,
        startedAt = startedAt.orEmpty(),
        createdAt = createdAt.orEmpty(),
        entryIntent = parsedEntryIntent,
        recoveredAt = recoveredAt,
        lastKnownDoneAttemptCount = lastKnownDoneAttemptCount,
        lastRoute = lastRoute,
        managedPrimaryPlaybackUri = managedPrimaryPlaybackUri,
        managedAdditionalPlaybackUris = managedAdditionalPlaybackUris,
        managedAttemptOnlyPlaybackUris = managedAttemptOnlyPlaybackUris,
        selectedHoldColorKey = selectedHoldColorKey,
        searchQuery = searchQuery,
        createStep = createStep,
        realtimeSetupStep = realtimeSetupStep,
        analysisLoadingPhase = analysisLoadingPhase,
        holdSelectionPhase = holdSelectionPhase,
        selectedStartHoldBoundingBox = selectedStartHoldBoundingBox,
        selectedEndHoldBoundingBox = selectedEndHoldBoundingBox,
        currentAttemptIndex = currentAttemptIndex,
        isAttemptOnlyMode = isAttemptOnlyMode,
        selectedNearbyPlace = selectedNearbyPlace,
        resolvedGym = resolvedGym,
        lastSearchLatitude = lastSearchLatitude,
        lastSearchLongitude = lastSearchLongitude,
        publishedAttemptResultSession = publishedAttemptResultSession
    )
}

private fun UploadRecoverySnapshot.toPayload(): UploadRecoverySnapshotPayload =
    UploadRecoverySnapshotPayload(
        userId = userId,
        challengeId = challengeId.takeIf { it > 0L },
        entryIntent = entryIntent.name,
        lastRoute = lastRoute,
        lastKnownDoneAttemptCount = lastKnownDoneAttemptCount,
        recoveredAt = recoveredAt,
        managedPrimaryPlaybackUri = managedPrimaryPlaybackUri,
        managedAdditionalPlaybackUris = managedAdditionalPlaybackUris,
        managedAttemptOnlyPlaybackUris = managedAttemptOnlyPlaybackUris,
        gymId = gymId.takeIf { it > 0L }?.toInt(),
        gymGradeId = gymGradeId.takeIf { it > 0L },
        gymName = gymName,
        problemColor = problemColor,
        gradeLabel = gradeLabel,
        selectedHoldColorKey = selectedHoldColorKey,
        searchQuery = searchQuery,
        createStep = createStep,
        realtimeSetupStep = realtimeSetupStep,
        analysisLoadingPhase = analysisLoadingPhase,
        holdSelectionPhase = holdSelectionPhase,
        selectedStartHoldBoundingBox = selectedStartHoldBoundingBox,
        selectedEndHoldBoundingBox = selectedEndHoldBoundingBox,
        currentAttemptIndex = currentAttemptIndex,
        isAttemptOnlyMode = isAttemptOnlyMode,
        startedAt = startedAt.ifBlank { null },
        createdAt = createdAt.ifBlank { null },
        selectedNearbyPlace = selectedNearbyPlace,
        resolvedGym = resolvedGym,
        lastSearchLatitude = lastSearchLatitude,
        lastSearchLongitude = lastSearchLongitude,
        publishedAttemptResultSession = publishedAttemptResultSession
    )
