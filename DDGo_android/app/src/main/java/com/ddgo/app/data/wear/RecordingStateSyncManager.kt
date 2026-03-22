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
        syncDataItem(recordingState)
        syncMessage(recordingState)
    }

    private fun syncDataItem(recordingState: RecordingState) {
        val request = PutDataMapRequest.create(DlPaths.DATA_RECORDING_STATE).apply {
            dataMap.putString(DlKeys.SESSION_ID, recordingState.sessionId)
            dataMap.putBoolean(DlKeys.IS_RECORDING, recordingState.isRecording)
            dataMap.putLong(DlKeys.UPDATED_AT, recordingState.updatedAt)
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnSuccessListener { item ->
                Log.d(TAG, "Recording state synced to Data Layer: ${item.uri}")
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to sync recording state data item.", throwable)
            }
    }

    private fun syncMessage(recordingState: RecordingState) {
        capabilityClient.getCapability(
            DlPaths.CAPABILITY_WATCH,
            CapabilityClient.FILTER_REACHABLE
        ).addOnSuccessListener { capabilityInfo ->
            val nodes = capabilityInfo.nodes
            if (nodes.isNotEmpty()) {
                nodes.forEach { node ->
                    sendMessage(node, recordingState)
                }
            } else {
                fallbackToConnectedNodes(recordingState)
            }
        }.addOnFailureListener { throwable ->
            Log.w(TAG, "Failed to resolve watch capability nodes. Falling back to connected nodes.", throwable)
            fallbackToConnectedNodes(recordingState)
        }
    }

    private fun fallbackToConnectedNodes(recordingState: RecordingState) {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    sendMessage(node, recordingState)
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to resolve connected wearable nodes.", throwable)
            }
    }

    private fun sendMessage(
        node: Node,
        recordingState: RecordingState
    ) {
        val path = if (recordingState.isRecording) {
            DlPaths.MSG_RECORDING_START
        } else {
            DlPaths.MSG_RECORDING_STOP
        }
        val payload = json.encodeToString(recordingState).encodeToByteArray()

        messageClient.sendMessage(node.id, path, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Recording state message sent to node=${node.id}, path=$path")
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to send recording state message to node=${node.id}", throwable)
            }
    }

    private companion object {
        const val TAG = "RecordingStateSync"
    }
}
