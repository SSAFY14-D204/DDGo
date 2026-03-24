package com.ddgo.wear

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ddgo.wear.ui.dashboard.WatchDashboardDevModeScreen
import com.ddgo.wear.ui.dashboard.WatchDashboardDevPreset
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.data.RecordingStateSyncProcessor
import com.ddgo.wear.runtime.SessionRecoveryCoordinator
import com.ddgo.wear.runtime.WatchHaptics
import com.ddgo.wear.runtime.WearPermissionHelper
import com.ddgo.wear.ui.dashboard.ExercisePermissionUiState
import com.ddgo.wear.ui.dashboard.WatchDashboardActionKind
import com.ddgo.wear.ui.dashboard.WatchDashboardScreen
import com.ddgo.wear.ui.dashboard.WatchDashboardVisualState
import com.ddgo.wear.ui.dashboard.buildWatchDashboardDevUiState
import com.ddgo.wear.ui.dashboard.buildWatchDashboardUiState
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DEV_MODE_TAP_WINDOW_MILLIS = 1200L
private const val DEV_MODE_TAP_COUNT = 3

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
                debugEnabled = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
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
    debugEnabled: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryRuntime: () -> Unit
) {
    val syncSnapshot by recordingStateStore.snapshot.collectAsState()
    val runtimeSnapshot by exerciseRuntimeStore.snapshot.collectAsState()
    val permissionState by permissionStateFlow.collectAsState()
    val liveUiState = remember(syncSnapshot, runtimeSnapshot, permissionState) {
        buildWatchDashboardUiState(
            syncSnapshot = syncSnapshot,
            runtimeSnapshot = runtimeSnapshot,
            permissionState = permissionState
        )
    }
    var devMenuVisible by rememberSaveable { mutableStateOf(false) }
    var selectedDevPresetName by rememberSaveable { mutableStateOf(WatchDashboardDevPreset.LIVE.name) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val appContext = LocalContext.current.applicationContext
    val haptics = remember(appContext) {
        WatchHaptics(appContext)
    }

    val selectedDevPreset = WatchDashboardDevPreset.valueOf(selectedDevPresetName)
    val displayedUiState = if (debugEnabled && selectedDevPreset != WatchDashboardDevPreset.LIVE) {
        buildWatchDashboardDevUiState(selectedDevPreset)
    } else {
        liveUiState
    }

    LaunchedEffect(selectedDevPreset, displayedUiState.visualState, debugEnabled) {
        if (
            debugEnabled &&
            selectedDevPreset != WatchDashboardDevPreset.LIVE &&
            displayedUiState.visualState == WatchDashboardVisualState.ALERTING
        ) {
            haptics.triggerAlert()
        }
    }

    fun openDevMenu() {
        devMenuVisible = true
        tapCount = 0
        lastTapAt = 0L
    }

    fun handleHeaderTap() {
        if (!debugEnabled) return
        val now = SystemClock.elapsedRealtime()
        tapCount = if (now - lastTapAt <= DEV_MODE_TAP_WINDOW_MILLIS) {
            tapCount + 1
        } else {
            1
        }
        lastTapAt = now
        if (tapCount >= DEV_MODE_TAP_COUNT) {
            openDevMenu()
        }
    }

    if (debugEnabled && devMenuVisible) {
        WatchDashboardDevModeScreen(
            selectedPreset = selectedDevPreset,
            onSelectPreset = { preset ->
                selectedDevPresetName = preset.name
                devMenuVisible = false
            },
            onReturnToLive = {
                selectedDevPresetName = WatchDashboardDevPreset.LIVE.name
                devMenuVisible = false
            },
            onClose = {
                devMenuVisible = false
            }
        )
    } else {
        WatchDashboardScreen(
            uiState = displayedUiState,
            onAction = { action ->
                if (debugEnabled && selectedDevPreset != WatchDashboardDevPreset.LIVE) {
                    return@WatchDashboardScreen
                }
                when (action) {
                    WatchDashboardActionKind.REQUEST_PERMISSION -> onRequestPermissions()
                    WatchDashboardActionKind.OPEN_SETTINGS -> onOpenSettings()
                    WatchDashboardActionKind.RETRY_SESSION -> onRetryRuntime()
                }
            },
            onHeaderTap = if (debugEnabled) {
                ::handleHeaderTap
            } else {
                null
            }
        )
    }
}
