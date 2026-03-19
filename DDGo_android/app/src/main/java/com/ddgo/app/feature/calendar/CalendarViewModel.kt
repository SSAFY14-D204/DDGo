package com.ddgo.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.usecase.GetCalendarEntriesUseCase
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarDisplayMode
import com.ddgo.app.feature.calendar.model.CalendarUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getCalendarEntriesUseCase: GetCalendarEntriesUseCase
) : ViewModel() {

    private val today = LocalDate.now()
    private val initialMonth = YearMonth.from(today)

    private var allEntries: List<CalendarEntry> = emptyList()
    private var currentMonth: YearMonth = initialMonth
    private var selectedDate: LocalDate = today
    private var displayMode: CalendarDisplayMode = CalendarDisplayMode.COLOR
    private var errorMessage: String? = null

    private val _uiState = MutableStateFlow(
        CalendarUiStateMapper.createCalendarUiState(
            today = today,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            availableMonths = listOf(initialMonth),
            displayMode = displayMode,
            entries = emptyList(),
            isLoading = true
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadEntries()
    }

    fun setDisplayMode(mode: CalendarDisplayMode) {
        if (displayMode == mode) return
        displayMode = mode
        publishState()
    }

    fun selectDate(date: LocalDate) {
        val targetMonth = YearMonth.from(date)
        if (targetMonth == currentMonth) {
            selectedDate = date
            publishState()
            return
        }

        currentMonth = targetMonth
        selectedDate = date
        publishState()
    }

    fun changeMonth(targetMonth: YearMonth) {
        if (currentMonth == targetMonth) return
        currentMonth = targetMonth
        selectedDate = CalendarUiStateMapper.resolveSelectedDateForMonth(selectedDate, targetMonth)
        publishState()
    }

    fun onPagerSettled(targetMonth: YearMonth) {
        if (currentMonth == targetMonth) return
        currentMonth = targetMonth
        selectedDate = CalendarUiStateMapper.resolveSelectedDateForMonth(selectedDate, targetMonth)
        publishState()
    }

    private fun loadEntries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            getCalendarEntriesUseCase()
                .onSuccess { entries ->
                    allEntries = entries
                    errorMessage = null
                    publishState(isLoading = false)
                }
                .onFailure { throwable ->
                    allEntries = emptyList()
                    errorMessage = throwable.message ?: "캘린더 기록을 불러오지 못했습니다."
                    publishState(isLoading = false)
                }
        }
    }

    private fun publishState(isLoading: Boolean = false) {
        _uiState.value = CalendarUiStateMapper.createCalendarUiState(
            today = today,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            availableMonths = CalendarUiStateMapper.buildAvailableMonths(allEntries, initialMonth),
            displayMode = displayMode,
            entries = allEntries,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }
}
