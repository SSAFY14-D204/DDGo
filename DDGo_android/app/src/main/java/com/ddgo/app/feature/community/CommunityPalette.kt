package com.ddgo.app.feature.community

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ddgo.app.core.ui.tokens.DdgoBrushTokens
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

internal object CommunityPalette {
    val BrandBlue = DdgoColorTokens.BrandBlue
    val BrandGray = DdgoColorTokens.BrandGray
    val BrandGreen = DdgoColorTokens.SurfaceTint
    val NeutralBlack = DdgoColorTokens.TextPrimary
    val NeutralGray = DdgoColorTokens.TextSecondary
    val NeutralWhite = DdgoColorTokens.Surface
    val NeutralBackground = DdgoColorTokens.Background
    val LightBlue = DdgoColorTokens.SurfaceTint
    val LightPurple = DdgoColorTokens.Border
    val Surface = DdgoColorTokens.Surface
    val SurfaceMuted = DdgoColorTokens.SurfaceMuted
    val Border = DdgoColorTokens.Border
    val TextPrimary = DdgoColorTokens.TextPrimary
    val TextSecondary = DdgoColorTokens.TextSecondary
    val Accent = DdgoColorTokens.BrandBlue
    val AccentStrong = DdgoColorTokens.BrandBlueStrong
    val AccentSoft = DdgoColorTokens.SurfaceTint
    val OnAccent = DdgoColorTokens.TextInverse

    val PageGradient = Brush.verticalGradient(
        colors = listOf(
            DdgoColorTokens.Background,
            DdgoColorTokens.SurfaceTint,
            DdgoColorTokens.Background
        )
    )

    val HeroGradient = DdgoBrushTokens.HeroGradient
    val Danger = DdgoColorTokens.Error
    val Success = DdgoColorTokens.Success
    val Warning = DdgoColorTokens.Warning
    val NeutralTransparent = Color.Transparent
}
