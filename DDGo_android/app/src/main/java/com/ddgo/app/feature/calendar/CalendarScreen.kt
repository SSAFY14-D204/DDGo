package com.ddgo.app.feature.calendar

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.ddgo.app.R
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarDayUiModel
import com.ddgo.app.feature.calendar.model.CalendarDisplayMode
import com.ddgo.app.feature.calendar.model.CalendarMarkerStyle
import com.ddgo.app.feature.calendar.model.CalendarMarkerUiModel
import com.ddgo.app.feature.calendar.model.CalendarUiState
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MonthFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val WeekdayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    CalendarContent(
        uiState = uiState,
        onModeChange = viewModel::setDisplayMode,
        onDateSelected = viewModel::selectDate,
        onMonthSelected = viewModel::changeMonth,
        onPagerSettled = viewModel::onPagerSettled
    )
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onModeChange: (CalendarDisplayMode) -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onPagerSettled: (YearMonth) -> Unit
) {
    val context = LocalContext.current
    val rootView = LocalView.current.rootView
    val scope = rememberCoroutineScope()
    val anchorMonth = remember(uiState.today) { YearMonth.from(uiState.today) }
    val centerPage = remember { Int.MAX_VALUE / 2 }
    val pagerState = rememberPagerState(initialPage = centerPage) { Int.MAX_VALUE }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.currentMonth) {
        val targetPage = pageForMonth(
            anchorMonth = anchorMonth,
            centerPage = centerPage,
            targetMonth = uiState.currentMonth
        )
        if (pagerState.currentPage == targetPage) return@LaunchedEffect

        if (abs(pagerState.currentPage - targetPage) > 6) {
            pagerState.scrollToPage(targetPage)
        } else {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState, anchorMonth, centerPage) {
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .collect { (page, isScrollInProgress) ->
                if (!isScrollInProgress) {
                    onPagerSettled(monthForPage(anchorMonth, centerPage, page))
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CalendarPalette.HeaderBackground)
    ) {
        CalendarHeader(
            month = uiState.currentMonth,
            solvedCount = uiState.headerSolvedCount,
            onShareClick = { shareCalendarCapture(context, rootView) }
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = CalendarPalette.Surface,
            shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonthChip(
                        month = uiState.currentMonth,
                        onClick = { showMonthPicker = true }
                    )
                    CalendarModeSegment(
                        selectedMode = uiState.displayMode,
                        onModeSelected = onModeChange
                    )
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = uiState.errorMessage,
                        color = CalendarPalette.Error,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val pageMonth = monthForPage(anchorMonth, centerPage, page)
                        val pageWeeks = remember(
                            pageMonth,
                            uiState.entries,
                            uiState.displayMode,
                            uiState.selectedDate,
                            uiState.today
                        ) {
                            CalendarUiStateMapper.buildMonthWeeks(
                                currentMonth = pageMonth,
                                today = uiState.today,
                                selectedDate = uiState.selectedDate,
                                entries = uiState.entries,
                                displayMode = uiState.displayMode
                            )
                        }

                        CalendarMonthPage(
                            weeks = pageWeeks,
                            onDateSelected = onDateSelected
                        )
                    }

                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = CalendarPalette.AccentBlue
                        )
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        CalendarMonthPickerDialog(
            availableMonths = uiState.availableMonths,
            selectedMonth = uiState.currentMonth,
            onDismiss = { showMonthPicker = false },
            onMonthSelected = { month ->
                showMonthPicker = false
                scope.launch {
                    onMonthSelected(month)
                }
            }
        )
    }
}

@Composable
private fun CalendarHeader(
    month: YearMonth,
    solvedCount: Int,
    onShareClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_ddgo_mascot),
                contentDescription = "디디고 마스코트",
                modifier = Modifier.size(54.dp)
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onShareClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CropFree,
                    contentDescription = "캘린더 공유",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "${month.monthValue}월에는 ${solvedCount}개의 문제를 풀었어요!",
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun MonthChip(
    month: YearMonth,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, CalendarPalette.Border, RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = month.format(MonthFormatter),
            color = CalendarPalette.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = "월 선택",
            tint = CalendarPalette.TextSecondary
        )
    }
}

