package com.ddgo.app.feature.calendar

import com.ddgo.app.core.ui.tokens.DdgoHoldColorTokens
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarPaletteTest {

    @Test
    fun `marker tone colors reuse shared hold difficulty tokens`() {
        assertEquals(DdgoHoldColorTokens.Red.color, CalendarPalette.markerToneColor(CalendarMarkerToneUiModel.RED))
        assertEquals(DdgoHoldColorTokens.SkyBlue.color, CalendarPalette.markerToneColor(CalendarMarkerToneUiModel.BLUE))
        assertEquals(DdgoHoldColorTokens.Navy.color, CalendarPalette.markerToneColor(CalendarMarkerToneUiModel.NAVY))
        assertEquals(DdgoHoldColorTokens.White.color, CalendarPalette.markerToneColor(CalendarMarkerToneUiModel.WHITE))
    }

    @Test
    fun `calendar placeholders use figma light gray tones`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF0F3F5), CalendarPalette.DayPlaceholder)
        assertEquals(CalendarPalette.DayPlaceholder.copy(alpha = 0.25f), CalendarPalette.DayPlaceholderMuted)
    }
}
