package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.PoseLandmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas as FCanvas
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// ── MediaPipe Pose 연결선 (COCO 33 랜드마크) ─────────────────────────────────
private val POSE_CONNECTIONS = listOf(
    // 몸통
    11 to 12, 11 to 23, 12 to 24, 23 to 24,
    // 왼팔
    11 to 13, 13 to 15, 15 to 17, 17 to 19, 19 to 15, 15 to 21,
    // 오른팔
    12 to 14, 14 to 16, 16 to 18, 18 to 20, 20 to 16, 16 to 22,
    // 왼다리
    23 to 25, 25 to 27, 27 to 29, 29 to 31, 31 to 27,
    // 오른다리
    24 to 26, 26 to 28, 28 to 30, 30 to 32, 32 to 28
)

// ── 색상 상수 ─────────────────────────────────────────────────────────────────
private val C_BG          = Color(0xFF0D0D0D)
private val C_CARD        = Color(0xFF1E1E1E)
private val C_CARD_SEL    = Color(0xFF1A2744)
private val C_ACCENT      = Color(0xFF2979FF)
private val C_ACCENT_GLOW = Color(0xFF82B1FF)
private val C_BONE        = Color(0xFF00E5FF).copy(alpha = 0.85f)
private val C_JOINT       = Color.White
private val C_TRACK_BG    = Color.White.copy(alpha = 0.18f)

@Composable
fun AttemptResultScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToCompare: () -> Unit = {}
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val scrollState   = rememberScrollState()
    val cardListState = rememberLazyListState()

    val currentAttemptIndex = viewModel.currentAttemptIndex
    val allAttemptUris = viewModel.allAttemptUris
    val currentVideoUri = allAttemptUris.getOrNull(currentAttemptIndex)

    // (임시) 현재 시도의 더미 분석 결과 가져오기
    // 실제 연동 시에는 서버에서 받아온 List<AttemptAnalysis> 에서 currentAttemptIndex로 조회
    val dummyResult = viewModel.attemptDummyResults.getOrElse(currentAttemptIndex % viewModel.attemptDummyResults.size) { viewModel.attemptDummyResults.first() }
    val isSuccess = dummyResult.first
    val currentAnalysisPoints = dummyResult.second
    val poseLandmarks = viewModel.currentPoseLandmarks

    // ── ExoPlayer 초기화 (URL 변경 시 재초기화 하거나 setMediaItem 교체) ──────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
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
    
    // URI가 바뀔 때마다 플레이어 아이템 교체
    LaunchedEffect(currentVideoUri) {
        currentVideoUri?.let { uri ->
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            playerVideoSize = exoPlayer.videoSize
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

    // ── 재생 위치 추적 (200ms 간격) ──────────────────────────────────────────
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs        by remember { mutableStateOf(1L) }
    var isPlaying         by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer, currentVideoUri) {
        while (isActive) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs        = exoPlayer.duration.coerceAtLeast(1L)
            isPlaying         = exoPlayer.isPlaying
            delay(200L)
        }
    }

    // ── 실시간 포즈 추론 (150ms 간격, 재생 중에만) ───────────────────────────
    LaunchedEffect(exoPlayer, videoContentRect) {
        while (isActive) {
            delay(150L)
            if (exoPlayer.isPlaying) {
                val bmp: Bitmap? = try {
                    textureViewRef.value?.capturePoseBitmap(videoContentRect)
                } catch (_: Exception) { null }
                bmp?.let { viewModel.updatePoseFrame(it) }
            }
        }
    }

    // ── 현재 위치에서 활성 분석 포인트 인덱스 (0-based) ─────────────────────
    val activeIdx by remember(currentAnalysisPoints, currentPositionMs) {
        derivedStateOf {
            currentAnalysisPoints.indexOfLast { it.timeMs <= currentPositionMs }
        }
    }

    // 활성 카드 자동 스크롤
    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0) scope.launch { cardListState.animateScrollToItem(activeIdx) }
    }

    // ── 레이아웃: Box 루트 → 버튼 하단 고정 ─────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C_BG)
    ) {
        // 스크롤 가능한 콘텐츠 (버튼 높이만큼 하단 여백)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 88.dp)
        ) {

            // 헤더
            HeaderSection(viewModel)

            // N차 시도 및 성공/실패 여부 UI
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

            // 비디오 + 포즈 오버레이
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio)
                    .onSizeChanged { videoContainerSize = it }
            ) {
                // ExoPlayer → TextureView
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv ->
                            textureViewRef.value = tv
                            exoPlayer.setVideoTextureView(tv)
                            exoPlayer.playWhenReady = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // MediaPipe 포즈 스켈레톤 오버레이
                FCanvas(modifier = Modifier.fillMaxSize()) {
                    if (poseLandmarks.size == 33) {
                        drawPoseSkeleton(
                            landmarks = poseLandmarks,
                            contentRect = videoContentRect
                        )
                    }
                }

                // 재생/일시정지 토글 (화면 탭)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                )

                // 하단 그라디언트 페이드
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, C_BG)))
                )
            }

            // 커스텀 스크러버
            Spacer(Modifier.height(4.dp))
            VideoScrubber(
                currentPositionMs = currentPositionMs,
                durationMs        = durationMs,
                analysisPoints    = currentAnalysisPoints,
                activeIdx         = activeIdx,
                onSeek            = { ms -> exoPlayer.seekTo(ms); exoPlayer.play() }
            )
            Spacer(Modifier.height(20.dp))

            // 분석 섹션 헤더
            val resultTitle = if (isSuccess) "성공 분석 및 타임라인" else "실패 원인 분석"
            Text(
                text       = resultTitle,
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                modifier   = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = "원인을 누르면 해당 장면으로 클립이 바뀌어요.",
                fontSize = 13.sp,
                color    = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(14.dp))

            // 분석 타임라인 카드 (수평 스크롤)
            LazyRow(
                state                 = cardListState,
                contentPadding        = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(currentAnalysisPoints) { idx, point ->
                    AnimatedVisibility(
                        visible = true,
                        enter   = fadeIn(tween(400, delayMillis = idx * 120)) +
                                  slideInVertically(tween(400, delayMillis = idx * 120)) { it / 2 }
                    ) {
                        AnalysisCard(
                            point      = point,
                            isSelected = idx == activeIdx,
                            onClick    = { exoPlayer.seekTo(point.timeMs); exoPlayer.play() }
                        )
                    }
                }
            }
        }

        // ── 하단 고정 버튼 (다음 시도 보기 or 완료) ───────────────────────────
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
            val isLastAttempt = currentAttemptIndex >= allAttemptUris.size - 1
            Button(
                onClick  = {
                    if (isLastAttempt) {
                        // TODO: 전체 서머리로 이동하거나 메인으로 이동
                        onNavigateToCompare()
                    } else {
                        viewModel.nextAttempt()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLastAttempt) Color(0xFF673AB7) else Color(0xFF03A9F4) // 보라색 / 파란색
                )
            ) {
                Text(
                    text       = if (isLastAttempt) "최종 분석 결과 보기" else "다음 시도 보기",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
        }
    }
}

