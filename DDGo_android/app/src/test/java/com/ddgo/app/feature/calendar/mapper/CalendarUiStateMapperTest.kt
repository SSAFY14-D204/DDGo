package com.ddgo.app.feature.calendar.mapper

import com.ddgo.app.domain.model.CalendarChallengeResult
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.feature.calendar.model.CalendarDisplayMode
import com.ddgo.app.feature.calendar.model.CalendarMarkerStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarUiStateMapperTest {

    @Test
    fun `builds a sunday first six week grid`() {
        val state = CalendarUiStateMapper.createCalendarUiState(
            today = LocalDate.of(2026, 4, 15),
            currentMonth = YearMonth.of(2026, 4),
            selectedDate = LocalDate.of(2026, 4, 15),
            availableMonths = listOf(YearMonth.of(2026, 4)),
            displayMode = CalendarDisplayMode.COLOR,
            entries = emptyList()
        )

        assertEquals(LocalDate.of(2026, 3, 29), state.weeks.first().first().date)
        assertEquals(LocalDate.of(2026, 5, 9), state.weeks.last().last().date)
        assertEquals(6, state.weeks.size)
        assertEquals(7, state.weeks.first().size)
    }

    @Test
    fun `counts monthly successes and maps color markers`() {
        val entries = listOf(
            calendarEntry(id = 1, dateTime = LocalDateTime.of(2026, 3, 2, 10, 0), colorHex = "#FF56A8", result = CalendarChallengeResult.SUCCESS),
            calendarEntry(id = 2, dateTime = LocalDateTime.of(2026, 3, 2, 11, 0), colorHex = "#876FFF", result = CalendarChallengeResult.FAIL),
            calendarEntry(id = 3, dateTime = LocalDateTime.of(2026, 3, 5, 9, 0), colorHex = "#65B969", result = CalendarChallengeResult.SUCCESS)
        )

        val state = CalendarUiStateMapper.createCalendarUiState(
            today = LocalDate.of(2026, 3, 2),
            currentMonth = YearMonth.of(2026, 3),
            selectedDate = LocalDate.of(2026, 3, 2),
            availableMonths = listOf(YearMonth.of(2026, 3)),
            displayMode = CalendarDisplayMode.COLOR,
            entries = entries
        )

        val marchSecond = state.weeks.flatten().first { it.date == LocalDate.of(2026, 3, 2) }

        assertEquals(2, state.headerSolvedCount)
        assertEquals(2, marchSecond.markers.size)
        assertEquals(CalendarMarkerStyle.DIFFICULTY, marchSecond.markers.first().style)
        assertTrue(marchSecond.markers.any { it.isSolved })
        assertTrue(marchSecond.markers.any { !it.isSolved })
    }

    @Test
    fun `limits markers to four and exposes overflow count`() {
        val entries = (1L..5L).map { index ->
            calendarEntry(
                id = index,
                dateTime = LocalDateTime.of(2026, 3, 9, 8 + index.toInt(), 0),
                colorHex = "#4396FB",
                result = CalendarChallengeResult.SUCCESS
            )
        }

        val weeks = CalendarUiStateMapper.buildMonthWeeks(
            currentMonth = YearMonth.of(2026, 3),
            today = LocalDate.of(2026, 3, 9),
            selectedDate = LocalDate.of(2026, 3, 9),
            entries = entries,
            displayMode = CalendarDisplayMode.COLOR
        )

        val marchNinth = weeks.flatten().first { it.date == LocalDate.of(2026, 3, 9) }

        assertEquals(4, marchNinth.markers.size)
        assertEquals(1, marchNinth.overflowCount)
    }

    @Test
    fun `prefers gym logo then brand logo in gym mode`() {
        val entries = listOf(
            calendarEntry(
                id = 1,
                dateTime = LocalDateTime.of(2026, 3, 12, 20, 0),
                colorHex = "#FED500",
                result = CalendarChallengeResult.SUCCESS,
                gymLogoUrl = null,
                brandLogoUrl = "https://example.com/brand.png"
            )
        )

        val weeks = CalendarUiStateMapper.buildMonthWeeks(
            currentMonth = YearMonth.of(2026, 3),
            today = LocalDate.of(2026, 3, 12),
            selectedDate = LocalDate.of(2026, 3, 12),
            entries = entries,
            displayMode = CalendarDisplayMode.GYM
        )

        val marker = weeks.flatten()
            .first { it.date == LocalDate.of(2026, 3, 12) }
            .markers
            .single()

        assertEquals(CalendarMarkerStyle.GYM, marker.style)
        assertEquals("https://example.com/brand.png", marker.logoUrl)
    }

    @Test
    fun `clamps selected day when target month is shorter`() {
        val selectedDate = CalendarUiStateMapper.resolveSelectedDateForMonth(
            currentSelection = LocalDate.of(2026, 3, 31),
            targetMonth = YearMonth.of(2026, 4)
        )

        assertEquals(LocalDate.of(2026, 4, 30), selectedDate)
    }

    private fun calendarEntry(
        id: Long,
        dateTime: LocalDateTime,
        colorHex: String,
        result: CalendarChallengeResult,
        gymLogoUrl: String? = "https://example.com/gym.png",
        brandLogoUrl: String? = null
    ): CalendarEntry {
        return CalendarEntry(
            id = id,
            date = dateTime.toLocalDate(),
            startedAt = dateTime,
            endedAt = null,
            createdAt = dateTime.minusMinutes(5),
            gymId = 1L,
            gymGradeId = 1L,
            gymName = "더클라임",
            problemColor = "보라",
            difficultyLabel = "V4",
            difficultyColorHex = colorHex,
            challengeStatus = "CLOSED",
            challengeResult = result,
            gymLogoUrl = gymLogoUrl,
            brandLogoUrl = brandLogoUrl
        )
    }
}
