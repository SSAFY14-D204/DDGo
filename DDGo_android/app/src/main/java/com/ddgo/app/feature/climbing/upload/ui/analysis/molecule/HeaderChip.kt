package com.ddgo.app.feature.climbing.upload.ui.analysis.molecule

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class HeaderChipTone(
    val background: Color,
    val content: Color,
    val border: Color
)

internal fun buildHeaderChipTone(baseColor: Color): HeaderChipTone {
    val contentColor = when {
        baseColor.luminance() < 0.12f -> lerp(baseColor, Color.White, 0.72f)
        baseColor.luminance() < 0.22f -> lerp(baseColor, Color.White, 0.52f)
        baseColor.luminance() > 0.84f -> lerp(baseColor, Color.Black, 0.28f)
        else -> baseColor
    }

    return HeaderChipTone(
        background = baseColor.copy(alpha = 0.32f),
        content = contentColor,
        border = baseColor.copy(alpha = 0.62f)
    )
}

@Composable
internal fun HeaderChip(
    text: String,
    background: Color,
    contentColor: Color,
    borderColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(
                width = if (borderColor.alpha > 0f) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
