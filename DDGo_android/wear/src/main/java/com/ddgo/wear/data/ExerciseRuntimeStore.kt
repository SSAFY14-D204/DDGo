package com.ddgo.wear.data

import android.content.Context
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.RecordingState
import com.ddgo.shared.model.WatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExerciseRuntimeStore private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val _snapshot = MutableStateFlow(loadSnapshot())

    val snapshot: StateFlow<ExerciseRuntimeSnapshot> = _snapshot.asStateFlow()

    fun update(transform: (ExerciseRuntimeSnapshot) -> ExerciseRuntimeSnapshot): ExerciseRuntimeSnapshot =
        synchronized(lock) {
            val updated = transform(_snapshot.value).normalize()
            _snapshot.value = updated
            persist(updated)
            updated
        }

    fun markRecovering(
        recordingState: RecordingState,
        reason: String? = null
    ): ExerciseRuntimeSnapshot = update {
        it.copy(
            sessionId = recordingState.sessionId,
            watchState = WatchState.SESSION_RECOVERING,
            serviceActive = true,
            measurementStatus = MeasurementStatus.RECOVERING,
            updatedAt = System.currentTimeMillis(),
            lastReason = reason
        )
    }

    fun markRecording(
        recordingState: RecordingState,
        latestHeartRate: Int? = null,
        lastMeasuredAt: Long? = null,
        sensorAvailable: Boolean,
        measurementStatus: MeasurementStatus,
        reason: String? = null
    ): ExerciseRuntimeSnapshot = update {
        it.copy(
            sessionId = recordingState.sessionId,
            watchState = WatchState.RECORDING,
            serviceActive = true,
            sensorAvailable = sensorAvailable,
            measurementStatus = measurementStatus,
            latestHeartRate = latestHeartRate ?: it.latestHeartRate,
            lastMeasuredAt = lastMeasuredAt ?: it.lastMeasuredAt,
            updatedAt = System.currentTimeMillis(),
            lastReason = reason
        )
    }

    fun markPermissionBlocked(
        recordingState: RecordingState?,
        reason: String
    ): ExerciseRuntimeSnapshot = update {
        it.copy(
            sessionId = recordingState?.sessionId ?: it.sessionId,
            watchState = WatchState.PERMISSION_BLOCKED,
            serviceActive = true,
            sensorAvailable = false,
            measurementStatus = MeasurementStatus.PERMISSION_BLOCKED,
            updatedAt = System.currentTimeMillis(),
            lastReason = reason
        )
    }

    fun markUnavailable(
        recordingState: RecordingState?,
        reason: String
    ): ExerciseRuntimeSnapshot = update {
        it.copy(
            sessionId = recordingState?.sessionId ?: it.sessionId,
            watchState = WatchState.SENSOR_UNAVAILABLE,
            serviceActive = true,
            sensorAvailable = false,
            measurementStatus = MeasurementStatus.UNAVAILABLE,
            updatedAt = System.currentTimeMillis(),
            lastReason = reason
        )
    }

    fun markIdle(reason: String? = null): ExerciseRuntimeSnapshot = update {
        ExerciseRuntimeSnapshot(
            watchState = WatchState.IDLE,
            measurementStatus = MeasurementStatus.UNAVAILABLE,
            updatedAt = System.currentTimeMillis(),
            lastReason = reason
        )
    }

    private fun loadSnapshot(): ExerciseRuntimeSnapshot {
        return prefs.getString(KEY_SNAPSHOT, null)
            ?.let { encoded ->
                runCatching { json.decodeFromString<ExerciseRuntimeSnapshot>(encoded) }.getOrNull()
            }
            ?.normalize()
            ?: ExerciseRuntimeSnapshot()
    }

    private fun persist(snapshot: ExerciseRuntimeSnapshot) {
        prefs.edit()
            .putString(KEY_SNAPSHOT, json.encodeToString(snapshot))
            .apply()
    }

    private fun ExerciseRuntimeSnapshot.normalize(): ExerciseRuntimeSnapshot {
        if (!serviceActive && watchState != WatchState.IDLE) {
            return copy(watchState = WatchState.IDLE)
        }
        return this
    }

    companion object {
        private const val PREF_NAME = "exercise_runtime_store"
        private const val KEY_SNAPSHOT = "snapshot"

        @Volatile
        private var instance: ExerciseRuntimeStore? = null

        fun get(context: Context): ExerciseRuntimeStore {
            return instance ?: synchronized(this) {
                instance ?: ExerciseRuntimeStore(context).also { instance = it }
            }
        }
    }
}
