package com.ddgo.app.domain.model

import java.time.LocalDate
import java.time.LocalTime

// 캘린더에 표시할 한 건의 활동 기록이다.
data class CalendarEntry(
    val id: Long,
    val date: LocalDate,
    val title: String,
    val problemColor: String,
    val result: CalendarEntryResult,
    val venue: String,
    val time: LocalTime?,
    val note: String
)

enum class CalendarEntryResult {
    SUCCESS,
    FAIL,
    ACTIVE,
    PENDING,
    UNKNOWN
}

// 상단 요약 카드에 필요한 월간 집계 값이다.
data class CalendarMonthSummary(
    val activeDays: Int = 0,
    val totalSessions: Int = 0,
    val longestStreak: Int = 0
)
