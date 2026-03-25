package com.ddgo.app.feature.calendar.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.calendar.model.CalendarDayMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerRenderStyleUiModel
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
    val MonthMenuWidth = 128.dp
    val MonthMenuOffsetY = 8.dp
    val MonthMenuHeight = 36.dp
    val MonthMenuCornerRadius = 20.dp
    val MonthMenuItemCornerRadius = 14.dp
    val ToggleWidth = 110.dp
    val ToggleHeight = 30.dp
    val ToggleCornerRadius = 24.dp
    val ToggleActiveWidth = 64.dp
    val ToggleLabelWidth = 49.dp
    val ToggleLabelGap = 3.dp
    val ToggleLabelStartPadding = 6.dp
    val ToggleLabelEndPadding = 3.dp
    val WeekdayRowWidth = 312.dp
    val GridWidth = 317.dp
    val DayCellHeight = 58.dp
    val DayRowSpacing = 13.dp
    val SelectedDateWidth = 26.dp
    val SelectedDateHeight = 16.dp
    val PlaceholderSize = 27.dp
    val SingleMarkerSize = 27.dp
    val OverlapGroupWidth = 30.dp
    val MarkerGroupHeight = 31.dp
    val VerticalPillWidth = 18.dp
    val VerticalPillHeight = 31.dp
    val VerticalPillStepX = 12.dp
    val SquareMarkerSize = 18.dp
    val ClusterGroupSize = 31.dp
    val ClusterStep = 13.dp
    val OverflowDotSize = 4.dp
    val OverflowDotOffset = 27.dp
    val MarkerAreaHeight = 35.dp
}

internal object CalendarMonthSectionTags {
    const val MonthSelector = "calendarMonthSelector"
    const val MonthMenu = "calendarMonthMenu"
    const val TodayBadge = "calendarTodayBadge"
    const val SingleGroup = "calendarMarkerGroupSingle"
    const val DoubleGroup = "calendarMarkerGroupDouble"
    const val TripleGroup = "calendarMarkerGroupTriple"
    const val FourGroup = "calendarMarkerGroupFour"
    const val FivePlusGroup = "calendarMarkerGroupFivePlus"
    const val FilledMarker = "calendarMarkerFilled"
    const val OutlinedMarker = "calendarMarkerOutlined"
    const val OverflowDot = "calendarMarkerOverflowDot"
    const val Placeholder = "calendarMarkerPlaceholder"

    fun monthItem(month: Int): String = "calendarMonthItem-$month"
}

@Composable
internal fun CalendarMonthSection(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    weeks: List<List<CalendarDayUiModel>>,
    today: LocalDate,
    activeMarkerFilter: CalendarMarkerFilterUiModel,
    onMonthSelected: (YearMonth) -> Unit,
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
            MonthSelectorControl(
                currentMonth = currentMonth,
                onMonthSelected = onMonthSelected
            )
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
private fun MonthSelectorControl(
    currentMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit
) {
    var expanded by remember(currentMonth.year) { mutableStateOf(false) }

    Box {
        MonthSelectorChip(
            text = currentMonth.format(GridMonthFormatter),
            onClick = { expanded = true }
        )

        MonthSelectorDropdownMenu(
            currentMonth = currentMonth,
            expanded = expanded,
            onDismissRequest = { expanded = false },
            onMonthSelected = { month ->
                expanded = false
                onMonthSelected(YearMonth.of(currentMonth.year, month))
            }
        )
    }
}

@Composable
private fun MonthSelectorDropdownMenu(
    currentMonth: YearMonth,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onMonthSelected: (Int) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = CalendarMonthSectionDefaults.MonthMenuOffsetY),
        modifier = Modifier
            .width(CalendarMonthSectionDefaults.MonthMenuWidth)
            .testTag(CalendarMonthSectionTags.MonthMenu),
        shape = RoundedCornerShape(CalendarMonthSectionDefaults.MonthMenuCornerRadius),
        containerColor = CalendarPalette.MonthMenuSurface,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, CalendarPalette.MonthMenuBorder)
    ) {
        (1..12).forEach { month ->
            MonthSelectorDropdownItem(
                year = currentMonth.year,
                month = month,
                selected = month == currentMonth.monthValue,
                onClick = { onMonthSelected(month) }
            )
        }
    }
}

