package com.ddgo.app.core.ui.theme

import com.ddgo.app.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 앱 전체에서 사용하는 폰트 패밀리
// 실제 사용 시: res/font 폴더에 폰트 파일을 추가하고 Font() 참조를 변경하세요
val DefaultFontFamily = FontFamily.Default

val DDGoTypography = Typography(
    // 화면 타이틀
    headlineLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    // 섹션 타이틀
    titleLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // 본문
    bodyLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // 라벨 (버튼, 배지 등)
    labelLarge = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DefaultFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

val PretendardFamily = FontFamily(
    Font(R.font.pretendard_thin, FontWeight.Thin),              // 100
    Font(R.font.pretendard_extra_light, FontWeight.ExtraLight), // 200
    Font(R.font.pretendard_light, FontWeight.Light),            // 300
    Font(R.font.pretendard_regular, FontWeight.Normal),         // 400 (기본)
    Font(R.font.pretendard_medium, FontWeight.Medium),          // 500
    Font(R.font.pretendard_semi_bold, FontWeight.SemiBold),     // 600
    Font(R.font.pretendard_bold, FontWeight.Bold),              // 700
    Font(R.font.pretendard_extra_bold, FontWeight.ExtraBold),   // 800
    Font(R.font.pretendard_black, FontWeight.Black)             // 900
)