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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.delay
import kotlin.math.abs

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
    var useOptimized by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        persistReadPermission(context, uri)
        viewModel.analyzeVideo(uri, context.resolveDisplayName(uri), useOptimized)
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
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var playerVideoSize by remember(videoUri) { mutableStateOf(VideoSize.UNKNOWN) }

    LaunchedEffect(exoPlayer, videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        playerVideoSize = exoPlayer.videoSize
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPositionMs = exoPlayer.currentPosition
            delay(16)
        }
    }

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

    val currentPose = remember(poseFrames, currentPositionMs) {
        poseFrames.minByOrNull { frame -> abs(frame.pose.frameTimeMs - currentPositionMs) }?.pose
    }

    val videoContentRect = remember(containerSize, playerVideoSize) {
        calculateVideoContentRect(
            containerSize = containerSize,
            videoSize = playerVideoSize
        )
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
                            useController = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    }
                )

                currentPose?.let { pose ->
                    PrePoseOverlay(
                        pose = pose,
                        contentRect = videoContentRect,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Text(
                text = "미리 계산된 ${poseFrames.size}개의 포즈 데이터를 사용하여 실시간으로 렌더링 중입니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun PrePoseOverlay(
    pose: Pose,
    contentRect: VideoContentRect,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (contentRect.width <= 0f || contentRect.height <= 0f) return@Canvas

        val landmarksByIndex = pose.landmarks.associateBy { landmark -> landmark.index }
        val jointRadius = 4.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val lineColor = Color.Cyan
        val pointColor = Color.Magenta

        POSE_CONNECTIONS.forEach { (startIndex, endIndex) ->
            val start = landmarksByIndex[startIndex] ?: return@forEach
            val end = landmarksByIndex[endIndex] ?: return@forEach

            drawLine(
                color = lineColor.copy(alpha = 0.8f),
                start = Offset(
                    x = contentRect.left + (start.x.coerceIn(0f, 1f) * contentRect.width),
                    y = contentRect.top + (start.y.coerceIn(0f, 1f) * contentRect.height)
                ),
                end = Offset(
                    x = contentRect.left + (end.x.coerceIn(0f, 1f) * contentRect.width),
                    y = contentRect.top + (end.y.coerceIn(0f, 1f) * contentRect.height)
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        pose.landmarks.forEach { landmark ->
            drawCircle(
                color = pointColor,
                radius = jointRadius,
                center = Offset(
                    x = contentRect.left + (landmark.x.coerceIn(0f, 1f) * contentRect.width),
                    y = contentRect.top + (landmark.y.coerceIn(0f, 1f) * contentRect.height)
                )
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

private val POSE_CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 27 to 29, 29 to 31,
    24 to 26, 26 to 28, 28 to 30, 30 to 32
)

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
