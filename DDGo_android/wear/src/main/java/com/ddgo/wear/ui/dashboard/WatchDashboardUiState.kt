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
    ACTIVE,
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

internal data class WatchDashboardInfoItem(
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
    val heroTitle: String,
    val heroValue: String,
    val heroUnit: String?,
    val messageTitle: String,
    val messageBody: String,
    val infoItems: List<WatchDashboardInfoItem>,
    val sessionLabel: String?,
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

    val (heroTitle, heroValue, heroUnit) = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED -> Triple("상태", "권한", null)
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> Triple("상태", "센서", null)
        WatchDashboardVisualState.ALERTING,
        WatchDashboardVisualState.MEASURING -> Triple(
            "심박수",
            runtimeSnapshot.latestHeartRate?.toString() ?: "--",
            runtimeSnapshot.latestHeartRate?.let { "bpm" }
        )

        else -> Triple("심박수", "--", null)
    }

    val (messageTitle, messageBody) = when (visualState) {
        WatchDashboardVisualState.IDLE ->
            "휴대폰에서 녹화를 시작하세요" to "워치가 심박 측정을 기다리고 있어요"

        WatchDashboardVisualState.RECOVERING ->
            "워치를 연결하는 중이에요" to "세션과 심박 센서를 준비하고 있어요"

        WatchDashboardVisualState.MEASURING ->
            "심박을 측정하고 있어요" to "손목에 밀착되도록 착용해주세요"

        WatchDashboardVisualState.ALERTING ->
            "심박수가 높아요" to "호흡을 가다듬고 자세를 확인해주세요"

        WatchDashboardVisualState.PERMISSION_REQUIRED ->
            "권한이 필요해요" to permissionState.summary

        WatchDashboardVisualState.SENSOR_UNAVAILABLE ->
            "센서를 확인해주세요" to "워치를 손목에 맞게 다시 착용해주세요"
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

    val secondaryAction = when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED -> WatchDashboardActionUi(
            label = "설정 열기",
            kind = WatchDashboardActionKind.OPEN_SETTINGS
        )

        else -> null
    }

    return WatchDashboardUiState(
        visualState = visualState,
        recordingChip = WatchDashboardChipUi(
            label = if (syncSnapshot.isRecording) "녹화 중" else "대기 중",
            tone = if (syncSnapshot.isRecording) {
                WatchDashboardChipTone.ACTIVE
            } else {
                WatchDashboardChipTone.NEUTRAL
            }
        ),
        connectionChip = WatchDashboardChipUi(
            label = if (isConnected) "연결됨" else "대기",
            tone = if (isConnected) {
                WatchDashboardChipTone.ACTIVE
            } else {
                WatchDashboardChipTone.NEUTRAL
            }
        ),
        heroTitle = heroTitle,
        heroValue = heroValue,
        heroUnit = heroUnit,
        messageTitle = messageTitle,
        messageBody = messageBody,
        infoItems = listOf(
            WatchDashboardInfoItem(
                label = "연결",
                value = if (isConnected) "연결됨" else "대기"
            ),
            WatchDashboardInfoItem(
                label = "측정",
                value = runtimeSnapshot.measurementStatus.toDashboardLabel(syncSnapshot.isRecording)
            ),
            WatchDashboardInfoItem(
                label = "경고",
                value = if (runtimeSnapshot.alerting) "주의" else "안전"
            )
        ),
        sessionLabel = runtimeSnapshot.sessionId?.take(8)?.let { "세션 $it" }
            ?: syncSnapshot.recordingState?.sessionId?.take(8)?.let { "세션 $it" },
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

private fun MeasurementStatus.toDashboardLabel(isRecording: Boolean): String {
    return when (this) {
        MeasurementStatus.MEASURING -> "정상"
        MeasurementStatus.RECOVERING -> "연결 중"
        MeasurementStatus.PERMISSION_BLOCKED -> "권한 필요"
        MeasurementStatus.UNAVAILABLE -> if (isRecording) "오류" else "대기"
    }
}
