package com.ddgo.app.feature.calendar.style

import androidx.compose.ui.graphics.Color
import com.ddgo.app.core.ui.tokens.DdgoHoldColorTokens
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel

// 피그마 캘린더 시안을 기준으로 캘린더 화면 전용 토큰을 모아둔다.
internal object CalendarPalette {
    val Accent = Color(0xFF4396FB)
    val AccentStrong = Color(0xFF4396FB)
    val AccentSoft = Color(0xFFF1F7FF)

    val BackgroundTop = Color(0xFFFFFFFF)
    val BackgroundBottom = Color(0xFFFFFFFF)
    val Surface = Color.White
    val SurfaceMuted = Color(0xFFF4F8FD)
    val Border = Color(0xFFD7E5F5)

    val TextPrimary = Color(0xFF1E232C)
    val TextSecondary = Color(0xFF505050)
    val OnAccent = Color.White
    val HeroBackground = Color(0xFF0B0B0E)

    val MonthSurface = Color.White
    val MonthBorder = Color(0xFF999999)
    val MonthShadow = Color(0x1A6A707C)
    val MonthSelectorText = Color(0xFF1E232C)
    val MonthSelectorChevron = Color(0xFF505050)
    val MonthMenuSurface = Color.White
    val MonthMenuBorder = Color(0xFFE6EBF2)
    val MonthMenuText = Color(0xFF1E232C)
    val MonthMenuSelectedBackground = Color(0xFFF1F7FF)
    val MonthMenuSelectedText = Color(0xFF4396FB)
    val ToggleShadow = Color(0x1A6A707C)
    val ToggleTrackBorder = Color(0xFF999999)
    val ToggleTrackBackground = Color.White
    val ToggleActive = Color(0xFF4396FB)
    val ToggleInactiveText = Color(0xFF505050)
    val ToggleActiveText = Color.White
    val WeekdayText = Color(0xFF0B0B0E)
    val DayCellText = Color(0xFF222B45)
    val DayCellTextMuted = Color(0xFF8F9BB3)
    val DayCellSelected = Color(0xFF4396FB)
    val DayCellSelectedText = Color.White
    val DayPlaceholder = Color(0xFF999999)
    val DayPlaceholderMuted = Color(0xFF8F9BB3).copy(alpha = 0.25f)
    val GymMarkerBackground = Color(0xFF999999)
    val GymMarkerText = Color.White
    val MarkerOutlineFill = Color.White
    val MarkerOverflowDot = Color(0xFFFF5656)

    fun markerToneColor(tone: CalendarMarkerToneUiModel): Color {
        return when (tone) {
            CalendarMarkerToneUiModel.RED -> DdgoHoldColorTokens.Red.color
            CalendarMarkerToneUiModel.ORANGE -> DdgoHoldColorTokens.Orange.color
            CalendarMarkerToneUiModel.YELLOW -> DdgoHoldColorTokens.Yellow.color
            CalendarMarkerToneUiModel.GREEN -> DdgoHoldColorTokens.Green.color
            CalendarMarkerToneUiModel.BLUE -> DdgoHoldColorTokens.SkyBlue.color
            CalendarMarkerToneUiModel.NAVY -> DdgoHoldColorTokens.Navy.color
            CalendarMarkerToneUiModel.PURPLE -> DdgoHoldColorTokens.Purple.color
            CalendarMarkerToneUiModel.PINK -> DdgoHoldColorTokens.Pink.color
            CalendarMarkerToneUiModel.BROWN -> DdgoHoldColorTokens.Brown.color
            CalendarMarkerToneUiModel.GRAY -> DdgoHoldColorTokens.Gray.color
            CalendarMarkerToneUiModel.BLACK -> DdgoHoldColorTokens.Black.color
            CalendarMarkerToneUiModel.WHITE -> DdgoHoldColorTokens.White.color
            CalendarMarkerToneUiModel.UNKNOWN -> DdgoHoldColorTokens.Gray.color
        }
    }
}
