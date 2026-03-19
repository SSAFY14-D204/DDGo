package com.ddgo.app.feature.calendar.model

import com.ddgo.app.domain.model.CalendarEntry
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarDisplayMode {
    COLOR,
    GYM
}

data class CalendarUiState(
    val today: LocalDate,
    val currentMonth: YearMonth,
    val selectedDate: LocalDate,
    val availableMonths: List<YearMonth>,
    val displayMode: CalendarDisplayMode,
    val headerSolvedCount: Int,
    val entries: List<CalendarEntry>,
    val weeks: List<List<CalendarDayUiModel>>,
    val isLoading: Boolean,
    val errorMessage: String?
)

data class CalendarDayUiModel(
    val date: LocalDate,
    val isInCurrentMonth: Boolean,
    val isSelected: Boolean,
    val isToday: Boolean,
    val markers: List<CalendarMarkerUiModel>,
    val overflowCount: Int
)

data class CalendarMarkerUiModel(
    val style: CalendarMarkerStyle,
    val colorHex: String? = null,
    val isSolved: Boolean = true,
    val logoUrl: String? = null,
    val fallbackLabel: String = ""
)

enum class CalendarMarkerStyle {
    DIFFICULTY,
    GYM
}
