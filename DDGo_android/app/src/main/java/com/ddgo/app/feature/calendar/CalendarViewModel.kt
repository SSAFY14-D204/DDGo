package com.ddgo.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.domain.usecase.GetCalendarEntriesUseCase
import com.ddgo.app.domain.usecase.GetCalendarMonthSummaryUseCase
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val getCalendarEntriesUseCase: GetCalendarEntriesUseCase,
    private val getCalendarMonthSummaryUseCase: GetCalendarMonthSummaryUseCase
) : ViewModel() {

    private val today = LocalDate.now()
    private val initialMonth = YearMonth.from(today)
    private var currentEntries: List<CalendarEntry> = emptyList()
    private var currentSummary = CalendarMonthSummary()
    private var activeMarkerFilter = CalendarMarkerFilterUiModel.COLOR
    private var loadMonthJob: Job? = null
    private var latestLoadRequestId = 0L

    private val _uiState = MutableStateFlow(
        CalendarUiStateMapper.createCalendarUiState(
            today = today,
            currentMonth = initialMonth,
            selectedDate = today,
            entries = emptyList(),
            summary = currentSummary,
            activeMarkerFilter = activeMarkerFilter,
            isLoading = true
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        loadMonth(initialMonth, today)
    }

    fun showPreviousMonth() {
        val targetMonth = _uiState.value.currentMonth.minusMonths(1)
        loadMonth(targetMonth, defaultSelectedDateForMonth(targetMonth))
    }

    fun showNextMonth() {
        val targetMonth = _uiState.value.currentMonth.plusMonths(1)
        loadMonth(targetMonth, defaultSelectedDateForMonth(targetMonth))
    }

    fun selectDate(date: LocalDate) {
        val targetMonth = YearMonth.from(date)
        if (targetMonth != _uiState.value.currentMonth) {
            loadMonth(targetMonth, date)
            return
        }

        publishUiState(
            currentMonth = targetMonth,
            selectedDate = date,
            errorMessage = _uiState.value.errorMessage
        )
    }

    fun selectMarkerFilter(filter: CalendarMarkerFilterUiModel) {
        if (filter == activeMarkerFilter) return
        activeMarkerFilter = filter
        publishUiState(
            currentMonth = _uiState.value.currentMonth,
            selectedDate = _uiState.value.selectedDate,
            isLoading = _uiState.value.isLoading,
            errorMessage = _uiState.value.errorMessage
        )
    }

    private fun loadMonth(targetMonth: YearMonth, selectedDate: LocalDate) {
        val requestId = latestLoadRequestId + 1
        latestLoadRequestId = requestId
        loadMonthJob?.cancel()
        currentEntries = emptyList()
        currentSummary = CalendarMonthSummary()
        publishUiState(
            currentMonth = targetMonth,
            selectedDate = selectedDate,
            isLoading = true
        )

        loadMonthJob = viewModelScope.launch {
            getCalendarEntriesUseCase(targetMonth)
                .onSuccess { entries ->
                    if (requestId != latestLoadRequestId) return@onSuccess
                    currentEntries = entries
                    currentSummary = getCalendarMonthSummaryUseCase(targetMonth, entries)
                    publishUiState(
                        currentMonth = targetMonth,
                        selectedDate = selectedDate
                    )
                }
                .onFailure { throwable ->
                    if (requestId != latestLoadRequestId) return@onFailure
                    currentEntries = emptyList()
                    currentSummary = CalendarMonthSummary()
                    publishUiState(
                        currentMonth = targetMonth,
                        selectedDate = selectedDate,
                        errorMessage = throwable.toUserFacingNetworkMessageOrNull()
                            ?: throwable.message
                            ?: "캘린더 기록을 불러오지 못했어요."
                    )
                }
        }
    }

    private fun publishUiState(
        currentMonth: YearMonth,
        selectedDate: LocalDate,
        isLoading: Boolean = false,
        errorMessage: String? = null
    ) {
        _uiState.value = CalendarUiStateMapper.createCalendarUiState(
            today = today,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            entries = currentEntries,
            summary = currentSummary,
            activeMarkerFilter = activeMarkerFilter,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    // 다른 월로 이동하면 그 달의 1일을 기본 선택값으로 사용한다.
    private fun defaultSelectedDateForMonth(targetMonth: YearMonth): LocalDate {
        return if (targetMonth == initialMonth) {
            today
        } else {
            targetMonth.atDay(1)
        }
    }
}
