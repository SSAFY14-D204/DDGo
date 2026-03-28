package com.ddgo.app.feature.profile.style

import androidx.compose.ui.graphics.Color
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

/**
 * 프로필 화면 전용 색상 팔레트입니다.
 *
 * 역할:
 * - 캘린더 화면과 같은 제품 톤을 유지하도록 같은 계열의 배경/포인트 색상을 사용합니다.
 * - 프로필만의 위험 액션 색상은 유지하되 기본 표면과 타이포 리듬은 캘린더와 맞춥니다.
 */
internal object ProfilePalette {
    val Accent = Color(0xFF42A5F5)
    val AccentStrong = Color(0xFF1E88E5)
    val AccentSoft = Color(0xFFE8F3FF)

    val BackgroundTop = Color.White
    val BackgroundBottom = Color.White
    val Surface = Color.White
    val SurfaceMuted = Color(0xFFF4F8FD)
    val Divider = Color(0xFFD7E5F5)
    val Border = Color(0xFFD7E5F5)

    val HeroStart = DdgoColorTokens.BrandBlue
    val HeroEnd = DdgoColorTokens.BrandBlueStrong

    val TextPrimary = Color(0xFF1E232C)
    val TextSecondary = Color(0xFF6C7B8A)
    val TextHint = Color(0xFFA0ACBA)
    val OnAccent = Color.White

    val Danger = Color(0xFFD84A4A)
    val DangerSoft = Color(0xFFFFEEEE)
}
