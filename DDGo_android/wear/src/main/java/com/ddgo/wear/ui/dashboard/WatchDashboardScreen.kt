package com.ddgo.wear.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DdigoBlue = Color(0xFF4396FB)
private val DdigoGray = Color(0xFF505050)
private val DdigoGradientStart = Color(0xFF8458FF)
private val DdigoGradientEnd = Color(0xFF42A7FF)
private val DdigoAlert = Color(0xFFFF6F8D)
private val DdigoIdleStart = Color(0xFF4B5064)
private val DdigoIdleEnd = Color(0xFF6A7391)
private val SurfaceBase = Color(0xFF0C0F17)
private val SurfaceRaised = Color(0xFF151A24)
private val SurfaceSoft = Color(0xFF1D2330)
private val TextPrimary = Color(0xFFF7F8FB)
private val TextSecondary = Color(0xFFAFB8CC)

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
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF12162A),
                            SurfaceBase
                        )
                        )
                    )
        ) {
            val compact = maxWidth <= 200.dp || maxHeight <= 200.dp
            val hasActions = uiState.primaryAction != null || uiState.secondaryAction != null
            val contentPadding = if (compact) 10.dp else 18.dp
            val heroSize = when {
                compact && hasActions -> 100.dp
                compact -> 114.dp
                else -> 150.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = contentPadding, vertical = if (compact) 10.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                ContextHeader(
                    uiState = uiState,
                    compact = compact
                )

                Spacer(modifier = Modifier.weight(if (compact) 0.12f else 0.14f))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    HeartHero(
                        uiState = uiState,
                        palette = palette,
                        compact = compact,
                        heroSize = heroSize
                    )

                    if (hasActions) {
                        Spacer(modifier = Modifier.size(if (compact) 10.dp else 14.dp))
                        HeadlineBlock(
                            headline = uiState.headline,
                            body = uiState.body,
                            compact = compact
                        )
                    }
                }

                if (hasActions) {
                    ActionArea(
                        primaryAction = uiState.primaryAction,
                        secondaryAction = uiState.secondaryAction,
                        compact = compact,
                        accentBrush = palette.primaryBrush,
                        onAction = onAction
                    )
                } else if (compact) {
                    StatusDock(metrics = uiState.metrics, compact = true)
                } else {
                    StatusDock(metrics = uiState.metrics, compact = false)
                }
            }
        }
    }
}

@Composable
private fun ContextHeader(
    uiState: WatchDashboardUiState,
    compact: Boolean
) {
    val accent = when (uiState.recordingChip.tone) {
        WatchDashboardChipTone.PRIMARY -> DdigoBlue
        WatchDashboardChipTone.WARNING -> DdigoAlert
        WatchDashboardChipTone.NEUTRAL -> DdigoGray
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 6.dp else 8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                text = uiState.headline,
                color = TextPrimary,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = uiState.body,
            color = TextSecondary,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            maxLines = if (uiState.primaryAction == null && uiState.secondaryAction == null) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HeartHero(
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean,
    heroSize: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier.size(heroSize + if (compact) 32.dp else 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val outerRadius = size.minDimension / 2f - 12.dp.toPx()
            val innerRadius = heroSize.toPx() / 2f - 6.dp.toPx()

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.glow.copy(alpha = 0.18f),
                        palette.glow.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    radius = outerRadius * 1.08f
                ),
                radius = outerRadius * 1.08f
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = innerRadius + 10.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = SurfaceRaised.copy(alpha = 0.96f),
                radius = innerRadius + 6.dp.toPx()
            )
            drawCircle(
                color = SurfaceBase,
                radius = innerRadius
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.04f),
                        Color.Transparent
                    ),
                    radius = innerRadius
                ),
                radius = innerRadius
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = uiState.title,
                color = TextSecondary,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = uiState.value,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                style = if (compact) {
                    TextStyle(fontSize = 52.sp, lineHeight = 50.sp)
                } else {
                    TextStyle(fontSize = 60.sp, lineHeight = 58.sp)
                }
            )
            uiState.unit?.let { unit ->
                Text(
                    text = unit,
                    color = TextSecondary,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatusDock(
    metrics: List<WatchDashboardMetricUi>,
    compact: Boolean
) {
    if (metrics.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSoft.copy(alpha = 0.7f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 7.dp else 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            metrics.forEach { metric ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = metric.label,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Text(
                        text = metric.value,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadlineBlock(
    headline: String,
    body: String,
    compact: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = headline,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = body,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionArea(
    primaryAction: WatchDashboardActionUi?,
    secondaryAction: WatchDashboardActionUi?,
    compact: Boolean,
    accentBrush: Brush,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    if (primaryAction == null && secondaryAction == null) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        primaryAction?.let { action ->
            Button(
                onClick = { onAction(action.kind) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentBrush)
                        .padding(vertical = if (compact) 8.dp else 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = action.label,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        secondaryAction?.let { action ->
            OutlinedButton(
                onClick = { onAction(action.kind) },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
            ) {
                Text(
                    text = action.label,
                    color = TextPrimary,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private data class WatchDashboardPalette(
    val primaryBrush: Brush,
    val glow: Color
)

private fun paletteFor(visualState: WatchDashboardVisualState): WatchDashboardPalette {
    val colors = when (visualState) {
        WatchDashboardVisualState.IDLE -> listOf(DdigoIdleStart, DdigoIdleEnd)
        WatchDashboardVisualState.RECOVERING -> listOf(DdigoBlue, DdigoGradientEnd)
        WatchDashboardVisualState.ALERTING -> listOf(DdigoAlert, DdigoGradientEnd)
        WatchDashboardVisualState.PERMISSION_REQUIRED -> listOf(DdigoGradientStart, DdigoGradientEnd)
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> listOf(DdigoBlue, DdigoGradientEnd)
        else -> listOf(DdigoGradientStart, DdigoGradientEnd)
    }

    return WatchDashboardPalette(
        primaryBrush = Brush.linearGradient(colors),
        glow = colors.last()
    )
}

@Preview(
    name = "워치 측정",
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true
)
@Composable
private fun WatchDashboardMeasuringPreview() {
    WatchDashboardScreen(
        uiState = WatchDashboardUiState(
            visualState = WatchDashboardVisualState.MEASURING,
            recordingChip = WatchDashboardChipUi("녹화", WatchDashboardChipTone.PRIMARY),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "심박수",
            value = "104",
            unit = "bpm",
            headline = "심박 측정 중",
            body = "손목에 밀착되도록 착용해주세요",
            metrics = listOf(
                WatchDashboardMetricUi("측정", "정상"),
                WatchDashboardMetricUi("경고", "안전"),
                WatchDashboardMetricUi("연결", "정상")
            ),
            footer = null
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
            recordingChip = WatchDashboardChipUi("대기", WatchDashboardChipTone.NEUTRAL),
            connectionChip = WatchDashboardChipUi("오프", WatchDashboardChipTone.NEUTRAL),
            title = "상태",
            value = "권한",
            unit = null,
            headline = "권한이 필요해요",
            body = "심박수와 활동 인식 권한을 허용해주세요",
            metrics = emptyList(),
            footer = null,
            primaryAction = WatchDashboardActionUi("허용하기", WatchDashboardActionKind.REQUEST_PERMISSION),
            secondaryAction = WatchDashboardActionUi("설정", WatchDashboardActionKind.OPEN_SETTINGS)
        ),
        onAction = {}
    )
}
