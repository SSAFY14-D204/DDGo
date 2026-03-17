package com.ddgo.app.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ddgo.app.R

val PretendardFamily = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),
    Font(R.font.pretendard_extra_light, FontWeight.ExtraLight),
    Font(R.font.pretendard_light, FontWeight.Light),
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semi_bold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extra_bold, FontWeight.ExtraBold),
    Font(R.font.pretendard_black, FontWeight.Black)
)

val DefaultFontFamily = PretendardFamily

private val BaselineTypography = Typography()

private fun TextStyle.withDdgoFont(
    fontWeight: FontWeight? = this.fontWeight,
    fontSize: TextUnit = this.fontSize,
    lineHeight: TextUnit = this.lineHeight,
    letterSpacing: TextUnit = this.letterSpacing
): TextStyle = copy(
    fontFamily = DefaultFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing
)

val DDGoTypography = Typography(
    displayLarge = BaselineTypography.displayLarge.withDdgoFont(),
    displayMedium = BaselineTypography.displayMedium.withDdgoFont(),
    displaySmall = BaselineTypography.displaySmall.withDdgoFont(),
    headlineLarge = BaselineTypography.headlineLarge.withDdgoFont(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = BaselineTypography.headlineMedium.withDdgoFont(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = BaselineTypography.headlineSmall.withDdgoFont(),
    titleLarge = BaselineTypography.titleLarge.withDdgoFont(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = BaselineTypography.titleMedium.withDdgoFont(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = BaselineTypography.titleSmall.withDdgoFont(),
    bodyLarge = BaselineTypography.bodyLarge.withDdgoFont(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = BaselineTypography.bodyMedium.withDdgoFont(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = BaselineTypography.bodySmall.withDdgoFont(),
    labelLarge = BaselineTypography.labelLarge.withDdgoFont(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = BaselineTypography.labelMedium.withDdgoFont(),
    labelSmall = BaselineTypography.labelSmall.withDdgoFont(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)