@Composable
private fun MonthSelectorDropdownItem(
    year: Int,
    month: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = "${year}년 ${month}월",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = (-0.16).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = if (selected) {
                    CalendarPalette.MonthMenuSelectedText
                } else {
                    CalendarPalette.MonthMenuText
                }
            )
        },
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .height(CalendarMonthSectionDefaults.MonthMenuHeight)
            .clip(RoundedCornerShape(CalendarMonthSectionDefaults.MonthMenuItemCornerRadius))
            .background(
                if (selected) {
                    CalendarPalette.MonthMenuSelectedBackground
                } else {
                    Color.Transparent
                }
            )
            .testTag(CalendarMonthSectionTags.monthItem(month))
    )
}

@Composable
private fun MonthSelectorChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .testTag(CalendarMonthSectionTags.MonthSelector)
            .width(CalendarMonthSectionDefaults.MonthChipWidth)
            .height(CalendarMonthSectionDefaults.MonthChipHeight)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = CalendarPalette.MonthShadow,
                spotColor = CalendarPalette.MonthShadow
            ),
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.MonthSurface,
        border = BorderStroke(1.dp, CalendarPalette.MonthBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = (-0.16).sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = CalendarPalette.MonthSelectorText
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                modifier = Modifier.size(14.dp),
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
    val indicatorOffset by animateDpAsState(
        targetValue = if (selectedMode == CalendarMarkerFilterUiModel.COLOR) {
            0.dp
        } else {
            CalendarMonthSectionDefaults.ToggleWidth - CalendarMonthSectionDefaults.ToggleActiveWidth
        },
        animationSpec = spring(),
        label = "calendar-toggle-indicator"
    )

    Surface(
        modifier = Modifier
            .width(CalendarMonthSectionDefaults.ToggleWidth)
            .height(CalendarMonthSectionDefaults.ToggleHeight)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(CalendarMonthSectionDefaults.ToggleCornerRadius),
                ambientColor = CalendarPalette.ToggleShadow,
                spotColor = CalendarPalette.ToggleShadow
            ),
        shape = RoundedCornerShape(CalendarMonthSectionDefaults.ToggleCornerRadius),
        color = CalendarPalette.ToggleTrackBackground,
        border = BorderStroke(1.dp, CalendarPalette.ToggleTrackBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(CalendarMonthSectionDefaults.ToggleActiveWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(CalendarMonthSectionDefaults.ToggleCornerRadius),
                color = CalendarPalette.ToggleActive
            ) {}

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = CalendarMonthSectionDefaults.ToggleLabelStartPadding,
                        end = CalendarMonthSectionDefaults.ToggleLabelEndPadding
                    ),
                horizontalArrangement = Arrangement.spacedBy(CalendarMonthSectionDefaults.ToggleLabelGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleSegment(
                    modifier = Modifier.width(CalendarMonthSectionDefaults.ToggleLabelWidth),
                    text = "색상",
                    selected = selectedMode == CalendarMarkerFilterUiModel.COLOR,
                    onClick = { onModeSelected(CalendarMarkerFilterUiModel.COLOR) }
                )
                ToggleSegment(
                    modifier = Modifier.width(CalendarMonthSectionDefaults.ToggleLabelWidth),
                    text = "암장",
                    selected = selectedMode == CalendarMarkerFilterUiModel.GYM,
                    onClick = { onModeSelected(CalendarMarkerFilterUiModel.GYM) }
                )
            }
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
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.14).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = if (selected) CalendarPalette.ToggleActiveText else CalendarPalette.ToggleInactiveText,
            textAlign = TextAlign.Center
        )
    }
}

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
                    fontWeight = FontWeight.Medium,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = CalendarPalette.WeekdayText,
                textAlign = TextAlign.Center
            )
        }
    }
}

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
            isSelected && !isToday -> CalendarPalette.Accent
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
            isInCurrentMonth = day.isInCurrentMonth,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
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
    val showHighlight = isToday

    if (showHighlight) {
        Surface(
            modifier = modifier
                .width(CalendarMonthSectionDefaults.SelectedDateWidth)
                .height(CalendarMonthSectionDefaults.SelectedDateHeight)
                .then(
                    if (isToday) {
                        Modifier.testTag(CalendarMonthSectionTags.TodayBadge)
                    } else {
                        Modifier
                    }
                ),
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
                        lineHeight = 16.sp,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        letterSpacing = (-0.15).sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
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
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                letterSpacing = (-0.15).sp,
                lineHeight = 20.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
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
    isInCurrentMonth: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(CalendarMonthSectionDefaults.MarkerAreaHeight)
            .alpha(if (isInCurrentMonth) 1f else 0.25f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        when {
            markers.isEmpty() -> EmptyMarkerToken(isInCurrentMonth = isInCurrentMonth)
            activeMarkerFilter == CalendarMarkerFilterUiModel.GYM -> GymMarkerRow(markers = markers.take(2))
            markers.size == 1 -> SingleMarkerGroup(marker = markers.first())
            markers.size == 2 -> DoubleMarkerGroup(markers = markers.take(2))
            markers.size == 3 -> TripleMarkerGroup(markers = markers.take(3))
            markers.size == 4 -> FourMarkerGroup(markers = markers.take(4))
            else -> FivePlusMarkerGroup(markers = markers.take(4))
        }
    }
}

@Composable
private fun SingleMarkerGroup(marker: CalendarDayMarkerUiModel) {
    Box(modifier = Modifier.testTag(CalendarMonthSectionTags.SingleGroup)) {
        MarkerToken(
            marker = marker,
            width = CalendarMonthSectionDefaults.SingleMarkerSize,
            height = CalendarMonthSectionDefaults.SingleMarkerSize
        )
    }
}

@Composable
private fun DoubleMarkerGroup(markers: List<CalendarDayMarkerUiModel>) {
    Box(
        modifier = Modifier
            .width(CalendarMonthSectionDefaults.OverlapGroupWidth)
            .height(CalendarMonthSectionDefaults.MarkerGroupHeight)
            .testTag(CalendarMonthSectionTags.DoubleGroup)
    ) {
        MarkerToken(
            marker = markers[0],
            width = CalendarMonthSectionDefaults.VerticalPillWidth,
            height = CalendarMonthSectionDefaults.VerticalPillHeight,
            modifier = Modifier.offset(x = 0.dp, y = 0.dp)
        )
        MarkerToken(
            marker = markers[1],
            width = CalendarMonthSectionDefaults.VerticalPillWidth,
            height = CalendarMonthSectionDefaults.VerticalPillHeight,
            modifier = Modifier.offset(x = CalendarMonthSectionDefaults.VerticalPillStepX, y = 0.dp)
        )
    }
}

@Composable
private fun TripleMarkerGroup(markers: List<CalendarDayMarkerUiModel>) {
    Box(
        modifier = Modifier
            .width(CalendarMonthSectionDefaults.OverlapGroupWidth)
            .height(CalendarMonthSectionDefaults.MarkerGroupHeight)
            .testTag(CalendarMonthSectionTags.TripleGroup)
    ) {
        MarkerToken(
            marker = markers[0],
            width = CalendarMonthSectionDefaults.VerticalPillWidth,
            height = CalendarMonthSectionDefaults.VerticalPillHeight,
            modifier = Modifier.offset(x = 0.dp, y = 0.dp)
        )
        MarkerToken(
            marker = markers[1],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = CalendarMonthSectionDefaults.VerticalPillStepX, y = 0.dp)
        )
        MarkerToken(
            marker = markers[2],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(
                x = CalendarMonthSectionDefaults.VerticalPillStepX,
                y = CalendarMonthSectionDefaults.ClusterStep
            )
        )
    }
}

