package com.ddgo.app.feature.calendar.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.calendar.model.CalendarDayMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val GridMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

private object CalendarMonthSectionDefaults {
    val ControlsWidth = 330.dp
    val MonthChipWidth = 128.dp
    val MonthChipHeight = 38.dp
    val ToggleWidth = 110.dp
    val ToggleHeight = 30.dp
    val WeekdayRowWidth = 312.dp
    val GridWidth = 317.dp
    val DayCellHeight = 55.dp
    val DayRowSpacing = 13.dp
    val SelectedDateWidth = 26.dp
    val SelectedDateHeight = 16.dp
    val MarkerSize = 27.dp
    val ClusterMarkerSize = 18.dp
}

// 월 섹션은 피그마와 같은 조작부와 달력 그리드를 그대로 조립한다.
@Composable
internal fun CalendarMonthSection(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    weeks: List<List<CalendarDayUiModel>>,
    today: LocalDate,
    activeMarkerFilter: CalendarMarkerFilterUiModel,
    onMarkerFilterSelected: (CalendarMarkerFilterUiModel) -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = CalendarMonthSectionDefaults.ControlsWidth)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonthSelectorChip(text = currentMonth.format(GridMonthFormatter))
            CalendarModeToggle(
                selectedMode = activeMarkerFilter,
                onModeSelected = onMarkerFilterSelected
            )
        }

        WeekdayHeader()

        Column(
            modifier = Modifier.widthIn(max = CalendarMonthSectionDefaults.GridWidth),
            verticalArrangement = Arrangement.spacedBy(CalendarMonthSectionDefaults.DayRowSpacing)
        ) {
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(21.dp)
                ) {
                    week.forEach { day ->
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = day,
                            activeMarkerFilter = activeMarkerFilter,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == today,
                            onClick = { onDateSelected(day.date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelectorChip(
    text: String
) {
    Surface(
        modifier = Modifier
            .width(CalendarMonthSectionDefaults.MonthChipWidth)
            .height(CalendarMonthSectionDefaults.MonthChipHeight)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = CalendarPalette.MonthShadow,
                spotColor = CalendarPalette.MonthShadow
            ),
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.MonthSurface,
        border = BorderStroke(1.dp, CalendarPalette.MonthBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.16).sp
                ),
                color = CalendarPalette.MonthSelectorText
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                modifier = Modifier.size(16.dp),
                contentDescription = "월 선택",
                tint = CalendarPalette.MonthSelectorChevron
            )
        }
    }
}

@Composable
private fun CalendarModeToggle(
    selectedMode: CalendarMarkerFilterUiModel,
    onModeSelected: (CalendarMarkerFilterUiModel) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(CalendarMonthSectionDefaults.ToggleWidth)
            .height(CalendarMonthSectionDefaults.ToggleHeight)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = CalendarPalette.ToggleShadow,
                spotColor = CalendarPalette.ToggleShadow
            ),
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.ToggleTrackBackground,
        border = BorderStroke(1.dp, CalendarPalette.ToggleTrackBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToggleSegment(
                modifier = Modifier.weight(1f),
                text = "색상",
                selected = selectedMode == CalendarMarkerFilterUiModel.COLOR,
                onClick = { onModeSelected(CalendarMarkerFilterUiModel.COLOR) }
            )
            ToggleSegment(
                modifier = Modifier.weight(1f),
                text = "암장",
                selected = selectedMode == CalendarMarkerFilterUiModel.GYM,
                onClick = { onModeSelected(CalendarMarkerFilterUiModel.GYM) }
            )
        }
    }
}

@Composable
private fun ToggleSegment(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) CalendarPalette.ToggleActive else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.14).sp
            ),
            color = if (selected) CalendarPalette.ToggleActiveText else CalendarPalette.ToggleInactiveText,
            textAlign = TextAlign.Center
        )
    }
}

