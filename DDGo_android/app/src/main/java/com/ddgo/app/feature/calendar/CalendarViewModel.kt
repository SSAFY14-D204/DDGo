package com.ddgo.app.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.domain.usecase.GetCalendarEntriesUseCase
import com.ddgo.app.domain.usecase.GetCalendarMonthSummaryUseCase
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    // 날짜만 바뀔 때는 다시 조회하지 않도록 현재 월 데이터를 메모리에 유지한다.
    private var currentEntries: List<CalendarEntry> = emptyList()
    private var currentSummary = CalendarMonthSummary()
    private var loadMonthJob: Job? = null
    private var latestLoadRequestId = 0L

    private val _uiState = MutableStateFlow(
        CalendarUiStateMapper.createCalendarUiState(
            today = today,
            currentMonth = initialMonth,
            selectedDate = today,
            entries = emptyList(),
            summary = currentSummary,
            isLoading = true
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        // 화면 진입 시 현재 월을 기본값으로 먼저 불러온다.
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

        _uiState.update { currentState ->
            CalendarUiStateMapper.createCalendarUiState(
                today = today,
                currentMonth = targetMonth,
                selectedDate = date,
                entries = currentEntries,
                summary = currentSummary,
                errorMessage = currentState.errorMessage
            )
        }
    }

    private fun loadMonth(targetMonth: YearMonth, selectedDate: LocalDate) {
        // 월이 바뀌면 이전 월 데이터가 잠깐 보이지 않도록 상태를 먼저 초기화한다.
        val requestId = latestLoadRequestId + 1
        latestLoadRequestId = requestId
        loadMonthJob?.cancel()
        currentEntries = emptyList()
        currentSummary = CalendarMonthSummary()
        _uiState.value = CalendarUiStateMapper.createCalendarUiState(
            today = today,
            currentMonth = targetMonth,
            selectedDate = selectedDate,
            entries = emptyList(),
            summary = currentSummary,
            isLoading = true
        )

        loadMonthJob = viewModelScope.launch {
            getCalendarEntriesUseCase(targetMonth)
                .onSuccess { entries ->
                    if (requestId != latestLoadRequestId) return@onSuccess
                    currentEntries = entries
                    currentSummary = getCalendarMonthSummaryUseCase(targetMonth, entries)
                    _uiState.value = CalendarUiStateMapper.createCalendarUiState(
                        today = today,
                        currentMonth = targetMonth,
                        selectedDate = selectedDate,
                        entries = entries,
                        summary = currentSummary
                    )
                }
                .onFailure { throwable ->
                    if (requestId != latestLoadRequestId) return@onFailure
                    currentEntries = emptyList()
                    currentSummary = CalendarMonthSummary()
                    _uiState.value = CalendarUiStateMapper.createCalendarUiState(
                        today = today,
                        currentMonth = targetMonth,
                        selectedDate = selectedDate,
                        entries = emptyList(),
                        summary = currentSummary,
                        errorMessage = throwable.message
                            ?: "\uCE98\uB9B0\uB354 \uAE30\uB85D\uC744 \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4."
                    )
                }
        }
    }

    // 다른 달로 이동하면 그 달의 1일을 기본 선택값으로 사용한다.
    private fun defaultSelectedDateForMonth(targetMonth: YearMonth): LocalDate {
        return if (targetMonth == initialMonth) {
            today
        } else {
            targetMonth.atDay(1)
        }
    }
}
