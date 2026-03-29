package com.ddgo.app.feature.calendar

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarEntryResult
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.domain.usecase.GetCalendarEntriesUseCase
import com.ddgo.app.domain.usecase.GetCalendarMonthSummaryUseCase
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `selectMarkerFilter keeps current month and selected date while updating markers`() = runTest {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        val entries = listOf(
            CalendarEntry(
                id = 1L,
                date = today,
                title = "보라 / V4",
                problemColor = "보라",
                result = CalendarEntryResult.SUCCESS,
                venue = "더클라임 강남",
                time = LocalTime.of(19, 0),
                note = "완등"
            ),
            CalendarEntry(
                id = 2L,
                date = today,
                title = "노랑 / V3",
                problemColor = "노랑",
                result = CalendarEntryResult.SUCCESS,
                venue = "서울숲 클라이밍",
                time = LocalTime.of(18, 0),
                note = "완등"
            )
        )
        val entriesUseCase = mockk<GetCalendarEntriesUseCase>()
        val summaryUseCase = mockk<GetCalendarMonthSummaryUseCase>()

        coEvery { entriesUseCase(any()) } returns Result.success(entries)
        every { summaryUseCase(any(), any()) } returns CalendarMonthSummary(
            activeDays = 1,
            totalSessions = 2,
            longestStreak = 1
        )

        val viewModel = CalendarViewModel(entriesUseCase, summaryUseCase)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectMarkerFilter(CalendarMarkerFilterUiModel.GYM)

        val state = viewModel.uiState.value
        assertEquals(currentMonth, state.currentMonth)
        assertEquals(today, state.selectedDate)
        assertEquals(CalendarMarkerFilterUiModel.GYM, state.activeMarkerFilter)
        assertEquals(listOf("강남", "서울"), state.weeks.flatten().first { it.date == today }.gymMarkers.map { it.label })
    }

    @Test
    fun `selectDate on same month updates selection without another load`() = runTest {
        val today = LocalDate.now()
        val currentMonth = YearMonth.from(today)
        val entriesUseCase = mockk<GetCalendarEntriesUseCase>()
        val summaryUseCase = mockk<GetCalendarMonthSummaryUseCase>()

        coEvery { entriesUseCase(any()) } returns Result.success(emptyList())
        every { summaryUseCase(any(), any()) } returns CalendarMonthSummary()

        val viewModel = CalendarViewModel(entriesUseCase, summaryUseCase)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val selectedDate = if (today.dayOfMonth < today.lengthOfMonth()) {
            today.plusDays(1)
        } else {
            today.minusDays(1)
        }
        viewModel.selectDate(selectedDate)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(currentMonth, state.currentMonth)
        assertEquals(selectedDate, state.selectedDate)
        coVerify(exactly = 1) { entriesUseCase(currentMonth) }
    }

    @Test
    fun `selectDate on another month reloads target month and keeps tapped day`() = runTest {
        val today = LocalDate.now()
        val targetDate = today.plusMonths(1).withDayOfMonth(7)
        val targetMonth = YearMonth.from(targetDate)
        val entriesUseCase = mockk<GetCalendarEntriesUseCase>()
        val summaryUseCase = mockk<GetCalendarMonthSummaryUseCase>()

        coEvery { entriesUseCase(any()) } returns Result.success(emptyList())
        every { summaryUseCase(any(), any()) } returns CalendarMonthSummary()

        val viewModel = CalendarViewModel(entriesUseCase, summaryUseCase)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectDate(targetDate)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetMonth, state.currentMonth)
        assertEquals(targetDate, state.selectedDate)
        coVerify(exactly = 1) { entriesUseCase(targetMonth) }
    }

    @Test
    fun `selectMonth reloads target month and preserves chosen filter`() = runTest {
        val today = LocalDate.now()
        val initialMonth = YearMonth.from(today)
        val targetMonth = initialMonth.plusMonths(1)
        val entriesUseCase = mockk<GetCalendarEntriesUseCase>()
        val summaryUseCase = mockk<GetCalendarMonthSummaryUseCase>()

        coEvery { entriesUseCase(any()) } returns Result.success(emptyList())
        every { summaryUseCase(any(), any()) } returns CalendarMonthSummary()

        val viewModel = CalendarViewModel(entriesUseCase, summaryUseCase)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.selectMarkerFilter(CalendarMarkerFilterUiModel.GYM)
        viewModel.selectMonth(targetMonth)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(targetMonth, state.currentMonth)
        assertEquals(targetMonth.atDay(1), state.selectedDate)
        assertEquals(CalendarMarkerFilterUiModel.GYM, state.activeMarkerFilter)
        coVerify(exactly = 1) { entriesUseCase(targetMonth) }
    }
}
