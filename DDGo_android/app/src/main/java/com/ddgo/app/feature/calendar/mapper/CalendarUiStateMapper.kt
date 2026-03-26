package com.ddgo.app.feature.calendar.mapper

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarEntryResult
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.domain.model.HoldDifficultyColor
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarDayMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarEntryUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerRenderStyleUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel
import com.ddgo.app.feature.calendar.model.CalendarMonthSummaryUiModel
import com.ddgo.app.feature.calendar.model.CalendarUiState
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object CalendarUiStateMapper {

    private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)

    // 도메인 데이터를 화면 전용 상태로 바꾸고 달력 그리드까지 함께 만든다.
    fun createCalendarUiState(
        today: LocalDate,
        currentMonth: YearMonth,
        selectedDate: LocalDate,
        entries: List<CalendarEntry>,
        summary: CalendarMonthSummary,
        activeMarkerFilter: CalendarMarkerFilterUiModel = CalendarMarkerFilterUiModel.COLOR,
        isLoading: Boolean = false,
        errorMessage: String? = null
    ): CalendarUiState {
        val entriesByDate = entries.groupBy { it.date }
        val selectedEntries = entriesByDate[selectedDate]
            .orEmpty()
            .sortedWith(entryComparator(activeMarkerFilter))
            .map { it.toUiModel() }

        return CalendarUiState(
            today = today,
            currentMonth = currentMonth,
            selectedDate = selectedDate,
            weeks = buildCalendarWeeks(currentMonth, entriesByDate),
            summary = summary.toUiModel(),
            selectedEntries = selectedEntries,
            activeMarkerFilter = activeMarkerFilter,
            isLoading = isLoading,
            errorMessage = errorMessage
        )
    }

    private fun CalendarMonthSummary.toUiModel(): CalendarMonthSummaryUiModel {
        return CalendarMonthSummaryUiModel(
            activeDays = activeDays,
            totalSessions = totalSessions,
            longestStreak = longestStreak
        )
    }

    private fun CalendarEntry.toUiModel(): CalendarEntryUiModel {
        return CalendarEntryUiModel(
            challengeId = id,
            title = title,
            problemColorLabel = problemColor,
            problemColorTone = problemColor.toMarkerTone(),
            venueLabel = venue,
            secondaryText = listOf(venue, note)
                .filter { it.isNotBlank() }
                .joinToString(" / "),
            timeLabel = time?.format(TimeFormatter).orEmpty()
        )
    }

    private fun entryComparator(
        activeMarkerFilter: CalendarMarkerFilterUiModel
    ): Comparator<CalendarEntry> {
        return when (activeMarkerFilter) {
            CalendarMarkerFilterUiModel.COLOR -> compareBy<CalendarEntry> { it.problemColor }
                .thenBy { it.venue }
                .thenByDescending { it.time }

            CalendarMarkerFilterUiModel.GYM -> compareBy<CalendarEntry> { it.venue }
                .thenBy { it.problemColor }
                .thenByDescending { it.time }
        }
    }

    // 달력은 현재 달을 감싸는 데 필요한 주차만 계산해 이전/다음 달 날짜를 함께 보여준다.
    private fun buildCalendarWeeks(
        currentMonth: YearMonth,
        entriesByDate: Map<LocalDate, List<CalendarEntry>>
    ): List<List<CalendarDayUiModel>> {
        val firstDay = currentMonth.atDay(1)
        val lastDay = currentMonth.atEndOfMonth()
        val leadingDays = firstDay.dayOfWeek.value % 7
        val trailingDays = 6 - (lastDay.dayOfWeek.value % 7)
        val gridStart = firstDay.minusDays(leadingDays.toLong())
        val totalDays = leadingDays + currentMonth.lengthOfMonth() + trailingDays

        return List(totalDays) { index ->
            val date = gridStart.plusDays(index.toLong())
            val dayEntries = entriesByDate[date].orEmpty()
            CalendarDayUiModel(
                date = date,
                isInCurrentMonth = YearMonth.from(date) == currentMonth,
                entryCount = dayEntries.size,
                colorMarkers = buildColorMarkers(dayEntries),
                gymMarkers = buildGymMarkers(dayEntries)
            )
        }.chunked(7)
    }

    private fun buildColorMarkers(entries: List<CalendarEntry>): List<CalendarDayMarkerUiModel> {
        return entries
            .filter { it.problemColor.isNotBlank() }
            .sortedWith(
                compareByDescending<CalendarEntry> { it.time ?: LocalTime.MIN }
                    .thenByDescending { it.id }
            )
            .map { entry ->
                CalendarDayMarkerUiModel(
                    key = "color-${entry.id}",
                    label = entry.problemColor,
                    tone = entry.problemColor.toMarkerTone(),
                    renderStyle = entry.result.toMarkerRenderStyle()
                )
            }
    }

    private fun buildGymMarkers(entries: List<CalendarEntry>): List<CalendarDayMarkerUiModel> {
        return entries
            .map { it.venue.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .mapIndexed { index, venue ->
                CalendarDayMarkerUiModel(
                    key = "gym-$index-$venue",
                    label = abbreviateVenue(venue),
                    tone = CalendarMarkerToneUiModel.GRAY,
                    renderStyle = CalendarMarkerRenderStyleUiModel.FILLED
                )
            }
    }

    private fun abbreviateVenue(venue: String): String {
        val condensed = venue
            .replace("더클라임", "")
            .replace("클라이밍", "")
            .replace(" ", "")

        val candidate = condensed.ifBlank { venue.replace(" ", "") }
        return candidate.take(2).ifBlank { "암장" }
    }

    private fun String.toMarkerTone(): CalendarMarkerToneUiModel {
        return when (HoldDifficultyColor.resolve(colorName = this, colorHex = null)) {
            HoldDifficultyColor.RED -> CalendarMarkerToneUiModel.RED
            HoldDifficultyColor.ORANGE -> CalendarMarkerToneUiModel.ORANGE
            HoldDifficultyColor.YELLOW -> CalendarMarkerToneUiModel.YELLOW
            HoldDifficultyColor.GREEN -> CalendarMarkerToneUiModel.GREEN
            HoldDifficultyColor.SKYBLUE -> CalendarMarkerToneUiModel.BLUE
            HoldDifficultyColor.NAVY -> CalendarMarkerToneUiModel.NAVY
            HoldDifficultyColor.PURPLE -> CalendarMarkerToneUiModel.PURPLE
            HoldDifficultyColor.PINK -> CalendarMarkerToneUiModel.PINK
            HoldDifficultyColor.BROWN -> CalendarMarkerToneUiModel.BROWN
            HoldDifficultyColor.GRAY -> CalendarMarkerToneUiModel.GRAY
            HoldDifficultyColor.BLACK -> CalendarMarkerToneUiModel.BLACK
            HoldDifficultyColor.WHITE -> CalendarMarkerToneUiModel.WHITE
            null -> CalendarMarkerToneUiModel.UNKNOWN
        }
    }

    private fun CalendarEntryResult.toMarkerRenderStyle(): CalendarMarkerRenderStyleUiModel {
        return when (this) {
            CalendarEntryResult.SUCCESS -> CalendarMarkerRenderStyleUiModel.FILLED
            CalendarEntryResult.FAIL,
            CalendarEntryResult.ACTIVE,
            CalendarEntryResult.PENDING,
            CalendarEntryResult.UNKNOWN -> CalendarMarkerRenderStyleUiModel.OUTLINED
        }
    }
}
