package com.ddgo.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.feature.calendar.components.CalendarErrorSection
import com.ddgo.app.feature.calendar.components.CalendarHeroSection
import com.ddgo.app.feature.calendar.components.CalendarMonthSection
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarUiState
import com.ddgo.app.feature.calendar.style.CalendarPalette
import com.ddgo.app.feature.main.MainChromeDefaults
import java.time.LocalDate
import java.time.YearMonth

// Screen은 ViewModel 상태를 구독하고 화면 섹션을 조합하는 역할만 맡는다.
@Composable
fun CalendarScreen(
    onDateSelected: (LocalDate) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val handleDateSelected: (LocalDate) -> Unit = { date ->
        viewModel.selectDate(date)
        onDateSelected(date)
    }

    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onMonthSelected = viewModel::selectMonth,
        onMarkerFilterSelected = viewModel::selectMarkerFilter,
        onDateSelected = handleDateSelected
    )
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onMarkerFilterSelected: (com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CalendarPalette.BackgroundTop)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = MainChromeDefaults.ContentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CalendarHeroSection(
                    currentMonth = uiState.currentMonth,
                    summary = uiState.summary,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth
                )
            }

            if (uiState.errorMessage != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        CalendarErrorSection(message = uiState.errorMessage)
                    }
                }
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    CalendarMonthSection(
                        currentMonth = uiState.currentMonth,
                        selectedDate = uiState.selectedDate,
                        weeks = uiState.weeks,
                        today = uiState.today,
                        activeMarkerFilter = uiState.activeMarkerFilter,
                        onMonthSelected = onMonthSelected,
                        onMarkerFilterSelected = onMarkerFilterSelected,
                        onDateSelected = onDateSelected
                    )
                }
            }
        }

        // 로딩 중에는 현재 레이아웃 위에 진행 상태를 겹쳐 보여준다.
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CalendarPalette.AccentStrong
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarScreenPreview() {
    val today = LocalDate.now()
    DDGoTheme(darkTheme = false) {
        CalendarContent(
            uiState = CalendarUiStateMapper.createCalendarUiState(
                today = today,
                currentMonth = YearMonth.from(today),
                selectedDate = today,
                entries = emptyList(),
                summary = CalendarMonthSummary()
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onMonthSelected = {},
            onMarkerFilterSelected = {},
            onDateSelected = {}
        )
    }
}
