package com.ddgo.app.feature.community

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

internal object CommunityPalette {
    val BrandBlue = Color(0xFF42A5F5)
    val BrandGray = Color(0xFF6C7B8A)
    val BrandGreen = Color(0xFFE8F3FF)
    val NeutralBlack = Color(0xFF1E232C)
    val NeutralGray = Color(0xFF90A0AF)
    val NeutralWhite = Color(0xFFFFFFFF)
    val NeutralBackground = Color(0xFFF7FBFF)
    val LightBlue = Color(0xFFE8F3FF)
    val LightPurple = Color(0xFFD7E5F5)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF4F8FD)
    val Border = Color(0xFFD7E5F5)
    val TextPrimary = Color(0xFF1E232C)
    val TextSecondary = Color(0xFF6C7B8A)
    val Accent = Color(0xFF42A5F5)
    val AccentStrong = Color(0xFF1E88E5)
    val AccentSoft = Color(0xFFE8F3FF)
    val OnAccent = Color.White

    val PageGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7FBFF),
            Color(0xFFEAF3FF),
            Color(0xFFF7FBFF)
        )
    )

    val HeroGradient = Brush.horizontalGradient(colors = listOf(Accent, AccentStrong))
}
