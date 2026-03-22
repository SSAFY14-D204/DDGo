package com.ddgo.app.data.wear

import android.content.Context
import android.util.Log
import com.ddgo.shared.contract.DlKeys
import com.ddgo.shared.contract.DlPaths
import com.ddgo.shared.model.HeartRateSnapshot
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchSessionStatus
import com.ddgo.shared.model.WatchState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.json.Json

object WatchRuntimeSyncProcessor {
    private val json = Json { ignoreUnknownKeys = true }

    fun refreshLatestRuntime(context: Context) {
        val appContext = context.applicationContext
        Wearable.getDataClient(appContext)
            .dataItems
            .addOnSuccessListener { buffer ->
                try {
                    for (index in 0 until buffer.count) {
                        handleDataItem(appContext, buffer[index])
                    }
                } finally {
                    buffer.release()
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to refresh latest watch runtime snapshots.", throwable)
            }
    }

    fun refreshConnectionState(context: Context) {
        val appContext = context.applicationContext
        val store = WatchRuntimeSyncStore.get(appContext)
        Wearable.getCapabilityClient(appContext)
            .getCapability(DlPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                store.markWatchConnected(isConnected = capabilityInfo.nodes.isNotEmpty())
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to resolve watch capability. Falling back to connected nodes.", throwable)
                Wearable.getNodeClient(appContext)
                    .connectedNodes
                    .addOnSuccessListener { nodes ->
                        store.markWatchConnected(isConnected = nodes.isNotEmpty())
                    }
                    .addOnFailureListener { nodeThrowable ->
                        Log.w(TAG, "Failed to resolve connected watch nodes.", nodeThrowable)
                    }
            }
    }

    fun handleDataChanged(
        context: Context,
        dataEvents: DataEventBuffer
    ) {
        val appContext = context.applicationContext
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) {
                continue
            }
            handleDataItem(appContext, event.dataItem)
        }
    }

    fun handleMessage(
        context: Context,
        path: String,
        payload: ByteArray
    ) {
        if (path != DlPaths.MSG_ALERT) {
            return
        }

        val status = runCatching {
            json.decodeFromString<WatchSessionStatus>(payload.decodeToString())
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to decode watch alert message.", throwable)
            return
        }

        WatchRuntimeSyncStore.get(context.applicationContext).applyWatchStatus(
            incoming = status,
            markAlertReceived = true
        )
    }

    fun createDataChangedListener(context: Context): DataClient.OnDataChangedListener {
        val appContext = context.applicationContext
        return DataClient.OnDataChangedListener { dataEvents ->
            handleDataChanged(appContext, dataEvents)
        }
    }

    fun createMessageReceivedListener(context: Context): MessageClient.OnMessageReceivedListener {
        val appContext = context.applicationContext
        return MessageClient.OnMessageReceivedListener { messageEvent ->
            handleMessage(appContext, messageEvent.path, messageEvent.data)
        }
    }

    private fun handleDataItem(
        context: Context,
        item: com.google.android.gms.wearable.DataItem
    ) {
        when (item.uri.path) {
            DlPaths.DATA_LIVE_HR -> {
                parseHeartRateSnapshot(item)?.let { snapshot ->
                    WatchRuntimeSyncStore.get(context).applyHeartRate(snapshot)
                }
            }

            DlPaths.DATA_WATCH_SESSION_STATUS -> {
                parseWatchSessionStatus(item)?.let { status ->
                    WatchRuntimeSyncStore.get(context).applyWatchStatus(status)
                }
            }
        }
    }

    private fun parseHeartRateSnapshot(
        item: com.google.android.gms.wearable.DataItem
    ): HeartRateSnapshot? {
        return runCatching {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            HeartRateSnapshot(
                heartRate = dataMap.getInt(DlKeys.HEART_RATE).takeIf { it >= 0 },
                alerting = dataMap.getBoolean(DlKeys.ALERTING),
                sensorAvailable = dataMap.getBoolean(DlKeys.SENSOR_AVAILABLE),
                measurementStatus = dataMap.getString(DlKeys.MEASUREMENT_STATUS)
                    ?.let { enumValueOf<MeasurementStatus>(it) }
                    ?: MeasurementStatus.UNAVAILABLE,
                lastMeasuredAt = dataMap.getLong(DlKeys.LAST_MEASURED_AT).takeIf { it > 0L },
                updatedAt = dataMap.getLong(DlKeys.UPDATED_AT)
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to parse heart rate snapshot: ${item.uri}", throwable)
            null
        }
    }

    private fun parseWatchSessionStatus(
        item: com.google.android.gms.wearable.DataItem
    ): WatchSessionStatus? {
        return runCatching {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            WatchSessionStatus(
                sessionId = dataMap.getString(DlKeys.SESSION_ID).orEmpty(),
                watchState = dataMap.getString(DlKeys.WATCH_STATE)
                    ?.let { enumValueOf<WatchState>(it) }
                    ?: WatchState.IDLE,
                serviceActive = dataMap.getBoolean(DlKeys.SERVICE_ACTIVE),
                alerting = dataMap.getBoolean(DlKeys.ALERTING),
                sensorAvailable = dataMap.getBoolean(DlKeys.SENSOR_AVAILABLE),
                updatedAt = dataMap.getLong(DlKeys.UPDATED_AT)
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to parse watch session status: ${item.uri}", throwable)
            null
        }?.takeIf { it.sessionId.isNotBlank() }
    }

    private const val TAG = "PhoneWatchRuntime"
}
