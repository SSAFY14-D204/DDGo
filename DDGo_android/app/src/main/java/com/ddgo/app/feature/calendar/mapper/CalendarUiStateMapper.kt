package com.ddgo.app.feature.calendar.mapper

import com.ddgo.app.domain.model.CalendarChallengeResult
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarDisplayMode
import com.ddgo.app.feature.calendar.model.CalendarMarkerStyle
import com.ddgo.app.feature.calendar.model.CalendarMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarUiState
import java.time.LocalDate
import java.time.YearMonth

internal object CalendarUiStateMapper {

    fun createCalendarUiState(
        today: LocalDate,
        currentMonth: YearMonth,
        selectedDate: LocalDate,
        availableMonths: List<YearMonth>,
        displayMode: CalendarDisplayMode,
        entries: List<CalendarEntry>,
        isLoading: Boolean = false,
        errorMessage: String? = null
    ): CalendarUiState {
        val entriesByDate = entries
            .sortedByDescending { it.startedAt ?: it.createdAt }
            .groupBy { it.date }

        val monthEntries = entries.filter { YearMonth.from(it.date) == currentMonth }

        return CalendarUiState(
            today = today,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            availableMonths = availableMonths,
            displayMode = displayMode,
            headerSolvedCount = monthEntries.count { it.challengeResult == CalendarChallengeResult.SUCCESS },
            entries = entries,
            weeks = buildCalendarWeeks(
                currentMonth = currentMonth,
                today = today,
                selectedDate = selectedDate,
                entriesByDate = entriesByDate,
                displayMode = displayMode
            ),
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    fun buildAvailableMonths(
        entries: List<CalendarEntry>,
        fallbackMonth: YearMonth
    ): List<YearMonth> {
        return (entries.map { YearMonth.from(it.date) } + fallbackMonth)
            .distinct()
            .sortedDescending()
    }

    fun resolveSelectedDateForMonth(
        currentSelection: LocalDate,
        targetMonth: YearMonth
    ): LocalDate {
        val targetDay = currentSelection.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth())
        return targetMonth.atDay(targetDay)
    }

    fun buildMonthWeeks(
        currentMonth: YearMonth,
        today: LocalDate,
        selectedDate: LocalDate,
        entries: List<CalendarEntry>,
        displayMode: CalendarDisplayMode
    ): List<List<CalendarDayUiModel>> {
        return buildCalendarWeeks(
            currentMonth = currentMonth,
            today = today,
            selectedDate = selectedDate,
            entriesByDate = entries
                .sortedByDescending { it.startedAt ?: it.createdAt }
                .groupBy { it.date },
            displayMode = displayMode
        )
    }

    private fun buildCalendarWeeks(
        currentMonth: YearMonth,
        today: LocalDate,
        selectedDate: LocalDate,
        entriesByDate: Map<LocalDate, List<CalendarEntry>>,
        displayMode: CalendarDisplayMode
    ): List<List<CalendarDayUiModel>> {
        val firstDay = currentMonth.atDay(1)
        val leadingDays = firstDay.dayOfWeek.value % 7
        val gridStart = firstDay.minusDays(leadingDays.toLong())

        return List(42) { index ->
            val date = gridStart.plusDays(index.toLong())
            val entries = entriesByDate[date].orEmpty()
            val visibleEntries = entries.take(MAX_VISIBLE_MARKERS)

            CalendarDayUiModel(
                date = date,
                isInCurrentMonth = YearMonth.from(date) == currentMonth,
                isSelected = date == selectedDate,
                isToday = date == today,
                markers = visibleEntries.map { entry ->
                    when (displayMode) {
                        CalendarDisplayMode.COLOR -> entry.toDifficultyMarker()
                        CalendarDisplayMode.GYM -> entry.toGymMarker()
                    }
                },
                overflowCount = (entries.size - MAX_VISIBLE_MARKERS).coerceAtLeast(0)
            )
        }.chunked(DAYS_IN_WEEK)
    }

    private fun CalendarEntry.toDifficultyMarker(): CalendarMarkerUiModel {
        return CalendarMarkerUiModel(
            style = CalendarMarkerStyle.DIFFICULTY,
            colorHex = difficultyColorHex,
            isSolved = challengeResult == CalendarChallengeResult.SUCCESS
        )
    }

    private fun CalendarEntry.toGymMarker(): CalendarMarkerUiModel {
        return CalendarMarkerUiModel(
            style = CalendarMarkerStyle.GYM,
            logoUrl = gymLogoUrl ?: brandLogoUrl,
            fallbackLabel = gymName.take(1).ifBlank { "G" }
        )
    }

    private const val DAYS_IN_WEEK = 7
    private const val MAX_VISIBLE_MARKERS = 4
}
