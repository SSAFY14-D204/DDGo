// [DEBUG ONLY] 이 화면은 개발 및 테스트를 위한 디버그 전용 포즈 분석용입니다.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ddgo.app.feature.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.feature.climbing.upload.PoseOverlay
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.PoseVideoScrubber
import com.ddgo.app.feature.climbing.upload.calculateVideoContentRect
import com.ddgo.app.feature.climbing.upload.findNearestPoseForPlayback
import com.ddgo.app.feature.climbing.upload.findNearestTimestamp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrePoseLandmarkerScreen(
    viewModel: PrePoseLandmarkerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selectedVideoUri = uiState.selectedVideoUri
    val selectedVideoName = uiState.selectedVideoName
    var useOptimized by remember { mutableStateOf(true) }
    var analysisFpsLimit by remember { mutableIntStateOf(uiState.analysisFpsLimit) }
    val analysisFpsOptions = remember { listOf(10, 20, 30) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        persistReadPermission(context, uri)
        viewModel.analyzeVideo(
            uri = uri,
            displayName = context.resolveDisplayName(uri),
            useOptimized = useOptimized,
            analysisFpsLimit = analysisFpsLimit
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pre-Pose Landmarker") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        var showVideoModeDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { showVideoModeDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Video Mode 성능 최적화 설명")
            }

            if (showVideoModeDialog) {
                AlertDialog(
                    onDismissRequest = { showVideoModeDialog = false },
                    title = { Text("Video Mode 성능 최적화 설명") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("현재 두 가지 분석 모드를 지원합니다:", fontWeight = FontWeight.Bold)

                            Text("1. 일반 모드 (Normal)", fontWeight = FontWeight.SemiBold)
                            Text("• 방식: MediaCodec(YUV) → Bitmap 변환(ARGB) → CPU 리사이징 → MediaPipe(CPU/GPU)")
                            Text("• 단점: Java 레이어의 Bitmap 생성 및 픽셀 변환 오버헤드가 큼")

                            Text("2. 최적화 모드 (Optimized)", fontWeight = FontWeight.SemiBold)
                            Text("• 방식: MediaCodec(YUV) → MediaImageBuilder → MediaPipe(GPU)")
                            Text("• 장점: Bitmap 변환 생략(Zero-Copy 지향), GPU Delegate 강제 활성화, 회전 정보 처리")
                            Text("• 결과: 분석 속도가 약 2~5배 향상되며 배터리 소모 감소")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showVideoModeDialog = false }) {
                            Text("확인")
                        }
                    }
                )
            }

            Text(
                text = "영상을 미리 분석하여 재생 시 지연 없는 포즈 오버레이를 제공합니다.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = useOptimized,
                    onCheckedChange = { useOptimized = it },
                    enabled = !uiState.isAnalyzing
                )
                Text("최적화 모드 사용 (Bitmap 패스, GPU 가속)")
            }

            Text(
                text = "분석 FPS 제한",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "MediaPipe에 초당 최대 몇 프레임을 보낼지 선택합니다. 10fps가 가장 빠르고, 30fps가 가장 촘촘합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                analysisFpsOptions.forEach { fps ->
                    FilterChip(
                        selected = analysisFpsLimit == fps,
                        onClick = { analysisFpsLimit = fps },
                        enabled = !uiState.isAnalyzing,
                        label = { Text("${fps}fps") }
                    )
                }
            }

            DdgoPrimaryButton(
                text = if (uiState.isAnalyzing) "분석 중 (${(uiState.analysisProgress * 100).toInt()}%)" else "동영상 선택 및 분석 시작",
                onClick = { pickerLauncher.launch(arrayOf("video/*")) },
                enabled = !uiState.isAnalyzing,
                isLoading = uiState.isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            )

            if (uiState.isAnalyzing) {
                LinearProgressIndicator(
                    progress = { uiState.analysisProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.analysisTimeMs > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("분석 결과 리포트", fontWeight = FontWeight.Bold)
                        Text("모드: ${if (uiState.isOptimized) "최적화 (Option B)" else "일반 (Option A)"}")
                        Text("소요 시간: ${uiState.analysisTimeMs}ms")
                        Text("검출된 포즈 수: ${uiState.poseFrames.size}")
                        
                        if (uiState.poseFrames.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            )
                            
                            TextButton(
                                onClick = { viewModel.exportPoseDataToJson(context) },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("JSON 데이터 내보내기")
                            }
                        }
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }

            if (selectedVideoUri != null && !uiState.isAnalyzing && uiState.errorMessage == null) {
                PrePoseResultView(
                    videoUri = selectedVideoUri,
                    videoName = selectedVideoName,
                    poseFrames = uiState.poseFrames
                )
                
                SampleFramesSection(poseFrames = uiState.poseFrames)
            }
        }
    }
}

