package com.ddgo.app.feature.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.feature.calendar.components.CalendarErrorSection
import com.ddgo.app.feature.calendar.components.CalendarHeroSection
import com.ddgo.app.feature.calendar.components.CalendarMonthSection
import com.ddgo.app.feature.calendar.components.SelectedDateSection
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarUiState
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.LocalDate
import java.time.YearMonth

// Screen은 ViewModel 상태를 구독하고 화면 섹션을 조합하는 역할만 맡는다.
@Composable
fun CalendarScreen(
    onEntrySelected: (Long) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onDateSelected = viewModel::selectDate,
        onEntrySelected = onEntrySelected
    )
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onEntrySelected: (Long) -> Unit
) {
    // 메인 화면과 톤을 맞추기 위해 밝은 블루 계열 배경을 사용한다.
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            CalendarPalette.BackgroundTop,
            CalendarPalette.BackgroundBottom,
            CalendarPalette.BackgroundTop
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-32).dp),
            brush = Brush.radialGradient(
                colors = listOf(
                    CalendarPalette.Accent.copy(alpha = 0.16f),
                    CalendarPalette.Accent.copy(alpha = 0f)
                )
            )
        )
        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-90).dp, y = 120.dp),
            brush = Brush.radialGradient(
                colors = listOf(
                    CalendarPalette.AccentStrong.copy(alpha = 0.10f),
                    CalendarPalette.AccentStrong.copy(alpha = 0f)
                )
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (uiState.errorMessage != null) {
                item {
                    CalendarErrorSection(message = uiState.errorMessage)
                }
            }

            item {
                CalendarHeroSection(
                    currentMonth = uiState.currentMonth,
                    summary = uiState.summary,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth
                )
            }

            item {
                CalendarMonthSection(
                    currentMonth = uiState.currentMonth,
                    selectedDate = uiState.selectedDate,
                    weeks = uiState.weeks,
                    today = uiState.today,
                    onDateSelected = onDateSelected
                )
            }

            item {
                AnimatedContent(
                    targetState = uiState.selectedDate,
                    label = "calendar-selected-date"
                ) { selectedDate ->
                    SelectedDateSection(
                        date = selectedDate,
                        entries = uiState.selectedEntries,
                        isToday = selectedDate == uiState.today,
                        onEntrySelected = onEntrySelected
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

@Composable
private fun DecorativeGlow(
    modifier: Modifier = Modifier,
    brush: Brush
) {
    // 배경이 너무 평평해 보이지 않도록 은은한 글로우를 추가한다.
    Box(
        modifier = modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(brush)
    )
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
            onDateSelected = {},
            onEntrySelected = {}
        )
    }
}
