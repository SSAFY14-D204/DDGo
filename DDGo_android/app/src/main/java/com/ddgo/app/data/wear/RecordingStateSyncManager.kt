package com.ddgo.app.data.wear

import android.content.Context
import android.util.Log
import com.ddgo.shared.contract.DlKeys
import com.ddgo.shared.contract.DlPaths
import com.ddgo.shared.model.RecordingState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class RecordingStateSyncManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val capabilityClient = Wearable.getCapabilityClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    fun sync(recordingState: RecordingState) {
        Log.d(
            TAG,
            "PHONE_SEND recordingState sessionId=${recordingState.sessionId} " +
                "isRecording=${recordingState.isRecording} updatedAt=${recordingState.updatedAt}"
        )
        syncDataItem(recordingState)
        syncMessage(recordingState)
    }

    fun prepareMeasurement(recordingState: RecordingState) {
        Log.d(
            TAG,
            "PHONE_PREPARE sessionId=${recordingState.sessionId} " +
                "isRecording=${recordingState.isRecording} updatedAt=${recordingState.updatedAt}"
        )
        syncCustomMessage(DlPaths.MSG_MEASUREMENT_PREPARE_START, recordingState)
    }

    fun stopPreparedMeasurement(recordingState: RecordingState) {
        Log.d(
            TAG,
            "PHONE_PREPARE_STOP sessionId=${recordingState.sessionId} " +
                "isRecording=${recordingState.isRecording} updatedAt=${recordingState.updatedAt}"
        )
        syncCustomMessage(DlPaths.MSG_MEASUREMENT_PREPARE_STOP, recordingState)
    }

    fun launchWatchApp() {
        capabilityClient.getCapability(
            DlPaths.CAPABILITY_WATCH,
            CapabilityClient.FILTER_REACHABLE
        ).addOnSuccessListener { capabilityInfo ->
            val nodes = capabilityInfo.nodes
            if (nodes.isNotEmpty()) {
                nodes.forEach(::sendOpenAppMessage)
            } else {
                fallbackToConnectedNodes(::sendOpenAppMessage)
            }
        }.addOnFailureListener { throwable ->
            Log.w(TAG, "Failed to resolve watch capability nodes for app launch. Falling back to connected nodes.", throwable)
            fallbackToConnectedNodes(::sendOpenAppMessage)
        }
    }

    private fun syncDataItem(recordingState: RecordingState) {
        val request = PutDataMapRequest.create(DlPaths.DATA_RECORDING_STATE).apply {
            dataMap.putString(DlKeys.SESSION_ID, recordingState.sessionId)
            dataMap.putBoolean(DlKeys.IS_RECORDING, recordingState.isRecording)
            dataMap.putLong(DlKeys.UPDATED_AT, recordingState.updatedAt)
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnSuccessListener { item ->
                Log.d(
                    TAG,
                    "PHONE_SEND_DATA_OK sessionId=${recordingState.sessionId} uri=${item.uri}"
                )
            }
            .addOnFailureListener { throwable ->
                Log.w(
                    TAG,
                    "PHONE_SEND_DATA_FAIL sessionId=${recordingState.sessionId}",
                    throwable
                )
            }
    }

    private fun syncMessage(recordingState: RecordingState) {
        val path = if (recordingState.isRecording) {
            DlPaths.MSG_RECORDING_START
        } else {
            DlPaths.MSG_RECORDING_STOP
        }
        syncCustomMessage(path, recordingState)
    }

    private fun syncCustomMessage(
        path: String,
        recordingState: RecordingState
    ) {
        capabilityClient.getCapability(
            DlPaths.CAPABILITY_WATCH,
            CapabilityClient.FILTER_REACHABLE
        ).addOnSuccessListener { capabilityInfo ->
            val nodes = capabilityInfo.nodes
            if (nodes.isNotEmpty()) {
                nodes.forEach { node ->
                    sendMessage(node, path, recordingState)
                }
            } else {
                fallbackToConnectedNodes(path, recordingState)
            }
        }.addOnFailureListener { throwable ->
            Log.w(TAG, "Failed to resolve watch capability nodes. Falling back to connected nodes.", throwable)
            fallbackToConnectedNodes(path, recordingState)
        }
    }

    private fun fallbackToConnectedNodes(
        path: String,
        recordingState: RecordingState
    ) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    sendMessage(node, path, recordingState)
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to resolve connected wearable nodes.", throwable)
            }
    }

    private fun fallbackToConnectedNodes(onNodeResolved: (Node) -> Unit) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach(onNodeResolved)
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to resolve connected wearable nodes.", throwable)
            }
    }

    private fun sendMessage(
        node: Node,
        path: String,
        recordingState: RecordingState
    ) {
        val payload = json.encodeToString(recordingState).encodeToByteArray()

        messageClient.sendMessage(node.id, path, payload)
            .addOnSuccessListener {
                Log.d(
                    TAG,
                    "PHONE_SEND_MSG_OK sessionId=${recordingState.sessionId} node=${node.id} path=$path"
                )
            }
            .addOnFailureListener { throwable ->
                Log.w(
                    TAG,
                    "PHONE_SEND_MSG_FAIL sessionId=${recordingState.sessionId} node=${node.id} path=$path",
                    throwable
                )
            }
    }

    private fun sendOpenAppMessage(node: Node) {
        messageClient.sendMessage(node.id, DlPaths.MSG_OPEN_APP, ByteArray(0))
            .addOnSuccessListener {
                Log.d(TAG, "Requested watch app open on node=${node.id}")
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to request watch app open on node=${node.id}", throwable)
            }
    }

    private companion object {
        const val TAG = "RecordingStateSync"
    }
}
