package com.ddgo.app.core.ui.tokens

import androidx.compose.ui.graphics.Brush

object DdgoBrushTokens {
    val PrimaryButtonGradient = Brush.horizontalGradient(
        colors = listOf(
            DdgoColorTokens.BrandGradientEnd,
            DdgoColorTokens.BrandGradientStart
        )
    )

    val HeroGradient = PrimaryButtonGradient
}
