package com.ddgo.wear.ui.dashboard

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DdigoBlue = Color(0xFF4396FB)
private val DdigoGray = Color(0xFF505050)
private val DdigoGradientStart = Color(0xFF8458FF)
private val DdigoGradientEnd = Color(0xFF42A7FF)
private val DdigoAlert = Color(0xFFFF5A6B)
private val DdigoAlertEnd = Color(0xFFFF8A5B)
private val DdigoIdleStart = Color(0xFF505769)
private val DdigoIdleEnd = Color(0xFF6A7490)
private val DdigoSensor = Color(0xFF8DA4C7)
private val SurfaceBase = Color(0xFF0C0F17)
private val SurfaceRaised = Color(0xFF151A24)
private val SurfaceSoft = Color(0xFF1D2330)
private val TextPrimary = Color(0xFFF7F8FB)
private val TextSecondary = Color(0xFFAFB8CC)

private enum class WatchDashboardLayoutMode {
    METRIC,
    PASSIVE,
    ACTION
}

@Composable
internal fun WatchDashboardScreen(
    uiState: WatchDashboardUiState,
    onAction: (WatchDashboardActionKind) -> Unit,
    onHeaderTap: (() -> Unit)? = null
) {
    val palette = paletteFor(uiState.visualState)
    val layoutMode = layoutModeFor(uiState.visualState)

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
            val contentPadding = if (compact) 12.dp else 18.dp
            val heroSize = when (layoutMode) {
                WatchDashboardLayoutMode.METRIC -> if (compact) 118.dp else 154.dp
                WatchDashboardLayoutMode.PASSIVE -> if (compact) 106.dp else 138.dp
                WatchDashboardLayoutMode.ACTION -> if (compact) 100.dp else 118.dp
            }
            val headerBody = uiState.body
            val headerBodyMaxLines = when (uiState.visualState) {
                WatchDashboardVisualState.ALERTING -> 2
                WatchDashboardVisualState.PERMISSION_REQUIRED,
                WatchDashboardVisualState.SENSOR_UNAVAILABLE -> if (compact) 3 else 2
                WatchDashboardVisualState.RECOVERING -> 1
                else -> 1
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = contentPadding, vertical = if (compact) 10.dp else 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)
            ) {
                ContextHeader(
                    visualState = uiState.visualState,
                    headline = uiState.headline,
                    body = headerBody,
                    bodyMaxLines = headerBodyMaxLines,
                    compact = compact,
                    accent = palette.accent,
                    onTap = onHeaderTap
                )

                when (layoutMode) {
                    WatchDashboardLayoutMode.METRIC -> MetricStateContent(
                        modifier = Modifier.weight(1f),
                        uiState = uiState,
                        palette = palette,
                        compact = compact,
                        heroSize = heroSize
                    )

                    WatchDashboardLayoutMode.PASSIVE -> PassiveStateContent(
                        modifier = Modifier.weight(1f),
                        uiState = uiState,
                        palette = palette,
                        compact = compact,
                        heroSize = heroSize
                    )

                    WatchDashboardLayoutMode.ACTION -> ActionStateContent(
                        modifier = Modifier.weight(1f),
                        uiState = uiState,
                        palette = palette,
                        compact = compact,
                        onAction = onAction
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextHeader(
    visualState: WatchDashboardVisualState,
    headline: String,
    body: String?,
    bodyMaxLines: Int,
    compact: Boolean,
    accent: Color,
    onTap: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(if (compact) 0.9f else 0.84f)
            .then(
                if (onTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = { onTap() })
                    }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HeaderMarker(
                visualState = visualState,
                accent = accent,
                compact = compact
            )
            Text(
                text = headline,
                color = TextPrimary,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        body?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                color = TextSecondary,
                style = if (compact) {
                    MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp)
                } else {
                    MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
                },
                maxLines = bodyMaxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MetricStateContent(
    modifier: Modifier = Modifier,
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean,
    heroSize: Dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.12f))
        HeartHero(
            uiState = uiState,
            palette = palette,
            compact = compact,
            heroSize = heroSize,
            mode = WatchDashboardLayoutMode.METRIC
        )
        Spacer(modifier = Modifier.weight(0.18f))
        StatusDock(
            metrics = uiState.metrics,
            compact = compact,
            subtle = false
        )
    }
}

@Composable
private fun PassiveStateContent(
    modifier: Modifier = Modifier,
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean,
    heroSize: Dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.16f))
        HeartHero(
            uiState = uiState,
            palette = palette,
            compact = compact,
            heroSize = heroSize,
            mode = WatchDashboardLayoutMode.PASSIVE
        )
        Spacer(modifier = Modifier.weight(0.16f))
        StatusDock(
            metrics = uiState.metrics,
            compact = compact,
            subtle = true
        )
    }
}

