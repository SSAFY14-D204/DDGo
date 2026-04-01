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
import androidx.compose.material.icons.rounded.Share
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

@Composable
internal fun CalendarHeroSection(
    currentMonth: YearMonth,
    summary: CalendarMonthSummaryUiModel,
    onShareClick: () -> Unit
) {
    val onHeroColor = CalendarPalette.OnAccent
    val headline = if (summary.totalSessions == 0) {
        "${currentMonth.monthValue}월에 아직 푼 문제가 없어요"
    } else {
        "${currentMonth.monthValue}월에 ${summary.totalSessions}개의 문제를 풀었어요"
    }
    val supportingText = "출석 ${summary.activeDays}일째 · 연속 ${summary.longestStreak}일"

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
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "캘린더 공유",
                            tint = onHeroColor
                        )
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
