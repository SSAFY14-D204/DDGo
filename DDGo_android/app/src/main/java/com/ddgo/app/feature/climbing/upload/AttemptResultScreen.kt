package com.ddgo.app.feature.climbing.upload

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.usecase.HoldNumbered
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

private val C_BG = Color(0xFF0D0D0D)
private val C_CARD = Color(0xFF1E1E1E)
private val C_CARD_SEL = Color(0xFF1A2744)
private val C_ACCENT = Color(0xFF2979FF)
private val C_ACCENT_GLOW = Color(0xFF82B1FF)
private val C_BONE = Color(0xFF00E5FF).copy(alpha = 0.85f)
private val C_JOINT = Color.White
private val HIDDEN_FACE_LANDMARK_INDICES = (1..10).toSet()

@Composable
fun AttemptResultScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToCompare: () -> Unit = {},
    onNavigateToAddAttempt: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val cardListState = rememberLazyListState()

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
    val currentAttemptHoldReachResult = viewModel.currentAttemptHoldReachResult
    val totalSelectedHoldCount = viewModel.totalSelectedHoldCount
    val isSuccess = currentAttemptHoldReachResult?.let { result ->
        totalSelectedHoldCount > 0 && result.highestReachedHoldNo >= totalSelectedHoldCount
    } ?: currentAttemptResult.first
    val currentAnalysisPoints = currentAttemptResult.second
    val currentAttemptPoses = viewModel.currentAttemptPoseSequence
    val currentAttemptPrePoseEntry = viewModel.currentAttemptPrePoseEntry
    val personObservationStartTimeMs = currentAttemptPrePoseEntry?.personObservationStartTimeMs
    val usesPoseDetectorTimeline = currentAttemptPrePoseEntry != null
    val numberedHolds = viewModel.numberedHolds
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

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var playerVideoSize by remember(currentVideoUri) { mutableStateOf(VideoSize.UNKNOWN) }
    var videoContainerSize by remember { mutableStateOf(IntSize.Zero) }
    var currentPositionMs by remember(currentVideoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(currentVideoUri) { mutableLongStateOf(0L) }
    var isPlaying by remember(currentVideoUri) { mutableStateOf(false) }
    var playbackState by remember(currentVideoUri) { mutableStateOf(Player.STATE_IDLE) }
    var isScrubbing by rememberSaveable(currentVideoUri) { mutableStateOf(false) }
    var scrubPositionMs by rememberSaveable(currentVideoUri) { mutableLongStateOf(0L) }
    var hasInitialAutoSeekApplied by rememberSaveable(currentVideoUri) { mutableStateOf(false) }
    var wasPlayingBeforeScrub by remember(currentVideoUri) { mutableStateOf(false) }

    val poseTimestamps = remember(currentAttemptPoses) {
        currentAttemptPoses
            .map { pose -> pose.frameTimeMs }
            .distinct()
            .sorted()
    }
    val displayedPositionMs = if (isScrubbing) scrubPositionMs else currentPositionMs
    val canScrub = durationMs > 0L && poseTimestamps.isNotEmpty()

    val videoAspectRatio = remember(playerVideoSize) {
        resolveDisplayedVideoAspectRatio(playerVideoSize)
    }
    val videoContentRect = remember(videoContainerSize, playerVideoSize) {
        calculateVideoContentRect(
            containerSize = videoContainerSize,
            videoSize = playerVideoSize
        )
    }

    val currentPose = remember(currentAttemptPoses, displayedPositionMs) {
        findNearestPoseForPlayback(
            poses = currentAttemptPoses,
            positionMs = displayedPositionMs
        )
    }

    val activeIdx by remember(currentAnalysisPoints, displayedPositionMs) {
        derivedStateOf {
            currentAnalysisPoints.indexOfLast { point -> point.timeMs <= displayedPositionMs }
        }
    }
    val scrubberMarkers = remember(currentAnalysisPoints, activeIdx) {
        currentAnalysisPoints.mapIndexed { index, point ->
            PoseScrubberMarker(
                index = point.index,
                timeMs = point.timeMs,
                isSelected = index == activeIdx
            )
        }
    }

    LaunchedEffect(currentVideoUri) {
        currentPositionMs = 0L
        durationMs = 0L
        scrubPositionMs = 0L
        isScrubbing = false
        currentVideoUri?.let { videoUri ->
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            playerVideoSize = exoPlayer.videoSize
        }
    }

    LaunchedEffect(currentVideoUri, personObservationStartTimeMs, poseTimestamps, hasInitialAutoSeekApplied) {
        if (currentVideoUri == null || hasInitialAutoSeekApplied) return@LaunchedEffect
        val initialStartMs = resolveInitialAttemptPlaybackStartTimeMs(
            personObservationStartTimeMs = personObservationStartTimeMs,
            poseTimestamps = poseTimestamps
        ) ?: return@LaunchedEffect
        exoPlayer.seekTo(initialStartMs)
        currentPositionMs = initialStartMs
        scrubPositionMs = initialStartMs
        hasInitialAutoSeekApplied = true
    }

    LaunchedEffect(exoPlayer, currentVideoUri, isScrubbing) {
        while (isActive) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            playbackState = exoPlayer.playbackState
            isPlaying = exoPlayer.isPlaying
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(16L)
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoSize = videoSize
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                if (!isScrubbing) {
                    currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0) {
            scope.launch {
                cardListState.animateScrollToItem(activeIdx)
            }
        }
    }

    val seekToNearestPoseFrame: (Long) -> Long? = { targetTimeMs ->
        if (!canScrub) {
            null
        } else {
            poseTimestamps
                .findNearestTimestamp(targetTimeMs.coerceIn(0L, durationMs))
                ?.also(exoPlayer::seekTo)
        }
    }

    SafeAreaScreen(containerColor = C_BG) {
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 88.dp)
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio)
                    .onSizeChanged { videoContainerSize = it }
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    }
                )

                currentPose?.let { pose ->
                    PoseOverlay(
                        pose = pose,
                        contentRect = videoContentRect,
                        modifier = Modifier.fillMaxSize(),
                        lineColor = C_BONE,
                        pointColor = C_JOINT,
                        hiddenLandmarkIndices = HIDDEN_FACE_LANDMARK_INDICES
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (numberedHolds.isNotEmpty()) {
                        drawHoldNumbers(
                            holds = numberedHolds,
                            contentRect = videoContentRect
                        )
                    }
                }

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

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            if (playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0L)
                            }
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, C_BG)))
                )
            }

            Spacer(Modifier.height(4.dp))

            PoseVideoScrubber(
                currentPositionMs = displayedPositionMs,
                durationMs = durationMs,
                enabled = canScrub,
                markers = scrubberMarkers,
                colors = PoseScrubberColors(
                    trackColor = Color.White.copy(alpha = if (canScrub) 0.18f else 0.08f),
                    progressColor = C_ACCENT_GLOW,
                    thumbColor = Color.White,
                    textColor = Color.White.copy(alpha = 0.65f)
                ),
                modifier = Modifier.padding(horizontal = 20.dp),
                onTapSeek = { requestedTimeMs ->
                    val wasPlaying = exoPlayer.isPlaying
                    seekToNearestPoseFrame(requestedTimeMs)?.let { snappedTimeMs ->
                        currentPositionMs = snappedTimeMs
                        scrubPositionMs = snappedTimeMs
                        if (wasPlaying) {
                            exoPlayer.play()
                        } else {
                            exoPlayer.pause()
                        }
                    }
                },
                onScrubStart = {
                    if (!canScrub) return@PoseVideoScrubber
                    wasPlayingBeforeScrub = exoPlayer.isPlaying
                    scrubPositionMs = poseTimestamps.findNearestTimestamp(displayedPositionMs)
                        ?: displayedPositionMs
                    isScrubbing = true
                    exoPlayer.pause()
                },
                onScrubMove = { requestedTimeMs ->
                    if (!canScrub) return@PoseVideoScrubber
                    seekToNearestPoseFrame(requestedTimeMs)?.let { snappedTimeMs ->
                        scrubPositionMs = snappedTimeMs
                        currentPositionMs = snappedTimeMs
                    }
                },
                onScrubStop = {
                    if (!isScrubbing) return@PoseVideoScrubber
                    val finalTimeMs = poseTimestamps.findNearestTimestamp(scrubPositionMs)
                        ?: scrubPositionMs
                    exoPlayer.seekTo(finalTimeMs)
                    currentPositionMs = finalTimeMs
                    scrubPositionMs = finalTimeMs
                    isScrubbing = false
                    if (wasPlayingBeforeScrub) {
                        exoPlayer.play()
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
            } else {
                LazyRow(
                    state = cardListState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(currentAnalysisPoints) { idx, point ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(400, delayMillis = idx * 120)) +
                                slideInVertically(tween(400, delayMillis = idx * 120)) { it / 2 }
                        ) {
                            AnalysisCard(
                                point = point,
                                isSelected = idx == activeIdx,
                                onClick = {
                                    exoPlayer.seekTo(
                                        resolveAnalysisSeekTimeMs(
                                            point = point,
                                            usesPoseDetectorTimeline = usesPoseDetectorTimeline
                                        )
                                    )
                                    exoPlayer.play()
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
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, C_BG.copy(alpha = 0.95f), C_BG)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            val isLastAttempt = currentAttemptIndex >= playbackAttemptUris.size - 1
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (isLastAttempt) {
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
                        containerColor = if (isLastAttempt) Color(0xFF673AB7) else Color(0xFF03A9F4)
                    )
                ) {
                    Text(
                        text = if (isLastAttempt) "최종 분석 결과 보기" else "다음 시도 보기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (hasChallenge) {
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
                            text = "같은 문제 다시 시도 업로드",
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

internal fun holdColorToUiColor(name: String) = when (name) {
    "빨강" -> Color(0xFFEF5350)
    "주황" -> Color(0xFFFF7043)
    "노랑" -> Color(0xFFFFCA28)
    "초록" -> Color(0xFF66BB6A)
    "파랑" -> Color(0xFF42A5F5)
    "남색" -> Color(0xFF3949AB)
    "보라" -> Color(0xFFAB47BC)
    "분홍" -> Color(0xFFEC407A)
    "회색" -> Color(0xFFE0E0E0)
    "검정" -> Color(0xFF424242)
    "갈색" -> Color(0xFF8D6E63)
    else -> Color(0xFF607D8B)
}

@Composable
private fun AnalysisCard(
    point: AnalysisPoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) C_CARD_SEL else C_CARD)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        if (isSelected) C_ACCENT else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
            ) {
                Text(
                    "${point.index}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = point.timeMs.toTimeString(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) C_ACCENT_GLOW else Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = point.description,
            fontSize = 13.sp,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            lineHeight = 19.sp
        )
    }
}

private fun DrawScope.drawHoldNumbers(
    holds: List<HoldNumbered>,
    contentRect: VideoContentRect
) {
    val drawArea = if (contentRect.width > 0f && contentRect.height > 0f) {
        contentRect
    } else {
        VideoContentRect(
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height
        )
    }

    holds.forEach { numbered ->
        val rect = numbered.hold.toScreenRect(
            offX = drawArea.left,
            offY = drawArea.top,
            scaledW = drawArea.width,
            scaledH = drawArea.height
        )
        val center = Offset(
            x = (rect.l + rect.r) / 2f,
            y = (rect.t + rect.b) / 2f
        )
        val badgeRadius = minOf(rect.r - rect.l, rect.b - rect.t)
            .times(0.22f)
            .coerceIn(12.dp.toPx(), 20.dp.toPx())
        val fillColor = when {
            numbered.isStart -> COLOR_START
            numbered.isEnd -> COLOR_END
            else -> holdLabelToComposeColor(numbered.hold.colorLabel)
        }.copy(alpha = 0.92f)
        val textColor = if (fillColor.luminance() < 0.45f) Color.White else Color.Black

        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = badgeRadius + 3.dp.toPx(),
            center = center
        )
        drawCircle(
            color = fillColor,
            radius = badgeRadius,
            center = center
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = badgeRadius,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = textColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = (badgeRadius * 1.05f).coerceAtLeast(12.sp.toPx())
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val baseline = center.y - ((paint.descent() + paint.ascent()) / 2f)
            canvas.nativeCanvas.drawText(
                numbered.holdNo.toString(),
                center.x,
                baseline,
                paint
            )
        }
    }
}

private fun Long.toTimeString() =
    "%02d:%02d".format(this / 60_000L, (this / 1_000L) % 60L)
