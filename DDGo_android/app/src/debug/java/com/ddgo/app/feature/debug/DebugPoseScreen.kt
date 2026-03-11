@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ddgo.app.feature.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.core.ui.components.DdgoPrimaryButton
import com.ddgo.app.domain.model.Pose
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugPoseScreen(
    viewModel: DebugPoseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val selectedVideoName = uiState.selectedVideoName
    val selectedVideoUri = uiState.selectedVideoUri
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        persistReadPermission(context, uri)
        viewModel.analyzeVideo(uri, context.resolveDisplayName(uri))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediaPipe Pose 디버그") }
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
            StatusCard(
                title = "구조",
                body = "debug 화면은 ViewModel이 디버그 전용 분석기를 호출하고, 분석기 내부에서 MediaCodec 순차 디코딩(cap.read() 유사) 후 MediaPipe Pose와 VisionMapper 변환을 수행하도록 분리했습니다."
            )

            DdgoPrimaryButton(
                text = if (uiState.isAnalyzing) "영상 분석 중" else "동영상 업로드",
                onClick = { pickerLauncher.launch(arrayOf("video/*")) },
                enabled = !uiState.isAnalyzing,
                isLoading = uiState.isAnalyzing
            )

            if (selectedVideoName != null) {
                StatusCard(
                    title = "선택한 영상",
                    body = selectedVideoName
                )
            } else {
                StatusCard(
                    title = "사용 방법",
                    body = "버튼을 누르면 문서 선택기가 열리고, 선택한 비디오를 즉시 샘플링해서 MediaPipe Pose를 적용합니다."
                )
            }

            if (uiState.isAnalyzing) {
                StatusCard(
                    title = "분석 상태",
                    body = "MediaExtractor와 MediaCodec으로 프레임을 처음부터 끝까지 순차 디코딩하면서, 일정 간격으로 MediaPipe Pose를 적용하고 있습니다."
                )
            }

            uiState.errorMessage?.let { message ->
                StatusCard(
                    title = "오류",
                    body = message,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (selectedVideoUri != null && !uiState.isAnalyzing && uiState.errorMessage == null) {
                DebugPoseResultCard(
                    videoUri = selectedVideoUri,
                    videoName = selectedVideoName,
                    poses = uiState.poseFrames
                )
            }
        }
    }
}

@Composable
private fun DebugPoseResultCard(
    videoUri: Uri,
    videoName: String?,
    poses: List<Pose>
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
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
            delay(50)
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

    val currentPose = remember(poses, currentPositionMs) {
        poses.minByOrNull { pose -> abs(pose.frameTimeMs - currentPositionMs) }
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
                text = "MediaPipe 적용 결과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = videoName ?: "선택한 영상",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
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
                    PoseOverlay(
                        pose = pose,
                        contentRect = videoContentRect,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(label = "검출 프레임", value = poses.size.toString())
                MetricChip(
                    label = "현재 오버레이",
                    value = currentPose?.frameTimeMs?.let { "${it}ms" } ?: "없음"
                )
            }

            Text(
                text = if (poses.isEmpty()) {
                    "샘플링한 프레임에서 포즈를 찾지 못했습니다. 모델 파일이 없거나 인물이 작게 보이는 영상이면 이렇게 나타날 수 있습니다."
                } else {
                    "PlayerView가 실제로 보여주는 비디오 영역에 맞춰 랜드마크를 오버레이합니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PoseOverlay(
    pose: Pose,
    contentRect: VideoContentRect,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (contentRect.width <= 0f || contentRect.height <= 0f) return@Canvas

        val landmarksByIndex = pose.landmarks.associateBy { landmark -> landmark.index }
        val jointRadius = 5.dp.toPx()
        val strokeWidth = 3.dp.toPx()
        val lineColor = Color(0xFFFFC857)
        val pointColor = Color(0xFF2EC4B6)

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

@Composable
private fun MetricChip(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Text(
            text = "$label  $value",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    body: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
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
    0 to 1,
    1 to 2,
    2 to 3,
    3 to 7,
    0 to 4,
    4 to 5,
    5 to 6,
    6 to 8,
    9 to 10,
    11 to 12,
    11 to 13,
    13 to 15,
    15 to 17,
    15 to 19,
    15 to 21,
    12 to 14,
    14 to 16,
    16 to 18,
    16 to 20,
    16 to 22,
    11 to 23,
    12 to 24,
    23 to 24,
    23 to 25,
    25 to 27,
    27 to 29,
    29 to 31,
    24 to 26,
    26 to 28,
    28 to 30,
    30 to 32
)



