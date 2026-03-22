package com.ddgo.app.feature.calendar.model

import java.time.LocalDate
import java.time.YearMonth

// Screen이 그리기 위해 필요한 상태만 모아둔 화면 전용 모델이다.
data class CalendarUiState(
    val today: LocalDate,
    val currentMonth: YearMonth,
    val selectedDate: LocalDate,
    val weeks: List<List<CalendarDayUiModel>>,
    val summary: CalendarMonthSummaryUiModel,
    val selectedEntries: List<CalendarEntryUiModel>,
    val isLoading: Boolean,
    val errorMessage: String?
)

// 날짜 셀은 날짜 자체와 현재 월 포함 여부, 기록 개수만 가진다.
data class CalendarDayUiModel(
    val date: LocalDate,
    val isInCurrentMonth: Boolean,
    val entryCount: Int
)

// 상단 요약 카드에서 쓰는 월간 집계 값이다.
data class CalendarMonthSummaryUiModel(
    val activeDays: Int = 0,
    val totalSessions: Int = 0,
    val longestStreak: Int = 0
)

// 상세 카드에서 바로 렌더링할 수 있도록 가공한 기록 모델이다.
data class CalendarEntryUiModel(
    val challengeId: Long,
    val title: String,
    val secondaryText: String,
    val timeLabel: String
)
