package com.ddgo.wear.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class WatchDashboardDevPreset(val menuLabel: String) {
    LIVE("실시간"),
    IDLE("대기"),
    RECOVERING("복구"),
    MEASURING("측정"),
    ALERTING("경고"),
    PERMISSION("권한"),
    SENSOR("센서")
}

@Composable
internal fun WatchDashboardDevModeScreen(
    selectedPreset: WatchDashboardDevPreset,
    onSelectPreset: (WatchDashboardDevPreset) -> Unit,
    onReturnToLive: () -> Unit,
    onClose: () -> Unit
) {
    val surfaceColor = Color(0xFF11162A)
    val cardColor = Color(0xFF1B2232)
    val accentBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8458FF),
            Color(0xFF42A7FF)
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0C0F17)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF12162A),
                            Color(0xFF0C0F17)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFFF8B73))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DEV",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = " 상태 미리보기",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WatchDashboardDevPreset.entries
                    .filter { it != WatchDashboardDevPreset.LIVE }
                    .chunked(3)
                    .forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val selected = preset == selectedPreset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) Color.Transparent else cardColor)
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) {
                                                Color(0xFF6EB8FF)
                                            } else {
                                                Color.White.copy(alpha = 0.08f)
                                            },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable { onSelectPreset(preset) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selected) accentBrush else Brush.verticalGradient(listOf(surfaceColor, surfaceColor)))
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = preset.menuLabel,
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = if (selectedPreset == WatchDashboardDevPreset.LIVE) onClose else onReturnToLive,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentBrush)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedPreset == WatchDashboardDevPreset.LIVE) "돌아가기" else "실시간 복귀",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

internal fun buildWatchDashboardDevUiState(
    preset: WatchDashboardDevPreset
): WatchDashboardUiState {
    return when (preset) {
        WatchDashboardDevPreset.LIVE -> error("LIVE preset must use runtime state")
        WatchDashboardDevPreset.IDLE -> WatchDashboardUiState(
            visualState = WatchDashboardVisualState.IDLE,
            recordingChip = WatchDashboardChipUi("대기", WatchDashboardChipTone.NEUTRAL),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "심박수",
            value = "--",
            unit = null,
            headline = "녹화 대기",
            body = "휴대폰에서 녹화를 시작해주세요",
            metrics = listOf(
                WatchDashboardMetricUi("측정", "대기"),
                WatchDashboardMetricUi("연결", "정상")
            ),
            footer = null
        )

        WatchDashboardDevPreset.RECOVERING -> WatchDashboardUiState(
            visualState = WatchDashboardVisualState.RECOVERING,
            recordingChip = WatchDashboardChipUi("녹화", WatchDashboardChipTone.PRIMARY),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "심박수",
            value = "--",
            unit = null,
            headline = "워치 준비 중",
            body = "세션과 센서를 복구하고 있어요",
            metrics = listOf(
                WatchDashboardMetricUi("세션", "복구"),
                WatchDashboardMetricUi("연결", "정상")
            ),
            footer = null
        )

        WatchDashboardDevPreset.MEASURING -> WatchDashboardUiState(
            visualState = WatchDashboardVisualState.MEASURING,
            recordingChip = WatchDashboardChipUi("녹화", WatchDashboardChipTone.PRIMARY),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "심박수",
            value = "104",
            unit = "bpm",
            headline = "심박 측정",
            body = "녹화 중 · 연결 정상",
            metrics = listOf(
                WatchDashboardMetricUi("측정", "정상"),
                WatchDashboardMetricUi("경고", "안전"),
                WatchDashboardMetricUi("연결", "정상")
            ),
            footer = null
        )

        WatchDashboardDevPreset.ALERTING -> WatchDashboardUiState(
            visualState = WatchDashboardVisualState.ALERTING,
            recordingChip = WatchDashboardChipUi("녹화", WatchDashboardChipTone.WARNING),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "심박수",
            value = "152",
            unit = "bpm",
            headline = "심박 경고",
            body = "호흡을 고르고 잠시 강도를 낮춰보세요",
            metrics = listOf(
                WatchDashboardMetricUi("측정", "정상"),
                WatchDashboardMetricUi("경고", "주의"),
                WatchDashboardMetricUi("연결", "정상")
            ),
            footer = null
        )

        WatchDashboardDevPreset.PERMISSION -> WatchDashboardUiState(
            visualState = WatchDashboardVisualState.PERMISSION_REQUIRED,
            recordingChip = WatchDashboardChipUi("대기", WatchDashboardChipTone.NEUTRAL),
            connectionChip = WatchDashboardChipUi("오프", WatchDashboardChipTone.NEUTRAL),
            title = "상태",
            value = "권한",
            unit = null,
            headline = "권한 필요",
            body = "심박수와 활동 인식 권한을 허용해주세요",
            metrics = emptyList(),
            footer = null,
            primaryAction = WatchDashboardActionUi("허용하기", WatchDashboardActionKind.REQUEST_PERMISSION),
            secondaryAction = WatchDashboardActionUi("설정", WatchDashboardActionKind.OPEN_SETTINGS)
        )

        WatchDashboardDevPreset.SENSOR -> WatchDashboardUiState(
            visualState = WatchDashboardVisualState.SENSOR_UNAVAILABLE,
            recordingChip = WatchDashboardChipUi("녹화", WatchDashboardChipTone.PRIMARY),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "상태",
            value = "센서",
            unit = null,
            headline = "센서 오류",
            body = "착용 상태를 확인하고 다시 시도해주세요",
            metrics = emptyList(),
            footer = null,
            primaryAction = WatchDashboardActionUi("다시 시도", WatchDashboardActionKind.RETRY_SESSION)
        )
    }
}
