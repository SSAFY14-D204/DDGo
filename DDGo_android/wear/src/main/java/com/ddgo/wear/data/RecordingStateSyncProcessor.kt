package com.ddgo.wear.data

import android.content.Context
import android.util.Log
import com.ddgo.shared.contract.DlKeys
import com.ddgo.shared.contract.DlPaths
import com.ddgo.shared.model.RecordingState
import com.ddgo.wear.runtime.SessionRecoveryCoordinator
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.json.Json

object RecordingStateSyncProcessor {
    private val json = Json { ignoreUnknownKeys = true }

    fun refreshLatestRecordingState(context: Context) {
        val appContext = context.applicationContext
        Wearable.getDataClient(appContext)
            .dataItems
            .addOnSuccessListener { buffer ->
                try {
                    for (index in 0 until buffer.count) {
                        val item = buffer[index]
                        if (item.uri.path == DlPaths.DATA_RECORDING_STATE) {
                            parseDataItem(item)?.let { recordingState ->
                                val applied = RecordingStateStore.get(appContext).apply(
                                    incoming = recordingState,
                                    source = RecordingStateEventSource.DATA_ITEM
                                )
                                if (applied) {
                                    SessionRecoveryCoordinator.syncDesiredState(appContext)
                                }
                            }
                        }
                    }
                } finally {
                    buffer.release()
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to refresh latest recording state.", throwable)
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
            val item = event.dataItem
            if (item.uri.path != DlPaths.DATA_RECORDING_STATE) {
                continue
            }
            val recordingState = parseDataItem(item) ?: continue
            val applied = RecordingStateStore.get(appContext).apply(
                incoming = recordingState,
                source = RecordingStateEventSource.DATA_ITEM
            )
            if (applied) {
                SessionRecoveryCoordinator.syncDesiredState(appContext)
            }
        }
    }

    fun handleMessage(
        context: Context,
        path: String,
        payload: ByteArray
    ) {
        if (path != DlPaths.MSG_RECORDING_START && path != DlPaths.MSG_RECORDING_STOP) {
            return
        }

        val recordingState = runCatching {
            json.decodeFromString<RecordingState>(payload.decodeToString())
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to decode recording state message for path=$path", throwable)
            return
        }

        val appContext = context.applicationContext
        val applied = RecordingStateStore.get(appContext).apply(
            incoming = recordingState,
            source = RecordingStateEventSource.MESSAGE
        )
        if (applied) {
            SessionRecoveryCoordinator.syncDesiredState(appContext)
        }
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

    private fun parseDataItem(item: com.google.android.gms.wearable.DataItem): RecordingState? {
        return runCatching {
            val dataMap = DataMapItem.fromDataItem(item).dataMap
            RecordingState(
                sessionId = dataMap.getString(DlKeys.SESSION_ID).orEmpty(),
                isRecording = dataMap.getBoolean(DlKeys.IS_RECORDING),
                updatedAt = dataMap.getLong(DlKeys.UPDATED_AT)
            )
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to parse recording state data item: ${item.uri}", throwable)
            null
        }?.takeIf { it.sessionId.isNotBlank() }
    }

    private const val TAG = "WearRecordingSync"
}
