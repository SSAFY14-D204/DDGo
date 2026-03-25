package com.ddgo.app.feature.calendar

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarEntryResult
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerRenderStyleUiModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarUiStateMapperTest {

    @Test
    fun `createCalendarUiState trims unnecessary sixth week`() {
        val yearMonth = YearMonth.of(2026, 3)

        val state = CalendarUiStateMapper.createCalendarUiState(
            today = LocalDate.of(2026, 3, 2),
            currentMonth = yearMonth,
            selectedDate = LocalDate.of(2026, 3, 2),
            entries = emptyList(),
            summary = CalendarMonthSummary()
        )

        assertEquals(5, state.weeks.size)
        assertEquals(35, state.weeks.flatten().size)
        assertEquals(LocalDate.of(2026, 3, 1), state.weeks.first().first().date)
        assertEquals(LocalDate.of(2026, 4, 4), state.weeks.last().last().date)
    }

    @Test
    fun `createCalendarUiState keeps six weeks when month spans six rows`() {
        val yearMonth = YearMonth.of(2026, 8)

        val state = CalendarUiStateMapper.createCalendarUiState(
            today = LocalDate.of(2026, 8, 1),
            currentMonth = yearMonth,
            selectedDate = LocalDate.of(2026, 8, 1),
            entries = emptyList(),
            summary = CalendarMonthSummary()
        )

        assertEquals(6, state.weeks.size)
        assertEquals(42, state.weeks.flatten().size)
        assertEquals(LocalDate.of(2026, 7, 26), state.weeks.first().first().date)
        assertEquals(LocalDate.of(2026, 9, 5), state.weeks.last().last().date)
    }

    @Test
    fun `color filter keeps per-entry markers ordered by time and result`() {
        val targetDate = LocalDate.of(2026, 3, 2)

        val state = CalendarUiStateMapper.createCalendarUiState(
            today = targetDate,
            currentMonth = YearMonth.from(targetDate),
            selectedDate = targetDate,
            entries = listOf(
                entry(
                    id = 1L,
                    date = targetDate,
                    venue = "더클라임 강남",
                    problemColor = "보라",
                    time = LocalTime.of(18, 0),
                    result = CalendarEntryResult.SUCCESS
                ),
                entry(
                    id = 2L,
                    date = targetDate,
                    venue = "더클라임 강남",
                    problemColor = "분홍",
                    time = LocalTime.of(20, 0),
                    result = CalendarEntryResult.FAIL
                ),
                entry(
                    id = 3L,
                    date = targetDate,
                    venue = "서울숲 클라이밍",
                    problemColor = "노랑",
                    time = LocalTime.of(19, 0),
                    result = CalendarEntryResult.SUCCESS
                ),
                entry(
                    id = 4L,
                    date = targetDate,
                    venue = "서울숲 클라이밍",
                    problemColor = "빨강",
                    time = LocalTime.of(17, 0),
                    result = CalendarEntryResult.FAIL
                )
            ),
            summary = CalendarMonthSummary(totalSessions = 4),
            activeMarkerFilter = CalendarMarkerFilterUiModel.COLOR
        )

        val day = state.weeks.flatten().first { it.date == targetDate }
        assertEquals(listOf("분홍", "노랑", "보라", "빨강"), day.colorMarkers.map { it.label })
        assertEquals(
            listOf(
                CalendarMarkerRenderStyleUiModel.OUTLINED,
                CalendarMarkerRenderStyleUiModel.FILLED,
                CalendarMarkerRenderStyleUiModel.FILLED,
                CalendarMarkerRenderStyleUiModel.OUTLINED
            ),
            day.colorMarkers.map { it.renderStyle }
        )
    }

    @Test
    fun `gym filter builds abbreviated gym markers and sorts selected entries by venue`() {
        val targetDate = LocalDate.of(2026, 3, 2)

        val state = CalendarUiStateMapper.createCalendarUiState(
            today = targetDate,
            currentMonth = YearMonth.from(targetDate),
            selectedDate = targetDate,
            entries = listOf(
                entry(1L, targetDate, "서울숲 클라이밍", "노랑", time = LocalTime.of(18, 0)),
                entry(2L, targetDate, "더클라임 강남", "보라", time = LocalTime.of(19, 0))
            ),
            summary = CalendarMonthSummary(totalSessions = 2),
            activeMarkerFilter = CalendarMarkerFilterUiModel.GYM
        )

        val day = state.weeks.flatten().first { it.date == targetDate }
        assertEquals(listOf("서울", "강남"), day.gymMarkers.map { it.label })
        assertEquals(listOf("더클라임 강남", "서울숲 클라이밍"), state.selectedEntries.map { it.venueLabel })
        assertTrue(day.gymMarkers.isNotEmpty())
    }

    @Test
    fun `color filter sorts selected entries by color venue and time`() {
        val targetDate = LocalDate.of(2026, 3, 2)

        val state = CalendarUiStateMapper.createCalendarUiState(
            today = targetDate,
            currentMonth = YearMonth.from(targetDate),
            selectedDate = targetDate,
            entries = listOf(
                entry(1L, targetDate, "서울숲 클라이밍", "노랑", time = LocalTime.of(19, 0)),
                entry(2L, targetDate, "더클라임 강남", "보라", time = LocalTime.of(18, 0)),
                entry(3L, targetDate, "더클라임 강남", "보라", time = LocalTime.of(20, 0))
            ),
            summary = CalendarMonthSummary(totalSessions = 3),
            activeMarkerFilter = CalendarMarkerFilterUiModel.COLOR
        )

        assertEquals(
            listOf(
                "노랑 / V3",
                "보라 / V3",
                "보라 / V3"
            ),
            state.selectedEntries.map { it.title }
        )
        assertEquals(
            listOf(
                "19:00",
                "20:00",
                "18:00"
            ),
            state.selectedEntries.map { it.timeLabel }
        )
    }

    private fun entry(
        id: Long,
        date: LocalDate,
        venue: String,
        problemColor: String,
        time: LocalTime = LocalTime.of(17, 0),
        result: CalendarEntryResult = CalendarEntryResult.SUCCESS
    ): CalendarEntry {
        return CalendarEntry(
            id = id,
            date = date,
            title = "$problemColor / V3",
            problemColor = problemColor,
            result = result,
            venue = venue,
            time = time,
            note = if (result == CalendarEntryResult.SUCCESS) "완등" else "미완등"
        )
    }
}
