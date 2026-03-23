package com.ddgo.wear.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DdigoBlue = Color(0xFF4396FB)
private val DdigoGray = Color(0xFF505050)
private val DdigoGradientStart = Color(0xFF8458FF)
private val DdigoGradientEnd = Color(0xFF42A7FF)
private val SurfaceBlack = Color(0xFF12141A)
private val SurfaceDeep = Color(0xFF171B24)
private val SurfaceCard = Color(0xFF1F2430)
private val SurfaceCardSoft = Color(0xFF262C38)
private val TextPrimary = Color(0xFFF7F8FB)
private val TextSecondary = Color(0xFFB8C0D1)

@Composable
internal fun WatchDashboardScreen(
    uiState: WatchDashboardUiState,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    val palette = paletteFor(uiState.visualState)

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            SurfaceBlack,
                            Color(0xFF17182B),
                            Color(0xFF10131B)
                        )
                    )
                )
        ) {
            val compact = maxWidth <= 192.dp || maxHeight <= 192.dp
            val showActions = uiState.primaryAction != null || uiState.secondaryAction != null
            val screenPadding = if (compact) 10.dp else 16.dp
            val heroSize = when {
                compact && showActions -> 84.dp
                compact -> 96.dp
                else -> 132.dp
            }
            val gap = when {
                compact && showActions -> 6.dp
                compact -> 8.dp
                else -> 12.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = screenPadding, vertical = if (compact) 10.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusChip(
                        chip = uiState.recordingChip,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                    StatusChip(
                        chip = uiState.connectionChip,
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }

                HeroHeartPanel(
                    uiState = uiState,
                    palette = palette,
                    compact = compact,
                    size = heroSize
                )

                MessagePanel(
                    title = uiState.messageTitle,
                    body = uiState.messageBody,
                    compact = compact,
                    emphasize = showActions
                )

                if (showActions) {
                    ActionRow(
                        primaryAction = uiState.primaryAction,
                        secondaryAction = uiState.secondaryAction,
                        accentBrush = palette.heroBrush,
                        compact = compact,
                        onAction = onAction
                    )
                } else {
                    BottomInfoBand(
                        items = uiState.infoItems,
                        compact = compact
                    )
                }

                if (!compact) {
                    uiState.sessionLabel?.let { sessionLabel ->
                        Text(
                            text = sessionLabel,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    chip: WatchDashboardChipUi,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val background = when (chip.tone) {
        WatchDashboardChipTone.ACTIVE -> DdigoBlue.copy(alpha = 0.24f)
        WatchDashboardChipTone.NEUTRAL -> DdigoGray.copy(alpha = 0.72f)
        WatchDashboardChipTone.WARNING -> DdigoGradientStart.copy(alpha = 0.32f)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Text(
            text = chip.label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 7.dp else 9.dp),
            color = TextPrimary,
            textAlign = TextAlign.Center,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun HeroHeartPanel(
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean,
    size: Dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(brush = palette.heroBrush)
            .padding(if (compact) 2.dp else 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(SurfaceDeep)
                .padding(if (compact) 9.dp else 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(SurfaceBlack),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = uiState.heroTitle,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Text(
                        text = uiState.heroValue,
                        style = if (compact) {
                            TextStyle(fontSize = 30.sp, lineHeight = 32.sp)
                        } else {
                            TextStyle(fontSize = 42.sp, lineHeight = 44.sp)
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.heroUnit?.let { unit ->
                        Text(
                            text = unit,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                            color = DdigoBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagePanel(
    title: String,
    body: String,
    compact: Boolean,
    emphasize: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = body,
            style = if (compact && emphasize) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BottomInfoBand(
    items: List<WatchDashboardInfoItem>,
    compact: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.label,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = item.value,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    primaryAction: WatchDashboardActionUi?,
    secondaryAction: WatchDashboardActionUi?,
    accentBrush: Brush,
    compact: Boolean,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    if (compact && primaryAction != null && secondaryAction != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledActionButton(
                action = primaryAction,
                compact = true,
                modifier = Modifier.weight(1f),
                accentBrush = accentBrush,
                onAction = onAction
            )
            OutlinedActionButton(
                action = secondaryAction,
                compact = true,
                modifier = Modifier.weight(1f),
                onAction = onAction
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        primaryAction?.let { action ->
            FilledActionButton(
                action = action,
                compact = compact,
                modifier = Modifier.fillMaxWidth(),
                accentBrush = accentBrush,
                onAction = onAction
            )
        }
        secondaryAction?.let { action ->
            OutlinedActionButton(
                action = action,
                compact = compact,
                modifier = Modifier.fillMaxWidth(),
                onAction = onAction
            )
        }
    }
}

@Composable
private fun FilledActionButton(
    action: WatchDashboardActionUi,
    compact: Boolean,
    modifier: Modifier,
    accentBrush: Brush,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    Button(
        onClick = { onAction(action.kind) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(100.dp))
                .background(accentBrush)
                .padding(vertical = if (compact) 6.dp else 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = action.label,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OutlinedActionButton(
    action: WatchDashboardActionUi,
    compact: Boolean,
    modifier: Modifier,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    OutlinedButton(
        onClick = { onAction(action.kind) },
        modifier = modifier,
        border = BorderStroke(1.dp, DdigoBlue.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
    ) {
        Text(
            text = action.label,
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

private data class WatchDashboardPalette(
    val heroBrush: Brush
)

private fun paletteFor(visualState: WatchDashboardVisualState): WatchDashboardPalette {
    val colors = when (visualState) {
        WatchDashboardVisualState.IDLE ->
            listOf(DdigoGradientStart.copy(alpha = 0.55f), DdigoGradientEnd.copy(alpha = 0.85f))

        WatchDashboardVisualState.RECOVERING ->
            listOf(DdigoGradientStart, DdigoGradientEnd)

        WatchDashboardVisualState.MEASURING ->
            listOf(DdigoGradientStart, DdigoGradientEnd)

        WatchDashboardVisualState.ALERTING ->
            listOf(Color(0xFFFF6F8D), DdigoGradientEnd)

        WatchDashboardVisualState.PERMISSION_REQUIRED ->
            listOf(DdigoGradientStart, DdigoGradientEnd)

        WatchDashboardVisualState.SENSOR_UNAVAILABLE ->
            listOf(DdigoBlue, DdigoGradientEnd)
    }

    return WatchDashboardPalette(
        heroBrush = Brush.linearGradient(colors = colors)
    )
}

@Preview(
    name = "워치 대기",
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true
)
@Composable
private fun WatchDashboardIdlePreview() {
    WatchDashboardScreen(
        uiState = WatchDashboardUiState(
            visualState = WatchDashboardVisualState.IDLE,
            recordingChip = WatchDashboardChipUi("대기 중", WatchDashboardChipTone.NEUTRAL),
            connectionChip = WatchDashboardChipUi("대기", WatchDashboardChipTone.NEUTRAL),
            heroTitle = "심박수",
            heroValue = "--",
            heroUnit = null,
            messageTitle = "휴대폰에서 녹화를 시작하세요",
            messageBody = "워치가 심박 측정을 기다리고 있어요",
            infoItems = listOf(
                WatchDashboardInfoItem("연결", "대기"),
                WatchDashboardInfoItem("측정", "대기"),
                WatchDashboardInfoItem("경고", "안전")
            ),
            sessionLabel = null
        ),
        onAction = {}
    )
}

@Preview(
    name = "워치 권한",
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true
)
@Composable
private fun WatchDashboardPermissionPreview() {
    WatchDashboardScreen(
        uiState = WatchDashboardUiState(
            visualState = WatchDashboardVisualState.PERMISSION_REQUIRED,
            recordingChip = WatchDashboardChipUi("대기 중", WatchDashboardChipTone.NEUTRAL),
            connectionChip = WatchDashboardChipUi("대기", WatchDashboardChipTone.NEUTRAL),
            heroTitle = "상태",
            heroValue = "권한",
            heroUnit = null,
            messageTitle = "권한이 필요해요",
            messageBody = "심박수와 활동 인식 권한을 허용해주세요",
            infoItems = emptyList(),
            sessionLabel = null,
            primaryAction = WatchDashboardActionUi("권한 허용", WatchDashboardActionKind.REQUEST_PERMISSION),
            secondaryAction = WatchDashboardActionUi("설정 열기", WatchDashboardActionKind.OPEN_SETTINGS)
        ),
        onAction = {}
    )
}
