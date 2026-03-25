package com.ddgo.app.feature.calendar.mapper

import com.ddgo.app.domain.model.CalendarEntry
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarDayMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarEntryUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel
import com.ddgo.app.feature.calendar.model.CalendarMonthSummaryUiModel
import com.ddgo.app.feature.calendar.model.CalendarUiState
import java.time.LocalDate
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

    // 달력은 6주 고정 그리드로 만들고 이전/다음 달 날짜도 함께 보여준다.
    private fun buildCalendarWeeks(
        currentMonth: YearMonth,
        entriesByDate: Map<LocalDate, List<CalendarEntry>>
    ): List<List<CalendarDayUiModel>> {
        val firstDay = currentMonth.atDay(1)
        val leadingDays = firstDay.dayOfWeek.value % 7
        val gridStart = firstDay.minusDays(leadingDays.toLong())

        return List(42) { index ->
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
            .map { it.problemColor }
            .filter { it.isNotBlank() }
            .distinct()
            .mapIndexed { index, colorLabel ->
                CalendarDayMarkerUiModel(
                    key = "color-$index-$colorLabel",
                    label = colorLabel,
                    tone = colorLabel.toMarkerTone()
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
                    tone = CalendarMarkerToneUiModel.GRAY
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
        return when (trim().lowercase(Locale.ROOT)) {
            "빨강", "레드", "red" -> CalendarMarkerToneUiModel.RED
            "주황", "오렌지", "orange" -> CalendarMarkerToneUiModel.ORANGE
            "노랑", "노란색", "옐로", "yellow" -> CalendarMarkerToneUiModel.YELLOW
            "초록", "초록색", "그린", "green" -> CalendarMarkerToneUiModel.GREEN
            "파랑", "파란색", "블루", "blue" -> CalendarMarkerToneUiModel.BLUE
            "남색", "네이비", "navy", "indigo" -> CalendarMarkerToneUiModel.NAVY
            "보라", "보라색", "퍼플", "purple" -> CalendarMarkerToneUiModel.PURPLE
            "분홍", "핑크", "pink" -> CalendarMarkerToneUiModel.PINK
            "갈색", "브라운", "brown" -> CalendarMarkerToneUiModel.BROWN
            "회색", "그레이", "gray", "grey" -> CalendarMarkerToneUiModel.GRAY
            "검정", "검은색", "블랙", "black" -> CalendarMarkerToneUiModel.BLACK
            "하양", "흰색", "화이트", "white" -> CalendarMarkerToneUiModel.WHITE
            else -> CalendarMarkerToneUiModel.UNKNOWN
        }
    }
}