@Composable
private fun FourMarkerGroup(markers: List<CalendarDayMarkerUiModel>) {
    Box(
        modifier = Modifier
            .size(CalendarMonthSectionDefaults.ClusterGroupSize)
            .testTag(CalendarMonthSectionTags.FourGroup)
    ) {
        MarkerToken(
            marker = markers[0],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = 0.dp, y = 0.dp)
        )
        MarkerToken(
            marker = markers[1],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = CalendarMonthSectionDefaults.ClusterStep, y = 0.dp)
        )
        MarkerToken(
            marker = markers[2],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = 0.dp, y = CalendarMonthSectionDefaults.ClusterStep)
        )
        MarkerToken(
            marker = markers[3],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(
                x = CalendarMonthSectionDefaults.ClusterStep,
                y = CalendarMonthSectionDefaults.ClusterStep
            )
        )
    }
}

@Composable
private fun FivePlusMarkerGroup(markers: List<CalendarDayMarkerUiModel>) {
    Box(
        modifier = Modifier
            .size(CalendarMonthSectionDefaults.ClusterGroupSize)
            .testTag(CalendarMonthSectionTags.FivePlusGroup)
    ) {
        MarkerToken(
            marker = markers[0],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = 0.dp, y = 0.dp)
        )
        MarkerToken(
            marker = markers[1],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = CalendarMonthSectionDefaults.ClusterStep, y = 0.dp)
        )
        MarkerToken(
            marker = markers[2],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(x = 0.dp, y = CalendarMonthSectionDefaults.ClusterStep)
        )
        MarkerToken(
            marker = markers[3],
            width = CalendarMonthSectionDefaults.SquareMarkerSize,
            height = CalendarMonthSectionDefaults.SquareMarkerSize,
            modifier = Modifier.offset(
                x = CalendarMonthSectionDefaults.ClusterStep,
                y = CalendarMonthSectionDefaults.ClusterStep
            )
        )
        Surface(
            modifier = Modifier
                .size(CalendarMonthSectionDefaults.OverflowDotSize)
                .offset(
                    x = CalendarMonthSectionDefaults.OverflowDotOffset,
                    y = CalendarMonthSectionDefaults.OverflowDotOffset
                )
                .testTag(CalendarMonthSectionTags.OverflowDot),
            shape = RoundedCornerShape(50),
            color = CalendarPalette.MarkerOverflowDot
        ) {}
    }
}

