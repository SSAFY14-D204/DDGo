package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ddgo.app.core.ui.tokens.DdgoBrushTokens
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisSuccess

internal val AnalysisBrandAccentBrush: Brush = DdgoBrushTokens.PrimaryButtonGradient

internal val AnalysisBrandSurfaceBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        DdgoColorTokens.BrandGradientEnd.copy(alpha = 0.18f),
        DdgoColorTokens.BrandGradientStart.copy(alpha = 0.18f)
    )
)

private val AnalysisSuccessAccentBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF7BEA8D),
        Color(0xFF48BE62)
    )
)

private val AnalysisSuccessSurfaceBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF7BEA8D).copy(alpha = 0.18f),
        Color(0xFF48BE62).copy(alpha = 0.18f)
    )
)

private val AnalysisWarningAccentBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFDA7A),
        Color(0xFFFFA84B)
    )
)

private val AnalysisWarningSurfaceBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFDA7A).copy(alpha = 0.18f),
        Color(0xFFFFA84B).copy(alpha = 0.18f)
    )
)

private val AnalysisFailureAccentBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFA096),
        Color(0xFFFF6262)
    )
)

private val AnalysisFailureSurfaceBrush: Brush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFFFFA096).copy(alpha = 0.18f),
        Color(0xFFFF6262).copy(alpha = 0.18f)
    )
)

private fun isWarningAccent(color: Color): Boolean =
    color == Color(0xFFFFC271) ||
        color == Color(0xFFFFC857) ||
        color == Color(0xFFFFA667) ||
        color == Color(0xFFFF8A57)

private fun isSuccessAccent(color: Color): Boolean =
    color == AnalysisSuccess || color == Color(0xFF62D26F)

private fun isFailureAccent(color: Color): Boolean =
    color == AnalysisFailure || color == Color(0xFFFF7D7D)

internal fun analysisAccentBrushFor(color: Color): Brush? = when {
    color == AnalysisPrimary -> AnalysisBrandAccentBrush
    isSuccessAccent(color) -> AnalysisSuccessAccentBrush
    isWarningAccent(color) -> AnalysisWarningAccentBrush
    isFailureAccent(color) -> AnalysisFailureAccentBrush
    else -> null
}

internal fun analysisSurfaceBrushFor(color: Color): Brush? = when {
    color == AnalysisPrimary -> AnalysisBrandSurfaceBrush
    isSuccessAccent(color) -> AnalysisSuccessSurfaceBrush
    isWarningAccent(color) -> AnalysisWarningSurfaceBrush
    isFailureAccent(color) -> AnalysisFailureSurfaceBrush
    else -> null
}

internal fun hasAnalysisGradientAccent(color: Color): Boolean =
    analysisAccentBrushFor(color) != null

@Composable
internal fun AnalysisAccentText(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true
) {
    val brush = analysisAccentBrushFor(accentColor)
    if (brush != null) {
        Text(
            text = text,
            modifier = modifier,
            style = style.copy(brush = brush),
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap
        )
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = style.copy(color = accentColor),
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap
        )
    }
}

@Composable
internal fun AnalysisBrandAccentText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true
) {
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = AnalysisBrandAccentBrush),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap
    )
}

@Composable
internal fun AnalysisAccentLinearProgressBar(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = AnalysisCardColor,
    cornerRadius: Dp = 999.dp
) {
    val brush = analysisAccentBrushFor(accentColor)
    if (brush == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(accentColor)
            )
        }
        return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(cornerRadius))
                .background(brush = brush)
        )
    }
}

@Composable
internal fun AnalysisBrandLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = AnalysisCardColor,
    cornerRadius: Dp = 999.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(cornerRadius))
                .background(brush = AnalysisBrandAccentBrush)
        )
    }
}

@Composable
internal fun AnalysisAccentCircularProgressIndicator(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = AnalysisCardColor,
    strokeWidth: Dp = 8.dp
) {
    val brush = analysisAccentBrushFor(accentColor)
    if (brush == null) {
        Canvas(modifier = modifier) {
            val safeProgress = progress.coerceIn(0f, 1f)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * safeProgress,
                useCenter = false,
                style = stroke
            )
        }
        return
    }

    Canvas(modifier = modifier) {
        val safeProgress = progress.coerceIn(0f, 1f)
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke
        )
        drawArc(
            brush = brush,
            startAngle = -90f,
            sweepAngle = 360f * safeProgress,
            useCenter = false,
            style = stroke
        )
    }
}

@Composable
internal fun AnalysisBrandCircularProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = AnalysisCardColor,
    strokeWidth: Dp = 8.dp
) {
    Canvas(modifier = modifier) {
        val safeProgress = progress.coerceIn(0f, 1f)
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = stroke
        )
        drawArc(
            brush = AnalysisBrandAccentBrush,
            startAngle = -90f,
            sweepAngle = 360f * safeProgress,
            useCenter = false,
            style = stroke
        )
    }
}
