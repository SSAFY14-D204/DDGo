package com.ddgo.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.data.RecordingStateSyncProcessor
import com.ddgo.wear.runtime.SessionRecoveryCoordinator
import com.ddgo.wear.runtime.WearPermissionHelper
import com.ddgo.wear.ui.dashboard.ExercisePermissionUiState
import com.ddgo.wear.ui.dashboard.WatchDashboardActionKind
import com.ddgo.wear.ui.dashboard.WatchDashboardScreen
import com.ddgo.wear.ui.dashboard.buildWatchDashboardUiState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var recordingStateStore: RecordingStateStore
    private lateinit var exerciseRuntimeStore: ExerciseRuntimeStore

    private val permissionUiState = MutableStateFlow(ExercisePermissionUiState())

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }

    private val dataChangedListener: DataClient.OnDataChangedListener by lazy {
        RecordingStateSyncProcessor.createDataChangedListener(applicationContext)
    }
    private val messageReceivedListener: MessageClient.OnMessageReceivedListener by lazy {
        RecordingStateSyncProcessor.createMessageReceivedListener(applicationContext)
    }

    private val foregroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionState()
        maybeRequestBackgroundPermission()
        SessionRecoveryCoordinator.syncDesiredState(applicationContext, forceRecovery = true)
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshPermissionState()
        SessionRecoveryCoordinator.syncDesiredState(applicationContext, forceRecovery = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordingStateStore = RecordingStateStore.get(applicationContext)
        exerciseRuntimeStore = ExerciseRuntimeStore.get(applicationContext)
        refreshPermissionState()

        setContent {
            WearApp(
                recordingStateStore = recordingStateStore,
                exerciseRuntimeStore = exerciseRuntimeStore,
                permissionStateFlow = permissionUiState.asStateFlow(),
                onRequestPermissions = ::requestExercisePermissions,
                onOpenSettings = ::openAppSettings,
                onRetryRuntime = ::retryRuntime
            )
        }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(dataChangedListener)
        messageClient.addListener(messageReceivedListener)
        RecordingStateSyncProcessor.refreshLatestRecordingState(applicationContext)
        refreshPermissionState()
        SessionRecoveryCoordinator.syncDesiredState(applicationContext, forceRecovery = true)
    }

    override fun onPause() {
        dataClient.removeListener(dataChangedListener)
        messageClient.removeListener(messageReceivedListener)
        super.onPause()
    }

    private fun requestExercisePermissions() {
        val missingForegroundPermissions = WearPermissionHelper.missingForegroundPermissions(this)
        if (missingForegroundPermissions.isNotEmpty()) {
            foregroundPermissionLauncher.launch(missingForegroundPermissions.toTypedArray())
            return
        }
        maybeRequestBackgroundPermission()
    }

    private fun maybeRequestBackgroundPermission() {
        val backgroundPermission = WearPermissionHelper.backgroundPermissionToRequest()
        if (backgroundPermission == null) {
            return
        }
        val needsPermission = when {
            WearPermissionHelper.isBackgroundHealthDataRequired() ->
                !WearPermissionHelper.hasBackgroundHealthDataPermission(this)

            WearPermissionHelper.isBackgroundBodySensorsRequired() ->
                !WearPermissionHelper.hasBackgroundBodySensorsPermission(this)

            else -> false
        }
        if (needsPermission) {
            backgroundPermissionLauncher.launch(backgroundPermission)
        }
    }

    private fun refreshPermissionState() {
        permissionUiState.value = ExercisePermissionUiState(
            missingForegroundPermissions = WearPermissionHelper.missingForegroundPermissions(this),
            needsBackgroundPermission = (
                WearPermissionHelper.isBackgroundBodySensorsRequired() &&
                    !WearPermissionHelper.hasBackgroundBodySensorsPermission(this)
                ) || (
                WearPermissionHelper.isBackgroundHealthDataRequired() &&
                    !WearPermissionHelper.hasBackgroundHealthDataPermission(this)
                )
        )
    }

    private fun retryRuntime() {
        SessionRecoveryCoordinator.syncDesiredState(applicationContext, forceRecovery = true)
    }

    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }
}

@Composable
private fun WearApp(
    recordingStateStore: RecordingStateStore,
    exerciseRuntimeStore: ExerciseRuntimeStore,
    permissionStateFlow: StateFlow<ExercisePermissionUiState>,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryRuntime: () -> Unit
) {
    val syncSnapshot by recordingStateStore.snapshot.collectAsState()
    val runtimeSnapshot by exerciseRuntimeStore.snapshot.collectAsState()
    val permissionState by permissionStateFlow.collectAsState()

    WatchDashboardScreen(
        uiState = buildWatchDashboardUiState(
            syncSnapshot = syncSnapshot,
            runtimeSnapshot = runtimeSnapshot,
            permissionState = permissionState
        ),
        onAction = { action ->
            when (action) {
                WatchDashboardActionKind.REQUEST_PERMISSION -> onRequestPermissions()
                WatchDashboardActionKind.OPEN_SETTINGS -> onOpenSettings()
                WatchDashboardActionKind.RETRY_SESSION -> onRetryRuntime()
            }
        }
    )
}
