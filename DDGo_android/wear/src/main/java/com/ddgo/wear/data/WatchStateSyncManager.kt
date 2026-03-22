package com.ddgo.wear.data

import android.content.Context
import android.util.Log
import com.ddgo.shared.contract.DlKeys
import com.ddgo.shared.contract.DlPaths
import com.ddgo.shared.model.HeartRateSnapshot
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchSessionStatus
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WatchStateSyncManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val json = Json { ignoreUnknownKeys = true }

    fun syncRuntime(snapshot: ExerciseRuntimeSnapshot) {
        val sessionId = snapshot.sessionId ?: return
        syncHeartRate(
            HeartRateSnapshot(
                heartRate = snapshot.latestHeartRate,
                alerting = snapshot.alerting,
                sensorAvailable = snapshot.sensorAvailable,
                measurementStatus = snapshot.measurementStatus,
                lastMeasuredAt = snapshot.lastMeasuredAt,
                updatedAt = snapshot.updatedAt
            )
        )
        syncWatchStatus(
            WatchSessionStatus(
                sessionId = sessionId,
                watchState = snapshot.watchState,
                serviceActive = snapshot.serviceActive,
                alerting = snapshot.alerting,
                sensorAvailable = snapshot.sensorAvailable,
                updatedAt = snapshot.updatedAt
            )
        )
    }

    fun sendAlertMessage(snapshot: ExerciseRuntimeSnapshot) {
        val sessionId = snapshot.sessionId ?: return
        val status = WatchSessionStatus(
            sessionId = sessionId,
            watchState = snapshot.watchState,
            serviceActive = snapshot.serviceActive,
            alerting = snapshot.alerting,
            sensorAvailable = snapshot.sensorAvailable,
            updatedAt = snapshot.updatedAt
        )
        val payload = json.encodeToString(status).encodeToByteArray()
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, DlPaths.MSG_ALERT, payload)
                        .addOnFailureListener { throwable ->
                            Log.w(TAG, "Failed to send alert message to node=${node.id}", throwable)
                        }
                }
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to resolve nodes for alert message.", throwable)
            }
    }

    private fun syncHeartRate(snapshot: HeartRateSnapshot) {
        val request = PutDataMapRequest.create(DlPaths.DATA_LIVE_HR).apply {
            dataMap.putInt(DlKeys.HEART_RATE, snapshot.heartRate ?: -1)
            dataMap.putBoolean(DlKeys.ALERTING, snapshot.alerting)
            dataMap.putBoolean(DlKeys.SENSOR_AVAILABLE, snapshot.sensorAvailable)
            dataMap.putString(DlKeys.MEASUREMENT_STATUS, snapshot.measurementStatus.name)
            dataMap.putLong(DlKeys.LAST_MEASURED_AT, snapshot.lastMeasuredAt ?: -1L)
            dataMap.putLong(DlKeys.UPDATED_AT, snapshot.updatedAt)
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to sync heart rate snapshot.", throwable)
            }
    }

    private fun syncWatchStatus(status: WatchSessionStatus) {
        val request = PutDataMapRequest.create(DlPaths.DATA_WATCH_SESSION_STATUS).apply {
            dataMap.putString(DlKeys.SESSION_ID, status.sessionId)
            dataMap.putString(DlKeys.WATCH_STATE, status.watchState.name)
            dataMap.putBoolean(DlKeys.SERVICE_ACTIVE, status.serviceActive)
            dataMap.putBoolean(DlKeys.ALERTING, status.alerting)
            dataMap.putBoolean(DlKeys.SENSOR_AVAILABLE, status.sensorAvailable)
            dataMap.putLong(DlKeys.UPDATED_AT, status.updatedAt)
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Failed to sync watch session status.", throwable)
            }
    }

    private companion object {
        const val TAG = "WatchStateSync"
    }
}
