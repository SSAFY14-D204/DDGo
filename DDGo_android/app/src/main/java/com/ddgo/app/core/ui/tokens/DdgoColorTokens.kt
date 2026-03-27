package com.ddgo.app.core.ui.tokens

import androidx.compose.ui.graphics.Color

object DdgoColorTokens {
    // Brand
    val BrandBlue = Color(0xFF53A6FF)
    val BrandBlueStrong = Color(0xFF1554D1)
    val BrandGray = Color(0xFF505050)
    val BrandGradientStart = Color(0xFF8458FF)
    val BrandGradientEnd = Color(0xFF42A7FF)

    // Hold domain
    val HoldRed = Color(0xFFFF0000)
    val HoldOrange = Color(0xFFFF7700)
    val HoldYellow = Color(0xFFFED500)
    val HoldBrown = Color(0xFF6B3E1C)
    val HoldPink = Color(0xFFFF56A8)
    val HoldNavy = Color(0xFF4E56E5)
    val HoldGreen = Color(0xFF65B969)
    val HoldPurple = Color(0xFF876FFF)
    val HoldIvory = Color(0xFFF7F4F4)
    val HoldSky = Color(0xFF60DCFF)
    val HoldBlue = BrandBlue
    val HoldGray = BrandGray
    val HoldBlack = Color(0xFF0B0B0E)

    // Neutral
    val NeutralBlack = HoldBlack
    val NeutralLightGray = Color(0xFF999999)
    val NeutralMediumGray = Color(0xFF8C8C8C)
    val NeutralWhiteGray = Color(0xFFF0F3F5)
    val NeutralWhite = Color(0xFFFFFFFF)
    val NeutralBackground = HoldIvory

    // Semantic
    val SemanticLightBlue = Color(0xFF71A3D0)
    val SemanticLightPurple = Color(0xFFC9C5FF)
    val SemanticLightGreen = Color(0xFF6FFF98)

    // Compatibility tokens used across the app
    val Background = NeutralBackground
    val Surface = NeutralWhite
    val SurfaceMuted = NeutralWhiteGray
    val SurfaceTint = SemanticLightBlue.copy(alpha = 0.18f)
    val Border = Color(0xFFD7E5F5)

    val TextPrimary = NeutralBlack
    val TextSecondary = BrandGray
    val TextHint = NeutralMediumGray
    val TextInverse = NeutralWhite

    val DisabledFill = BrandGray
    val DisabledContent = NeutralWhite

    val Success = SemanticLightGreen
    val Warning = HoldOrange
    val Error = HoldRed

    val DarkBackground = NeutralBlack
    val DarkSurface = Color(0xFF171F2B)
    val DarkSurfaceMuted = Color(0xFF253041)
    val DarkBorder = Color(0xFF3A4A5E)
    val DarkTextPrimary = NeutralWhite
    val DarkTextSecondary = Color(0xFFB5C2D1)
}