// ── 헤더 섹션 ─────────────────────────────────────────────────────────────────

@Composable
private fun HeaderSection(viewModel: UploadViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Text(
                text       = viewModel.gymName.ifBlank { "클라이밍장" },
                fontSize   = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Color.White,
                modifier   = Modifier.weight(1f)
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
            text     = LocalDate.now().run { "${year}년 ${monthValue}월 ${dayOfMonth}일" },
            fontSize = 13.sp,
            color    = Color.White.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun InfoChip(text: String, bg: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

internal fun holdColorToUiColor(name: String) = when (name) {
    "빨강" -> Color(0xFFEF5350); "주황" -> Color(0xFFFF7043)
    "노랑" -> Color(0xFFFFCA28); "초록" -> Color(0xFF66BB6A)
    "파랑" -> Color(0xFF42A5F5); "남색" -> Color(0xFF3949AB)
    "보라" -> Color(0xFFAB47BC); "분홍" -> Color(0xFFEC407A)
    "흰색" -> Color(0xFFE0E0E0); "검정" -> Color(0xFF424242)
    "갈색" -> Color(0xFF8D6E63); else  -> Color(0xFF607D8B)
}

// ── 커스텀 스크러버 ────────────────────────────────────────────────────────────

@Composable
private fun VideoScrubber(
    currentPositionMs: Long,
    durationMs: Long,
    analysisPoints: List<AnalysisPoint>,
    activeIdx: Int,
    onSeek: (Long) -> Unit
) {
    val progress by animateFloatAsState(
        targetValue   = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f,
        animationSpec = tween(180),
        label         = "progress"
    )

    // 스크러버 트랙 + 뱃지 (Canvas로 단일 레이어)
    FCanvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 20.dp)
            .pointerInput(analysisPoints, durationMs) {
                detectTapGestures { tap ->
                    if (durationMs <= 0 || analysisPoints.isEmpty()) return@detectTapGestures
                    // 탭한 위치의 시간으로 가장 가까운 분석 포인트 seek
                    val tappedMs = (tap.x / size.width.toFloat() * durationMs).toLong()
                    val nearest  = analysisPoints.minByOrNull { abs(it.timeMs - tappedMs) }
                    nearest?.let { onSeek(it.timeMs) }
                }
            }
    ) {
        val trackY = size.height / 2f
        val trackH = 3.dp.toPx()

        // 트랙 배경
        drawLine(C_TRACK_BG, Offset(0f, trackY), Offset(size.width, trackY), trackH)
        // 재생 진행 바
        drawLine(C_ACCENT_GLOW, Offset(0f, trackY), Offset(size.width * progress, trackY), trackH)
        // 재생 헤드
        drawCircle(Color.White, 6.dp.toPx(), Offset(size.width * progress, trackY))

        // 분석 포인트 뱃지
        analysisPoints.forEachIndexed { idx, point ->
            val bx     = size.width * (point.timeMs.toFloat() / durationMs)
            val radius = 11.dp.toPx()
            val sel    = idx == activeIdx

            drawCircle(if (sel) C_ACCENT else Color(0xFF424242), radius, Offset(bx, trackY))
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize    = 10.sp.toPx()
                    typeface    = android.graphics.Typeface.DEFAULT_BOLD
                    color       = android.graphics.Color.WHITE
                    textAlign   = android.graphics.Paint.Align.CENTER
                }
                canvas.nativeCanvas.drawText(
                    "${point.index}", bx, trackY + paint.textSize * 0.35f, paint
                )
            }
        }
    }

    // 시간 표시
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(currentPositionMs.toTimeString(), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
        Text(durationMs.toTimeString(),        fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f))
    }
}

