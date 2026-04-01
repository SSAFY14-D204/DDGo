package com.ddgo.wear.runtime

import android.os.SystemClock
import android.util.Log
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.clearUpdateCallback
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.startExercise
import androidx.health.services.client.unregisterMeasureCallback
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
import com.ddgo.wear.data.WatchStateSyncManager
import com.ddgo.wear.service.WatchExerciseService
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExerciseSessionManager(
    private val service: WatchExerciseService,
    private val runtimeStore: ExerciseRuntimeStore = ExerciseRuntimeStore.get(service.applicationContext),
    private val recordingStateStore: RecordingStateStore = RecordingStateStore.get(service.applicationContext),
    private val ongoingActivityController: OngoingActivityController = OngoingActivityController(service.applicationContext),
    private val watchStateSyncManager: WatchStateSyncManager = WatchStateSyncManager(service.applicationContext),
    private val riskEvaluator: RiskEvaluator = RiskEvaluator(),
    private val watchHaptics: WatchHaptics = WatchHaptics(service.applicationContext),
    private val stopService: () -> Unit
) {
    private val appContext = service.applicationContext
    private val exerciseClient = HealthServices.getClient(appContext).exerciseClient
    private val measureClient = HealthServices.getClient(appContext).measureClient

    private var callbackRegistered = false
    private var measureCallbackRegistered = false
    private var sensorAvailable = false
    private val sessionCommandMutex = Mutex()

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
            Log.d(
                TAG,
                "WATCH_AVAILABILITY_CHANGED sessionId=${recordingState.sessionId} " +
                    "sensorAvailable=$sensorAvailable availability=${availability.javaClass.simpleName}"
            )
            val currentSnapshot = runtimeStore.snapshot.value
            val snapshot = if (sensorAvailable) {
                runtimeStore.markRecording(
                    recordingState = recordingState,
                    sensorAvailable = true,
                    measurementStatus = MeasurementStatus.MEASURING,
                    alerting = currentSnapshot.alerting,
                    aboveThresholdStartedAt = currentSnapshot.aboveThresholdStartedAt,
                    belowThresholdStartedAt = currentSnapshot.belowThresholdStartedAt,
                    lastAlertTriggeredAt = currentSnapshot.lastAlertTriggeredAt,
                    lastHapticAt = currentSnapshot.lastHapticAt,
                    reason = "Heart rate sensor is available"
                )
            } else {
                runtimeStore.markUnavailable(
                    recordingState = recordingState,
                    reason = "Heart rate sensor is unavailable"
                )
            }
            publish(snapshot)
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val recordingState = recordingStateStore.snapshot.value.recordingState
            val stateName = update.exerciseStateInfo.state.name
            Log.d(
                TAG,
                "WATCH_EXERCISE_UPDATE sessionId=${recordingState?.sessionId} state=$stateName"
            )
            if (stateName.contains("ENDED", ignoreCase = true)) {
                val snapshot = runtimeStore.markIdle("Exercise ended: $stateName")
                publish(snapshot)
                stopService()
                return
            }

            val latestHeartRateDataPoint = update.latestMetrics
                .getData(DataType.HEART_RATE_BPM)
                .lastOrNull()
            val heartRate = latestHeartRateDataPoint
                ?.value
                ?.roundToInt()
            val measuredAt = latestHeartRateDataPoint?.let(::toEpochMillis)
            if (heartRate != null && runtimeStore.snapshot.value.latestHeartRate == null) {
                Log.d(
                    TAG,
                    "WATCH_FIRST_BPM sessionId=${recordingState?.sessionId} " +
                        "bpm=$heartRate measuredAt=$measuredAt"
                )
            }

            val effectiveRecordingState = recordingState ?: RecordingState(
                sessionId = runtimeStore.snapshot.value.sessionId ?: "recovered",
                isRecording = true,
                updatedAt = System.currentTimeMillis()
            )
            val currentSnapshot = runtimeStore.snapshot.value
            val risk = riskEvaluator.evaluate(
                current = currentSnapshot,
                heartRate = heartRate,
                measuredAt = measuredAt
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
                alerting = risk.alerting,
                aboveThresholdStartedAt = risk.aboveThresholdStartedAt,
                belowThresholdStartedAt = risk.belowThresholdStartedAt,
                lastAlertTriggeredAt = risk.lastAlertTriggeredAt,
                lastHapticAt = risk.lastHapticAt,
                reason = if (risk.alerting) {
                    "High heart rate alert active"
                } else {
                    "Exercise update received"
                }
            )
            if (risk.shouldTriggerHaptic) {
                watchHaptics.triggerAlert()
            }
            publish(snapshot, sendAlertMessage = risk.shouldTriggerHaptic)
        }
    }

    private val measureCallback = object : MeasureCallback {
        override fun onRegistered() {
            Log.d(TAG, "WATCH_MEASURE_CALLBACK_REGISTERED")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.w(TAG, "WATCH_MEASURE_CALLBACK_REGISTER_FAIL", throwable)
        }

        override fun onAvailabilityChanged(
            dataType: androidx.health.services.client.data.DeltaDataType<*, *>,
            availability: Availability
        ) {
            if (dataType != DataType.HEART_RATE_BPM) {
                return
            }
            sensorAvailable = !availability.javaClass.simpleName.contains("Unavailable", ignoreCase = true)
            val recordingState = recordingStateStore.snapshot.value.recordingState ?: return
            Log.d(
                TAG,
                "WATCH_MEASURE_AVAILABILITY_CHANGED sessionId=${recordingState.sessionId} " +
                    "sensorAvailable=$sensorAvailable availability=${availability.javaClass.simpleName}"
            )
        }

        override fun onDataReceived(data: androidx.health.services.client.data.DataPointContainer) {
            val recordingState = recordingStateStore.snapshot.value.recordingState ?: return
            val latestHeartRateDataPoint = data.getData(DataType.HEART_RATE_BPM).lastOrNull() ?: return
            val heartRate = latestHeartRateDataPoint.value.roundToInt()
            val measuredAt = toEpochMillis(latestHeartRateDataPoint)
            if (runtimeStore.snapshot.value.latestHeartRate == null) {
                Log.d(
                    TAG,
                    "WATCH_MEASURE_FIRST_BPM sessionId=${recordingState.sessionId} " +
                        "bpm=$heartRate measuredAt=$measuredAt"
                )
            }
            val currentSnapshot = runtimeStore.snapshot.value
            val risk = riskEvaluator.evaluate(
                current = currentSnapshot,
                heartRate = heartRate,
                measuredAt = measuredAt
            )
            val snapshot = runtimeStore.markRecording(
                recordingState = recordingState,
                latestHeartRate = heartRate,
                lastMeasuredAt = measuredAt,
                sensorAvailable = true,
                measurementStatus = MeasurementStatus.MEASURING,
                alerting = risk.alerting,
                aboveThresholdStartedAt = risk.aboveThresholdStartedAt,
                belowThresholdStartedAt = risk.belowThresholdStartedAt,
                lastAlertTriggeredAt = risk.lastAlertTriggeredAt,
                lastHapticAt = risk.lastHapticAt,
                reason = if (risk.alerting) {
                    "High heart rate alert active from measure callback"
                } else {
                    "Measure callback received heart rate"
                }
            )
            if (risk.shouldTriggerHaptic) {
                watchHaptics.triggerAlert()
            }
            publish(snapshot, sendAlertMessage = risk.shouldTriggerHaptic)
        }
    }

    suspend fun syncWithCurrentRecordingState(forceRecovery: Boolean = false) {
        sessionCommandMutex.withLock {
            val recordingState = recordingStateStore.snapshot.value.recordingState
            Log.d(
                TAG,
                "WATCH_SYNC_REQUEST sessionId=${recordingState?.sessionId} " +
                    "forceRecovery=$forceRecovery isRecording=${recordingState?.isRecording}"
            )
            if (recordingState?.isRecording != true) {
                endSessionAndStopLocked("Recording state is idle")
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
            publish(recoveringSnapshot)

            if (!WearPermissionHelper.hasAllExercisePermissions(appContext)) {
                Log.w(
                    TAG,
                    "WATCH_SYNC_BLOCKED sessionId=${recordingState.sessionId} reason=missing_permissions"
                )
                val snapshot = runtimeStore.markPermissionBlocked(
                    recordingState = recordingState,
                    reason = "Grant body sensor and activity permissions on watch"
                )
                publish(snapshot)
                return
            }

            if (!deviceSupportsHeartRate()) {
                Log.w(
                    TAG,
                    "WATCH_SYNC_BLOCKED sessionId=${recordingState.sessionId} reason=heart_rate_unsupported"
                )
                val snapshot = runtimeStore.markUnavailable(
                    recordingState = recordingState,
                    reason = "This watch does not expose heart rate workout data"
                )
                publish(snapshot)
                return
            }

            ensureCallbackRegistered()
            ensureMeasureCallbackRegistered()
            val trackedStatus = currentTrackedStatus()
            if (!isCurrentRecordingSession(recordingState)) {
                Log.d(
                    TAG,
                    "WATCH_SYNC_ABORT sessionId=${recordingState.sessionId} reason=recording_changed_before_start"
                )
                return
            }
            if (trackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                Log.d(
                    TAG,
                    "WATCH_SESSION_REUSED sessionId=${recordingState.sessionId} trackedStatus=$trackedStatus"
                )
                val currentSnapshot = runtimeStore.snapshot.value
                val isAlreadyMeasuring = currentSnapshot.measurementStatus == MeasurementStatus.MEASURING ||
                    currentSnapshot.latestHeartRate != null
                val snapshot = runtimeStore.markRecording(
                    recordingState = recordingState,
                    latestHeartRate = currentSnapshot.latestHeartRate,
                    lastMeasuredAt = currentSnapshot.lastMeasuredAt,
                    sensorAvailable = sensorAvailable || currentSnapshot.sensorAvailable || currentSnapshot.latestHeartRate != null,
                    measurementStatus = if (isAlreadyMeasuring) {
                        MeasurementStatus.MEASURING
                    } else {
                        MeasurementStatus.RECOVERING
                    },
                    alerting = currentSnapshot.alerting,
                    aboveThresholdStartedAt = currentSnapshot.aboveThresholdStartedAt,
                    belowThresholdStartedAt = currentSnapshot.belowThresholdStartedAt,
                    lastAlertTriggeredAt = currentSnapshot.lastAlertTriggeredAt,
                    lastHapticAt = currentSnapshot.lastHapticAt,
                    reason = if (isAlreadyMeasuring) {
                        "Recovered active measurement session"
                    } else {
                        "Recovered existing exercise session"
                    }
                )
                publish(snapshot)
                return
            }

            val config = ExerciseConfig(
                exerciseType = ExerciseType.WORKOUT,
                dataTypes = setOf(DataType.HEART_RATE_BPM),
                isAutoPauseAndResumeEnabled = false,
                isGpsEnabled = false
            )

            runCatching {
                if (!isCurrentRecordingSession(recordingState)) {
                    Log.d(
                        TAG,
                        "WATCH_SYNC_ABORT sessionId=${recordingState.sessionId} reason=recording_changed_during_start"
                    )
                    return
                }
                Log.d(
                    TAG,
                    "WATCH_SESSION_START sessionId=${recordingState.sessionId} exerciseType=${ExerciseType.WORKOUT}"
                )
                exerciseClient.startExercise(config)
                if (!isCurrentRecordingSession(recordingState)) {
                    Log.d(
                        TAG,
                        "WATCH_SYNC_ABORT sessionId=${recordingState.sessionId} reason=recording_changed_after_start"
                    )
                    return
                }
                val snapshot = runtimeStore.markRecording(
                    recordingState = recordingState,
                    sensorAvailable = sensorAvailable,
                    measurementStatus = MeasurementStatus.RECOVERING,
                    alerting = runtimeStore.snapshot.value.alerting,
                    aboveThresholdStartedAt = runtimeStore.snapshot.value.aboveThresholdStartedAt,
                    belowThresholdStartedAt = runtimeStore.snapshot.value.belowThresholdStartedAt,
                    lastAlertTriggeredAt = runtimeStore.snapshot.value.lastAlertTriggeredAt,
                    lastHapticAt = runtimeStore.snapshot.value.lastHapticAt,
                    reason = "Exercise session started"
                )
                publish(snapshot)
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "WATCH_SESSION_START_FAIL sessionId=${recordingState.sessionId}",
                    throwable
                )
                val snapshot = runtimeStore.markUnavailable(
                    recordingState = recordingState,
                    reason = throwable.message ?: "Unable to start exercise session"
                )
                publish(snapshot)
            }
        }
    }

    suspend fun endSessionAndStop(reason: String) {
        sessionCommandMutex.withLock {
            endSessionAndStopLocked(reason)
        }
    }

    private suspend fun endSessionAndStopLocked(reason: String) {
        if (callbackRegistered) {
            val trackedStatus = currentTrackedStatus()
            if (trackedStatus == ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                runCatching {
                    exerciseClient.endExercise()
                }.onFailure { throwable ->
                    Log.d(TAG, "Exercise end was not applied cleanly.", throwable)
                }
            } else {
                Log.d(TAG, "WATCH_END_SKIP trackedStatus=$trackedStatus reason=no_active_exercise")
            }
            clearCallback()
        }
        clearMeasureCallback()
        val snapshot = runtimeStore.markIdle(reason)
        publish(snapshot)
        stopService()
    }

    suspend fun shutdown() {
        clearCallback()
        clearMeasureCallback()
    }

    private suspend fun ensureCallbackRegistered() {
        if (callbackRegistered) {
            return
        }
        withContext(Dispatchers.Main) {
            exerciseClient.setUpdateCallback(updateCallback)
        }
        callbackRegistered = true
        Log.d(TAG, "WATCH_CALLBACK_REGISTERED")
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
        Log.d(TAG, "WATCH_CALLBACK_CLEARED")
    }

    private suspend fun ensureMeasureCallbackRegistered() {
        if (measureCallbackRegistered) {
            return
        }
        if (!deviceSupportsHeartRateMeasure()) {
            Log.d(TAG, "WATCH_MEASURE_UNSUPPORTED")
            return
        }
        runCatching {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
        }.onSuccess {
            measureCallbackRegistered = true
        }.onFailure { throwable ->
            Log.w(TAG, "WATCH_MEASURE_CALLBACK_REGISTER_FAIL", throwable)
        }
    }

    private suspend fun clearMeasureCallback() {
        if (!measureCallbackRegistered) {
            return
        }
        runCatching {
            measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
        }.onFailure { throwable ->
            Log.d(TAG, "Failed to clear measure callback.", throwable)
        }
        measureCallbackRegistered = false
        Log.d(TAG, "WATCH_MEASURE_CALLBACK_CLEARED")
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

    private suspend fun deviceSupportsHeartRateMeasure(): Boolean {
        return runCatching {
            val capabilities = measureClient.getCapabilities()
            DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
        }.getOrElse { throwable ->
            Log.w(TAG, "Failed to inspect measure capabilities.", throwable)
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

    private fun isCurrentRecordingSession(recordingState: RecordingState): Boolean {
        val latestState = recordingStateStore.snapshot.value.recordingState
        return latestState?.isRecording == true && latestState.sessionId == recordingState.sessionId
    }

    private fun publish(
        snapshot: com.ddgo.wear.data.ExerciseRuntimeSnapshot,
        sendAlertMessage: Boolean = false
    ) {
        ongoingActivityController.startOrUpdate(service, snapshot)
        watchStateSyncManager.syncRuntime(snapshot)
        if (sendAlertMessage && snapshot.alerting) {
            watchStateSyncManager.sendAlertMessage(snapshot)
        }
    }

    private companion object {
        const val TAG = "ExerciseSessionMgr"
    }
}
