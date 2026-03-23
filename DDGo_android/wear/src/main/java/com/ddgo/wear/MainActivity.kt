package com.ddgo.wear

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.wear.data.ExerciseRuntimeSnapshot
import com.ddgo.wear.data.ExerciseRuntimeStore
import com.ddgo.wear.data.RecordingStateStore
import com.ddgo.wear.data.RecordingStateSyncProcessor
import com.ddgo.wear.data.WearRecordingSyncSnapshot
import com.ddgo.wear.runtime.SessionRecoveryCoordinator
import com.ddgo.wear.runtime.WearPermissionHelper
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {
    private lateinit var recordingStateStore: RecordingStateStore
    private lateinit var exerciseRuntimeStore: ExerciseRuntimeStore

    private val permissionUiState = MutableStateFlow(PermissionUiState())

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
                onOpenSettings = ::openAppSettings
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
        permissionUiState.value = PermissionUiState(
            missingForegroundPermissions = WearPermissionHelper.missingForegroundPermissions(this),
            needsBackgroundBodySensors = (
                WearPermissionHelper.isBackgroundBodySensorsRequired() &&
                    !WearPermissionHelper.hasBackgroundBodySensorsPermission(this)
                ) || (
                WearPermissionHelper.isBackgroundHealthDataRequired() &&
                    !WearPermissionHelper.hasBackgroundHealthDataPermission(this)
                )
        )
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
    permissionStateFlow: StateFlow<PermissionUiState>,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val syncSnapshot by recordingStateStore.snapshot.collectAsState()
    val runtimeSnapshot by exerciseRuntimeStore.snapshot.collectAsState()
    val permissionState by permissionStateFlow.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0A1B20),
                                Color(0xFF14343D),
                                Color(0xFF2A5C55)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                RuntimeDashboard(
                    syncSnapshot = syncSnapshot,
                    runtimeSnapshot = runtimeSnapshot,
                    permissionState = permissionState,
                    onRequestPermissions = onRequestPermissions,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun RuntimeDashboard(
    syncSnapshot: WearRecordingSyncSnapshot,
    runtimeSnapshot: ExerciseRuntimeSnapshot,
    permissionState: PermissionUiState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "DDGo Watch Runtime",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        PermissionCard(
            permissionState = permissionState,
            onRequestPermissions = onRequestPermissions,
            onOpenSettings = onOpenSettings
        )
        StatusPill(
            label = when {
                runtimeSnapshot.serviceActive -> "Service ACTIVE"
                syncSnapshot.isRecording -> "Waiting for runtime"
                else -> "Service IDLE"
            },
            active = runtimeSnapshot.serviceActive
        )
        StatusPill(
            label = if (runtimeSnapshot.alerting) "Alert ACTIVE" else "Alert SAFE",
            active = runtimeSnapshot.alerting
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                InfoCard(
                    title = "Input",
                    value = if (syncSnapshot.isRecording) "Recording ON" else "Recording IDLE",
                    subtitle = syncSnapshot.lastEventSource.name
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                InfoCard(
                    title = "Watch",
                    value = runtimeSnapshot.watchState.name,
                    subtitle = if (runtimeSnapshot.serviceActive) "Foreground service active" else "No service"
                )
            }
        }
        InfoCard(
            title = "Session",
            value = runtimeSnapshot.sessionId?.take(8) ?: "-",
            subtitle = "Sync session ${syncSnapshot.recordingState?.sessionId?.take(8) ?: "-"}"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                InfoCard(
                    title = "Heart rate",
                    value = runtimeSnapshot.latestHeartRate?.let { "$it bpm" } ?: "--",
                    subtitle = runtimeSnapshot.lastMeasuredAt.toReadableTime()
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                InfoCard(
                    title = "Measure",
                    value = runtimeSnapshot.measurementStatus.displayName(),
                    subtitle = if (runtimeSnapshot.sensorAvailable) "Sensor ready" else "Sensor not ready"
                )
            }
        }
        InfoCard(
            title = "Updated",
            value = runtimeSnapshot.updatedAt.toReadableTime(),
            subtitle = runtimeSnapshot.lastReason ?: "Waiting for watch runtime events"
        )
    }
}

@Composable
private fun PermissionCard(
    permissionState: PermissionUiState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val ready = permissionState.allGranted
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) Color(0xFFECFFF3) else Color(0xFFFFF2E6)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (ready) "Permissions ready" else "Permissions required",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF14221D)
            )
            Text(
                text = if (ready) {
                    "Heart rate session can run with screen off."
                } else {
                    permissionState.summary
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4E615B)
            )
            if (!ready) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRequestPermissions) {
                        Text("Grant")
                    }
                    OutlinedButton(onClick = onOpenSettings) {
                        Text("Settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean
) {
    val background = if (active) {
        Color(0xFFB7F5D0)
    } else {
        Color(0xFFE5EFE9)
    }
    val content = if (active) {
        Color(0xFF0F4A2D)
    } else {
        Color(0xFF27413A)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = content,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    subtitle: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FBF9))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF50625C)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF13221D),
                fontWeight = FontWeight.SemiBold
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5F736C)
                )
            }
        }
    }
}

private fun Long?.toReadableTime(): String {
    val value = this ?: return "-"
    if (value == 0L) {
        return "-"
    }
    return Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)
}

private fun MeasurementStatus.displayName(): String {
    return when (this) {
        MeasurementStatus.MEASURING -> "Measuring"
        MeasurementStatus.UNAVAILABLE -> "Unavailable"
        MeasurementStatus.PERMISSION_BLOCKED -> "Permission blocked"
        MeasurementStatus.RECOVERING -> "Recovering"
    }
}

private data class PermissionUiState(
    val missingForegroundPermissions: List<String> = emptyList(),
    val needsBackgroundBodySensors: Boolean = false
) {
    val allGranted: Boolean
        get() = missingForegroundPermissions.isEmpty() && !needsBackgroundBodySensors

    val summary: String
        get() {
            val parts = buildList {
                if (missingForegroundPermissions.isNotEmpty()) {
                    add(
                        if (Build.VERSION.SDK_INT >= 36) {
                            "Grant heart rate and activity recognition"
                        } else {
                            "Grant body sensors and activity recognition"
                        }
                    )
                }
                if (needsBackgroundBodySensors) {
                    add(
                        if (Build.VERSION.SDK_INT >= 36) {
                            "Enable background health data"
                        } else {
                            "Enable background body sensors"
                        }
                    )
                }
            }
            return parts.joinToString(separator = " | ")
        }
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
