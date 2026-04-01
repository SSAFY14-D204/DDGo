package com.ddgo.app.feature.calendar

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.domain.model.CalendarMonthSummary
import com.ddgo.app.feature.calendar.components.CalendarErrorSection
import com.ddgo.app.feature.calendar.components.CalendarHeroSection
import com.ddgo.app.feature.calendar.components.CalendarMonthSection
import com.ddgo.app.feature.calendar.mapper.CalendarUiStateMapper
import com.ddgo.app.feature.calendar.model.CalendarUiState
import com.ddgo.app.feature.calendar.style.CalendarPalette
import com.ddgo.app.feature.main.MainChromeDefaults
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private data class CalendarCaptureBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
)

@Composable
fun CalendarScreen(
    onDateSelected: (LocalDate) -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current

    var sharePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    var captureBounds by remember { mutableStateOf<CalendarCaptureBounds?>(null) }
    val bottomInsetPx = with(density) { MainChromeDefaults.ContentBottomPadding.roundToPx() }

    val handleDateSelected: (LocalDate) -> Unit = { date ->
        viewModel.selectDate(date)
        onDateSelected(date)
    }

    val handleShareClick: () -> Unit = {
        val bounds = captureBounds
        val bitmap = if (bounds == null) {
            null
        } else {
            captureCalendarBitmap(
                source = view.drawToBitmap(),
                bounds = bounds,
                bottomInsetPx = bottomInsetPx
            )
        }

        if (bitmap == null) {
            Toast.makeText(context, "캘린더 이미지를 준비하지 못했어요.", Toast.LENGTH_SHORT).show()
        } else {
            sharePreviewBitmap = bitmap
            showShareSheet = true
        }
    }

    CalendarContent(
        uiState = uiState,
        onShareClick = handleShareClick,
        onMonthSelected = viewModel::selectMonth,
        onMarkerFilterSelected = viewModel::selectMarkerFilter,
        onDateSelected = handleDateSelected,
        onCaptureBoundsChanged = { captureBounds = it }
    )

    if (showShareSheet && sharePreviewBitmap != null) {
        CalendarSharePreviewSheet(
            bitmap = sharePreviewBitmap!!,
            onDismissRequest = {
                showShareSheet = false
                sharePreviewBitmap = null
            },
            onShareClick = {
                shareCalendarBitmap(context = context, bitmap = sharePreviewBitmap!!)
            }
        )
    }
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onShareClick: () -> Unit,
    onMonthSelected: (YearMonth) -> Unit,
    onMarkerFilterSelected: (com.ddgo.app.feature.calendar.model.CalendarMarkerFilterUiModel) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onCaptureBoundsChanged: (CalendarCaptureBounds) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CalendarPalette.BackgroundTop)
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                onCaptureBoundsChanged(
                    CalendarCaptureBounds(
                        left = position.x.roundToInt(),
                        top = position.y.roundToInt(),
                        width = coordinates.size.width,
                        height = coordinates.size.height
                    )
                )
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = MainChromeDefaults.ContentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CalendarHeroSection(
                    currentMonth = uiState.currentMonth,
                    summary = uiState.summary,
                    onShareClick = onShareClick
                )
            }

            if (uiState.errorMessage != null) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        CalendarErrorSection(message = uiState.errorMessage)
                    }
                }
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    CalendarMonthSection(
                        currentMonth = uiState.currentMonth,
                        selectedDate = uiState.selectedDate,
                        weeks = uiState.weeks,
                        today = uiState.today,
                        activeMarkerFilter = uiState.activeMarkerFilter,
                        onMonthSelected = onMonthSelected,
                        onMarkerFilterSelected = onMarkerFilterSelected,
                        onDateSelected = onDateSelected
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSharePreviewSheet(
    bitmap: Bitmap,
    onDismissRequest: () -> Unit,
    onShareClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = CalendarPalette.Surface,
        contentColor = CalendarPalette.TextPrimary
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "캘린더 공유",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CalendarPalette.TextPrimary
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    color = CalendarPalette.SurfaceMuted
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "공유할 캘린더 미리보기",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onShareClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CalendarPalette.AccentStrong,
                        contentColor = CalendarPalette.OnAccent
                    )
                ) {
                    Text(
                        text = "공유하기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun captureCalendarBitmap(
    source: Bitmap,
    bounds: CalendarCaptureBounds,
    bottomInsetPx: Int
): Bitmap? {
    val left = bounds.left.coerceAtLeast(0)
    val top = bounds.top.coerceAtLeast(0)
    val width = bounds.width.coerceAtMost(source.width - left)
    val targetHeight = (bounds.height - bottomInsetPx).coerceAtLeast(1)
    val height = targetHeight.coerceAtMost(source.height - top)

    if (width <= 0 || height <= 0) return null

    return Bitmap.createBitmap(source, left, top, width, height)
}

private fun shareCalendarBitmap(
    context: android.content.Context,
    bitmap: Bitmap
) {
    runCatching {
        val file = File(context.cacheDir, "calendar-share-${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "calendar-share", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(sendIntent, "공유할 앱을 선택하세요")
        )
    }.onFailure {
        Toast.makeText(context, "이미지를 공유하지 못했어요.", Toast.LENGTH_SHORT).show()
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarScreenPreview() {
    val today = LocalDate.now()
    DDGoTheme(darkTheme = false) {
        CalendarContent(
            uiState = CalendarUiStateMapper.createCalendarUiState(
                today = today,
                currentMonth = YearMonth.from(today),
                selectedDate = today,
                entries = emptyList(),
                summary = CalendarMonthSummary()
            ),
            onShareClick = {},
            onMonthSelected = {},
            onMarkerFilterSelected = {},
            onDateSelected = {},
            onCaptureBoundsChanged = {}
        )
    }
}
