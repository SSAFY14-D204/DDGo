package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarMonthSummary
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// 월별 기록 목록을 상단 요약 카드에 필요한 집계 값으로 변환한다.
class GetCalendarMonthSummaryUseCase @Inject constructor() {

    operator fun invoke(
        yearMonth: YearMonth,
        entries: List<CalendarEntry>
    ): CalendarMonthSummary {
        val monthEntries = entries
            .groupBy { it.date }
            .filterKeys { YearMonth.from(it) == yearMonth }

        return CalendarMonthSummary(
            activeDays = monthEntries.size,
            totalSessions = monthEntries.values.sumOf { it.size },
            longestStreak = longestActiveDayStreak(monthEntries.keys.sorted())
        )
    }

    // 날짜가 하루씩 이어지는지 비교해 가장 긴 연속 활동 일수를 계산한다.
    private fun longestActiveDayStreak(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        var best = 1
        var current = 1

        for (index in 1 until dates.size) {
            current = if (dates[index - 1].plusDays(1) == dates[index]) current + 1 else 1
            best = maxOf(best, current)
        }

        return best
    }
}
