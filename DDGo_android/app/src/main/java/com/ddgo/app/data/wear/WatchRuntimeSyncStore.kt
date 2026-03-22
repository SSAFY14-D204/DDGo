package com.ddgo.app.data.wear

import android.content.Context
import com.ddgo.shared.model.HeartRateSnapshot
import com.ddgo.shared.model.WatchSessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WatchRuntimeSyncStore private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val _snapshot = MutableStateFlow(loadSnapshot())

    val snapshot: StateFlow<WatchRuntimeSyncSnapshot> = _snapshot.asStateFlow()

    fun applyHeartRate(
        incoming: HeartRateSnapshot,
        connectedAt: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val result = WatchRuntimeSyncReducer.applyHeartRate(_snapshot.value, incoming, connectedAt)
        if (!result.applied) {
            return false
        }
        _snapshot.value = result.snapshot
        persistSnapshot(result.snapshot)
        true
    }

    fun applyWatchStatus(
        incoming: WatchSessionStatus,
        markAlertReceived: Boolean = false,
        connectedAt: Long = System.currentTimeMillis()
    ): Boolean = synchronized(lock) {
        val result = WatchRuntimeSyncReducer.applyWatchStatus(
            current = _snapshot.value,
            incoming = incoming,
            connectedAt = connectedAt,
            markAlertReceived = markAlertReceived
        )
        if (!result.applied) {
            return false
        }
        _snapshot.value = result.snapshot
        persistSnapshot(result.snapshot)
        true
    }

    fun markWatchConnected(
        isConnected: Boolean,
        checkedAt: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val updatedSnapshot = WatchRuntimeSyncReducer.applyConnection(
            current = _snapshot.value,
            isConnected = isConnected,
            checkedAt = checkedAt
        )
        _snapshot.value = updatedSnapshot
        persistSnapshot(updatedSnapshot)
    }

    private fun loadSnapshot(): WatchRuntimeSyncSnapshot {
        val heartRateSnapshot = prefs.getString(KEY_HEART_RATE_SNAPSHOT, null)
            ?.let { encoded ->
                runCatching { json.decodeFromString<HeartRateSnapshot>(encoded) }.getOrNull()
            }
        val watchSessionStatus = prefs.getString(KEY_WATCH_SESSION_STATUS, null)
            ?.let { encoded ->
                runCatching { json.decodeFromString<WatchSessionStatus>(encoded) }.getOrNull()
            }
        val lastConnectionCheckedAt = prefs.getLong(KEY_LAST_CONNECTION_CHECKED_AT, NO_TIMESTAMP)
            .takeIf { it != NO_TIMESTAMP }
        val lastAlertReceivedAt = prefs.getLong(KEY_LAST_ALERT_RECEIVED_AT, NO_TIMESTAMP)
            .takeIf { it != NO_TIMESTAMP }

        return WatchRuntimeSyncSnapshot(
            heartRateSnapshot = heartRateSnapshot,
            watchSessionStatus = watchSessionStatus,
            isWatchConnected = prefs.getBoolean(KEY_IS_WATCH_CONNECTED, false),
            lastConnectionCheckedAt = lastConnectionCheckedAt,
            lastAlertReceivedAt = lastAlertReceivedAt
        )
    }

    private fun persistSnapshot(snapshot: WatchRuntimeSyncSnapshot) {
        prefs.edit()
            .putString(
                KEY_HEART_RATE_SNAPSHOT,
                snapshot.heartRateSnapshot?.let(json::encodeToString)
            )
            .putString(
                KEY_WATCH_SESSION_STATUS,
                snapshot.watchSessionStatus?.let(json::encodeToString)
            )
            .putBoolean(KEY_IS_WATCH_CONNECTED, snapshot.isWatchConnected)
            .putLong(KEY_LAST_CONNECTION_CHECKED_AT, snapshot.lastConnectionCheckedAt ?: NO_TIMESTAMP)
            .putLong(KEY_LAST_ALERT_RECEIVED_AT, snapshot.lastAlertReceivedAt ?: NO_TIMESTAMP)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "watch_runtime_sync_store"
        private const val KEY_HEART_RATE_SNAPSHOT = "heart_rate_snapshot"
        private const val KEY_WATCH_SESSION_STATUS = "watch_session_status"
        private const val KEY_IS_WATCH_CONNECTED = "is_watch_connected"
        private const val KEY_LAST_CONNECTION_CHECKED_AT = "last_connection_checked_at"
        private const val KEY_LAST_ALERT_RECEIVED_AT = "last_alert_received_at"
        private const val NO_TIMESTAMP = -1L

        @Volatile
        private var instance: WatchRuntimeSyncStore? = null

        fun get(context: Context): WatchRuntimeSyncStore {
            return instance ?: synchronized(this) {
                instance ?: WatchRuntimeSyncStore(context).also { instance = it }
            }
        }
    }
}
