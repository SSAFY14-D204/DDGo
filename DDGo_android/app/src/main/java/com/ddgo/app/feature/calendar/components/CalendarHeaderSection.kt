package com.ddgo.app.feature.calendar.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.R
import com.ddgo.app.feature.calendar.model.CalendarMonthSummaryUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)

// 상단 히어로는 피그마의 검은 헤더 톤을 유지하면서 기존 월 이동 기능을 함께 제공한다.
@Composable
internal fun CalendarHeroSection(
    currentMonth: YearMonth,
    summary: CalendarMonthSummaryUiModel,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val onHeroColor = CalendarPalette.OnAccent
    val headline = if (summary.totalSessions == 0) {
        "${currentMonth.monthValue}월에는 아직 푼 문제가 없어요!"
    } else {
        "${currentMonth.monthValue}월에는 ${summary.totalSessions}개의 문제를 풀었어요!"
    }
    val supportingText = "활동 ${summary.activeDays}일 · 연속 ${summary.longestStreak}일"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = CalendarPalette.HeroBackground
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarHeroMark()

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onPreviousMonth, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "이전 달",
                                tint = onHeroColor
                            )
                        }
                        Text(
                            text = currentMonth.format(MonthFormatter),
                            style = MaterialTheme.typography.labelLarge,
                            color = onHeroColor.copy(alpha = 0.90f),
                            maxLines = 1
                        )
                        IconButton(onClick = onNextMonth, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "다음 달",
                                tint = onHeroColor
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    color = onHeroColor,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onHeroColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CalendarHeroMark() {
    Image(
        painter = painterResource(id = R.drawable.ic_calendar_hero_subtract),
        contentDescription = null,
        modifier = Modifier.size(width = 61.dp, height = 40.dp)
    )
}
