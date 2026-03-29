package com.ddgo.app.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

private val DarkColorScheme = darkColorScheme(
    primary = DdgoColorTokens.BrandBlue,
    onPrimary = DdgoColorTokens.TextInverse,
    secondary = DdgoColorTokens.BrandGradientEnd,
    onSecondary = DdgoColorTokens.TextInverse,
    tertiary = DdgoColorTokens.BrandGradientStart,
    onTertiary = DdgoColorTokens.TextInverse,
    background = DdgoColorTokens.DarkBackground,
    onBackground = DdgoColorTokens.DarkTextPrimary,
    surface = DdgoColorTokens.DarkSurface,
    onSurface = DdgoColorTokens.DarkTextPrimary,
    surfaceVariant = DdgoColorTokens.DarkSurfaceMuted,
    onSurfaceVariant = DdgoColorTokens.DarkTextSecondary,
    outline = DdgoColorTokens.DarkBorder,
    error = DdgoColorTokens.Error,
    onError = DdgoColorTokens.TextInverse
)

private val LightColorScheme = lightColorScheme(
    primary = DdgoColorTokens.BrandBlue,
    onPrimary = DdgoColorTokens.TextInverse,
    secondary = DdgoColorTokens.BrandBlueStrong,
    onSecondary = DdgoColorTokens.TextInverse,
    tertiary = DdgoColorTokens.BrandGradientStart,
    onTertiary = DdgoColorTokens.TextInverse,
    background = DdgoColorTokens.Background,
    onBackground = DdgoColorTokens.TextPrimary,
    surface = DdgoColorTokens.Surface,
    onSurface = DdgoColorTokens.TextPrimary,
    surfaceVariant = DdgoColorTokens.SurfaceMuted,
    onSurfaceVariant = DdgoColorTokens.TextSecondary,
    outline = DdgoColorTokens.Border,
    error = DdgoColorTokens.Error,
    onError = DdgoColorTokens.TextInverse
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
