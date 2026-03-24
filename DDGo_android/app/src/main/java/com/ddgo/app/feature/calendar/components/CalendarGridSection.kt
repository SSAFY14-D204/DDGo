package com.ddgo.app.feature.calendar.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val GridMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

// 월 카드 안에서 헤더와 날짜 그리드를 함께 렌더링한다.
@Composable
internal fun CalendarMonthSection(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    weeks: List<List<CalendarDayUiModel>>,
    today: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = CalendarPalette.Surface,
        border = BorderStroke(1.dp, CalendarPalette.Border),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentMonth.format(GridMonthFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        color = CalendarPalette.TextPrimary
                    )
                    Text(
                        text = "날짜를 누르면 해당 하루의 기록을 확인할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CalendarPalette.TextSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CalendarPalette.AccentSoft
                ) {
                    Text(
                        text = "오늘 ${today.dayOfMonth}일",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = CalendarPalette.AccentStrong
                    )
                }
            }

            WeekdayHeader()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        week.forEach { day ->
                            DayCell(
                                modifier = Modifier.weight(1f),
                                day = day,
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
}

// 요일은 월요일부터 시작하도록 고정해 한국식 캘린더 흐름에 맞춘다.
@Composable
private fun WeekdayHeader() {
    val weekDays = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        weekDays.forEach { dayOfWeek ->
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.KOREAN),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = CalendarPalette.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 날짜 셀은 선택 상태, 오늘 여부, 기록 개수를 동시에 표현한다.
@Composable
private fun DayCell(
    modifier: Modifier = Modifier,
    day: CalendarDayUiModel,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> CalendarPalette.AccentStrong
            day.entryCount > 0 -> CalendarPalette.AccentSoft
            else -> CalendarPalette.Surface
        },
        animationSpec = spring(),
        label = "calendar-cell-container"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> CalendarPalette.OnAccent
            day.isInCurrentMonth -> CalendarPalette.TextPrimary
            else -> CalendarPalette.TextSecondary.copy(alpha = 0.55f)
        },
        animationSpec = spring(),
        label = "calendar-cell-content"
    )
    val borderColor = when {
        isSelected -> Color.Transparent
        isToday -> CalendarPalette.Accent.copy(alpha = 0.55f)
        day.entryCount > 0 -> CalendarPalette.Accent.copy(alpha = 0.28f)
        else -> CalendarPalette.Border
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isSelected) 12.dp else 0.dp,
                shape = shape,
                clip = false
            )
            .aspectRatio(0.86f)
            .clip(shape)
            .background(
                brush = if (isSelected) {
                    Brush.linearGradient(
                        colors = listOf(
                            CalendarPalette.Accent,
                            CalendarPalette.AccentStrong
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(containerColor, containerColor)
                    )
                }
            )
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
            )

            if (day.entryCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) {
                        CalendarPalette.OnAccent.copy(alpha = 0.18f)
                    } else {
                        CalendarPalette.Accent.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = if (day.entryCount > 9) "9+" else day.entryCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) CalendarPalette.OnAccent else CalendarPalette.AccentStrong
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}