@Composable
private fun MarkerToken(
    marker: CalendarDayMarkerUiModel,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val toneColor = CalendarPalette.markerToneColor(marker.tone)
    val border = if (marker.renderStyle == CalendarMarkerRenderStyleUiModel.OUTLINED) {
        BorderStroke(2.dp, toneColor)
    } else if (marker.tone == CalendarMarkerToneUiModel.WHITE) {
        BorderStroke(1.dp, CalendarPalette.Border)
    } else {
        null
    }

    Surface(
        modifier = modifier
            .width(width)
            .height(height)
            .testTag(
                if (marker.renderStyle == CalendarMarkerRenderStyleUiModel.OUTLINED) {
                    CalendarMonthSectionTags.OutlinedMarker
                } else {
                    CalendarMonthSectionTags.FilledMarker
                }
            ),
        shape = RoundedCornerShape(10.dp),
        color = if (marker.renderStyle == CalendarMarkerRenderStyleUiModel.OUTLINED) {
            CalendarPalette.MarkerOutlineFill
        } else {
            toneColor
        },
        border = border
    ) {}
}

@Composable
private fun EmptyMarkerToken(
    isInCurrentMonth: Boolean
) {
    Surface(
        modifier = Modifier
            .size(CalendarMonthSectionDefaults.PlaceholderSize)
            .testTag(CalendarMonthSectionTags.Placeholder),
        shape = RoundedCornerShape(10.dp),
        color = if (isInCurrentMonth) {
            CalendarPalette.DayPlaceholder
        } else {
            CalendarPalette.DayPlaceholderMuted
        }
    ) {}
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
                        letterSpacing = (-0.11).sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = CalendarPalette.GymMarkerText
                )
            }
        }
    }
}
