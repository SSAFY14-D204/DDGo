package com.ddgo.app.feature.calendar.mapper

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarEntryUiModel
import com.ddgo.app.feature.calendar.model.CalendarMonthSummaryUiModel
import com.ddgo.app.feature.calendar.model.CalendarUiState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object CalendarUiStateMapper {

    private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    // 도메인 데이터를 화면 전용 상태로 바꾸고 달력 그리드까지 함께 만든다.
    fun createCalendarUiState(
        today: LocalDate,
        currentMonth: YearMonth,
        selectedDate: LocalDate,
        entries: List<CalendarEntry>,
        summary: CalendarMonthSummary,
        isLoading: Boolean = false,
        errorMessage: String? = null
    ): CalendarUiState {
        val entriesByDate = entries.groupBy { it.date }

        return CalendarUiState(
            today = today,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            weeks = buildCalendarWeeks(currentMonth, entriesByDate),
            summary = summary.toUiModel(),
            selectedEntries = entriesByDate[selectedDate].orEmpty().map { it.toUiModel() },
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    private fun CalendarMonthSummary.toUiModel(): CalendarMonthSummaryUiModel {
        return CalendarMonthSummaryUiModel(
            activeDays = activeDays,
            totalSessions = totalSessions,
            longestStreak = longestStreak
        )
    }

    private fun CalendarEntry.toUiModel(): CalendarEntryUiModel {
        return CalendarEntryUiModel(
            challengeId = id,
            title = title,
            secondaryText = listOf(venue, note)
                .filter { it.isNotBlank() }
                .joinToString(" / "),
            timeLabel = time?.format(TimeFormatter).orEmpty()
        )
    }

    // 달력은 6주 고정 그리드로 만들고 이전/다음 달 날짜도 함께 보여준다.
    private fun buildCalendarWeeks(
        currentMonth: YearMonth,
        entriesByDate: Map<LocalDate, List<CalendarEntry>>
    ): List<List<CalendarDayUiModel>> {
        val firstDay = currentMonth.atDay(1)
        val leadingDays = ((firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value) + 7) % 7
        val gridStart = firstDay.minusDays(leadingDays.toLong())

        return List(42) { index ->
            val date = gridStart.plusDays(index.toLong())
            CalendarDayUiModel(
                date = date,
                isInCurrentMonth = YearMonth.from(date) == currentMonth,
                entryCount = entriesByDate[date]?.size ?: 0
            )
        }.chunked(7)
    }
}