@Composable
private fun PrePoseResultView(
    videoUri: Uri,
    videoName: String?,
    poseFrames: List<DebugPoseFrameResult>
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    var currentPositionMs by remember(videoUri) { mutableLongStateOf(0L) }
    var durationMs by remember(videoUri) { mutableLongStateOf(0L) }
    var containerSize by remember(videoUri) { mutableStateOf(IntSize.Zero) }
    var playerVideoSize by remember(videoUri) { mutableStateOf(VideoSize.UNKNOWN) }
    var playbackState by remember(videoUri) { mutableIntStateOf(Player.STATE_IDLE) }
    var isScrubbing by remember(videoUri) { mutableStateOf(false) }
    var scrubPositionMs by remember(videoUri) { mutableLongStateOf(0L) }
    var wasPlayingBeforeScrub by remember(videoUri) { mutableStateOf(false) }
    val poseTimestamps = remember(poseFrames) {
        poseFrames.map { frame -> frame.pose.frameTimeMs }
            .distinct()
            .sorted()
    }
    val displayedPositionMs = if (isScrubbing) scrubPositionMs else currentPositionMs
    val canScrub = durationMs > 0L && poseTimestamps.isNotEmpty()

    LaunchedEffect(exoPlayer, videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        playerVideoSize = exoPlayer.videoSize
        playbackState = exoPlayer.playbackState
        durationMs = exoPlayer.duration.coerceAtLeast(0L)
    }

    LaunchedEffect(exoPlayer, isScrubbing) {
        while (true) {
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            delay(16)
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

    val currentPose = remember(poseFrames, displayedPositionMs) {
        findNearestPoseForPlayback(
            poses = poseFrames.map { frame -> frame.pose },
            positionMs = displayedPositionMs
        )
    }

    val videoContentRect = remember(containerSize, playerVideoSize) {
        calculateVideoContentRect(
            containerSize = containerSize,
            videoSize = playerVideoSize
        )
    }
    val seekToNearestPoseFrame: (Long) -> Long? = { targetTimeMs ->
        if (!canScrub) {
            null
        } else {
            poseTimestamps.findNearestTimestamp(targetTimeMs.coerceIn(0L, durationMs))
                ?.also { snappedTimeMs ->
                    exoPlayer.seekTo(snappedTimeMs)
                }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = videoName ?: "분석 완료",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .onSizeChanged { containerSize = it }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
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
                        lineColor = Color.Cyan.copy(alpha = 0.8f),
                        pointColor = Color.Magenta
                    )
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
            }

            PoseVideoScrubber(
                currentPositionMs = displayedPositionMs,
                durationMs = durationMs,
                enabled = canScrub,
                colors = PoseScrubberColors(
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(
                        alpha = if (canScrub) 0.9f else 0.4f
                    ),
                    progressColor = MaterialTheme.colorScheme.primary,
                    thumbColor = Color.White,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
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

            Text(
                text = "미리 계산된 ${poseFrames.size}개의 포즈 데이터를 사용하여 실시간으로 렌더링 중입니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun Context.resolveDisplayName(uri: Uri): String {
    val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "selected_video"
    val cursor = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    ) ?: return fallback

    cursor.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) {
            return it.getString(nameIndex) ?: fallback
        }
    }

    return fallback
}

@Composable
private fun SampleFramesSection(poseFrames: List<DebugPoseFrameResult>) {
    val sampleFrames = remember(poseFrames) {
        poseFrames.filter { it.capturedBitmap != null }
    }

    if (sampleFrames.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "MediaPipe 입력 전처리 이미지 (5초 간격)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(sampleFrames) { frame ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier
                                .size(120.dp, 160.dp), // 세로형 비율로 공간 확보
                            shape = RoundedCornerShape(8.dp),
                            color = Color.DarkGray
                        ) {
                            frame.capturedBitmap?.let { bmp ->
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Sample frame at ${frame.pose.frameTimeMs}ms",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                        Text(
                            text = "${frame.pose.frameTimeMs / 1000}s",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
