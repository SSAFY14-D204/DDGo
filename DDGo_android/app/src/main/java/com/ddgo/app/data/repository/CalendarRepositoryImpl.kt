package com.ddgo.app.data.repository

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.repository.CalendarRepository
import java.time.YearMonth
import javax.inject.Inject

class CalendarRepositoryImpl @Inject constructor() : CalendarRepository {

    override suspend fun getCalendarEntries(yearMonth: YearMonth): Result<List<CalendarEntry>> {
        // 실제 API나 로컬 저장소가 아직 연결되지 않아 현재는 빈 결과를 반환한다.
        return Result.success(emptyList())
    }
}
