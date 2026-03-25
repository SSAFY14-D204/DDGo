package com.ddgo.app.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.calendar.components.CalendarErrorSection
import com.ddgo.app.feature.calendar.components.SelectedDateSection
import com.ddgo.app.feature.calendar.style.CalendarPalette
import java.time.LocalDate

@Composable
fun CalendarDetailScreen(
    requestedDate: LocalDate,
    onNavigateBack: () -> Unit,
    onEntrySelected: (Long) -> Unit,
    viewModel: CalendarViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(requestedDate) {
        viewModel.selectDate(requestedDate)
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            CalendarPalette.BackgroundTop,
            CalendarPalette.BackgroundBottom,
            CalendarPalette.BackgroundTop
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-32).dp),
            brush = Brush.radialGradient(
                colors = listOf(
                    CalendarPalette.Accent.copy(alpha = 0.16f),
                    CalendarPalette.Accent.copy(alpha = 0f)
                )
            )
        )
        DecorativeGlow(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-90).dp, y = 120.dp),
            brush = Brush.radialGradient(
                colors = listOf(
                    CalendarPalette.AccentStrong.copy(alpha = 0.10f),
                    CalendarPalette.AccentStrong.copy(alpha = 0f)
                )
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CalendarDetailTopBar(onNavigateBack = onNavigateBack)
            }

            uiState.errorMessage?.let { errorMessage ->
                item {
                    CalendarErrorSection(message = errorMessage)
                }
            }

            item {
                SelectedDateSection(
                    date = uiState.selectedDate,
                    entries = uiState.selectedEntries,
                    isToday = uiState.selectedDate == uiState.today,
                    onEntrySelected = onEntrySelected
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CalendarPalette.AccentStrong
                )
            }
        }
    }
}

@Composable
private fun CalendarDetailTopBar(
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = CalendarPalette.Surface,
            border = BorderStroke(1.dp, CalendarPalette.Border)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로 가기",
                    tint = CalendarPalette.TextPrimary
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "하루 기록",
                style = MaterialTheme.typography.titleLarge,
                color = CalendarPalette.TextPrimary
            )
            Text(
                text = "선택한 날짜의 세션을 모아볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = CalendarPalette.TextSecondary
            )
        }
    }
}

@Composable
private fun DecorativeGlow(
    modifier: Modifier = Modifier,
    brush: Brush
) {
    Box(
        modifier = modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(brush)
    )
}
