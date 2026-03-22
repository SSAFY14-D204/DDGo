package com.ddgo.app.feature.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.calendar.model.CalendarEntryUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FullDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M\uC6D4 d\uC77C EEEE", Locale.KOREAN)

// 선택한 날짜의 기록 목록을 보여주고, 비어 있으면 빈 상태를 노출한다.
@Composable
internal fun SelectedDateSection(
    date: LocalDate,
    entries: List<CalendarEntryUiModel>,
    isToday: Boolean,
    onEntrySelected: (Long) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = CalendarPalette.Surface,
        border = BorderStroke(1.dp, CalendarPalette.Border),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        text = date.format(FullDateFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        color = CalendarPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (entries.isEmpty()) {
                            "\uC120\uD0DD\uD55C \uB0A0\uC9DC\uC5D0 \uAE30\uB85D\uB41C \uC138\uC158\uC774 \uC5C6\uC5B4\uC694."
                        } else {
                            "${entries.size}\uAC1C\uC758 \uAE30\uB85D\uC774 \uC788\uC5B4\uC694."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalendarPalette.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isToday) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = CalendarPalette.AccentSoft
                    ) {
                        Text(
                            text = "TODAY",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = CalendarPalette.AccentStrong
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                EmptyCalendarState()
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    entries.forEach { entry ->
                        CalendarEntryRow(
                            entry = entry,
                            onClick = { onEntrySelected(entry.challengeId) }
                        )
                    }
                }
            }
        }
    }
}

// 아직 해당 날짜에 표시할 기록이 없을 때의 안내 화면이다.
@Composable
private fun EmptyCalendarState() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CalendarPalette.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(CalendarPalette.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "\uBE48 \uC77C\uC815",
                    tint = CalendarPalette.AccentStrong
                )
            }
            Text(
                text = "\uC544\uC9C1 \uAE30\uB85D\uC774 \uC5C6\uC5B4\uC694",
                style = MaterialTheme.typography.titleMedium,
                color = CalendarPalette.TextPrimary
            )
            Text(
                text = "\uC120\uD0DD\uD55C \uB0A0\uC9DC\uC5D0 \uD65C\uB3D9 \uAE30\uB85D\uC774 \uC788\uC73C\uBA74 \uC5EC\uAE30\uC5D0 \uD45C\uC2DC\uB429\uB2C8\uB2E4.",
                style = MaterialTheme.typography.bodyMedium,
                color = CalendarPalette.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// 기록 한 건을 제목, 보조 정보, 시간 중심의 간단한 카드로 표현한다.
@Composable
private fun CalendarEntryRow(
    entry: CalendarEntryUiModel,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.SurfaceMuted
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CalendarPalette.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "\uAE30\uB85D",
                    tint = CalendarPalette.AccentStrong
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CalendarPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.secondaryText.isNotBlank()) {
                    Text(
                        text = entry.secondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalendarPalette.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                if (entry.timeLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CalendarPalette.AccentSoft
                    ) {
                        Text(
                            text = entry.timeLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = CalendarPalette.AccentStrong,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = "\uAE30\uB85D",
                        style = MaterialTheme.typography.labelLarge,
                        color = CalendarPalette.TextSecondary
                    )
                }
            }
        }
    }
}
