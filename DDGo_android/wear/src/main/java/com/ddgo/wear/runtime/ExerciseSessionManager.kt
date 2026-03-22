package com.ddgo.wear.runtime

import android.os.SystemClock
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.startExercise
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseTrackedStatus
import androidx.health.services.client.data.ExerciseUpdate
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.SampleDataPoint
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.RecordingState
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.service.WatchExerciseService
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExerciseSessionManager(
    private val service: WatchExerciseService,
    private val runtimeStore: ExerciseRuntimeStore = ExerciseRuntimeStore.get(service.applicationContext),
    private val recordingStateStore: RecordingStateStore = RecordingStateStore.get(service.applicationContext),
    private val ongoingActivityController: OngoingActivityController = OngoingActivityController(service.applicationContext),
    private val stopService: () -> Unit
) {
    private val appContext = service.applicationContext
    private val exerciseClient = HealthServices.getClient(appContext).exerciseClient

    private var callbackRegistered = false
    private var sensorAvailable = false

    private val updateCallback = object : ExerciseUpdateCallback {
        override fun onRegistered() = Unit

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "Exercise update callback registration failed.", throwable)
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) = Unit

        override fun onAvailabilityChanged(
            dataType: DataType<*, *>,
            availability: Availability
        ) {
            if (dataType != DataType.HEART_RATE_BPM) {
                return
            }
            sensorAvailable = !availability.javaClass.simpleName.contains("Unavailable", ignoreCase = true)
            val recordingState = recordingStateStore.snapshot.value.recordingState ?: return
            val snapshot = if (sensorAvailable) {
                runtimeStore.markRecording(
                    recordingState = recordingState,
                    sensorAvailable = true,
                    measurementStatus = MeasurementStatus.MEASURING,
                    reason = "Heart rate sensor is available"
                )
            } else {
                runtimeStore.markUnavailable(
                    recordingState = recordingState,
                    reason = "Heart rate sensor is unavailable"
                )
            }
            ongoingActivityController.startOrUpdate(service, snapshot)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val recordingState = recordingStateStore.snapshot.value.recordingState
            val stateName = update.exerciseStateInfo.state.name
            if (stateName.contains("ENDED", ignoreCase = true)) {
                val snapshot = runtimeStore.markIdle("Exercise ended: $stateName")
                ongoingActivityController.startOrUpdate(service, snapshot)
                stopService()
                return
            }

            val heartRate = update.latestMetrics
                .getData(DataType.HEART_RATE_BPM)
                .lastOrNull()
                ?.value
                ?.roundToInt()
            val measuredAt = update.latestMetrics
                .getData(DataType.HEART_RATE_BPM)
                .lastOrNull()
                ?.let(::toEpochMillis)

            val effectiveRecordingState = recordingState ?: RecordingState(
                sessionId = runtimeStore.snapshot.value.sessionId ?: "recovered",
                isRecording = true,
                updatedAt = System.currentTimeMillis()
            )
            val snapshot = runtimeStore.markRecording(
                recordingState = effectiveRecordingState,
                latestHeartRate = heartRate,
                lastMeasuredAt = measuredAt,
                sensorAvailable = sensorAvailable || heartRate != null,
                measurementStatus = if (heartRate != null) {
                    MeasurementStatus.MEASURING
                } else {
                    MeasurementStatus.RECOVERING
                },
                reason = "Exercise update received"
            )
            ongoingActivityController.startOrUpdate(service, snapshot)
        }
    }

    suspend fun syncWithCurrentRecordingState(forceRecovery: Boolean = false) {
        val recordingState = recordingStateStore.snapshot.value.recordingState
        if (recordingState?.isRecording != true) {
            endSessionAndStop("Recording state is idle")
            return
        }

        val recoveringSnapshot = runtimeStore.markRecovering(
            recordingState = recordingState,
            reason = if (forceRecovery) {
                "Recovering watch exercise session"
            } else {
                "Syncing watch exercise session"
            }
        )
        ongoingActivityController.startOrUpdate(service, recoveringSnapshot)

        if (!WearPermissionHelper.hasAllExercisePermissions(appContext)) {
            val snapshot = runtimeStore.markPermissionBlocked(
                recordingState = recordingState,
                reason = "Grant body sensor and activity permissions on watch"
            )
            ongoingActivityController.startOrUpdate(service, snapshot)
            return
        }

        if (!deviceSupportsHeartRate()) {
            val snapshot = runtimeStore.markUnavailable(
                recordingState = recordingState,
                reason = "This watch does not expose heart rate workout data"
            )
            ongoingActivityController.startOrUpdate(service, snapshot)
            return
        }

        ensureCallbackRegistered()
        val trackedStatus = currentTrackedStatus()
        if (trackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
            val snapshot = runtimeStore.markRecording(
                recordingState = recordingState,
                sensorAvailable = sensorAvailable,
                measurementStatus = MeasurementStatus.RECOVERING,
                reason = "Recovered existing exercise session"
            )
            ongoingActivityController.startOrUpdate(service, snapshot)
            return
        }

        val config = ExerciseConfig(
            exerciseType = ExerciseType.WORKOUT,
            dataTypes = setOf(DataType.HEART_RATE_BPM),
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false
        )

        runCatching {
            exerciseClient.startExercise(config)
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to start exercise session.", throwable)
            val snapshot = runtimeStore.markUnavailable(
                recordingState = recordingState,
                reason = throwable.message ?: "Unable to start exercise session"
            )
            ongoingActivityController.startOrUpdate(service, snapshot)
        }
    }

    suspend fun endSessionAndStop(reason: String) {
        if (callbackRegistered) {
            runCatching {
                exerciseClient.endExercise()
            }.onFailure { throwable ->
                Log.d(TAG, "Exercise end was not applied cleanly.", throwable)
            }
            clearCallback()
        }
        val snapshot = runtimeStore.markIdle(reason)
        ongoingActivityController.startOrUpdate(service, snapshot)
        stopService()
    }

    suspend fun shutdown() {
        clearCallback()
    }

    private suspend fun ensureCallbackRegistered() {
        if (callbackRegistered) {
            return
        }
        withContext(Dispatchers.Main) {
            exerciseClient.setUpdateCallback(updateCallback)
        }
        callbackRegistered = true
    }

    private suspend fun clearCallback() {
        if (!callbackRegistered) {
            return
        }
        runCatching {
            exerciseClient.clearUpdateCallback(updateCallback)
        }.onFailure { throwable ->
            Log.d(TAG, "Failed to clear exercise callback.", throwable)
        }
        callbackRegistered = false
    }

    private suspend fun deviceSupportsHeartRate(): Boolean {
        return runCatching {
            val capabilities = exerciseClient.getCapabilities()
            if (ExerciseType.WORKOUT !in capabilities.supportedExerciseTypes) {
                return false
            }
            val exerciseTypeCapabilities = capabilities.getExerciseTypeCapabilities(ExerciseType.WORKOUT)
            DataType.HEART_RATE_BPM in exerciseTypeCapabilities.supportedDataTypes
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to inspect exercise capabilities.", throwable)
            false
        }
    }

    private suspend fun currentTrackedStatus(): Int? {
        return runCatching {
            exerciseClient.getCurrentExerciseInfo().exerciseTrackedStatus
        }.getOrElse { throwable ->
            Log.d(TAG, "No current exercise info available.", throwable)
            null
        }
    }

    private fun toEpochMillis(dataPoint: SampleDataPoint<Double>): Long {
        val bootInstant = Instant.ofEpochMilli(System.currentTimeMillis() - SystemClock.elapsedRealtime())
        return dataPoint.getTimeInstant(bootInstant).toEpochMilli()
    }

    private companion object {
        const val TAG = "ExerciseSessionMgr"
    }
}