@Composable
private fun ActionStateContent(
    modifier: Modifier = Modifier,
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = if (compact) 4.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ActionGuideCard(
            uiState = uiState,
            palette = palette,
            compact = compact,
            modifier = Modifier.fillMaxWidth(if (compact) 0.9f else 0.86f)
        )
        Spacer(modifier = Modifier.weight(1f))
        ActionArea(
            primaryAction = uiState.primaryAction,
            secondaryAction = uiState.secondaryAction,
            compact = compact,
            accentBrush = palette.primaryBrush,
            accent = palette.accent,
            onAction = onAction
        )
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
    }
}

@Composable
private fun HeartHero(
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean,
    heroSize: Dp,
    mode: WatchDashboardLayoutMode
) {
    val isMetricHero = mode == WatchDashboardLayoutMode.METRIC
    val isAlertingHero =
        mode == WatchDashboardLayoutMode.METRIC &&
            uiState.visualState == WatchDashboardVisualState.ALERTING
    val isMeasuringHero =
        isMetricHero && uiState.visualState == WatchDashboardVisualState.MEASURING
    val pulseTransition = rememberInfiniteTransition(label = "alertPulse")
    val pulseScale = if (isMetricHero) {
        pulseTransition.animateFloat(
            initialValue = if (isAlertingHero) 0.9f else 0.96f,
            targetValue = if (isAlertingHero) 1.08f else 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isAlertingHero) 1700 else 2200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alertPulseScale"
        ).value
    } else {
        1f
    }
    val pulseAlpha = if (isMetricHero) {
        pulseTransition.animateFloat(
            initialValue = if (isAlertingHero) 0.16f else 0.08f,
            targetValue = if (isAlertingHero) 0.34f else 0.18f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isAlertingHero) 1700 else 2200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alertPulseAlpha"
        ).value
    } else {
        0f
    }
    val haloScale = if (isMetricHero) {
        pulseTransition.animateFloat(
            initialValue = if (isAlertingHero) 0.98f else 1f,
            targetValue = if (isAlertingHero) 1.12f else 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (isAlertingHero) 2100 else 2600,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alertHaloScale"
        ).value
    } else {
        1f
    }
    val glowAlpha = when (mode) {
        WatchDashboardLayoutMode.METRIC -> when {
            isAlertingHero -> 0.1f
            isMeasuringHero -> 0.12f
            else -> 0.16f
        }
        WatchDashboardLayoutMode.PASSIVE -> 0.07f
        WatchDashboardLayoutMode.ACTION -> 0.12f
    }
    val ringAlpha = when (mode) {
        WatchDashboardLayoutMode.METRIC -> when {
            isAlertingHero -> 0.88f
            isMeasuringHero -> 0.92f
            else -> 0.95f
        }
        WatchDashboardLayoutMode.PASSIVE -> 0.28f
        WatchDashboardLayoutMode.ACTION -> 0.6f
    }
    val valueStyle = when (mode) {
        WatchDashboardLayoutMode.METRIC -> if (compact) {
            TextStyle(fontSize = 52.sp, lineHeight = 50.sp)
        } else {
            TextStyle(fontSize = 60.sp, lineHeight = 58.sp)
        }

        WatchDashboardLayoutMode.PASSIVE -> if (compact) {
            TextStyle(fontSize = 46.sp, lineHeight = 44.sp)
        } else {
            TextStyle(fontSize = 52.sp, lineHeight = 50.sp)
        }

        WatchDashboardLayoutMode.ACTION -> if (compact) {
            TextStyle(fontSize = 46.sp, lineHeight = 44.sp)
        } else {
            TextStyle(fontSize = 52.sp, lineHeight = 50.sp)
        }
    }

    Box(
        modifier = Modifier.size(heroSize + if (compact) 34.dp else 44.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.minDimension / 2f - 10.dp.toPx()
            val innerRadius = heroSize.toPx() / 2f
            val ringInset = if (isAlertingHero) 5.dp.toPx() else 7.dp.toPx()
            val ringStroke = if (mode == WatchDashboardLayoutMode.METRIC) {
                if (isAlertingHero) 3.dp.toPx() else 4.dp.toPx()
            } else {
                2.dp.toPx()
            }

            if (isMetricHero) {
                val leadingGlow = if (isAlertingHero) DdigoAlert else DdigoGradientStart
                val trailingGlow = if (isAlertingHero) DdigoAlertEnd else palette.glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            leadingGlow.copy(alpha = pulseAlpha),
                            trailingGlow.copy(alpha = pulseAlpha * 0.72f),
                            Color.Transparent
                        ),
                        radius = innerRadius * 1.55f * pulseScale
                    ),
                    radius = innerRadius * 1.28f * pulseScale
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            trailingGlow.copy(alpha = pulseAlpha * if (isAlertingHero) 0.56f else 0.42f),
                            Color.Transparent
                        ),
                        radius = innerRadius * 1.78f * haloScale
                    ),
                    radius = innerRadius * 1.42f * haloScale
                )
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.glow.copy(alpha = glowAlpha),
                        palette.glow.copy(alpha = glowAlpha * 0.22f),
                        Color.Transparent
                    ),
                    radius = outerRadius * 1.12f
                ),
                radius = outerRadius * 1.12f
            )
            drawCircle(
                brush = palette.primaryBrush,
                radius = innerRadius + ringInset,
                style = Stroke(width = ringStroke),
                alpha = ringAlpha
            )
            drawCircle(
                color = SurfaceRaised.copy(alpha = 0.92f),
                radius = innerRadius + 2.dp.toPx()
            )
            drawCircle(
                color = SurfaceBase,
                radius = innerRadius - 2.dp.toPx()
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
                style = valueStyle
            )
            uiState.unit?.let { unit ->
                Text(
                    text = unit,
                    color = palette.accent,
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StatusDock(
    metrics: List<WatchDashboardMetricUi>,
    compact: Boolean,
    subtle: Boolean
) {
    if (metrics.isEmpty()) return

    val valueStyle = if (compact || subtle) {
        MaterialTheme.typography.labelMedium
    } else {
        MaterialTheme.typography.labelLarge
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (subtle) 22.dp else 26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (subtle) {
                SurfaceSoft.copy(alpha = 0.48f)
            } else {
                SurfaceSoft.copy(alpha = 0.76f)
            }
        ),
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = if (subtle) 0.05f else 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            metrics.forEach { metric ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = metric.label,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        text = metric.value,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = valueStyle,
                        textAlign = TextAlign.Center,
                        maxLines = if (compact) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionArea(
    primaryAction: WatchDashboardActionUi?,
    secondaryAction: WatchDashboardActionUi?,
    compact: Boolean,
    accentBrush: Brush,
    accent: Color,
    onAction: (WatchDashboardActionKind) -> Unit
) {
    if (primaryAction == null && secondaryAction == null) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
    ) {
        secondaryAction?.let { action ->
            SecondaryActionButton(
                modifier = Modifier,
                label = action.label,
                compact = compact,
                accent = accent,
                onClick = { onAction(action.kind) }
            )
        }
        primaryAction?.let { action ->
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(if (compact) 0.74f else 0.76f),
                label = action.label,
                icon = action.kind.icon(),
                compact = compact,
                accentBrush = accentBrush,
                onClick = { onAction(action.kind) }
            )
        }
    }
}

@Composable
private fun ActionGuideCard(
    modifier: Modifier = Modifier,
    uiState: WatchDashboardUiState,
    palette: WatchDashboardPalette,
    compact: Boolean
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceSoft.copy(alpha = 0.72f)
        ),
        border = BorderStroke(
            1.dp,
            palette.accent.copy(alpha = 0.14f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 12.dp else 14.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
        ) {
            uiState.actionHighlights.forEach { item ->
                ActionGuideRow(
                    label = item,
                    accent = palette.accent,
                    compact = compact
                )
            }
        }
    }
}

@Composable
private fun ActionGuideRow(
    label: String,
    accent: Color,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(if (compact) 15.dp else 16.dp)
        )
        Text(
            text = label,
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeaderMarker(
    visualState: WatchDashboardVisualState,
    accent: Color,
    compact: Boolean
) {
    val size = if (compact) 16.dp else 18.dp
    when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED,
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (visualState) {
                        WatchDashboardVisualState.PERMISSION_REQUIRED -> Icons.Filled.Lock
                        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> Icons.Filled.Warning
                        else -> Icons.Filled.Lock
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(if (compact) 9.dp else 10.dp)
                )
            }
        }

        else -> Box(
            modifier = Modifier
                .size(if (compact) 7.dp else 8.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

@Composable
private fun PrimaryActionButton(
    modifier: Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    compact: Boolean,
    accentBrush: Brush,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(if (compact) 50.dp else 52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(accentBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
            Text(
                text = label,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun WatchDashboardActionKind.icon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        WatchDashboardActionKind.REQUEST_PERMISSION -> Icons.Filled.Lock
        WatchDashboardActionKind.OPEN_SETTINGS -> Icons.Filled.Settings
        WatchDashboardActionKind.RETRY_SESSION -> Icons.Filled.Refresh
    }
}

@Composable
private fun SecondaryActionButton(
    modifier: Modifier,
    label: String,
    compact: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = accent.copy(alpha = 0.92f),
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            Text(
                text = label,
                color = accent.copy(alpha = 0.92f),
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private data class WatchDashboardPalette(
    val primaryBrush: Brush,
    val glow: Color,
    val accent: Color
)

private fun layoutModeFor(visualState: WatchDashboardVisualState): WatchDashboardLayoutMode {
    return when (visualState) {
        WatchDashboardVisualState.PERMISSION_REQUIRED,
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> WatchDashboardLayoutMode.ACTION

        WatchDashboardVisualState.IDLE,
        WatchDashboardVisualState.RECOVERING -> WatchDashboardLayoutMode.PASSIVE

        WatchDashboardVisualState.MEASURING,
        WatchDashboardVisualState.ALERTING -> WatchDashboardLayoutMode.METRIC
    }
}

private fun paletteFor(visualState: WatchDashboardVisualState): WatchDashboardPalette {
    val colors = when (visualState) {
        WatchDashboardVisualState.IDLE -> listOf(DdigoIdleStart, DdigoIdleEnd)
        WatchDashboardVisualState.RECOVERING -> listOf(DdigoBlue, DdigoGradientEnd)
        WatchDashboardVisualState.ALERTING -> listOf(DdigoAlert, DdigoAlertEnd)
        WatchDashboardVisualState.PERMISSION_REQUIRED -> listOf(DdigoGradientStart, DdigoGradientEnd)
        WatchDashboardVisualState.SENSOR_UNAVAILABLE -> listOf(DdigoBlue, DdigoGradientEnd)
        WatchDashboardVisualState.MEASURING -> listOf(DdigoGradientStart, DdigoGradientEnd)
    }

    return WatchDashboardPalette(
        primaryBrush = Brush.linearGradient(colors),
        glow = colors.last(),
        accent = colors.first()
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
            actionHighlights = emptyList(),
            footer = null
        ),
        onAction = {}
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
            headline = "심박 측정",
            body = "녹화 중 · 연결 정상",
            metrics = listOf(
                WatchDashboardMetricUi("측정", "정상"),
                WatchDashboardMetricUi("경고", "안전"),
                WatchDashboardMetricUi("연결", "정상")
            ),
            actionHighlights = emptyList(),
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
            headline = "권한 필요",
            body = "심박 측정을 위해 권한을 허용해주세요",
            metrics = emptyList(),
            actionHighlights = listOf(
                "심박수 측정",
                "활동 인식 · 백그라운드"
            ),
            footer = null,
            primaryAction = WatchDashboardActionUi("권한 허용", WatchDashboardActionKind.REQUEST_PERMISSION)
        ),
        onAction = {}
    )
}

@Preview(
    name = "워치 센서 오류",
    device = Devices.WEAR_OS_SMALL_ROUND,
    showSystemUi = true
)
@Composable
private fun WatchDashboardSensorPreview() {
    WatchDashboardScreen(
        uiState = WatchDashboardUiState(
            visualState = WatchDashboardVisualState.SENSOR_UNAVAILABLE,
            recordingChip = WatchDashboardChipUi("녹화", WatchDashboardChipTone.PRIMARY),
            connectionChip = WatchDashboardChipUi("연결", WatchDashboardChipTone.PRIMARY),
            title = "상태",
            value = "센서",
            unit = null,
            headline = "센서 오류",
            body = "심박 신호를 다시 읽을 준비가 필요해요",
            metrics = emptyList(),
            actionHighlights = listOf(
                "손목에 밀착해서 착용",
                "움직임을 줄이고 잠시 대기"
            ),
            footer = null,
            primaryAction = WatchDashboardActionUi("다시 시도", WatchDashboardActionKind.RETRY_SESSION)
        ),
        onAction = {}
    )
}
