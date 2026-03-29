package com.ddgo.wear.runtime

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.service.WatchExerciseService

object SessionRecoveryCoordinator {
    fun syncDesiredState(
        context: Context,
        forceRecovery: Boolean = false
    ) {
        val appContext = context.applicationContext
        val recordingState = RecordingStateStore.get(appContext).snapshot.value.recordingState

        if (recordingState?.isRecording == true) {
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

        if (ExerciseRuntimeStore.get(appContext).snapshot.value.serviceActive) {
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
}
