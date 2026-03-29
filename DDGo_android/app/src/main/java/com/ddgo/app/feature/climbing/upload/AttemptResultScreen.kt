package com.ddgo.app.feature.climbing.upload

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.R
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSection
import com.ddgo.app.feature.climbing.upload.ui.shared.organism.AttemptVideoSectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private val C_BG = Color(0xFF0D0D0D)
private val C_ACCENT = Color(0xFF2979FF)
private val C_ACCENT_GLOW = Color(0xFF82B1FF)
private val C_BONE = Color(0xFF00E5FF).copy(alpha = 0.85f)
private val C_JOINT = Color.White
private val C_TIMESTAMP_CARD = Color(0xFF1294FF)
private val C_TIMESTAMP_TEXT = Color(0xFF626262)
private val C_TIMESTAMP_BORDER = Color(0xFF121212)
private val HIDDEN_FACE_LANDMARK_INDICES = (1..10).toSet()
private val ANALYSIS_CARD_WIDTH = 160.dp
private val ANALYSIS_CARD_HEIGHT = 104.dp
private val VIDEO_FRAME_TOP_SAFE_INSET = 24.dp
private val VIDEO_FRAME_BOTTOM_SAFE_INSET = 64.dp
private val VIDEO_CONTROL_AREA_HEIGHT = 132.dp

