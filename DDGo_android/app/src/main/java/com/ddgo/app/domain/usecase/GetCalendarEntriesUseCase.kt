package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.repository.CalendarRepository
import java.time.YearMonth
import javax.inject.Inject

// ViewModel이 저장소 구현을 직접 몰라도 되도록 월별 기록 조회를 감싼다.
class GetCalendarEntriesUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository
) {
    suspend operator fun invoke(yearMonth: YearMonth): Result<List<CalendarEntry>> {
        return calendarRepository.getCalendarEntries(yearMonth)
    }
}
