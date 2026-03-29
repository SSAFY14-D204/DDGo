package com.ddgo.wear.data

import android.content.Context
import com.ddgo.shared.model.RecordingState
import com.ddgo.shared.model.RecordingStateConflictResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecordingStateStore private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val _snapshot = MutableStateFlow(loadSnapshot())

    val snapshot: StateFlow<WearRecordingSyncSnapshot> = _snapshot.asStateFlow()

    fun apply(
        incoming: RecordingState,
        source: RecordingStateEventSource
    ): Boolean = synchronized(lock) {
        val currentSnapshot = _snapshot.value
        val currentState = currentSnapshot.recordingState
        if (!RecordingStateConflictResolver.shouldApply(currentState, incoming)) {
            val ignoredSnapshot = currentSnapshot.copy(
                ignoredEventCount = currentSnapshot.ignoredEventCount + 1
            )
            _snapshot.value = ignoredSnapshot
            persistSnapshot(ignoredSnapshot)
            return false
        }

        val updatedSnapshot = currentSnapshot.copy(
            recordingState = incoming,
            lastEventSource = source,
            lastAppliedAt = System.currentTimeMillis()
        )
        _snapshot.value = updatedSnapshot
        persistSnapshot(updatedSnapshot)
        true
    }

    private fun loadSnapshot(): WearRecordingSyncSnapshot {
        val recordingState = prefs.getString(KEY_RECORDING_STATE, null)
            ?.let { encoded ->
                runCatching { json.decodeFromString<RecordingState>(encoded) }.getOrNull()
            }
        val source = prefs.getString(KEY_LAST_SOURCE, null)
            ?.let { name -> enumValues<RecordingStateEventSource>().firstOrNull { it.name == name } }
            ?: RecordingStateEventSource.NONE
        val ignoredEventCount = prefs.getInt(KEY_IGNORED_COUNT, 0)
        val lastAppliedAt = prefs.getLong(KEY_LAST_APPLIED_AT, NO_TIMESTAMP)
            .takeIf { it != NO_TIMESTAMP }

        return WearRecordingSyncSnapshot(
            recordingState = recordingState,
            lastEventSource = source,
            ignoredEventCount = ignoredEventCount,
            lastAppliedAt = lastAppliedAt
        )
    }

    private fun persistSnapshot(snapshot: WearRecordingSyncSnapshot) {
        prefs.edit()
            .putString(
                KEY_RECORDING_STATE,
                snapshot.recordingState?.let(json::encodeToString)
            )
            .putString(KEY_LAST_SOURCE, snapshot.lastEventSource.name)
            .putInt(KEY_IGNORED_COUNT, snapshot.ignoredEventCount)
            .putLong(KEY_LAST_APPLIED_AT, snapshot.lastAppliedAt ?: NO_TIMESTAMP)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "recording_state_store"
        private const val KEY_RECORDING_STATE = "recording_state"
        private const val KEY_LAST_SOURCE = "last_source"
        private const val KEY_IGNORED_COUNT = "ignored_count"
        private const val KEY_LAST_APPLIED_AT = "last_applied_at"
        private const val NO_TIMESTAMP = -1L

        @Volatile
        private var instance: RecordingStateStore? = null

        fun get(context: Context): RecordingStateStore {
            return instance ?: synchronized(this) {
                instance ?: RecordingStateStore(context).also { instance = it }
            }
        }
    }
}
