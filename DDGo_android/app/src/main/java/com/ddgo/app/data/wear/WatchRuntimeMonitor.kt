package com.ddgo.app.data.wear

import android.content.Context
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class WatchRuntimeMonitor @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val store = WatchRuntimeSyncStore.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private val dataChangedListener: DataClient.OnDataChangedListener by lazy {
        WatchRuntimeSyncProcessor.createDataChangedListener(appContext)
    }
    private val messageReceivedListener: MessageClient.OnMessageReceivedListener by lazy {
        WatchRuntimeSyncProcessor.createMessageReceivedListener(appContext)
    }

    private var observerCount: Int = 0
    private var connectionPollingJob: Job? = null

    val snapshot: StateFlow<WatchRuntimeSyncSnapshot> = store.snapshot

    fun start() {
        synchronized(lock) {
            observerCount += 1
            if (observerCount > 1) {
                WatchRuntimeSyncProcessor.refreshLatestRuntime(appContext)
                WatchRuntimeSyncProcessor.refreshConnectionState(appContext)
                return
            }
            dataClient.addListener(dataChangedListener)
            messageClient.addListener(messageReceivedListener)
            startConnectionPolling()
        }
        WatchRuntimeSyncProcessor.refreshLatestRuntime(appContext)
        WatchRuntimeSyncProcessor.refreshConnectionState(appContext)
    }

    fun stop() {
        synchronized(lock) {
            observerCount = (observerCount - 1).coerceAtLeast(0)
            if (observerCount > 0) {
                return
            }
            dataClient.removeListener(dataChangedListener)
            messageClient.removeListener(messageReceivedListener)
            connectionPollingJob?.cancel()
            connectionPollingJob = null
        }
    }

    private fun startConnectionPolling() {
        connectionPollingJob?.cancel()
        connectionPollingJob = scope.launch {
            while (isActive) {
                WatchRuntimeSyncProcessor.refreshConnectionState(appContext)
                delay(CONNECTION_POLL_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val CONNECTION_POLL_INTERVAL_MS = 5_000L
    }
}
