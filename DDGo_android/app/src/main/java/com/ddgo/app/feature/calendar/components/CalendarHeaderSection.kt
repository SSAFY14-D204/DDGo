package com.ddgo.app.feature.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.calendar.model.CalendarMonthSummaryUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

// 상단 카드는 월 이동과 월간 요약 정보를 한 번에 보여준다.
@Composable
internal fun CalendarHeroSection(
    currentMonth: YearMonth,
    summary: CalendarMonthSummaryUiModel,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val onHeroColor = CalendarPalette.OnAccent
    val subtitle = if (summary.totalSessions == 0) {
        "이 달에 기록된 활동이\n아직 없어요."
    } else {
        "이 달에 ${summary.totalSessions}회 기록, 최대 ${summary.longestStreak}일 연속 활동을 달성했어요."
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            CalendarPalette.Accent,
                            CalendarPalette.AccentStrong
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "캘린더",
                            style = MaterialTheme.typography.headlineMedium,
                            color = onHeroColor
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onHeroColor.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = onHeroColor.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowLeft,
                                    contentDescription = "이전 달",
                                    tint = onHeroColor
                                )
                            }
                            Text(
                                text = currentMonth.format(MonthFormatter),
                                style = MaterialTheme.typography.titleMedium,
                                color = onHeroColor,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowRight,
                                    contentDescription = "다음 달",
                                    tint = onHeroColor
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SummaryChip(
                        icon = Icons.Rounded.Today,
                        title = "활동 일수",
                        value = "${summary.activeDays}일",
                        tint = onHeroColor
                    )
                    SummaryChip(
                        icon = Icons.Rounded.CalendarMonth,
                        title = "기록",
                        value = "${summary.totalSessions}회",
                        tint = onHeroColor
                    )
                    SummaryChip(
                        icon = Icons.Rounded.LocalFireDepartment,
                        title = "연속",
                        value = "${summary.longestStreak}일",
                        tint = onHeroColor
                    )
                }
            }
        }
    }
}

// 요약 수치를 같은 형태로 반복해서 보여주기 위한 공통 카드다.
@Composable
private fun SummaryChip(
    icon: ImageVector,
    title: String,
    value: String,
    tint: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(tint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = tint.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
