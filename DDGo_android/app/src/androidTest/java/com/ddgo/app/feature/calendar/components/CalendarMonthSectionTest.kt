package com.ddgo.app.feature.calendar.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.feature.calendar.model.CalendarDayMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerRenderStyleUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarMonthSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `month selector opens dropdown and returns selected month`() {
        var selectedMonth: YearMonth? = null

        composeRule.setContent {
            DDGoTheme(darkTheme = false) {
                CalendarMonthSection(
                    currentMonth = YearMonth.of(2026, 3),
                    selectedDate = LocalDate.of(2026, 3, 2),
                    weeks = sampleWeeks(),
                    today = LocalDate.of(2026, 3, 2),
                    activeMarkerFilter = CalendarMarkerFilterUiModel.COLOR,
                    onMonthSelected = { selectedMonth = it },
                    onMarkerFilterSelected = {},
                    onDateSelected = {}
                )
            }
        }

        composeRule.onNodeWithTag(CalendarMonthSectionTags.MonthSelector).performClick()
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.monthItem(4)).assertCountEquals(1)
        composeRule.onNodeWithTag(CalendarMonthSectionTags.monthItem(4)).performClick()

        composeRule.runOnIdle {
            assertEquals(YearMonth.of(2026, 4), selectedMonth)
        }
    }

    @Test
    fun `today date keeps highlighted badge even when another day is selected`() {
        composeRule.setContent {
            DDGoTheme(darkTheme = false) {
                CalendarMonthSection(
                    currentMonth = YearMonth.of(2026, 3),
                    selectedDate = LocalDate.of(2026, 3, 3),
                    weeks = sampleWeeks(),
                    today = LocalDate.of(2026, 3, 2),
                    activeMarkerFilter = CalendarMarkerFilterUiModel.COLOR,
                    onMonthSelected = {},
                    onMarkerFilterSelected = {},
                    onDateSelected = {}
                )
            }
        }

        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.TodayBadge).assertCountEquals(1)
    }

    @Test
    fun `color mode renders group variants and fill outline markers`() {
        composeRule.setContent {
            DDGoTheme(darkTheme = false) {
                CalendarMonthSection(
                    currentMonth = YearMonth.of(2026, 3),
                    selectedDate = LocalDate.of(2026, 3, 2),
                    weeks = sampleWeeks(),
                    today = LocalDate.of(2026, 3, 2),
                    activeMarkerFilter = CalendarMarkerFilterUiModel.COLOR,
                    onMonthSelected = {},
                    onMarkerFilterSelected = {},
                    onDateSelected = {}
                )
            }
        }

        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.SingleGroup).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.DoubleGroup).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.TripleGroup).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.FourGroup).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.FivePlusGroup).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.OverflowDot).assertCountEquals(1)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.FilledMarker).assertCountEquals(9)
        composeRule.onAllNodesWithTag(CalendarMonthSectionTags.OutlinedMarker).assertCountEquals(6)
    }

    private fun sampleWeeks(): List<List<CalendarDayUiModel>> {
        return listOf(
            listOf(
                day(2026, 3, 1),
                day(
                    2026,
                    3,
                    2,
                    colorMarkers = listOf(
                        marker("보라", CalendarMarkerToneUiModel.PURPLE, CalendarMarkerRenderStyleUiModel.FILLED)
                    )
                ),
                day(
                    2026,
                    3,
                    3,
                    colorMarkers = listOf(
                        marker("초록", CalendarMarkerToneUiModel.GREEN, CalendarMarkerRenderStyleUiModel.FILLED),
                        marker("분홍", CalendarMarkerToneUiModel.PINK, CalendarMarkerRenderStyleUiModel.OUTLINED)
                    )
                ),
                day(
                    2026,
                    3,
                    4,
                    colorMarkers = listOf(
                        marker("초록", CalendarMarkerToneUiModel.GREEN, CalendarMarkerRenderStyleUiModel.FILLED),
                        marker("보라", CalendarMarkerToneUiModel.PURPLE, CalendarMarkerRenderStyleUiModel.OUTLINED),
                        marker("분홍", CalendarMarkerToneUiModel.PINK, CalendarMarkerRenderStyleUiModel.FILLED)
                    )
                ),
                day(
                    2026,
                    3,
                    5,
                    colorMarkers = listOf(
                        marker("초록", CalendarMarkerToneUiModel.GREEN, CalendarMarkerRenderStyleUiModel.FILLED),
                        marker("분홍", CalendarMarkerToneUiModel.PINK, CalendarMarkerRenderStyleUiModel.OUTLINED),
                        marker("보라", CalendarMarkerToneUiModel.PURPLE, CalendarMarkerRenderStyleUiModel.FILLED),
                        marker("노랑", CalendarMarkerToneUiModel.YELLOW, CalendarMarkerRenderStyleUiModel.OUTLINED)
                    )
                ),
                day(
                    2026,
                    3,
                    6,
                    colorMarkers = listOf(
                        marker("노랑", CalendarMarkerToneUiModel.YELLOW, CalendarMarkerRenderStyleUiModel.FILLED),
                        marker("빨강", CalendarMarkerToneUiModel.RED, CalendarMarkerRenderStyleUiModel.OUTLINED),
                        marker("초록", CalendarMarkerToneUiModel.GREEN, CalendarMarkerRenderStyleUiModel.FILLED),
                        marker("보라", CalendarMarkerToneUiModel.PURPLE, CalendarMarkerRenderStyleUiModel.OUTLINED),
                        marker("분홍", CalendarMarkerToneUiModel.PINK, CalendarMarkerRenderStyleUiModel.FILLED)
                    )
                ),
                day(2026, 3, 7)
            )
        )
    }

    private fun day(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        colorMarkers: List<CalendarDayMarkerUiModel> = emptyList()
    ): CalendarDayUiModel {
        return CalendarDayUiModel(
            date = LocalDate.of(year, month, dayOfMonth),
            isInCurrentMonth = true,
            entryCount = colorMarkers.size,
            colorMarkers = colorMarkers,
            gymMarkers = emptyList()
        )
    }

    private fun marker(
        label: String,
        tone: CalendarMarkerToneUiModel,
        renderStyle: CalendarMarkerRenderStyleUiModel
    ): CalendarDayMarkerUiModel {
        return CalendarDayMarkerUiModel(
            key = "$label-${tone.name}-${renderStyle.name}",
            label = label,
            tone = tone,
            renderStyle = renderStyle
        )
    }
}
