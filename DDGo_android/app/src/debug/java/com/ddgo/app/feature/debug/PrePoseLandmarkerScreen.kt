// [DEBUG ONLY] 이 화면은 개발 및 테스트를 위한 디버그 전용 포즈 분석용입니다.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ddgo.app.feature.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
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
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.feature.climbing.upload.PoseOverlay
import com.ddgo.app.feature.climbing.upload.PoseScrubberColors
import com.ddgo.app.feature.climbing.upload.PoseScrubberMarker
import com.ddgo.app.feature.climbing.upload.PoseVideoScrubber
import com.ddgo.app.feature.climbing.upload.calculateVideoContentRect
import com.ddgo.app.feature.climbing.upload.findNearestPoseForPlayback
import com.ddgo.app.feature.climbing.upload.findNearestTimestamp
import com.ddgo.app.feature.climbing.upload.resolveAnalysisSeekTimeMs
import com.ddgo.app.feature.climbing.upload.toVideoTimeString
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sqrt

private const val SMOOTH_FILTER_DEFAULT_ANALYSIS_FPS_LIMIT = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrePoseLandmarkerScreen(
    viewModel: PrePoseLandmarkerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSmoothFilterCompare: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selectedVideoUri = uiState.selectedVideoUri
    val selectedVideoName = uiState.selectedVideoName
    var useOptimized by remember { mutableStateOf(true) }
    var useGpuAcceleration by remember { mutableStateOf(uiState.useGpuAcceleration) }
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
            useGpuAcceleration = useGpuAcceleration,
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
                            Text("• 방식: MediaCodec(YUV) → Bitmap 변환(ARGB) → CPU 리사이징 → MediaPipe(CPU/GPU 선택)")
                            Text("• 단점: Java 레이어의 Bitmap 생성 및 픽셀 변환 오버헤드가 큼")

                            Text("2. 최적화 모드 (Optimized)", fontWeight = FontWeight.SemiBold)
                            Text("• 방식: MediaCodec(YUV) → MediaImageBuilder → MediaPipe(CPU/GPU 선택)")
                            Text("• 장점: Bitmap 변환 생략(Zero-Copy 지향), 회전 정보 처리")
                            Text("• GPU를 켜면 지원 기기에서 더 빠를 수 있고, 실패 시 CPU로 자동 폴백됩니다.")
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
                Text("최적화 모드 사용 (Surface/Zero-Copy 경로)")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = useGpuAcceleration,
                    onCheckedChange = { useGpuAcceleration = it },
                    enabled = !uiState.isAnalyzing
                )
                Text("MediaPipe GPU 가속 사용")
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

            OutlinedButton(
                onClick = onNavigateToSmoothFilterCompare,
                enabled = !uiState.isAnalyzing && uiState.poseFrames.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Smooth Filter")
            }

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
                        Text(
                            text = uiState.handPeakAnnotation?.endTimeMs?.toVideoTimeString()
                                ?.let { endPointTime -> "종료 지점: $endPointTime" }
                                ?: "종료 지점을 찾지 못함"
                        )
                        
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
                    poseFrames = uiState.poseFrames,
                    analysisPoints = uiState.analysisPoints
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
    poseFrames: List<DebugPoseFrameResult>,
    analysisPoints: List<AnalysisPoint>
) {
    PosePlaybackCard(
        videoUri = videoUri,
        title = videoName ?: "Analysis Result",
        poseFrames = poseFrames,
        analysisPoints = analysisPoints,
        lineColor = Color.Cyan.copy(alpha = 0.8f),
        pointColor = Color.Magenta,
        helperText = "Playing ${poseFrames.size} precomputed poses in sync with the original video."
    )
}

