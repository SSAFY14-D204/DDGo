package com.ddgo.wear.ui.dashboard

import android.os.Build
import com.ddgo.shared.model.MeasurementStatus
import com.ddgo.shared.model.WatchState
import com.ddgo.wear.data.ExerciseRuntimeSnapshot
import com.ddgo.wear.data.WearRecordingSyncSnapshot

internal data class ExercisePermissionUiState(
    val missingForegroundPermissions: List<String> = emptyList(),
    val needsBackgroundPermission: Boolean = false
) {
    val allGranted: Boolean
        get() = missingForegroundPermissions.isEmpty() && !needsBackgroundPermission

    val summary: String
        get() {
            return when {
                missingForegroundPermissions.isNotEmpty() && needsBackgroundPermission ->
                    "심박 측정을 위해 권한을 허용해주세요"

                missingForegroundPermissions.isNotEmpty() ->
                    "필요한 권한을 허용해주세요"

                needsBackgroundPermission ->
                    if (Build.VERSION.SDK_INT >= 36) {
                        "백그라운드 측정을 위해 추가 권한이 필요해요"
                    } else {
                        "백그라운드 센서 권한을 허용해주세요"
                    }

                else -> ""
            }
        }
}

internal enum class WatchDashboardVisualState {
    IDLE,
    RECOVERING,
    MEASURING,
    ALERTING,
    PERMISSION_REQUIRED,
    SENSOR_UNAVAILABLE
}

internal enum class WatchDashboardChipTone {
    PRIMARY,
    NEUTRAL,
    WARNING
}

internal enum class WatchDashboardActionKind {
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
    RETRY_SESSION
}

internal data class WatchDashboardChipUi(
    val label: String,
    val tone: WatchDashboardChipTone
)

internal data class WatchDashboardMetricUi(
    val label: String,
    val value: String
)

internal data class WatchDashboardActionUi(
    val label: String,
    val kind: WatchDashboardActionKind
)

internal data class WatchDashboardUiState(
    val visualState: WatchDashboardVisualState,
    val recordingChip: WatchDashboardChipUi,
    val connectionChip: WatchDashboardChipUi,
    val title: String,
    val value: String,
    val unit: String?,
    val headline: String,
    val body: String,
    val metrics: List<WatchDashboardMetricUi>,
    val actionHighlights: List<String>,
    val footer: String?,
    val primaryAction: WatchDashboardActionUi? = null,
    val secondaryAction: WatchDashboardActionUi? = null
)

internal fun buildWatchDashboardUiState(
    syncSnapshot: WearRecordingSyncSnapshot,
    runtimeSnapshot: ExerciseRuntimeSnapshot,
    permissionState: ExercisePermissionUiState
): WatchDashboardUiState {
    val visualState = resolveVisualState(syncSnapshot, runtimeSnapshot, permissionState)
    val isConnected = syncSnapshot.lastAppliedAt != null ||
        runtimeSnapshot.serviceActive ||
        syncSnapshot.isRecording

    val title = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED,
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> "상태"

        else -> "심박수"
    }

    val value = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED -> "권한"
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> "센서"
        WatchDashboardVisualState.MEASURING,
        WatchDashboardVisualState.ALERTING -> runtimeSnapshot.latestHeartRate?.toString() ?: "--"

        WatchDashboardVisualState.IDLE,
        WatchDashboardVisualState.RECOVERING -> "--"
    }

    val unit = when (visualState) {
        WatchDashboardVisualState.MEASURING,
        WatchDashboardVisualState.ALERTING -> runtimeSnapshot.latestHeartRate?.let { "bpm" }

        else -> null
    }

    val headline = when (visualState) {
        WatchDashboardVisualState.IDLE -> "녹화 대기"
        WatchDashboardVisualState.RECOVERING -> "워치 준비 중"
        WatchDashboardVisualState.MEASURING -> "심박 측정"
        WatchDashboardVisualState.ALERTING -> "심박 경고"
        WatchDashboardVisualState.PERMISSION_REQUIRED -> "권한 필요"
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> "센서 오류"
    }

    val body = when (visualState) {
        WatchDashboardVisualState.IDLE -> "휴대폰에서 시작해주세요"
        WatchDashboardVisualState.RECOVERING -> "세션을 복구하고 있어요"
        WatchDashboardVisualState.MEASURING -> "녹화 중 · 연결 정상"
        WatchDashboardVisualState.ALERTING -> "강도를 낮추고 호흡을 고르세요"
        WatchDashboardVisualState.PERMISSION_REQUIRED -> permissionState.summary
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> "심박 신호를 다시 읽을 준비가 필요해요"
    }

    val metrics = if (visualState == WatchDashboardVisualState.PERMISSION_REQUIRED ||
        visualState == WatchDashboardVisualState.SENSOR_UNAVAILABLE
    ) {
        emptyList()
    } else {
        when (visualState) {
            WatchDashboardVisualState.IDLE -> listOf(
                WatchDashboardMetricUi(
                    label = "측정",
                    value = "대기"
                ),
                WatchDashboardMetricUi(
                    label = "연결",
                    value = if (isConnected) "정상" else "대기"
                )
            )

            WatchDashboardVisualState.RECOVERING -> listOf(
                WatchDashboardMetricUi(
                    label = "세션",
                    value = "복구"
                ),
                WatchDashboardMetricUi(
                    label = "연결",
                    value = if (isConnected) "정상" else "대기"
                )
            )

            else -> listOf(
                WatchDashboardMetricUi(
                    label = "측정",
                    value = runtimeSnapshot.measurementStatus.toMetricLabel(syncSnapshot.isRecording)
                ),
                WatchDashboardMetricUi(
                    label = "경고",
                    value = if (runtimeSnapshot.alerting) "주의" else "안전"
                ),
                WatchDashboardMetricUi(
                    label = "연결",
                    value = if (isConnected) "정상" else "대기"
                )
            )
        }
    }

    val primaryAction = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED -> WatchDashboardActionUi(
            label = "권한 허용",
            kind = WatchDashboardActionKind.REQUEST_PERMISSION
        )

        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> WatchDashboardActionUi(
            label = "다시 시도",
            kind = WatchDashboardActionKind.RETRY_SESSION
        )

        else -> null
    }

    val secondaryAction = null

    return WatchDashboardUiState(
        visualState = visualState,
        recordingChip = WatchDashboardChipUi(
            label = if (syncSnapshot.isRecording) "녹화" else "대기",
            tone = if (syncSnapshot.isRecording) {
                WatchDashboardChipTone.PRIMARY
            } else {
                WatchDashboardChipTone.NEUTRAL
            }
        ),
        connectionChip = WatchDashboardChipUi(
            label = if (isConnected) "연결" else "오프",
            tone = if (isConnected) {
                WatchDashboardChipTone.PRIMARY
            } else {
                WatchDashboardChipTone.NEUTRAL
            }
        ),
        title = title,
        value = value,
        unit = unit,
        headline = headline,
        body = body,
        metrics = metrics,
        actionHighlights = when (visualState) {
            WatchDashboardVisualState.PERMISSION_REQUIRED -> buildPermissionHighlights(permissionState)
            WatchDashboardVisualState.SENSOR_UNAVAILABLE -> listOf(
                "손목에 밀착해서 착용",
                "움직임을 줄이고 잠시 대기"
            )

            else -> emptyList()
        },
        footer = runtimeSnapshot.sessionId?.take(8)?.let { "세션 $it" },
        primaryAction = primaryAction,
        secondaryAction = secondaryAction
    )
}

