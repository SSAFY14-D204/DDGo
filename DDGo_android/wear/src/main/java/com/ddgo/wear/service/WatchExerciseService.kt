package com.ddgo.wear.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.runtime.ExerciseSessionManager
import com.ddgo.wear.runtime.OngoingActivityController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WatchExerciseService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtimeStore: ExerciseRuntimeStore
    private lateinit var ongoingActivityController: OngoingActivityController
    private lateinit var sessionManager: ExerciseSessionManager

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WATCH_SERVICE_CREATED")
        runtimeStore = ExerciseRuntimeStore.get(applicationContext)
        ongoingActivityController = OngoingActivityController(applicationContext)
        sessionManager = ExerciseSessionManager(
            service = this,
            runtimeStore = runtimeStore,
            ongoingActivityController = ongoingActivityController,
            stopService = ::stopSelfSafely
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        Log.d(TAG, "WATCH_SERVICE_ON_START action=${intent?.action} startId=$startId")
        ongoingActivityController.startOrUpdate(this, runtimeStore.snapshot.value)

        serviceScope.launch {
            when (intent?.action) {
                ACTION_STOP -> sessionManager.endSessionAndStop("Recording stopped from phone")
                ACTION_RECOVER -> sessionManager.syncWithCurrentRecordingState(forceRecovery = true)
                else -> sessionManager.syncWithCurrentRecordingState(forceRecovery = false)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "WATCH_SERVICE_DESTROYED")
        serviceScope.launch {
            sessionManager.shutdown()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopSelfSafely() {
        ongoingActivityController.stop(this)
        stopSelf()
    }

    companion object {
        private const val TAG = "WatchExerciseService"
        const val ACTION_SYNC_RECORDING = "com.ddgo.wear.action.SYNC_RECORDING"
        const val ACTION_RECOVER = "com.ddgo.wear.action.RECOVER"
        const val ACTION_STOP = "com.ddgo.wear.action.STOP"
    }
}
