package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.CalendarEntry
import java.time.YearMonth

// 특정 월에 표시할 기록 목록을 가져오는 도메인 계약이다.
interface CalendarRepository {
    suspend fun getCalendarEntries(yearMonth: YearMonth): Result<List<CalendarEntry>>
}