// 요일은 일요일부터 시작하도록 맞춘다.
@Composable
private fun WeekdayHeader() {
    val weekDays = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY
    )

    Row(
        modifier = Modifier.widthIn(max = CalendarMonthSectionDefaults.WeekdayRowWidth),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        weekDays.forEach { dayOfWeek ->
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = CalendarPalette.WeekdayText,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 날짜 셀은 선택 상태와 현재 달 여부를 표현하고, 현재 표시 모드에 맞는 마커를 렌더링한다.
@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: CalendarDayUiModel,
    activeMarkerFilter: CalendarMarkerFilterUiModel,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> CalendarPalette.DayCellSelectedText
            day.isInCurrentMonth -> CalendarPalette.DayCellText
            else -> CalendarPalette.DayCellTextMuted
        },
        animationSpec = spring(),
        label = "calendar-cell-content"
    )

    Box(
        modifier = modifier
            .height(CalendarMonthSectionDefaults.DayCellHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp)
    ) {
        DayNumberBadge(
            text = day.date.dayOfMonth.toString(),
            isSelected = isSelected,
            contentColor = contentColor,
            isToday = isToday,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
        )

        CalendarMarkerBlock(
            markers = if (activeMarkerFilter == CalendarMarkerFilterUiModel.COLOR) {
                day.colorMarkers
            } else {
                day.gymMarkers
            },
            activeMarkerFilter = activeMarkerFilter,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun DayNumberBadge(
    text: String,
    isSelected: Boolean,
    contentColor: Color,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Surface(
            modifier = modifier
                .width(CalendarMonthSectionDefaults.SelectedDateWidth)
                .height(CalendarMonthSectionDefaults.SelectedDateHeight),
            shape = RoundedCornerShape(8.dp),
            color = CalendarPalette.DayCellSelected
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.15).sp
                    ),
                    color = CalendarPalette.DayCellSelectedText,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Text(
            text = text,
            modifier = modifier,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal,
                letterSpacing = (-0.15).sp
            ),
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalendarMarkerBlock(
    markers: List<CalendarDayMarkerUiModel>,
    activeMarkerFilter: CalendarMarkerFilterUiModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(CalendarMonthSectionDefaults.MarkerSize)
            .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        when {
            markers.isEmpty() -> Unit

            activeMarkerFilter == CalendarMarkerFilterUiModel.COLOR && markers.size == 1 -> {
                ColorMarkerToken(marker = markers.first())
            }

            activeMarkerFilter == CalendarMarkerFilterUiModel.COLOR -> {
                ColorMarkerCluster(markers = markers.take(4))
            }

            else -> {
                GymMarkerRow(markers = markers.take(2))
            }
        }
    }
}

@Composable
private fun ColorMarkerToken(
    marker: CalendarDayMarkerUiModel
) {
    Box(
        modifier = Modifier
            .size(CalendarMonthSectionDefaults.MarkerSize)
            .background(
                color = CalendarPalette.markerToneColor(marker.tone),
                shape = RoundedCornerShape(10.dp)
            )
    )
}

@Composable
private fun ColorMarkerCluster(
    markers: List<CalendarDayMarkerUiModel>
) {
    BoxWithConstraints(modifier = Modifier.size(CalendarMonthSectionDefaults.MarkerSize)) {
        val positions = listOf(
            Alignment.TopStart,
            Alignment.TopEnd,
            Alignment.BottomStart,
            Alignment.BottomEnd
        )

        markers.forEachIndexed { index, marker ->
            val toneColor = CalendarPalette.markerToneColor(marker.tone)
            Box(
                modifier = Modifier
                    .align(positions[index])
                    .offset(
                        x = if (positions[index] == Alignment.TopEnd || positions[index] == Alignment.BottomEnd) {
                            (-1).dp
                        } else {
                            1.dp
                        },
                        y = if (positions[index] == Alignment.BottomStart || positions[index] == Alignment.BottomEnd) {
                            0.dp
                        } else {
                            (-1).dp
                        }
                    )
                    .size(CalendarMonthSectionDefaults.ClusterMarkerSize)
                    .then(
                        if (marker.tone == CalendarMarkerToneUiModel.PINK) {
                            Modifier.border(
                                BorderStroke(2.dp, toneColor),
                                RoundedCornerShape(10.dp)
                            )
                        } else {
                            Modifier.background(
                                color = toneColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    )
            )
        }
    }
}

@Composable
private fun GymMarkerRow(
    markers: List<CalendarDayMarkerUiModel>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        markers.forEach { marker ->
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = CalendarPalette.GymMarkerBackground
            ) {
                Text(
                    text = marker.label,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.11).sp
                    ),
                    color = CalendarPalette.GymMarkerText
                )
            }
        }
    }
}
