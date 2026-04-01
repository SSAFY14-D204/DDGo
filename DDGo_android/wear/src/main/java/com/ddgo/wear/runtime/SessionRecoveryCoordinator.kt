package com.ddgo.wear.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchState
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.service.WatchExerciseService

object SessionRecoveryCoordinator {
    @Synchronized
    fun syncDesiredState(
        context: Context,
        forceRecovery: Boolean = false
    ) {
        val appContext = context.applicationContext
        val recordingState = RecordingStateStore.get(appContext).snapshot.value.recordingState
        val runtimeSnapshot = ExerciseRuntimeStore.get(appContext).snapshot.value

        if (recordingState?.isRecording == true) {
            val sameSession = runtimeSnapshot.sessionId == recordingState.sessionId
            val alreadyMeasuringSameSession = !forceRecovery &&
                runtimeSnapshot.serviceActive &&
                sameSession &&
                runtimeSnapshot.measurementStatus == MeasurementStatus.MEASURING &&
                runtimeSnapshot.watchState in setOf(
                    WatchState.RECORDING,
                    WatchState.ALERTING
                )
            val recoveryInFlightSameSession = !forceRecovery &&
                runtimeSnapshot.serviceActive &&
                sameSession &&
                runtimeSnapshot.measurementStatus == MeasurementStatus.RECOVERING &&
                System.currentTimeMillis() - runtimeSnapshot.updatedAt <= RECOVERY_GRACE_WINDOW_MS

            if (alreadyMeasuringSameSession) {
                Log.d(
                    TAG,
                    "WATCH_RECOVERY_SKIP sessionId=${recordingState.sessionId} reason=already_measuring"
                )
                return
            }

            if (recoveryInFlightSameSession) {
                Log.d(
                    TAG,
                    "WATCH_RECOVERY_SKIP sessionId=${recordingState.sessionId} reason=recovery_in_flight"
                )
                return
            }

            ExerciseRuntimeStore.get(appContext).markRecovering(
                recordingState = recordingState,
                reason = if (forceRecovery) {
                    "Recovering exercise runtime"
                } else {
                    "Recording state requires active session"
                }
            )
            startService(
                context = appContext,
                action = if (forceRecovery) {
                    WatchExerciseService.ACTION_RECOVER
                } else {
                    WatchExerciseService.ACTION_SYNC_RECORDING
                }
            )
            return
        }

        if (runtimeSnapshot.serviceActive) {
            Log.d(
                TAG,
                "WATCH_RECOVERY_STOP sessionId=${runtimeSnapshot.sessionId} reason=recording_idle"
            )
            startService(appContext, WatchExerciseService.ACTION_STOP)
        } else {
            ExerciseRuntimeStore.get(appContext).markIdle("Recording session is idle")
        }
    }

    private fun startService(
        context: Context,
        action: String
    ) {
        val intent = Intent(context, WatchExerciseService::class.java).setAction(action)
        Log.d(TAG, "WATCH_SERVICE_START_REQUEST action=$action")
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure { throwable ->
            Log.w(TAG, "Failed to start watch exercise service for action=$action", throwable)
            ExerciseRuntimeStore.get(context).markUnavailable(
                recordingState = RecordingStateStore.get(context).snapshot.value.recordingState,
                reason = throwable.message ?: "Unable to launch exercise service"
            )
        }
    }

    private const val TAG = "SessionRecovery"
    private const val RECOVERY_GRACE_WINDOW_MS = 30_000L
}
