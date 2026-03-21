package com.ddgo.app.feature.community

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal object CommunityPalette {
    val BrandBlue = Color(0xFF4396FB)
    val BrandGray = Color(0xFF505050)
    val BrandGreen = Color(0xFF6FFF98)
    val NeutralBlack = Color(0xFF0B0B0E)
    val NeutralGray = Color(0xFF999999)
    val NeutralWhite = Color(0xFFFFFFFF)
    val NeutralBackground = Color(0xFFF7F4F4)
    val LightBlue = Color(0xFF71A3D0)
    val LightPurple = Color(0xFFC9C5FF)

    val PageGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7F4F4),
            Color(0xFFF9FBFF),
            Color(0xFFFFFFFF)
        )
    )

    val HeroGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF8458FF), Color(0xFF42A7FF))
    )
}
