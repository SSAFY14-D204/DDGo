package com.ddgo.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun SafeAreaScreen(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    applyTopInset: Boolean = true,
    applyBottomInset: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var resolvedModifier = modifier.fillMaxSize()

    if (applyTopInset) {
        resolvedModifier = resolvedModifier.statusBarsPadding()
    }

    if (applyBottomInset) {
        resolvedModifier = resolvedModifier.navigationBarsPadding()
    }

    Box(
        modifier = resolvedModifier.background(containerColor),
        content = content
    )
}
