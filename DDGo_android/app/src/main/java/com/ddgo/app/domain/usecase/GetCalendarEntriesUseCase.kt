package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.repository.CalendarRepository
import javax.inject.Inject

class GetCalendarEntriesUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository
) {
    suspend operator fun invoke(): Result<List<CalendarEntry>> {
        return calendarRepository.getCalendarEntries()
    }
}
