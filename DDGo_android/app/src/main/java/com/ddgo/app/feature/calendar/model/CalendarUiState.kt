package com.ddgo.app.feature.calendar.model

import java.time.LocalDate
import java.time.YearMonth

// Screen을 그리기 위해 필요한 상태만 모아둔 화면 전용 모델이다.
data class CalendarUiState(
    val today: LocalDate,
    val currentMonth: YearMonth,
    val selectedDate: LocalDate,
    val weeks: List<List<CalendarDayUiModel>>,
    val summary: CalendarMonthSummaryUiModel,
    val selectedEntries: List<CalendarEntryUiModel>,
    val activeMarkerFilter: CalendarMarkerFilterUiModel,
    val isLoading: Boolean,
    val errorMessage: String?
)

enum class CalendarMarkerFilterUiModel {
    COLOR,
    GYM
}

enum class CalendarMarkerToneUiModel {
    RED,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    NAVY,
    PURPLE,
    PINK,
    BROWN,
    GRAY,
    BLACK,
    WHITE,
    UNKNOWN
}

enum class CalendarMarkerRenderStyleUiModel {
    FILLED,
    OUTLINED
}

data class CalendarDayMarkerUiModel(
    val key: String,
    val label: String,
    val tone: CalendarMarkerToneUiModel,
    val renderStyle: CalendarMarkerRenderStyleUiModel
)

// 날짜 타일은 날짜 자체와 현재 달 포함 여부, 기록 수, 필터별 마커 정보를 함께 가진다.
data class CalendarDayUiModel(
    val date: LocalDate,
    val isInCurrentMonth: Boolean,
    val entryCount: Int,
    val colorMarkers: List<CalendarDayMarkerUiModel>,
    val gymMarkers: List<CalendarDayMarkerUiModel>
)

// 상단 요약 카드에서 다루는 월간 집계 값이다.
data class CalendarMonthSummaryUiModel(
    val activeDays: Int = 0,
    val totalSessions: Int = 0,
    val longestStreak: Int = 0
)

// 상세 카드에서 바로 렌더링할 수 있도록 가공한 기록 모델이다.
data class CalendarEntryUiModel(
    val challengeId: Long,
    val title: String,
    val problemColorLabel: String,
    val problemColorTone: CalendarMarkerToneUiModel,
    val venueLabel: String,
    val secondaryText: String,
    val timeLabel: String
)
