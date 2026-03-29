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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.calendar.model.CalendarEntryUiModel
import com.ddgo.app.feature.calendar.model.CalendarMarkerToneUiModel
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FullDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

// 선택한 날짜의 기록 목록을 보여주고, 비어 있으면 빈 상태를 노출한다.
@Composable
internal fun SelectedDateSection(
    date: LocalDate,
    entries: List<CalendarEntryUiModel>,
    isToday: Boolean,
    onEntrySelected: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DetailSectionHeader(
            date = date,
            entryCount = entries.size,
            isToday = isToday
        )

        if (entries.isEmpty()) {
            EmptyCalendarState()
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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

@Composable
private fun DetailSectionHeader(
    date: LocalDate,
    entryCount: Int,
    isToday: Boolean
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
                text = if (isToday) "오늘의 기록" else "기록 리스트",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = CalendarPalette.TextPrimary
            )
            Text(
                text = date.format(FullDateFormatter),
                style = MaterialTheme.typography.bodyMedium.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = CalendarPalette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isToday) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CalendarPalette.AccentSoft
                ) {
                    Text(
                        text = "TODAY",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium,
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        ),
                        color = CalendarPalette.AccentStrong
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (entryCount == 0) CalendarPalette.SurfaceMuted else CalendarPalette.AccentSoft
            ) {
                Text(
                    text = "${entryCount}개",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Medium,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = if (entryCount == 0) CalendarPalette.TextSecondary else CalendarPalette.AccentStrong
                )
            }
        }
    }
}

// 아직 해당 날짜에 표시할 기록이 없을 때의 안내 화면이다.
@Composable
private fun EmptyCalendarState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.Surface,
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
                    contentDescription = "빈 일정",
                    tint = CalendarPalette.AccentStrong
                )
            }
            Text(
                text = "아직 기록이 없어요",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
                color = CalendarPalette.TextPrimary
            )
            Text(
                text = "선택한 날짜에 활동 기록이 있으면 여기에 표시됩니다.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ),
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
    val toneColor = CalendarPalette.markerToneColor(entry.problemColorTone)

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = CalendarPalette.Surface,
        border = BorderStroke(1.dp, CalendarPalette.Border),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CalendarPalette.SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = toneColor,
                    border = if (entry.problemColorTone == CalendarMarkerToneUiModel.WHITE) {
                        BorderStroke(1.dp, CalendarPalette.Border)
                    } else {
                        null
                    }
                ) {}
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    color = CalendarPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DetailMetaChip(
                        text = if (entry.problemColorLabel.isBlank()) "문제" else entry.problemColorLabel,
                        backgroundColor = CalendarPalette.AccentSoft,
                        textColor = CalendarPalette.AccentStrong
                    )
                    if (entry.venueLabel.isNotBlank()) {
                        DetailMetaChip(
                            text = entry.venueLabel,
                            backgroundColor = CalendarPalette.SurfaceMuted,
                            textColor = CalendarPalette.TextSecondary
                        )
                    }
                    if (entry.resultLabel.isNotBlank()) {
                        DetailMetaChip(
                            text = entry.resultLabel,
                            backgroundColor = completionChipBackground(entry.resultLabel),
                            textColor = completionChipText(entry.resultLabel)
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entry.timeLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CalendarPalette.AccentSoft
                    ) {
                        Text(
                            text = entry.timeLabel,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            color = CalendarPalette.AccentStrong,
                            maxLines = 1
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "상세 이동",
                    tint = CalendarPalette.TextSecondary
                )
            }
        }
    }
}

private fun completionChipBackground(resultLabel: String): Color {
    return when (resultLabel) {
        "완등" -> Color(0xFFE9F8EF)
        "미완등" -> Color(0xFFFFF1F1)
        else -> CalendarPalette.SurfaceMuted
    }
}

private fun completionChipText(resultLabel: String): Color {
    return when (resultLabel) {
        "완등" -> Color(0xFF1E9E5A)
        "미완등" -> Color(0xFFE45858)
        else -> CalendarPalette.TextSecondary
    }
}

@Composable
private fun DetailMetaChip(
    text: String,
    backgroundColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