@UnstableApi
@Composable
fun AttemptResultScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    isRealtimeAttemptFlow: Boolean = false,
    onNavigateToCompare: () -> Unit = {},
    onNavigateToAddAttempt: () -> Unit = {}
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val cardListState = rememberLazyListState()
    var bottomActionAreaHeightPx by remember { mutableIntStateOf(0) }

    val currentAttemptIndex = viewModel.currentAttemptIndex
    val playbackAttemptUris = viewModel.playbackAttemptUris
    val currentVideoUri = playbackAttemptUris.getOrNull(currentAttemptIndex)
    val hasChallenge = (viewModel.challengeId ?: 0L) > 0L

    val presentationResults = viewModel.attemptPresentationResults
    val currentAttemptResult = presentationResults.getOrElse(
        currentAttemptIndex.coerceAtLeast(0)
    ) {
        presentationResults.firstOrNull() ?: (false to emptyList())
    }
    val currentAnalysisPoints = remember(currentAttemptResult.second) {
        currentAttemptResult.second.toAttemptDisplayAnalysisPoints()
    }
    val cardAnalysisPoints = remember(currentAnalysisPoints) {
        currentAnalysisPoints.filterNot(AnalysisPoint::isAttemptStartPoint)
    }
    val currentAttemptHoldReachResult = viewModel.currentAttemptHoldReachResult
    val totalSelectedHoldCount = viewModel.totalSelectedHoldCount
    val isSuccess = currentAttemptHoldReachResult?.let { result ->
        totalSelectedHoldCount > 0 && result.completedWithBothHandsOnEndHold
    } ?: currentAttemptResult.first
    val currentAttemptPoses = viewModel.currentAttemptPoseSequence
    val currentAttemptOverlayCache = viewModel.currentAttemptOverlayCache
    val currentAttemptPrePoseEntry = viewModel.currentAttemptPrePoseEntry
    val officialAttemptStartTimeMs = currentAttemptPrePoseEntry.officialAttemptStartTimeMs()
    val usesPoseDetectorTimeline = currentAttemptPrePoseEntry != null
    val numberedHolds = viewModel.currentAttemptDisplayHolds
    val allRawHolds = viewModel.allRawHolds
    val selectedAttemptCropBounds = remember(numberedHolds) {
        calculateExpandedVerticalCropBoundsFromSelectedHoldExtents(numberedHolds)
    }
    val endpointStatusMessage = remember(currentAttemptPrePoseEntry, currentAnalysisPoints) {
        when {
            currentAttemptPrePoseEntry == null -> null
            currentAttemptPrePoseEntry.status == PrePoseStatus.Pending ||
                currentAttemptPrePoseEntry.status == PrePoseStatus.Running -> "분석 포인트를 계산 중입니다"

            currentAttemptPrePoseEntry.status == PrePoseStatus.Failed -> "pre-pose 분석에 실패했습니다"
            currentAttemptPrePoseEntry.status == PrePoseStatus.Ready &&
                currentAnalysisPoints.isEmpty() -> "분석 포인트를 찾지 못함"

            else -> null
        }
    }

    var displayedPositionMs by remember(currentVideoUri) { mutableLongStateOf(0L) }
    var seekRequestId by rememberSaveable(currentVideoUri) { mutableLongStateOf(0L) }
    var pendingSeekTimeMs by rememberSaveable(currentVideoUri) { mutableStateOf<Long?>(null) }
    var tappedCardOverrideIdx by rememberSaveable(currentVideoUri) { mutableIntStateOf(-1) }
    LaunchedEffect(currentVideoUri, numberedHolds, allRawHolds, selectedAttemptCropBounds) {
        if (currentVideoUri.isNullOrBlank()) return@LaunchedEffect

        val normalizedBoxes = if (numberedHolds.isEmpty()) {
            "[]"
        } else {
            numberedHolds.joinToString(prefix = "[", postfix = "]") { numbered ->
                "#${numbered.holdNo}=${UploadAiTraceLogger.formatBoundingBox(numbered.hold.boundingBox)}"
            }
        }
        val rawHoldBounds = if (allRawHolds.isEmpty()) {
            "[]"
        } else {
            allRawHolds.joinToString(prefix = "[", postfix = "]") { hold ->
                UploadAiTraceLogger.formatBoundingBox(hold.boundingBox)
            }
        }

        UploadAiTraceLogger.log(
            event = "attempt_result_screen_rects_resolved",
            playbackUri = currentVideoUri,
            details = mapOf(
                "cropSource" to if (selectedAttemptCropBounds != null) "selectedExtentHolds" else "uncroppedFallback",
                "resolvedCropBounds" to UploadAiTraceLogger.formatCropBounds(selectedAttemptCropBounds),
                "cropHoldCount" to numberedHolds.size,
                "displayHoldCount" to numberedHolds.size,
                "cropMarginMode" to "visibleHeightDiv10",
                "rawHoldBounds" to rawHoldBounds,
                "normalizedBBoxes" to normalizedBoxes
            )
        )
    }
    val activeCardIdx by remember(cardAnalysisPoints, displayedPositionMs, tappedCardOverrideIdx) {
        derivedStateOf {
            resolveActiveAnalysisCardIndex(
                points = cardAnalysisPoints,
                displayedPositionMs = displayedPositionMs,
                tappedCardOverrideIdx = tappedCardOverrideIdx
            )
        }
    }
    val activeMarkerIdx by remember(currentAnalysisPoints, displayedPositionMs) {
        derivedStateOf {
            resolvePlaybackActiveAnalysisCardIndex(
                points = currentAnalysisPoints,
                displayedPositionMs = displayedPositionMs
            )
        }
    }
    val scrubberMarkers = remember(currentAnalysisPoints, activeMarkerIdx) {
        currentAnalysisPoints.mapIndexed { index, point ->
            PoseScrubberMarker(
                index = point.index,
                timeMs = point.timeMs,
                kind = point.kind,
                isSelected = index == activeMarkerIdx
            )
        }
    }

    LaunchedEffect(currentVideoUri) {
        displayedPositionMs = 0L
        seekRequestId = 0L
        pendingSeekTimeMs = null
        tappedCardOverrideIdx = -1
    }

    LaunchedEffect(cardAnalysisPoints, tappedCardOverrideIdx) {
        if (tappedCardOverrideIdx !in cardAnalysisPoints.indices) {
            tappedCardOverrideIdx = -1
        }
    }

    LaunchedEffect(cardAnalysisPoints, displayedPositionMs, tappedCardOverrideIdx) {
        if (
            tappedCardOverrideIdx >= 0 &&
            !shouldKeepTappedAnalysisCardOverride(
                points = cardAnalysisPoints,
                tappedCardOverrideIdx = tappedCardOverrideIdx,
                displayedPositionMs = displayedPositionMs
            )
        ) {
            tappedCardOverrideIdx = -1
        }
    }

    LaunchedEffect(activeCardIdx) {
        if (activeCardIdx >= 0) {
            scope.launch {
                cardListState.animateScrollToItem(activeCardIdx)
            }
        }
    }
    val bottomActionAreaPadding = with(density) { bottomActionAreaHeightPx.toDp() + 24.dp }

    SafeAreaScreen(containerColor = C_BG) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = bottomActionAreaPadding)
        ) {
            HeaderSection(viewModel)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentAttemptIndex + 1}차 시도",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                val statusText = if (isSuccess) "성공" else "실패"
                val statusColor = if (isSuccess) Color(0xFF4CAF50) else Color(0xFFE53935)
                Text(
                    text = statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            AttemptVideoSection(
                state = AttemptVideoSectionState(
                    videoUri = currentVideoUri,
                    numberedHolds = numberedHolds,
                    rawHolds = allRawHolds,
                    viewportCropBounds = selectedAttemptCropBounds,
                    analysisPoints = currentAnalysisPoints,
                    markers = scrubberMarkers,
                    attemptPoseSequence = currentAttemptPoses,
                    overlayCache = currentAttemptOverlayCache,
                    wallArrivalTimeMs = officialAttemptStartTimeMs,
                    personObservationStartTimeMs = currentAttemptPrePoseEntry?.personObservationStartTimeMs,
                    seekRequestId = seekRequestId,
                    seekRequestTimeMs = pendingSeekTimeMs
                ),
                modifier = Modifier.fillMaxWidth(),
                lineColor = C_BONE,
                pointColor = C_JOINT,
                scrubberColors = PoseScrubberColors(
                    trackColor = Color.White.copy(alpha = 0.18f),
                    progressColor = C_ACCENT_GLOW,
                    thumbColor = Color.White,
                    textColor = Color.White.copy(alpha = 0.82f)
                ),
                controlSurfaceColor = C_BG,
                hiddenLandmarkIndices = HIDDEN_FACE_LANDMARK_INDICES,
                topSafeInset = VIDEO_FRAME_TOP_SAFE_INSET,
                bottomSafeInset = VIDEO_FRAME_BOTTOM_SAFE_INSET,
                controlAreaHeight = VIDEO_CONTROL_AREA_HEIGHT,
                logDisplayedPoseRawData = true,
                onDisplayedPositionChanged = { displayedPositionMs = it },
                topOverlayContent = {
                    if (currentAttemptPrePoseEntry?.status == PrePoseStatus.Failed) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "pre-pose를 불러오지 못했습니다",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = if (isSuccess) "성공 분석 및 타임라인" else "실패 원인 분석",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "원인을 누르면 해당 장면으로 이동합니다",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))

            if (currentAnalysisPoints.isEmpty() && endpointStatusMessage != null) {
                Text(
                    text = endpointStatusMessage,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else if (cardAnalysisPoints.isNotEmpty()) {
                LazyRow(
                    state = cardListState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(cardAnalysisPoints) { idx, point ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(400, delayMillis = idx * 120)) +
                                slideInVertically(tween(400, delayMillis = idx * 120)) { it / 2 }
                        ) {
                            AnalysisCard(
                                point = point,
                                isSelected = idx == activeCardIdx,
                                onClick = {
                                    val targetSeekMs = resolveAnalysisSeekTimeMs(
                                        point = point,
                                        usesPoseDetectorTimeline = usesPoseDetectorTimeline
                                    )
                                    tappedCardOverrideIdx = idx
                                    displayedPositionMs = targetSeekMs
                                    pendingSeekTimeMs = targetSeekMs
                                    seekRequestId += 1L
                                }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomActionAreaHeightPx = it.height }
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, C_BG.copy(alpha = 0.95f), C_BG)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val isLastAttempt = currentAttemptIndex >= playbackAttemptUris.size - 1
            val primaryButtonText = when {
                isRealtimeAttemptFlow -> "최종 분석 보기"
                isLastAttempt -> "최종 분석 결과 보기"
                else -> "다음 시도 보기"
            }
            val primaryButtonColor = when {
                isRealtimeAttemptFlow -> Color(0xFF673AB7)
                isLastAttempt -> Color(0xFF673AB7)
                else -> Color(0xFF03A9F4)
            }
            val secondaryButtonText = if (isRealtimeAttemptFlow) {
                "다시 찍기"
            } else {
                "같은 문제 다시 시도 업로드"
            }
            val showSecondaryAction = isRealtimeAttemptFlow || hasChallenge
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (isRealtimeAttemptFlow || isLastAttempt) {
                            onNavigateToCompare()
                        } else {
                            viewModel.nextAttempt()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryButtonColor
                    )
                ) {
                    Text(
                        text = primaryButtonText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (showSecondaryAction) {
                    Button(
                        onClick = onNavigateToAddAttempt,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2B2B2E),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = secondaryButtonText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        UploadBackgroundUploadSnackbarHost(
            viewModel = viewModel,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 96.dp)
        )
        }
    }
}

@Composable
private fun HeaderSection(viewModel: UploadViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = viewModel.gymName.ifBlank { "클라이밍장" },
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            if (viewModel.difficultyLevel.isNotBlank()) {
                InfoChip(text = viewModel.difficultyLevel, bg = Color(0xFF333333))
                Spacer(Modifier.width(6.dp))
            }
            if (viewModel.holdColor.isNotBlank()) {
                InfoChip(text = viewModel.holdColor, bg = holdColorToUiColor(viewModel.holdColor))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = LocalDate.now().run { "${year}년 ${monthValue}월 ${dayOfMonth}일" },
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun InfoChip(text: String, bg: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

internal fun holdColorToUiColor(name: String): Color {
    return holdLabelToComposeColor(name)
}

@Composable
private fun AnalysisCard(
    point: AnalysisPoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(ANALYSIS_CARD_WIDTH)
            .padding(top = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(ANALYSIS_CARD_HEIGHT)
                .shadow(
                    elevation = if (isSelected) 10.dp else 6.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Color.Black.copy(alpha = 0.28f),
                    spotColor = Color.Black.copy(alpha = 0.28f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) Color(0xFFF7FBFF) else Color.White)
                .border(
                    width = 2.dp,
                    color = if (isSelected) C_TIMESTAMP_CARD else C_TIMESTAMP_BORDER,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = point.timeMs.toTimeString(),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = C_TIMESTAMP_CARD
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = point.description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = C_TIMESTAMP_TEXT,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = (-10).dp)
                .clip(CircleShape)
                .background(if (isSelected) C_ACCENT else C_TIMESTAMP_CARD)
        ) {
            Text(
                text = "${point.index}",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }
    }
}

private fun Long.toTimeString() =
    "%02d:%02d".format(this / 60_000L, (this / 1_000L) % 60L)

private fun List<AnalysisPoint>.toAttemptDisplayAnalysisPoints(): List<AnalysisPoint> {
    var nextDisplayIndex = 1
    return map { point ->
        if (point.isAttemptStartPoint()) {
            point.copy(index = 0)
        } else {
            point.copy(index = nextDisplayIndex++)
        }
    }
}

private fun AnalysisPoint.isAttemptStartPoint(): Boolean =
    kind == AnalysisPointKind.PERSON_OBSERVATION_START

