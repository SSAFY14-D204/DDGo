package com.ddgo.app.feature.community

import androidx.compose.ui.graphics.Brush
import com.ddgo.app.feature.analysis.style.AnalysisPalette
import com.ddgo.app.feature.calendar.style.CalendarPalette

internal object CommunityPalette {
    val Accent = CalendarPalette.Accent
    val AccentStrong = CalendarPalette.AccentStrong
    val AccentSoft = CalendarPalette.AccentSoft

    val BackgroundTop = CalendarPalette.BackgroundTop
    val BackgroundBottom = CalendarPalette.BackgroundBottom
    val Surface = CalendarPalette.Surface
    val SurfaceMuted = CalendarPalette.SurfaceMuted
    val Border = CalendarPalette.Border

    val TextPrimary = CalendarPalette.TextPrimary
    val TextSecondary = CalendarPalette.TextSecondary
    val TextHint = AnalysisPalette.TextHint
    val OnAccent = CalendarPalette.OnAccent

    val Success = AnalysisPalette.Success
    val SuccessSoft = AnalysisPalette.SuccessSoft
    val Warning = AnalysisPalette.Warning
    val WarningSoft = AnalysisPalette.WarningSoft
    val Danger = AnalysisPalette.Danger
    val DangerSoft = AnalysisPalette.DangerSoft

    val PageGradient = Brush.verticalGradient(
        colors = listOf(
            BackgroundTop,
            BackgroundBottom,
            BackgroundTop
        )
    )

    val HeroGradient = Brush.horizontalGradient(
        colors = listOf(
            Accent,
            AccentStrong
        )
    )
}
