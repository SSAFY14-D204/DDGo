package com.ddgo.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    secondary = Secondary80,
    tertiary = Tertiary80,
    background = NeutralBackground,
    surface = NeutralSurface,
    outline = NeutralOutline,
    error = Error
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    secondary = Secondary40,
    tertiary = Tertiary40,
    error = Error
)

/**
 * DDGo 앱 전체에서 사용하는 테마.
 * MainActivity에서 최상위에 감싸서 사용합니다.
 *
 * 사용법:
 *   DDGoTheme {
 *       // 여기 안에 있는 모든 Composable은 DDGo 테마가 적용됩니다.
 *   }
 */
@Composable
fun DDGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DDGoTypography,
        content = content
    )
}
