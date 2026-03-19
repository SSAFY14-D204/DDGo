package com.ddgo.app.core.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
fun Modifier.keyboardAwareBottomPadding(
    keyboardOpenPadding: Dp = 16.dp,
    keyboardClosePadding: Dp = 24.dp
): Modifier = composed {
    val density = LocalDensity.current
    val imeVisible = WindowInsets.isImeVisible
    val navigationBottomInset = with(density) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val bottomPadding = if (imeVisible) {
        keyboardOpenPadding
    } else {
        keyboardClosePadding + navigationBottomInset
    }

    this
        .padding(bottom = bottomPadding)
        .imePadding()
}
