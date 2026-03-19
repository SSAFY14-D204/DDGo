package com.ddgo.app.feature.analysis.style

import androidx.compose.ui.graphics.Color

/**
 * 분석 화면 전용 팔레트입니다.
 *
 * 역할:
 * - 캘린더/프로필 탭과 동일한 제품 톤을 유지합니다.
 * - 성공/실패/주의/중립 상태를 카드와 배지에서 일관되게 표현합니다.
 */
internal object AnalysisPalette {
    val Accent = Color(0xFF4C9BFF)
    val AccentStrong = Color(0xFF256FE8)
    val AccentSoft = Color(0xFFE9F3FF)

    val Success = Color(0xFF1E9B65)
    val SuccessSoft = Color(0xFFE6F7F0)
    val Danger = Color(0xFFD64242)
    val DangerSoft = Color(0xFFFFE9E9)
    val Warning = Color(0xFFDA8A17)
    val WarningBright = Color(0xFFFFA033)
    val WarningSoft = Color(0xFFFFF4DF)

    val BackgroundTop = Color(0xFFF7FBFF)
    val BackgroundBottom = Color(0xFFEAF3FF)
    val Surface = Color.White
    val SurfaceMuted = Color(0xFFF5F8FD)
    val SurfaceSelected = Color(0xFFF0F6FF)
    val Border = Color(0xFFD7E5F5)
    val Divider = Color(0xFFE7EEF8)

    val HeroStart = Color(0xFF3C86FF)
    val HeroEnd = Color(0xFF6AB4FF)

    val TextPrimary = Color(0xFF1D2530)
    val TextSecondary = Color(0xFF697A8B)
    val TextHint = Color(0xFF90A0AF)
    val OnAccent = Color.White
}