private fun resolveVisualState(
    syncSnapshot: WearRecordingSyncSnapshot,
    runtimeSnapshot: ExerciseRuntimeSnapshot,
    permissionState: ExercisePermissionUiState
): WatchDashboardVisualState {
    return when {
        !permissionState.allGranted || runtimeSnapshot.watchState == WatchState.PERMISSION_BLOCKED ->
            WatchDashboardVisualState.PERMISSION_REQUIRED

        runtimeSnapshot.watchState == WatchState.SENSOR_UNAVAILABLE ||
            (
                syncSnapshot.isRecording &&
                    runtimeSnapshot.measurementStatus == MeasurementStatus.UNAVAILABLE &&
                    !runtimeSnapshot.sensorAvailable
                ) -> WatchDashboardVisualState.SENSOR_UNAVAILABLE

        runtimeSnapshot.alerting || runtimeSnapshot.watchState == WatchState.ALERTING ->
            WatchDashboardVisualState.ALERTING

        syncSnapshot.isRecording &&
            (
                runtimeSnapshot.watchState == WatchState.SESSION_RECOVERING ||
                    runtimeSnapshot.measurementStatus == MeasurementStatus.RECOVERING
                ) -> WatchDashboardVisualState.RECOVERING

        syncSnapshot.isRecording || runtimeSnapshot.watchState == WatchState.RECORDING ->
            WatchDashboardVisualState.MEASURING

        else -> WatchDashboardVisualState.IDLE
    }
}

private fun MeasurementStatus.toMetricLabel(isRecording: Boolean): String {
    return when (this) {
        MeasurementStatus.MEASURING -> "정상"
        MeasurementStatus.RECOVERING -> "복구"
        MeasurementStatus.PERMISSION_BLOCKED -> "권한"
        MeasurementStatus.UNAVAILABLE -> if (isRecording) "오류" else "대기"
    }
}

private fun buildPermissionHighlights(permissionState: ExercisePermissionUiState): List<String> {
    val missing = permissionState.missingForegroundPermissions.toSet()
    val items = buildList {
        if (
            missing.contains("android.permission.health.READ_HEART_RATE") ||
            missing.contains(android.Manifest.permission.BODY_SENSORS)
        ) {
            add("심박수 측정")
        }
        if (missing.contains(android.Manifest.permission.ACTIVITY_RECOGNITION) && permissionState.needsBackgroundPermission) {
            add("활동 인식 · 백그라운드")
        } else {
            if (missing.contains(android.Manifest.permission.ACTIVITY_RECOGNITION)) {
                add("활동 인식")
            }
            if (permissionState.needsBackgroundPermission) {
                add("백그라운드 접근")
            }
        }
    }

    return items.take(2).ifEmpty {
        listOf("심박수 측정", "활동 인식")
    }
}
