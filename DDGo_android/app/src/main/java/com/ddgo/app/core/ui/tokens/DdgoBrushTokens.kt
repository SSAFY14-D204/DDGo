package com.ddgo.app.core.ui.tokens

import androidx.compose.ui.graphics.Brush

object DdgoBrushTokens {
    val PrimaryButtonGradient = Brush.verticalGradient(
        colors = listOf(
            DdgoColorTokens.BrandGradientStart,
            DdgoColorTokens.BrandGradientEnd
        )
    )

    val HeroGradient = PrimaryButtonGradient
}