@Composable
private fun PosePlaybackCard(
    videoUri: Uri,
    title: String,
    poseFrames: List<DebugPoseFrameResult>,
    analysisPoints: List<AnalysisPoint>,
    lineColor: Color,
    pointColor: Color,
    helperText: String
) {
    val context = LocalContext.current
    val videoName = title
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
    val activeIdx by remember(analysisPoints, displayedPositionMs) {
        derivedStateOf {
            analysisPoints.indexOfLast { point -> point.timeMs <= displayedPositionMs }
        }
    }
    val scrubberMarkers = remember(analysisPoints, activeIdx) {
        analysisPoints.mapIndexed { index, point ->
            PoseScrubberMarker(
                index = point.index,
                timeMs = point.timeMs,
                isSelected = index == activeIdx
            )
        }
    }

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
                        lineColor = lineColor,
                        pointColor = pointColor
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
                markers = scrubberMarkers,
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

            analysisPoints.firstOrNull()?.let { endPoint ->
                TextButton(
                    onClick = {
                        val seekStartMs = resolveAnalysisSeekTimeMs(
                            point = endPoint,
                            usesPoseDetectorTimeline = true
                        )
                        seekToNearestPoseFrame(seekStartMs)?.let { snappedTimeMs ->
                            currentPositionMs = snappedTimeMs
                            scrubPositionMs = snappedTimeMs
                            exoPlayer.play()
                        }
                    },
                    enabled = canScrub,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("종료 지점 보기 (${endPoint.timeMs.toVideoTimeString()})")
                }
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrePoseLandmarkerSmoothFilterCompareScreen(
    viewModel: PrePoseLandmarkerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selectedVideoUri = uiState.selectedVideoUri
    val selectedVideoName = uiState.selectedVideoName
    val poseFrames = uiState.poseFrames
    var useOptimized by remember { mutableStateOf(uiState.isOptimized) }
    var analysisFpsLimit by remember {
        mutableIntStateOf(
            uiState.analysisFpsLimit.takeIf { it > 0 } ?: SMOOTH_FILTER_DEFAULT_ANALYSIS_FPS_LIMIT
        )
    }
    var selectedMode by remember { mutableStateOf(FilterMode.CausalLiveLike) }
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
    val filterSpecs = remember(selectedMode) {
        poseSmoothingSpecs(selectedMode)
    }
    var selectedFilterKey by remember(poseFrames, selectedMode) {
        mutableStateOf(RAW_FILTER_KEY)
    }
    val filteredPoseFramesByMode = remember(poseFrames) {
        buildFilteredPoseFramesByMode(poseFrames)
    }
    val filteredPoseFramesByKey = remember(filteredPoseFramesByMode, selectedMode) {
        filteredPoseFramesByMode[selectedMode].orEmpty()
    }
    val motionByFilterKey = remember(filteredPoseFramesByKey) {
        buildMeanMotionByFilterKey(filteredPoseFramesByKey)
    }
    val selectedSpec = remember(selectedFilterKey, filterSpecs) {
        filterSpecs.firstOrNull { it.key == selectedFilterKey } ?: filterSpecs.firstOrNull()
    }
    val selectedPoseFrames = remember(filteredPoseFramesByKey, selectedFilterKey) {
        filteredPoseFramesByKey[selectedFilterKey].orEmpty()
    }
    val rawMotion = motionByFilterKey[RAW_FILTER_KEY] ?: 0f
    val selectedMotion = motionByFilterKey[selectedFilterKey] ?: 0f
    val selectedMotionRatio = if (rawMotion > 0f) selectedMotion / rawMotion else 1f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smooth Filter Compare") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedVideoName != null) {
                Card {
                    Text(
                        text = selectedVideoName,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = useOptimized,
                    onCheckedChange = { useOptimized = it },
                    enabled = !uiState.isAnalyzing
                )
                Text("Use optimized pre-pose analysis")
            }

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
                text = if (uiState.isAnalyzing) {
                    "Analyzing (${(uiState.analysisProgress * 100).toInt()}%)"
                } else {
                    "Pick Video And Rebuild Filters"
                },
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

            uiState.errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (poseFrames.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Run pre-pose analysis first to preview smoothing playback.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                return@Column
            }

            SmoothFilterModeSelectorCard(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selectedMode.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = selectedMode.subtitle,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = selectedSpec?.label ?: "Raw",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = selectedSpec?.subtitle ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "avg motion ${formatMotion(selectedMotion)} | vs raw ${formatRatio(selectedMotionRatio)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            SmoothFilterSelectorCard(
                specs = filterSpecs,
                selectedFilterKey = selectedFilterKey,
                onFilterSelected = { selectedFilterKey = it }
            )

            if (selectedVideoUri == null || selectedPoseFrames.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Select a video to start playback.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                PosePlaybackCard(
                    videoUri = selectedVideoUri,
                    title = "${selectedMode.shortLabel} · ${selectedSpec?.label ?: "Raw"}",
                    poseFrames = selectedPoseFrames,
                    analysisPoints = uiState.analysisPoints,
                    lineColor = (selectedSpec?.accent ?: Color.Cyan).copy(alpha = 0.88f),
                    pointColor = Color.White,
                    helperText = "Showing ${selectedMode.shortLabel} playback with ${selectedPoseFrames.size} cached poses."
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LegacyPrePoseLandmarkerSmoothFilterCompareScreen(
    viewModel: PrePoseLandmarkerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val poseFrames = uiState.poseFrames
    val selectedVideoName = uiState.selectedVideoName
    var useOptimized by remember { mutableStateOf(uiState.isOptimized) }
    var analysisFpsLimit by remember {
        mutableIntStateOf(
            uiState.analysisFpsLimit.takeIf { it > 0 } ?: SMOOTH_FILTER_DEFAULT_ANALYSIS_FPS_LIMIT
        )
    }
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
    val comparisons = remember(poseFrames) {
        buildPoseSmoothingComparisons(poseFrames)
    }
    val sampleFrames = remember(poseFrames) {
        poseFrames.filter { it.capturedBitmap != null }.ifEmpty { poseFrames }
    }

    var selectedFrameTimeMs by remember(poseFrames) {
        mutableLongStateOf(sampleFrames.firstOrNull()?.pose?.frameTimeMs ?: 0L)
    }

    LaunchedEffect(sampleFrames) {
        val initialTime = sampleFrames.firstOrNull()?.pose?.frameTimeMs ?: 0L
        if (sampleFrames.isNotEmpty() && sampleFrames.none { it.pose.frameTimeMs == selectedFrameTimeMs }) {
            selectedFrameTimeMs = initialTime
        }
    }

    val selectedSourceFrame = remember(poseFrames, selectedFrameTimeMs) {
        findNearestDebugPoseFrame(poseFrames, selectedFrameTimeMs)
    }
    val selectedComparisonCards = remember(comparisons, selectedFrameTimeMs) {
        comparisons.map { comparison ->
            comparison.copy(
                selectedFrame = findNearestDebugPoseFrame(
                    comparison.frames,
                    selectedFrameTimeMs
                )
            )
        }
    }
    val rawMotion = selectedComparisonCards.firstOrNull { it.spec.key == RAW_FILTER_KEY }?.meanMotion ?: 0f
    val strongestReduction = selectedComparisonCards.minByOrNull { it.motionRatio }?.spec?.label ?: "-"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smooth Filter Compare") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedVideoName != null) {
                Card {
                    Text(
                        text = selectedVideoName,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = useOptimized,
                    onCheckedChange = { useOptimized = it },
                    enabled = !uiState.isAnalyzing
                )
                Text("최적화된 pre-pose 분석 사용")
            }

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
                text = if (uiState.isAnalyzing) {
                    "분석 중 (${(uiState.analysisProgress * 100).toInt()}%)"
                } else {
                    "동영상 선택 및 비교 시작"
                },
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

            uiState.errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (poseFrames.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "비교할 pre-pose 데이터가 없습니다.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                return@Column
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Smooth Filter Debug",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "같은 pre-pose 데이터를 여러 temporal filter로 나란히 비교합니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Raw motion = ${formatMotion(rawMotion)} | Most stable = $strongestReduction",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "샘플 프레임 선택",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sampleFrames) { frame ->
                    val isSelected = frame.pose.frameTimeMs == selectedFrameTimeMs
                    Surface(
                        onClick = { selectedFrameTimeMs = frame.pose.frameTimeMs },
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = if (isSelected) 4.dp else 1.dp,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val bmp = frame.capturedBitmap
                            val aspectRatio = if (bmp != null && bmp.height > 0) {
                                bmp.width.toFloat() / bmp.height.toFloat()
                            } else {
                                16f / 9f
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspectRatio)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10131A))
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRect(color = Color(0xFF161A22))
                                    }
                                }
                            }
                            Text(
                                text = "${frame.pose.frameTimeMs / 1000}s",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Text(
                text = "필터 비교",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2
            ) {
                selectedComparisonCards.forEach { comparison ->
                    SmoothFilterComparisonCard(
                        comparison = comparison,
                        fallbackBitmap = selectedSourceFrame?.capturedBitmap
                    )
                }
            }
        }
    }
}

@Composable
private fun SmoothFilterModeSelectorCard(
    selectedMode: FilterMode,
    onModeSelected: (FilterMode) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.shortLabel) }
                    )
                }
            }
            Text(
                text = selectedMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmoothFilterSelectorCard(
    specs: List<PoseSmoothingSpec>,
    selectedFilterKey: String,
    onFilterSelected: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Filter Playback",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            specs.forEach { spec ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .selectable(
                            selected = selectedFilterKey == spec.key,
                            onClick = { onFilterSelected(spec.key) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = selectedFilterKey == spec.key,
                        onClick = null
                    )
                    Column {
                        Text(
                            text = spec.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = spec.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmoothFilterComparisonCard(
    comparison: PoseSmoothingComparison,
    fallbackBitmap: android.graphics.Bitmap? = null
) {
    val selectedFrame = comparison.selectedFrame
    val previewBitmap = selectedFrame?.capturedBitmap ?: fallbackBitmap
    val previewAspectRatio = remember(previewBitmap) {
        if (previewBitmap != null && previewBitmap.height > 0) {
            previewBitmap.width.toFloat() / previewBitmap.height.toFloat()
        } else {
            16f / 9f
        }
    }
    var previewSize by remember(comparison.spec.key, selectedFrame?.pose?.frameTimeMs) {
        mutableStateOf(IntSize.Zero)
    }
    val previewVideoSize = previewBitmap?.let { VideoSize(it.width, it.height) } ?: VideoSize(16, 9)
    val previewContentRect = remember(previewSize, previewVideoSize) {
        calculateVideoContentRect(
            containerSize = previewSize,
            videoSize = previewVideoSize
        )
    }

    Card(
        modifier = Modifier.widthIn(min = 280.dp, max = 420.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(comparison.spec.accent, RoundedCornerShape(50))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comparison.spec.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = comparison.spec.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(previewAspectRatio)
                    .onSizeChanged { previewSize = it }
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF10131A))
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRect(color = Color(0xFF161A22))
                    }
                }

                selectedFrame?.let { frame ->
                    PoseOverlay(
                        pose = frame.pose,
                        contentRect = previewContentRect,
                        modifier = Modifier.fillMaxSize(),
                        lineColor = comparison.spec.accent.copy(alpha = 0.88f),
                        pointColor = Color.White
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "avg motion ${formatMotion(comparison.meanMotion)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "vs raw ${formatRatio(comparison.motionRatio)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "frame ${selectedFrame?.pose?.frameTimeMs ?: 0L}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildFilteredPoseFramesByMode(
    frames: List<DebugPoseFrameResult>
): Map<FilterMode, Map<String, List<DebugPoseFrameResult>>> {
    if (frames.isEmpty()) return emptyMap()

    return FilterMode.entries.associateWith { mode ->
        buildFilteredPoseFramesByKey(
            frames = frames,
            specs = poseSmoothingSpecs(mode)
        )
    }
}

private fun buildFilteredPoseFramesByKey(
    frames: List<DebugPoseFrameResult>,
    specs: List<PoseSmoothingSpec>
): Map<String, List<DebugPoseFrameResult>> {
    if (frames.isEmpty()) return emptyMap()

    return specs.associate { spec ->
        spec.key to spec.buildFrames(frames)
    }
}

private fun buildMeanMotionByFilterKey(
    filteredPoseFramesByKey: Map<String, List<DebugPoseFrameResult>>
): Map<String, Float> {
    return filteredPoseFramesByKey.mapValues { (_, frames) ->
        calculateMeanMotion(frames)
    }
}

private enum class FilterMode(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val description: String
) {
    CausalLiveLike(
        label = "Causal / Live-like",
        shortLabel = "Causal",
        subtitle = "Past-only smoothing. Playback can feel delayed like live filtering.",
        description = "Uses only previous frames, so noise drops but lag can remain."
    ),
    OfflineNoLag(
        label = "Offline / No-Lag",
        shortLabel = "Offline",
        subtitle = "Future-aware smoothing for precomputed playback.",
        description = "Uses the full pre-pose sequence to reduce noise without adding phase lag."
    )
}

private data class PoseSmoothingSpec(
    val key: String,
    val label: String,
    val subtitle: String,
    val accent: Color,
    val buildFrames: (List<DebugPoseFrameResult>) -> List<DebugPoseFrameResult>
)

private data class PoseSmoothingComparison(
    val spec: PoseSmoothingSpec,
    val frames: List<DebugPoseFrameResult>,
    val meanMotion: Float,
    val motionRatio: Float,
    val selectedFrame: DebugPoseFrameResult? = null
)

private interface PoseSmoother {
    fun smooth(frame: DebugPoseFrameResult): Pose
}

private fun buildPoseSmoothingComparisons(
    frames: List<DebugPoseFrameResult>
): List<PoseSmoothingComparison> {
    if (frames.isEmpty()) return emptyList()

    val rawMotion = calculateMeanMotion(frames)
    return poseSmoothingSpecs(FilterMode.CausalLiveLike).map { spec ->
        val filteredFrames = spec.buildFrames(frames)
        val motion = calculateMeanMotion(filteredFrames)
        PoseSmoothingComparison(
            spec = spec,
            frames = filteredFrames,
            meanMotion = motion,
            motionRatio = if (rawMotion > 0f) motion / rawMotion else 1f
        )
    }
}

private fun applyPoseSmoother(
    frames: List<DebugPoseFrameResult>,
    smoother: PoseSmoother
): List<DebugPoseFrameResult> {
    return frames.map { frame ->
        frame.copy(pose = smoother.smooth(frame))
    }
}

private fun poseSmoothingSpecs(mode: FilterMode): List<PoseSmoothingSpec> {
    return when (mode) {
        FilterMode.CausalLiveLike -> causalPoseSmoothingSpecs()
        FilterMode.OfflineNoLag -> offlinePoseSmoothingSpecs()
    }
}

private fun causalPoseSmoothingSpecs(): List<PoseSmoothingSpec> = listOf(
    PoseSmoothingSpec(
        key = RAW_FILTER_KEY,
        label = "Raw",
        subtitle = "baseline frame sequence",
        accent = Color(0xFF8AA1FF),
        buildFrames = { frames -> frames }
    ),
    PoseSmoothingSpec(
        key = EMA_FILTER_KEY,
        label = "EMA",
        subtitle = "past-only smoothing, live-like lag",
        accent = Color(0xFF67D4A4),
        buildFrames = { frames -> applyPoseSmoother(frames, EmaPoseSmoother(alpha = 0.35f)) }
    ),
    PoseSmoothingSpec(
        key = MOVING_AVERAGE_FILTER_KEY,
        label = "Moving Average",
        subtitle = "trailing window average",
        accent = Color(0xFFFFC46B),
        buildFrames = { frames -> applyPoseSmoother(frames, MovingAveragePoseSmoother(windowSize = 5)) }
    ),
    PoseSmoothingSpec(
        key = MEDIAN_FILTER_KEY,
        label = "Median",
        subtitle = "trailing window median",
        accent = Color(0xFFFF7A90),
        buildFrames = { frames -> applyPoseSmoother(frames, MedianPoseSmoother(windowSize = 5)) }
    ),
    PoseSmoothingSpec(
        key = ONE_EURO_FILTER_KEY,
        label = "One Euro",
        subtitle = "adaptive online-style smoothing",
        accent = Color(0xFFB18CFF),
        buildFrames = {
            frames -> applyPoseSmoother(
                frames,
                OneEuroPoseSmoother(minCutoff = 1.15f, beta = 0.007f, dCutoff = 1.0f)
            )
        }
    )
)

private fun offlinePoseSmoothingSpecs(): List<PoseSmoothingSpec> = listOf(
    PoseSmoothingSpec(
        key = RAW_FILTER_KEY,
        label = "Raw",
        subtitle = "baseline frame sequence",
        accent = Color(0xFF8AA1FF),
        buildFrames = { frames -> frames }
    ),
    PoseSmoothingSpec(
        key = ZERO_PHASE_EMA_FILTER_KEY,
        label = "Zero-Phase EMA",
        subtitle = "forward + backward EMA",
        accent = Color(0xFF67D4A4),
        buildFrames = { frames -> applyZeroPhaseEma(frames, alpha = 0.35f) }
    ),
    PoseSmoothingSpec(
        key = CENTERED_MOVING_AVERAGE_FILTER_KEY,
        label = "Centered Moving Average",
        subtitle = "future-aware window average",
        accent = Color(0xFFFFC46B),
        buildFrames = { frames -> applyCenteredMovingAverage(frames, windowSize = 5) }
    ),
    PoseSmoothingSpec(
        key = CENTERED_MEDIAN_FILTER_KEY,
        label = "Centered Median",
        subtitle = "future-aware window median",
        accent = Color(0xFFFF7A90),
        buildFrames = { frames -> applyCenteredMedian(frames, windowSize = 5) }
    )
)

private object RawPoseSmoother : PoseSmoother {
    override fun smooth(frame: DebugPoseFrameResult): Pose = frame.pose
}

private class EmaPoseSmoother(
    private val alpha: Float
) : PoseSmoother {
    private var previousPose: Pose? = null

    override fun smooth(frame: DebugPoseFrameResult): Pose {
        val current = frame.pose
        val previous = previousPose
        if (previous == null) {
            previousPose = current
            return current
        }

        val smoothedLandmarks = current.landmarks.mapIndexed { index, currentLandmark ->
            val previousLandmark = previous.landmarks.getOrNull(index) ?: currentLandmark
            currentLandmark.blend(previousLandmark, alpha)
        }

        val pose = current.copy(landmarks = smoothedLandmarks)
        previousPose = pose
        return pose
    }
}

private class MovingAveragePoseSmoother(
    private val windowSize: Int
) : PoseSmoother {
    private val history = ArrayDeque<Pose>()

    override fun smooth(frame: DebugPoseFrameResult): Pose {
        history.addLast(frame.pose)
        while (history.size > windowSize) {
            history.removeFirst()
        }
        return averagePose(frame.pose, history.toList())
    }
}

private class MedianPoseSmoother(
    private val windowSize: Int
) : PoseSmoother {
    private val history = ArrayDeque<Pose>()

    override fun smooth(frame: DebugPoseFrameResult): Pose {
        history.addLast(frame.pose)
        while (history.size > windowSize) {
            history.removeFirst()
        }
        return medianPose(frame.pose, history.toList())
    }
}

private class OneEuroPoseSmoother(
    private val minCutoff: Float,
    private val beta: Float,
    private val dCutoff: Float
) : PoseSmoother {
    private val landmarkFilters = mutableMapOf<Int, OneEuroLandmarkFilter>()

    override fun smooth(frame: DebugPoseFrameResult): Pose {
        val timeSec = frame.pose.frameTimeMs / 1000f
        val filteredLandmarks = frame.pose.landmarks.map { landmark ->
            val filter = landmarkFilters.getOrPut(landmark.index) {
                OneEuroLandmarkFilter(
                    minCutoff = minCutoff,
                    beta = beta,
                    dCutoff = dCutoff
                )
            }
            filter.filter(landmark, timeSec)
        }
        return frame.pose.copy(landmarks = filteredLandmarks)
    }
}

private class OneEuroLandmarkFilter(
    private val minCutoff: Float,
    private val beta: Float,
    private val dCutoff: Float
) {
    private val xFilter = OneEuroScalarFilter(minCutoff, beta, dCutoff)
    private val yFilter = OneEuroScalarFilter(minCutoff, beta, dCutoff)
    private val zFilter = OneEuroScalarFilter(minCutoff, beta, dCutoff)
    private val visibilityFilter = OneEuroScalarFilter(minCutoff, 0f, dCutoff)
    private val presenceFilter = OneEuroScalarFilter(minCutoff, 0f, dCutoff)

    fun filter(landmark: PoseLandmark, timeSec: Float): PoseLandmark {
        return landmark.copy(
            x = xFilter.filter(landmark.x, timeSec),
            y = yFilter.filter(landmark.y, timeSec),
            z = zFilter.filter(landmark.z, timeSec),
            visibility = landmark.visibility?.let { visibilityFilter.filter(it, timeSec) },
            presence = landmark.presence?.let { presenceFilter.filter(it, timeSec) }
        )
    }
}

private class OneEuroScalarFilter(
    private val minCutoff: Float,
    private val beta: Float,
    private val dCutoff: Float
) {
    private var previousValue: Float? = null
    private var previousDerivative: Float = 0f
    private var previousTimeSec: Float? = null

    fun filter(value: Float, timeSec: Float): Float {
        val previous = previousValue
        val previousTime = previousTimeSec
        if (previous == null || previousTime == null) {
            previousValue = value
            previousTimeSec = timeSec
            previousDerivative = 0f
            return value
        }

        val dt = (timeSec - previousTime).coerceAtLeast(1e-3f)
        val derivative = (value - previous) / dt
        val derivativeAlpha = smoothingAlpha(dCutoff, dt)
        val filteredDerivative = lowPass(derivative, previousDerivative, derivativeAlpha)
        val cutoff = minCutoff + beta * abs(filteredDerivative)
        val alpha = smoothingAlpha(cutoff, dt)
        val filteredValue = lowPass(value, previous, alpha)

        previousDerivative = filteredDerivative
        previousValue = filteredValue
        previousTimeSec = timeSec
        return filteredValue
    }
}

private fun applyZeroPhaseEma(
    frames: List<DebugPoseFrameResult>,
    alpha: Float
): List<DebugPoseFrameResult> {
    if (frames.isEmpty()) return emptyList()

    val forward = applyPoseSmoother(frames, EmaPoseSmoother(alpha))
    val backward = applyPoseSmoother(forward.asReversed(), EmaPoseSmoother(alpha)).asReversed()
    return frames.indices.map { index ->
        frames[index].copy(pose = backward[index].pose)
    }
}

private fun applyCenteredMovingAverage(
    frames: List<DebugPoseFrameResult>,
    windowSize: Int
): List<DebugPoseFrameResult> {
    return applyCenteredPoseReducer(
        frames = frames,
        windowSize = windowSize,
        reducer = ::averagePose
    )
}

private fun applyCenteredMedian(
    frames: List<DebugPoseFrameResult>,
    windowSize: Int
): List<DebugPoseFrameResult> {
    return applyCenteredPoseReducer(
        frames = frames,
        windowSize = windowSize,
        reducer = ::medianPose
    )
}

private fun applyCenteredPoseReducer(
    frames: List<DebugPoseFrameResult>,
    windowSize: Int,
    reducer: (Pose, List<Pose>) -> Pose
): List<DebugPoseFrameResult> {
    if (frames.isEmpty()) return emptyList()

    val normalizedWindowSize = normalizeWindowSize(windowSize)
    val halfWindow = normalizedWindowSize / 2
    return frames.mapIndexed { index, frame ->
        val samples = (-halfWindow..halfWindow).map { offset ->
            frames[mirrorIndex(index + offset, frames.size)].pose
        }
        frame.copy(pose = reducer(frame.pose, samples))
    }
}

private fun normalizeWindowSize(windowSize: Int): Int {
    val clamped = windowSize.coerceAtLeast(1)
    return if (clamped % 2 == 0) clamped + 1 else clamped
}

private fun mirrorIndex(index: Int, size: Int): Int {
    if (size <= 1) return 0

    val lastIndex = size - 1
    var mirrored = index
    while (mirrored < 0 || mirrored > lastIndex) {
        mirrored = if (mirrored < 0) {
            -mirrored
        } else {
            2 * lastIndex - mirrored
        }
    }
    return mirrored
}

private fun averagePose(
    template: Pose,
    history: List<Pose>
): Pose {
    val smoothedLandmarks = template.landmarks.mapIndexed { index, currentLandmark ->
        val samples = history.mapNotNull { pose -> pose.landmarks.getOrNull(index) }
        currentLandmark.averageWith(samples)
    }
    return template.copy(landmarks = smoothedLandmarks)
}

private fun medianPose(
    template: Pose,
    history: List<Pose>
): Pose {
    val smoothedLandmarks = template.landmarks.mapIndexed { index, currentLandmark ->
        val samples = history.mapNotNull { pose -> pose.landmarks.getOrNull(index) }
        currentLandmark.medianWith(samples)
    }
    return template.copy(landmarks = smoothedLandmarks)
}

private fun PoseLandmark.averageWith(samples: List<PoseLandmark>): PoseLandmark {
    val totalSamples = samples.ifEmpty { listOf(this) }
    return copy(
        x = totalSamples.map { it.x }.average().toFloat(),
        y = totalSamples.map { it.y }.average().toFloat(),
        z = totalSamples.map { it.z }.average().toFloat(),
        visibility = totalSamples.mapNotNull { it.visibility }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        presence = totalSamples.mapNotNull { it.presence }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    )
}

private fun PoseLandmark.medianWith(samples: List<PoseLandmark>): PoseLandmark {
    val totalSamples = samples.ifEmpty { listOf(this) }
    return copy(
        x = totalSamples.map { it.x }.median(),
        y = totalSamples.map { it.y }.median(),
        z = totalSamples.map { it.z }.median(),
        visibility = totalSamples.mapNotNull { it.visibility }.takeIf { it.isNotEmpty() }?.median(),
        presence = totalSamples.mapNotNull { it.presence }.takeIf { it.isNotEmpty() }?.median()
    )
}

private fun PoseLandmark.blend(previous: PoseLandmark, alpha: Float): PoseLandmark {
    return copy(
        x = lowPass(x, previous.x, alpha),
        y = lowPass(y, previous.y, alpha),
        z = lowPass(z, previous.z, alpha),
        visibility = blendNullable(previous.visibility, visibility, alpha),
        presence = blendNullable(previous.presence, presence, alpha)
    )
}

private fun blendNullable(previous: Float?, current: Float?, alpha: Float): Float? {
    if (previous == null && current == null) return null
    val resolvedPrevious = previous ?: current ?: return null
    val resolvedCurrent = current ?: resolvedPrevious
    return lowPass(resolvedCurrent, resolvedPrevious, alpha)
}

private fun lowPass(current: Float, previous: Float, alpha: Float): Float {
    return alpha * current + (1f - alpha) * previous
}

private fun smoothingAlpha(cutoff: Float, dt: Float): Float {
    val tau = (1.0 / (2.0 * PI * cutoff.coerceAtLeast(1e-3f))).toFloat()
    return 1f / (1f + tau / dt.coerceAtLeast(1e-3f))
}

private fun List<Float>.median(): Float {
    if (isEmpty()) return 0f
    val sorted = sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[mid - 1] + sorted[mid]) / 2f
    } else {
        sorted[mid]
    }
}

private fun calculateMeanMotion(frames: List<DebugPoseFrameResult>): Float {
    if (frames.size < 2) return 0f

    var total = 0f
    var transitionCount = 0
    for (index in 1 until frames.size) {
        val previousLandmarks = frames[index - 1].pose.landmarks
        val currentLandmarks = frames[index].pose.landmarks
        val pairCount = kotlin.math.min(previousLandmarks.size, currentLandmarks.size)
        if (pairCount == 0) continue

        var frameDelta = 0f
        for (landmarkIndex in 0 until pairCount) {
            val previous = previousLandmarks[landmarkIndex]
            val current = currentLandmarks[landmarkIndex]
            val dx = current.x - previous.x
            val dy = current.y - previous.y
            val dz = current.z - previous.z
            frameDelta += sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        }
        total += frameDelta / pairCount.toFloat()
        transitionCount++
    }

    return if (transitionCount == 0) 0f else total / transitionCount.toFloat()
}

private fun findNearestDebugPoseFrame(
    frames: List<DebugPoseFrameResult>,
    targetTimeMs: Long
): DebugPoseFrameResult? {
    return frames.minByOrNull { frame ->
        abs(frame.pose.frameTimeMs - targetTimeMs)
    }
}

private fun formatMotion(value: Float): String {
    return String.format(java.util.Locale.US, "%.4f", value)
}

private fun formatRatio(value: Float): String {
    return String.format(java.util.Locale.US, "%.0f%%", value * 100f)
}

private const val RAW_FILTER_KEY = "raw"
private const val EMA_FILTER_KEY = "ema"
private const val MOVING_AVERAGE_FILTER_KEY = "moving_average"
private const val MEDIAN_FILTER_KEY = "median"
private const val ONE_EURO_FILTER_KEY = "one_euro"
private const val ZERO_PHASE_EMA_FILTER_KEY = "zero_phase_ema"
private const val CENTERED_MOVING_AVERAGE_FILTER_KEY = "centered_moving_average"
private const val CENTERED_MEDIAN_FILTER_KEY = "centered_median"
