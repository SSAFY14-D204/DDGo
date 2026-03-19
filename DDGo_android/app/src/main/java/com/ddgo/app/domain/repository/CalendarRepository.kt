package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.CalendarEntry

interface CalendarRepository {
    suspend fun getCalendarEntries(): Result<List<CalendarEntry>>
}
