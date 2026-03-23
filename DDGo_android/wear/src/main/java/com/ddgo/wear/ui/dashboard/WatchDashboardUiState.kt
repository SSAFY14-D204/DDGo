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
            val parts = buildList {
                if (missingForegroundPermissions.isNotEmpty()) {
                    add(
                        if (Build.VERSION.SDK_INT >= 36) {
                            "심박수와 활동 인식 권한을 허용해주세요"
                        } else {
                            "센서와 활동 인식 권한을 허용해주세요"
                        }
                    )
                }
                if (needsBackgroundPermission) {
                    add(
                        if (Build.VERSION.SDK_INT >= 36) {
                            "백그라운드 건강 데이터 접근을 허용해주세요"
                        } else {
                            "백그라운드 센서 접근을 허용해주세요"
                        }
                    )
                }
            }
            return parts.joinToString(separator = " / ")
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
        WatchDashboardVisualState.IDLE -> "휴대폰에서 시작하세요"
        WatchDashboardVisualState.RECOVERING -> "세션과 센서를 준비하고 있어요"
        WatchDashboardVisualState.MEASURING -> "녹화 중 · 연결 정상"
        WatchDashboardVisualState.ALERTING -> "심박이 높아요 · 강도를 낮춰보세요"
        WatchDashboardVisualState.PERMISSION_REQUIRED -> permissionState.summary
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> "워치를 다시 착용하고 재시도해주세요"
    }

    val metrics = if (visualState == WatchDashboardVisualState.PERMISSION_REQUIRED ||
        visualState == WatchDashboardVisualState.SENSOR_UNAVAILABLE
    ) {
        emptyList()
    } else {
        listOf(
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

    val primaryAction = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED -> WatchDashboardActionUi(
            label = "허용하기",
            kind = WatchDashboardActionKind.REQUEST_PERMISSION
        )

        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> WatchDashboardActionUi(
            label = "다시 시도",
            kind = WatchDashboardActionKind.RETRY_SESSION
        )

        else -> null
    }

    val secondaryAction = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED -> WatchDashboardActionUi(
            label = "설정",
            kind = WatchDashboardActionKind.OPEN_SETTINGS
        )

        else -> null
    }

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
        MeasurementStatus.RECOVERING -> "복구 중"
        MeasurementStatus.PERMISSION_BLOCKED -> "권한"
        MeasurementStatus.UNAVAILABLE -> if (isRecording) "오류" else "대기"
    }
}
