package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.PoseLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private enum class AttemptTab(val label: String) {
    Summary("간단 분석"),
    Stability("안정도"),
    Crux("크럭스")
}

private val PoseConnections = listOf(
    11 to 12, 11 to 23, 12 to 24, 23 to 24,
    11 to 13, 13 to 15, 15 to 17, 17 to 19, 19 to 15, 15 to 21,
    12 to 14, 14 to 16, 16 to 18, 18 to 20, 20 to 16, 16 to 22,
    23 to 25, 25 to 27, 27 to 29, 29 to 31, 31 to 27,
    24 to 26, 26 to 28, 28 to 30, 30 to 32, 32 to 28
)

private val PoseBoneColor = Color(0xFF00E5FF).copy(alpha = 0.88f)
private val PoseJointColor = Color.White
private val TrackBackgroundColor = Color.White.copy(alpha = 0.2f)
private val HiddenFaceLandmarkIndices = (1..10).toSet()

@Composable
fun AttemptResultScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToCompare: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val cardListState = rememberLazyListState()

    val allAttemptUris = viewModel.allAttemptUris
    val currentAttemptIndex = viewModel.currentAttemptIndex.coerceIn(
        0,
        (allAttemptUris.size - 1).coerceAtLeast(0)
    )
    val totalHolds = viewModel.detectedHolds.size.takeIf { it > 0 } ?: 14
    val attemptSummaries = remember(
        allAttemptUris,
        viewModel.analysisPoints,
        viewModel.attemptDummyResults,
        totalHolds
    ) {
        buildAttemptSummaries(
            totalAttempts = allAttemptUris.size,
            fallbackPoints = viewModel.analysisPoints,
            dummyResults = viewModel.attemptDummyResults,
            totalHolds = totalHolds
        )
    }
    val currentSummary = attemptSummaries.getOrElse(currentAttemptIndex) { attemptSummaries.first() }
    val currentVideoUri = allAttemptUris.getOrNull(currentAttemptIndex)
    val displayDate = remember(viewModel.createdChallenge?.startedAt) {
        formatAnalysisDate(viewModel.createdChallenge?.startedAt)
    }
    var selectedTab by rememberSaveable { mutableStateOf(AttemptTab.Summary) }

    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    var playerVideoSize by remember(currentVideoUri) { mutableStateOf(VideoSize.UNKNOWN) }
    var videoContainerSize by remember { mutableStateOf(IntSize.Zero) }
    val videoAspectRatio = remember(playerVideoSize) {
        resolveDisplayedVideoAspectRatio(playerVideoSize)
    }
    val videoContentRect = remember(videoContainerSize, playerVideoSize) {
        calculateVideoContentRect(
            containerSize = videoContainerSize,
            videoSize = playerVideoSize
        )
    }

    LaunchedEffect(currentVideoUri) {
        currentVideoUri?.let { uri ->
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            playerVideoSize = exoPlayer.videoSize
        } ?: run {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            playerVideoSize = VideoSize.UNKNOWN
        }
    }

    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoSize = videoSize
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(1L) }

    LaunchedEffect(exoPlayer, currentVideoUri) {
        while (isActive) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(1L)
            delay(180L)
        }
    }

    LaunchedEffect(exoPlayer, videoContentRect) {
        while (isActive) {
            delay(150L)
            if (exoPlayer.isPlaying) {
                val bitmap: Bitmap? = try {
                    textureViewRef.value?.capturePoseBitmap(videoContentRect)
                } catch (_: Exception) {
                    null
                }
                bitmap?.let { viewModel.updatePoseFrame(it) }
            }
        }
    }

    val poseLandmarks = viewModel.currentPoseLandmarks
    val activeIdx by remember(currentSummary.analysisPoints, currentPositionMs) {
        derivedStateOf {
            currentSummary.analysisPoints.indexOfLast { it.timeMs <= currentPositionMs }
        }
    }

    LaunchedEffect(activeIdx, selectedTab) {
        if (selectedTab == AttemptTab.Crux && activeIdx >= 0) {
            scope.launch { cardListState.animateScrollToItem(activeIdx) }
        }
    }

    val actionText = remember(currentAttemptIndex, allAttemptUris.size) {
        when {
            currentAttemptIndex < allAttemptUris.lastIndex -> "다음 시도 보기"
            allAttemptUris.size > 1 -> "다음 시도들과 비교분석 하기"
            else -> "문제 분석 보기"
        }
    }

    val (levelStart, levelEnd, levelGain) = remember(
        viewModel.difficultyLevel,
        currentSummary.isSuccess
    ) {
        buildLevelLabels(
            level = viewModel.difficultyLevel,
            increment = if (currentSummary.isSuccess) 20 else 10
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AnalysisBgColor)
            .statusBarsPadding()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "시도 분석",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 8.dp),
            color = AnalysisText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )

        AnalysisSectionTabs(
            labels = AttemptTab.entries.map { it.label },
            selectedIndex = selectedTab.ordinal,
            onSelected = { selectedTab = AttemptTab.entries[it] }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = viewModel.gymName.ifBlank { "클라이밍장" },
                    color = AnalysisText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displayDate,
                    color = AnalysisMuted,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (viewModel.difficultyLevel.isNotBlank()) {
                        MetaChip(
                            text = viewModel.difficultyLevel,
                            background = Color.White,
                            contentColor = Color.Black
                        )
                    }
                    if (viewModel.holdColor.isNotBlank()) {
                        MetaChip(
                            text = viewModel.holdColor,
                            background = holdColorToUiColor(viewModel.holdColor),
                            contentColor = if (viewModel.holdColor == "흰색") Color.Black else Color.White
                        )
                    }
                }
            }

            HoldOverviewPreview(
                bitmap = viewModel.bestFrameBitmap,
                holds = viewModel.detectedHolds,
                modifier = Modifier.size(width = 76.dp, height = 68.dp),
                showZoomBadge = true
            )
        }

        when (selectedTab) {
            AttemptTab.Summary -> {
                AttemptHeroSection(
                    attemptNo = currentSummary.attemptNo,
                    isSuccess = currentSummary.isSuccess,
                    videoAspectRatio = videoAspectRatio,
                    onVideoContainerMeasured = { videoContainerSize = it },
                    exoPlayer = exoPlayer,
                    textureViewRef = textureViewRef,
                    poseLandmarks = poseLandmarks,
                    videoContentRect = videoContentRect
                )

                SummaryAnalysisSection(
                    analysisPoints = currentSummary.analysisPoints,
                    onPointClick = { point ->
                        exoPlayer.seekTo(point.timeMs)
                        exoPlayer.play()
                    }
                )

                MissionSection(
                    missionLines = currentSummary.missionLines,
                    levelStart = levelStart,
                    levelEnd = levelEnd,
                    levelGain = levelGain
                )
            }

            AttemptTab.Stability -> {
                AttemptHeroSection(
                    attemptNo = currentSummary.attemptNo,
                    isSuccess = currentSummary.isSuccess,
                    videoAspectRatio = videoAspectRatio,
                    onVideoContainerMeasured = { videoContainerSize = it },
                    exoPlayer = exoPlayer,
                    textureViewRef = textureViewRef,
                    poseLandmarks = poseLandmarks,
                    videoContentRect = videoContentRect
                )

                StabilitySection(
                    summary = currentSummary,
                    totalHolds = totalHolds
                )
            }

            AttemptTab.Crux -> {
                AttemptHeroSection(
                    attemptNo = currentSummary.attemptNo,
                    isSuccess = currentSummary.isSuccess,
                    videoAspectRatio = videoAspectRatio,
                    onVideoContainerMeasured = { videoContainerSize = it },
                    exoPlayer = exoPlayer,
                    textureViewRef = textureViewRef,
                    poseLandmarks = poseLandmarks,
                    videoContentRect = videoContentRect
                )

                Column(
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text(
                        text = "실패 원인 분석",
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = AnalysisText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "원인을 누르면 해당 장면으로 클립이 바뀌어요.",
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = AnalysisMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    VideoScrubber(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        analysisPoints = currentSummary.analysisPoints,
                        activeIdx = activeIdx,
                        onSeek = { ms ->
                            exoPlayer.seekTo(ms)
                            exoPlayer.play()
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyRow(
                        state = cardListState,
                        contentPadding = PaddingValues(horizontal = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(currentSummary.analysisPoints) { index, point ->
                            AnalysisCard(
                                point = point,
                                isSelected = index == activeIdx,
                                onClick = {
                                    exoPlayer.seekTo(point.timeMs)
                                    exoPlayer.play()
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        AnalysisGradientButton(
            text = actionText,
            onClick = {
                if (currentAttemptIndex < allAttemptUris.lastIndex) {
                    viewModel.nextAttempt()
                    selectedTab = AttemptTab.Summary
                } else {
                    onNavigateToCompare()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        )

        Spacer(
            modifier = Modifier
                .height(24.dp)
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun AttemptHeroSection(
    attemptNo: Int,
    isSuccess: Boolean,
    videoAspectRatio: Float,
    onVideoContainerMeasured: (IntSize) -> Unit,
    exoPlayer: ExoPlayer,
    textureViewRef: androidx.compose.runtime.MutableState<TextureView?>,
    poseLandmarks: List<PoseLandmark>,
    videoContentRect: VideoContentRect
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${attemptNo}차 시도",
                color = AnalysisText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isSuccess) "성공" else "실패",
                color = if (isSuccess) AnalysisSuccess else AnalysisFailure,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0E0E10))
                .aspectRatio(videoAspectRatio)
                .onSizeChanged(onVideoContainerMeasured)
        ) {
            AndroidView(
                factory = { context ->
                    TextureView(context).also { textureView ->
                        textureViewRef.value = textureView
                        exoPlayer.setVideoTextureView(textureView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (poseLandmarks.size == 33) {
                    drawPoseSkeleton(
                        landmarks = poseLandmarks,
                        contentRect = videoContentRect
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
            )
        }
    }
}

@Composable
private fun SummaryAnalysisSection(
    analysisPoints: List<AnalysisPoint>,
    onPointClick: (AnalysisPoint) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
    ) {
        SectionBlock(
            title = "실패 원인 분석"
        ) {
            analysisPoints.forEach { point ->
                FailureCauseRow(
                    point = point,
                    onClick = { onPointClick(point) }
                )
            }
        }
    }
}

@Composable
private fun MissionSection(
    missionLines: List<String>,
    levelStart: String,
    levelEnd: String,
    levelGain: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(AnalysisPanelColor)
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Text(
            text = "다음 시도 미션",
            color = AnalysisText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))

        missionLines.forEach { line ->
            Text(
                text = buildMissionText(line),
                color = AnalysisText,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(26.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF191A1E))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = levelGain,
                        color = AnalysisFailure,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = levelStart,
                        color = AnalysisText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(AnalysisSecondary, AnalysisPrimary, Color(0xFFFF5DB1))
                                    )
                                )
                        )
                    }
                    Text(
                        text = levelEnd,
                        color = AnalysisMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun StabilitySection(
    summary: AnalysisAttemptSummary,
    totalHolds: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp)
            .padding(horizontal = 22.dp)
    ) {
        Text(
            text = "안정도 요약",
            color = AnalysisText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "무게중심 안정 비율",
                value = "${summary.balanceRatio}%",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "도달 홀드",
                value = "${summary.reachedHolds}/$totalHolds",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(AnalysisPanelColor)
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column {
                Text(
                    text = "흐름 그래프",
                    color = AnalysisText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                StabilityLineChart(
                    data = summary.stabilityTimeline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    focusFraction = 0.76f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (summary.isSuccess) {
                "후반으로 갈수록 안정도가 높아지며 마무리가 깔끔했습니다."
            } else {
                "중후반 구간에서 중심이 무너지는 순간이 반복되어 다음 미션이 중요합니다."
            },
            color = AnalysisMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun SectionBlock(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AnalysisPanelColor)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        content = {
            Text(
                text = title,
                color = AnalysisText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    )
}

@Composable
private fun FailureCauseRow(
    point: AnalysisPoint,
    onClick: () -> Unit
) {
    Text(
        text = buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = AnalysisPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(point.timeMs.toTimeString())
                append(" ")
            }
            append(point.description.replace("\n", " "))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 3.dp),
        color = AnalysisText,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 14.dp, vertical = 18.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = AnalysisMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MetaChip(
    text: String,
    background: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VideoScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    analysisPoints: List<AnalysisPoint>,
    activeIdx: Int,
    onSeek: (Long) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 22.dp)
            .pointerInput(analysisPoints, durationMs) {
                detectTapGestures { tap ->
                    if (durationMs <= 0L || analysisPoints.isEmpty()) return@detectTapGestures
                    val tappedMs = (tap.x / size.width.toFloat() * durationMs).toLong()
                    val nearest = analysisPoints.minByOrNull { abs(it.timeMs - tappedMs) }
                    nearest?.let { onSeek(it.timeMs) }
                }
            }
    ) {
        val safeDuration = durationMs.coerceAtLeast(1L)
        val trackY = size.height / 2f
        drawLine(
            color = TrackBackgroundColor,
            start = Offset(0f, trackY),
            end = Offset(size.width, trackY),
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = AnalysisPrimary,
            start = Offset(0f, trackY),
            end = Offset(size.width * (currentPositionMs.toFloat() / safeDuration), trackY),
            strokeWidth = 3.dp.toPx()
        )
        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = Offset(
                x = size.width * (currentPositionMs.toFloat() / safeDuration),
                y = trackY
            )
        )

        analysisPoints.forEachIndexed { index, point ->
            val centerX = size.width * (point.timeMs.toFloat() / safeDuration)
            val isSelected = index == activeIdx
            drawCircle(
                color = if (isSelected) AnalysisPrimary else Color(0xFF4C4C4C),
                radius = 11.dp.toPx(),
                center = Offset(centerX, trackY)
            )
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 10.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText(
                    "${point.index}",
                    centerX,
                    trackY + paint.textSize * 0.35f,
                    paint
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = currentPositionMs.toTimeString(),
            color = AnalysisMuted,
            fontSize = 11.sp
        )
        Text(
            text = durationMs.toTimeString(),
            color = AnalysisMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AnalysisCard(
    point: AnalysisPoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(152.dp)
            .padding(top = 14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Color.White else Color(0xFF8F8F8F))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = point.timeMs.toTimeString(),
                color = AnalysisPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = point.description.replace("\n", " "),
                color = Color(0xFF292929),
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .align(Alignment.TopStart)
                .background(
                    color = if (isSelected) AnalysisPrimary else Color.Black,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${point.index}",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun DrawScope.drawPoseSkeleton(
    landmarks: List<PoseLandmark>,
    contentRect: VideoContentRect
) {
    val drawingRect = if (contentRect.width > 0f && contentRect.height > 0f) {
        contentRect
    } else {
        VideoContentRect(
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height
        )
    }

    PoseConnections.forEach { (startIndex, endIndex) ->
        if (startIndex < landmarks.size && endIndex < landmarks.size) {
            drawLine(
                color = PoseBoneColor,
                start = Offset(
                    x = drawingRect.left + (landmarks[startIndex].x.coerceIn(0f, 1f) * drawingRect.width),
                    y = drawingRect.top + (landmarks[startIndex].y.coerceIn(0f, 1f) * drawingRect.height)
                ),
                end = Offset(
                    x = drawingRect.left + (landmarks[endIndex].x.coerceIn(0f, 1f) * drawingRect.width),
                    y = drawingRect.top + (landmarks[endIndex].y.coerceIn(0f, 1f) * drawingRect.height)
                ),
                strokeWidth = 2.4.dp.toPx()
            )
        }
    }

    landmarks.forEach { landmark ->
        if (landmark.index in HiddenFaceLandmarkIndices) return@forEach
        drawCircle(
            color = PoseJointColor,
            radius = 3.3.dp.toPx(),
            center = Offset(
                x = drawingRect.left + (landmark.x.coerceIn(0f, 1f) * drawingRect.width),
                y = drawingRect.top + (landmark.y.coerceIn(0f, 1f) * drawingRect.height)
            )
        )
    }
}

internal fun holdColorToUiColor(name: String): Color = when (name) {
    "빨강" -> Color(0xFFE94C4C)
    "주황" -> Color(0xFFFF8A34)
    "노랑" -> Color(0xFFFFD54F)
    "초록" -> Color(0xFF4CAF50)
    "파랑" -> Color(0xFF2196F3)
    "남색" -> Color(0xFF3F51B5)
    "보라" -> Color(0xFF8B5CFF)
    "분홍" -> Color(0xFFF16698)
    "흰색" -> Color(0xFFECECEC)
    "검정" -> Color(0xFF242424)
    "갈색" -> Color(0xFF8D6E63)
    else -> Color(0xFF607D8B)
}

private fun Long.toTimeString(): String =
    "%02d:%02d".format(this / 60_000L, (this / 1_000L) % 60L)

private fun TextureView.capturePoseBitmap(contentRect: VideoContentRect): Bitmap? {
    if (!isAvailable || width <= 0 || height <= 0) return null

    val longestSide = max(width, height)
    val scale = (MaxPoseCaptureDimensionPx.toFloat() / longestSide.toFloat()).coerceAtMost(1f)
    val captureWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val captureHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val capturedBitmap = getBitmap(captureWidth, captureHeight) ?: return null

    if (contentRect.width <= 0f || contentRect.height <= 0f) {
        return capturedBitmap
    }

    val scaleX = captureWidth / width.toFloat()
    val scaleY = captureHeight / height.toFloat()
    val cropLeft = (contentRect.left * scaleX).roundToInt().coerceIn(0, captureWidth - 1)
    val cropTop = (contentRect.top * scaleY).roundToInt().coerceIn(0, captureHeight - 1)
    val cropWidth = (contentRect.width * scaleX).roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(captureWidth - cropLeft)
    val cropHeight = (contentRect.height * scaleY).roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(captureHeight - cropTop)

    val shouldCrop = cropLeft > 0 ||
        cropTop > 0 ||
        cropWidth < captureWidth ||
        cropHeight < captureHeight
    if (!shouldCrop) {
        return capturedBitmap
    }

    return Bitmap.createBitmap(
        capturedBitmap,
        cropLeft,
        cropTop,
        cropWidth,
        cropHeight
    ).also {
        capturedBitmap.recycle()
    }
}

private fun resolveDisplayedVideoAspectRatio(videoSize: VideoSize): Float {
    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return DefaultVideoAspectRatio
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return DefaultVideoAspectRatio
    }

    return displayedWidth / displayedHeight
}

private fun calculateVideoContentRect(
    containerSize: IntSize,
    videoSize: VideoSize
): VideoContentRect {
    val containerWidth = containerSize.width.toFloat()
    val containerHeight = containerSize.height.toFloat()
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return VideoContentRect(0f, 0f, 0f, 0f)
    }

    if (videoSize.width <= 0 || videoSize.height <= 0) {
        return VideoContentRect(0f, 0f, containerWidth, containerHeight)
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return VideoContentRect(0f, 0f, containerWidth, containerHeight)
    }

    val videoAspectRatio = displayedWidth / displayedHeight
    val containerAspectRatio = containerWidth / containerHeight

    return if (containerAspectRatio > videoAspectRatio) {
        val fittedHeight = containerHeight
        val fittedWidth = fittedHeight * videoAspectRatio
        VideoContentRect(
            left = (containerWidth - fittedWidth) / 2f,
            top = 0f,
            width = fittedWidth,
            height = fittedHeight
        )
    } else {
        val fittedWidth = containerWidth
        val fittedHeight = fittedWidth / videoAspectRatio
        VideoContentRect(
            left = 0f,
            top = (containerHeight - fittedHeight) / 2f,
            width = fittedWidth,
            height = fittedHeight
        )
    }
}

private data class VideoContentRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private const val DefaultVideoAspectRatio = 331f / 428f
private const val MaxPoseCaptureDimensionPx = 720