@Composable
private fun CalendarModeSegment(
    selectedMode: CalendarDisplayMode,
    onModeSelected: (CalendarDisplayMode) -> Unit
) {
    Row(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, CalendarPalette.Border, RoundedCornerShape(28.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarModeButton(
            text = "색상",
            isSelected = selectedMode == CalendarDisplayMode.COLOR,
            modifier = Modifier.weight(1f),
            onClick = { onModeSelected(CalendarDisplayMode.COLOR) }
        )
        CalendarModeButton(
            text = "암장",
            isSelected = selectedMode == CalendarDisplayMode.GYM,
            modifier = Modifier.weight(1f),
            onClick = { onModeSelected(CalendarDisplayMode.GYM) }
        )
    }
}

@Composable
private fun CalendarModeButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) CalendarPalette.AccentBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else CalendarPalette.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CalendarMonthPage(
    weeks: List<List<CalendarDayUiModel>>,
    onDateSelected: (java.time.LocalDate) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = CalendarPalette.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                week.forEach { day ->
                    CalendarDayCell(
                        day = day,
                        modifier = Modifier.weight(1f),
                        onClick = { onDateSelected(day.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUiModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 6.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DatePill(day = day)
        Spacer(modifier = Modifier.height(8.dp))
        CalendarMarkerStack(day = day)
    }
}

@Composable
private fun DatePill(day: CalendarDayUiModel) {
    val textColor = when {
        day.isSelected -> Color.White
        day.isInCurrentMonth -> CalendarPalette.TextPrimary
        else -> CalendarPalette.TextMuted
    }

    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (day.isSelected) CalendarPalette.AccentBlue else Color.Transparent)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = textColor,
            fontSize = 17.sp,
            fontWeight = if (day.isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun CalendarMarkerStack(day: CalendarDayUiModel) {
    if (day.markers.isEmpty() && day.overflowCount == 0) {
        EmptyMarkerPlaceholder(isInCurrentMonth = day.isInCurrentMonth)
        return
    }

    val offsets = markerOffsets(day.markers.size)

    Box(
        modifier = Modifier
            .width(58.dp)
            .height(34.dp)
    ) {
        day.markers.forEachIndexed { index, marker ->
            val (x, y) = offsets.getOrElse(index) { 0.dp to 0.dp }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = x, y = y)
            ) {
                when (marker.style) {
                    CalendarMarkerStyle.DIFFICULTY -> DifficultyMarker(marker = marker)
                    CalendarMarkerStyle.GYM -> GymMarker(marker = marker)
                }
            }
        }

        if (day.overflowCount > 0) {
            OverflowBadge(
                count = day.overflowCount,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun EmptyMarkerPlaceholder(
    isInCurrentMonth: Boolean
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isInCurrentMonth) CalendarPalette.Placeholder
                else CalendarPalette.PlaceholderLight
            )
    )
}

@Composable
private fun DifficultyMarker(
    marker: CalendarMarkerUiModel
) {
    val markerColor = marker.colorHex.toComposeColor()
    val strokeColor = if (markerColor.luminance() > 0.9f) CalendarPalette.Border else markerColor

    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (marker.isSolved) markerColor else Color.White)
            .border(2.dp, strokeColor, CircleShape)
    )
}

@Composable
private fun GymMarker(
    marker: CalendarMarkerUiModel
) {
    Surface(
        modifier = Modifier.size(22.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        if (marker.logoUrl != null) {
            AsyncImage(
                model = marker.logoUrl,
                contentDescription = "암장 로고",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CalendarPalette.SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = marker.fallbackLabel,
                    color = CalendarPalette.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OverflowBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, CalendarPalette.Border, RoundedCornerShape(10.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            color = CalendarPalette.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CalendarMonthPickerDialog(
    availableMonths: List<YearMonth>,
    selectedMonth: YearMonth,
    onDismiss: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .padding(vertical = 20.dp)
            ) {
                Text(
                    text = "월 선택",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = CalendarPalette.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(availableMonths) { month ->
                        val isSelected = month == selectedMonth
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMonthSelected(month) }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text = month.format(MonthFormatter),
                                color = if (isSelected) CalendarPalette.AccentBlue else CalendarPalette.TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        HorizontalDivider(color = CalendarPalette.SurfaceMuted)
                    }
                }
            }
        }
    }
}

private fun markerOffsets(count: Int): List<Pair<Dp, Dp>> {
    return when (count) {
        0 -> emptyList()
        1 -> listOf(0.dp to 0.dp)
        2 -> listOf((-8).dp to 0.dp, 8.dp to 0.dp)
        3 -> listOf(0.dp to (-7).dp, (-10).dp to 7.dp, 10.dp to 7.dp)
        else -> listOf(0.dp to (-8).dp, (-10).dp to 4.dp, 10.dp to 4.dp, 0.dp to 12.dp)
    }
}

private fun pageForMonth(
    anchorMonth: YearMonth,
    centerPage: Int,
    targetMonth: YearMonth
): Int {
    val monthDelta = (targetMonth.year - anchorMonth.year) * 12 +
        (targetMonth.monthValue - anchorMonth.monthValue)
    return centerPage + monthDelta
}

private fun monthForPage(
    anchorMonth: YearMonth,
    centerPage: Int,
    page: Int
): YearMonth {
    return anchorMonth.plusMonths((page - centerPage).toLong())
}

private fun String?.toComposeColor(): Color {
    val normalized = this
        ?.trim()
        ?.removePrefix("#")
        ?.takeIf { it.length == 6 }
        ?: return CalendarPalette.Placeholder

    return Color(android.graphics.Color.parseColor("#$normalized"))
}

private fun shareCalendarCapture(
    context: Context,
    rootView: View
) {
    runCatching {
        val file = File(context.cacheDir, "calendar-share-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { stream ->
            rootView.drawToBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "캘린더 공유"))
    }.onFailure {
        Toast.makeText(context, "캘린더 이미지를 공유하지 못했습니다.", Toast.LENGTH_SHORT).show()
    }
}
