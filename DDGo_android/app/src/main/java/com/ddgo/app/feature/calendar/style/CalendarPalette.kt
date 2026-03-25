package com.ddgo.app.feature.calendar.style

import androidx.compose.ui.graphics.Color
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
    val GymMarkerBackground = Color(0xFF999999)
    val GymMarkerText = Color.White

    private val MarkerRed = Color(0xFFFF0000)
    private val MarkerOrange = Color(0xFFFF9F43)
    private val MarkerYellow = Color(0xFFFED500)
    private val MarkerGreen = Color(0xFF96FF6F)
    private val MarkerBlue = Color(0xFF4396FB)
    private val MarkerNavy = Color(0xFF4B5BD7)
    private val MarkerPurple = Color(0xFF876FFF)
    private val MarkerPink = Color(0xFFFF56A8)
    private val MarkerBrown = Color(0xFF9A6B45)
    private val MarkerGray = Color(0xFF999999)
    private val MarkerBlack = Color(0xFF505050)
    private val MarkerWhite = Color(0xFFE9EDF2)

    fun markerToneColor(tone: CalendarMarkerToneUiModel): Color {
        return when (tone) {
            CalendarMarkerToneUiModel.RED -> MarkerRed
            CalendarMarkerToneUiModel.ORANGE -> MarkerOrange
            CalendarMarkerToneUiModel.YELLOW -> MarkerYellow
            CalendarMarkerToneUiModel.GREEN -> MarkerGreen
            CalendarMarkerToneUiModel.BLUE -> MarkerBlue
            CalendarMarkerToneUiModel.NAVY -> MarkerNavy
            CalendarMarkerToneUiModel.PURPLE -> MarkerPurple
            CalendarMarkerToneUiModel.PINK -> MarkerPink
            CalendarMarkerToneUiModel.BROWN -> MarkerBrown
            CalendarMarkerToneUiModel.GRAY -> MarkerGray
            CalendarMarkerToneUiModel.BLACK -> MarkerBlack
            CalendarMarkerToneUiModel.WHITE -> MarkerWhite
            CalendarMarkerToneUiModel.UNKNOWN -> MarkerGray
        }
    }
}
