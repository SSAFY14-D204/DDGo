package com.ddgo.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class ScreenTopInsetMode {
    StatusBar,
    None
}

enum class ScreenBottomInsetMode {
    NavigationBar,
    ImeAware,
    None
}

enum class ScreenContentMode {
    Scrollable,
    Fixed,
    FullBleed
}

@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
    topInsetMode: ScreenTopInsetMode = ScreenTopInsetMode.StatusBar,
    bottomInsetMode: ScreenBottomInsetMode = ScreenBottomInsetMode.NavigationBar,
    contentMode: ScreenContentMode = ScreenContentMode.Fixed,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    topBar: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    snackbar: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomActionHeightPx by remember { mutableIntStateOf(0) }

    val topInset = rememberInsetDp(
        density = density,
        mode = topInsetMode
    )
    val bottomInset = rememberInsetDp(
        density = density,
        mode = bottomInsetMode
    )

    val layoutPadding = when (contentMode) {
        ScreenContentMode.FullBleed -> PaddingValues(0.dp)
        ScreenContentMode.Scrollable,
        ScreenContentMode.Fixed -> PaddingValues(
            top = topInset + if (topBar == null) 0.dp else with(density) { topBarHeightPx.toDp() },
            bottom = bottomInset + if (bottomAction == null) 0.dp else with(density) { bottomActionHeightPx.toDp() }
        )
    }
    val resolvedPadding = remember(layoutPadding, contentPadding) {
        CombinedPaddingValues(layoutPadding, contentPadding)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor)
    ) {
        content(resolvedPadding)

        topBar?.let { topContent ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeAreaTop(topInset)
            ) {
                Box(
                    modifier = Modifier.onSizeChanged { topBarHeightPx = it.height }
                ) {
                    topContent()
                }
            }
        }

        bottomAction?.let { bottomContent ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeAreaBottom(bottomInset)
            ) {
                Box(
                    modifier = Modifier.onSizeChanged { bottomActionHeightPx = it.height }
                ) {
                    bottomContent()
                }
            }
        }

        snackbar?.let { snackbarContent ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeAreaBottom(
                        bottomInset +
                            if (bottomAction == null) 16.dp else with(density) { bottomActionHeightPx.toDp() + 16.dp }
                    ),
                content = snackbarContent
            )
        }
    }
}

@Composable
private fun rememberInsetDp(
    density: Density,
    mode: ScreenTopInsetMode
): Dp = with(density) {
    when (mode) {
        ScreenTopInsetMode.StatusBar -> WindowInsets.statusBars.getTop(this).toDp()
        ScreenTopInsetMode.None -> 0.dp
    }
}

@Composable
private fun rememberInsetDp(
    density: Density,
    mode: ScreenBottomInsetMode
): Dp = with(density) {
    when (mode) {
        ScreenBottomInsetMode.NavigationBar -> WindowInsets.navigationBars.getBottom(this).toDp()
        ScreenBottomInsetMode.ImeAware -> {
            val bottomInsetPx = maxOf(
                WindowInsets.navigationBars.getBottom(this),
                WindowInsets.ime.getBottom(this)
            )
            bottomInsetPx.toDp()
        }

        ScreenBottomInsetMode.None -> 0.dp
    }
}

private data class CombinedPaddingValues(
    private val first: PaddingValues,
    private val second: PaddingValues
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp {
        return first.calculateLeftPadding(layoutDirection) + second.calculateLeftPadding(layoutDirection)
    }

    override fun calculateTopPadding(): Dp {
        return first.calculateTopPadding() + second.calculateTopPadding()
    }

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp {
        return first.calculateRightPadding(layoutDirection) + second.calculateRightPadding(layoutDirection)
    }

    override fun calculateBottomPadding(): Dp {
        return first.calculateBottomPadding() + second.calculateBottomPadding()
    }
}

private fun Modifier.safeAreaTop(topInset: Dp): Modifier {
    return if (topInset == 0.dp) this else this.padding(top = topInset)
}

private fun Modifier.safeAreaBottom(bottomInset: Dp): Modifier {
    return if (bottomInset == 0.dp) this else this.padding(bottom = bottomInset)
}