// ── 분석 카드 ────────────────────────────────────────────────────────────────

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
                modifier         = Modifier
                    .size(26.dp)
                    .background(
                        if (isSelected) C_ACCENT else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
            ) {
                Text(
                    "${point.index}",
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text       = point.timeMs.toTimeString(),
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = if (isSelected) C_ACCENT_GLOW else Color.White
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text       = point.description,
            fontSize   = 13.sp,
            color      = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            lineHeight = 19.sp
        )
    }
}

// ── DrawScope 헬퍼: 포즈 스켈레톤 ────────────────────────────────────────────

private fun DrawScope.drawPoseSkeleton(
    landmarks: List<PoseLandmark>,
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

    // 연결선 (뼈)
    POSE_CONNECTIONS.forEach { (si, ei) ->
        if (si < landmarks.size && ei < landmarks.size) {
            drawLine(
                color       = C_BONE,
                start       = Offset(
                    x = drawArea.left + (landmarks[si].x.coerceIn(0f, 1f) * drawArea.width),
                    y = drawArea.top + (landmarks[si].y.coerceIn(0f, 1f) * drawArea.height)
                ),
                end         = Offset(
                    x = drawArea.left + (landmarks[ei].x.coerceIn(0f, 1f) * drawArea.width),
                    y = drawArea.top + (landmarks[ei].y.coerceIn(0f, 1f) * drawArea.height)
                ),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
    // 관절 점
    landmarks.forEach { lm ->
        if (lm.index in HIDDEN_FACE_LANDMARK_INDICES) return@forEach
        drawCircle(
            color = C_JOINT,
            radius = 3.5.dp.toPx(),
            center = Offset(
                x = drawArea.left + (lm.x.coerceIn(0f, 1f) * drawArea.width),
                y = drawArea.top + (lm.y.coerceIn(0f, 1f) * drawArea.height)
            )
        )
    }
}

// ── 유틸 ─────────────────────────────────────────────────────────────────────

private fun Long.toTimeString() =
    "%02d:%02d".format(this / 60_000L, (this / 1_000L) % 60L)

private fun TextureView.capturePoseBitmap(contentRect: VideoContentRect): Bitmap? {
    if (!isAvailable || width <= 0 || height <= 0) return null

    val longestSide = max(width, height)
    val scale = (MAX_POSE_CAPTURE_DIMENSION_PX.toFloat() / longestSide.toFloat()).coerceAtMost(1f)
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
        return DEFAULT_VIDEO_ASPECT_RATIO
    }

    val sourceWidth = videoSize.width * videoSize.pixelWidthHeightRatio
    val sourceHeight = videoSize.height.toFloat()
    val isRotated = videoSize.unappliedRotationDegrees % 180 != 0
    val displayedWidth = if (isRotated) sourceHeight else sourceWidth
    val displayedHeight = if (isRotated) sourceWidth else sourceHeight
    if (displayedWidth <= 0f || displayedHeight <= 0f) {
        return DEFAULT_VIDEO_ASPECT_RATIO
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

private val HIDDEN_FACE_LANDMARK_INDICES = (1..10).toSet()

private const val DEFAULT_VIDEO_ASPECT_RATIO = 9f / 16f
private const val MAX_POSE_CAPTURE_DIMENSION_PX = 720
