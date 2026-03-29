package com.ddgo.app.feature.climbing.upload

import android.os.SystemClock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.R
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.domain.model.Hold
import kotlinx.coroutines.delay

private data class LoadingHoldAsset(
    val drawableRes: Int,
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp,
    val alignment: Alignment,
    val offsetX: androidx.compose.ui.unit.Dp,
    val offsetY: androidx.compose.ui.unit.Dp
)

private val LOADING_HOLD_ASSETS = listOf(
    LoadingHoldAsset(
        drawableRes = R.drawable.loading_hold_yellow,
        width = 132.dp,
        height = 88.dp,
        alignment = Alignment.TopStart,
        offsetX = 4.dp,
        offsetY = 8.dp
    ),
    LoadingHoldAsset(
        drawableRes = R.drawable.loading_hold_green,
        width = 118.dp,
        height = 130.dp,
        alignment = Alignment.TopEnd,
        offsetX = (-2).dp,
        offsetY = 54.dp
    ),
    LoadingHoldAsset(
        drawableRes = R.drawable.loading_hold_pink,
        width = 86.dp,
        height = 108.dp,
        alignment = Alignment.BottomStart,
        offsetX = 28.dp,
        offsetY = (-2).dp
    ),
)

private const val HOLD_SELECT_MIN_LOADING_DISPLAY_MILLIS = 3_000L

@Composable
fun ChallengeHoldScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    allowAdditionalUpload: Boolean = true,
    onNavigateToAdditional: () -> Unit = {},
    onNavigateToHoldSelect: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val videoUri = viewModel.videoUri
    val debugBestFrameImageUri = viewModel.debugBestFrameImageUri
    val shouldBypassInitialLoadingGate = remember(uiState, videoUri, debugBestFrameImageUri) {
        uiState == UploadUiState.Success || uiState is UploadUiState.Error
    }
    var showDialog by remember { mutableStateOf(false) }
    var displayedUiState by remember(videoUri, debugBestFrameImageUri) {
        mutableStateOf(
            if (shouldBypassInitialLoadingGate) {
                uiState
            } else {
                UploadUiState.Loading
            }
        )
    }
    var loadingStartedAtMillis by remember(videoUri, debugBestFrameImageUri) {
        mutableStateOf(
            if (shouldBypassInitialLoadingGate) {
                SystemClock.elapsedRealtime() - HOLD_SELECT_MIN_LOADING_DISPLAY_MILLIS
            } else {
                SystemClock.elapsedRealtime()
            }
        )
    }

    if (false && showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    text = "추가 시도 영상 업로드",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "이 문제 도전 영상을 더 업로드하시겠습니까?\nN개의 영상을 한 번에 분석해 드릴게요!",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            containerColor = Color(0xFF2E2E2E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onNavigateToAdditional()
                    }
                ) {
                    Text(
                        text = "네, 더 올릴게요",
                        color = Color(0xFF42A5F5),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onNavigateToHoldSelect()
                    }
                ) {
                    Text("아니오, 여기서 끝", color = Color.White)
                }
            }
        )
    }

    LaunchedEffect(videoUri, debugBestFrameImageUri) {
        if (videoUri != null || debugBestFrameImageUri != null) {
            viewModel.ensureHoldDetectionReadyForCurrentColor()
        }
    }

    LaunchedEffect(uiState, videoUri, debugBestFrameImageUri) {
        when (val currentState = uiState) {
            UploadUiState.Success -> {
                val waitMillis = remainingLoadingDisplayMillis(
                    startedAtMillis = loadingStartedAtMillis,
                    nowMillis = SystemClock.elapsedRealtime(),
                    minimumDisplayMillis = HOLD_SELECT_MIN_LOADING_DISPLAY_MILLIS
                )
                if (waitMillis > 0L) {
                    delay(waitMillis)
                }
                displayedUiState = UploadUiState.Success
            }

            UploadUiState.Loading,
            UploadUiState.Idle -> {
                loadingStartedAtMillis = SystemClock.elapsedRealtime()
                displayedUiState = currentState
            }

            is UploadUiState.Error -> {
                displayedUiState = currentState
            }
        }
    }

    SafeAreaScreen(containerColor = Color.Black) {
        BackHandler(enabled = displayedUiState is UploadUiState.Success) {
            onNavigateBack()
        }

        AnimatedContent(
            targetState = displayedUiState is UploadUiState.Success,
            transitionSpec = {
                fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(300))
            },
            label = "hold_screen_transition"
        ) { isSuccess ->
            if (isSuccess) {
                HoldReviewContent(
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onProceed = {
                        onNavigateToHoldSelect()
                    }
                )
            } else {
                HoldLoadingContent(uiState = displayedUiState)
            }
        }
    }
}

@Composable
private fun HoldReviewContent(
    viewModel: UploadViewModel,
    onNavigateBack: () -> Unit,
    onProceed: () -> Unit
) {
    val bitmap = viewModel.bestFrameBitmap
    val holds = viewModel.detectedHolds
    val allRawHolds = viewModel.allRawHolds
    val candidateHolds = viewModel.candidateHolds
    val showCandidatePopup = viewModel.showCandidatePopup

    var zoom by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "프레임을 불러올 수 없어요.\n다시 시도해주세요.",
                color = Color.White,
                fontSize = 18.sp,
                lineHeight = 28.sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        UploadFlowTopBar(
            title = "홀드 확인",
            onNavigateBack = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "AI가 홀드를 놓쳤다면\n직접 터치해 선택/해제하세요",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    lineHeight = 28.6.sp,
                    letterSpacing = (-0.22).sp
                )
            )
            Spacer(Modifier.height(6.dp))
//            Text(
//                text = "${holds.size}개의 홀드가 감지됐어요",
//                fontSize = 13.sp,
//                color = Color.White.copy(alpha = 0.5f)
//            )
//            if (allRawHolds.isNotEmpty()) {
//                Spacer(Modifier.height(4.dp))
//                Text(
//                    text = "홀드 주변을 터치하면 선택/취소할 수 있어요",
//                    fontSize = 11.sp,
//                    color = Color.White.copy(alpha = 0.35f)
//                )
//            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val cW = constraints.maxWidth.toFloat()
            val cH = constraints.maxHeight.toFloat()
            val scale = minOf(cW / bitmap.width, cH / bitmap.height)
            val scaledW = bitmap.width * scale
            val scaledH = bitmap.height * scale
            val offX = (cW - scaledW) / 2f
            val offY = (cH - scaledH) / 2f
            val pivotX = cW / 2f
            val pivotY = cH / 2f

            // pointerInput 내부에서 항상 최신 상태값을 참조하기 위한 스냅샷
            val currentZoom by rememberUpdatedState(zoom)
            val currentPan by rememberUpdatedState(panOffset)

            // 터치 이벤트 수신 레이어 (graphicsLayer 변환 적용 전 레이아웃 좌표 기준)
            // clipToBounds: 줌 시 이미지가 이 영역 밖으로 넘치지 않도록 클리핑
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    // 핀치 줌·패닝 처리
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val newZoom = (currentZoom * gestureZoom).coerceIn(1f, 5f)
                            val maxX = cW * (newZoom - 1f) / 2f
                            val maxY = cH * (newZoom - 1f) / 2f
                            zoom = newZoom
                            panOffset = if (newZoom == 1f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    x = (currentPan.x + pan.x).coerceIn(-maxX, maxX),
                                    y = (currentPan.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            }
                        }
                    }
                    // 단일 탭·더블탭 처리
                    .pointerInput(allRawHolds) {
                        detectTapGestures(
                            onDoubleTap = {
                                // 더블탭으로 줌 초기화
                                zoom = 1f
                                panOffset = Offset.Zero
                            },
                            onTap = { tap ->
                                val z = currentZoom
                                val p = currentPan
                                // graphicsLayer 역변환: 화면 좌표 → 이미지 레이아웃 좌표
                                val localX = (tap.x - p.x - pivotX) / z + pivotX
                                val localY = (tap.y - p.y - pivotY) / z + pivotY
                                val localTap = Offset(localX, localY)

                                val imageRight = offX + scaledW
                                val imageBottom = offY + scaledH
                                if (localTap.x !in offX..imageRight || localTap.y !in offY..imageBottom) {
                                    return@detectTapGestures
                                }

                                // 탭 위치 근처 홀드 팝업 열기 (선택됨/선택 안 됨 모두 포함)
                                val normX = ((localTap.x - offX) / scaledW).coerceIn(0f, 1f)
                                val normY = ((localTap.y - offY) / scaledH).coerceIn(0f, 1f)
                                viewModel.findCandidatesNearTap(normX, normY)
                            }
                        )
                    }
            ) {
                // 줌·패닝 변환이 적용되는 시각적 레이어 (Image + Canvas 오버레이 함께 이동)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panOffset.x
                            translationY = panOffset.y
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "홀드 탐지 프레임",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        holds.forEach { hold ->
                            val rect = hold.toScreenRect(offX, offY, scaledW, scaledH)
                            val polygon = hold.toScreenPolygon(offX, offY, scaledW, scaledH)
                            val polygonPath = polygon.toPath()
                            val hasPolygon = polygon.size >= 3
                            val outlineColor = holdLabelToComposeColor(hold.colorLabel)
                            val strokePx = 2f * density

                            if (hasPolygon) {
                                drawPath(
                                    path = polygonPath,
                                    color = outlineColor.copy(alpha = 0.15f)
                                )
                                drawPath(
                                    path = polygonPath,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = Stroke(
                                        width = strokePx,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            } else {
                                drawRect(
                                    color = outlineColor.copy(alpha = 0.12f),
                                    topLeft = Offset(rect.l, rect.t),
                                    size = Size(rect.r - rect.l, rect.b - rect.t)
                                )
                                drawRect(
                                    color = Color.White.copy(alpha = 0.85f),
                                    topLeft = Offset(rect.l, rect.t),
                                    size = Size(rect.r - rect.l, rect.b - rect.t),
                                    style = Stroke(width = strokePx)
                                )
                            }

                            drawConfidenceLabel(
                                label = "${(hold.confidence * 100).toInt()}%",
                                boxLeft = rect.l,
                                boxTop = rect.t,
                                boxBottom = rect.b,
                                color = outlineColor,
                                isSelected = false
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onProceed,
                enabled = holds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = COLOR_START,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                    disabledContentColor = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text = if (holds.isEmpty()) {
                        "먼저 홀드를 한 개 이상 추가해주세요"
                    } else {
                        "완료되었어요!"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showCandidatePopup && candidateHolds.isNotEmpty()) {
        CandidateHoldPopup(
            bitmap = bitmap,
            candidates = candidateHolds,
            alreadySelected = holds,
            accentColor = COLOR_START,
            onApply = { toAdd, toRemove -> viewModel.applyHoldChanges(toAdd, toRemove) },
            onDismiss = { viewModel.dismissCandidatePopup() }
        )
    }
}

@Composable
private fun HoldLoadingContent(uiState: UploadUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "hold_float")
    val sharedFloatOffset by infiniteTransition.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shared_hold_float_y"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            when (uiState) {
                is UploadUiState.Error -> {
                    Text(
                        text = "홀드 탐지 중\n오류가 발생했어요",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 36.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = uiState.message,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    Text(
                        text = "디디고가 홀드를 찾고 있어요",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 30.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Box(
                modifier = Modifier.size(width = 320.dp, height = 268.dp),
                contentAlignment = Alignment.Center
            ) {
                LOADING_HOLD_ASSETS.forEachIndexed { index, hold ->
                    Image(
                        painter = painterResource(id = hold.drawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(hold.alignment)
                            .padding(start = hold.offsetX.takeIf { it > 0.dp } ?: 0.dp)
                            .padding(end = (-hold.offsetX).takeIf { it > 0.dp } ?: 0.dp)
                            .padding(top = hold.offsetY.takeIf { it > 0.dp } ?: 0.dp)
                            .padding(bottom = (-hold.offsetY).takeIf { it > 0.dp } ?: 0.dp)
                            .offset(y = sharedFloatOffset.dp)
                            .size(width = hold.width, height = hold.height)
                    )
                }
            }
        }
    }
}

private fun Hold.toScreenPolygon(
    offX: Float,
    offY: Float,
    scaledW: Float,
    scaledH: Float
): List<Offset> = polygon.map { point ->
    Offset(
        x = offX + point.x * scaledW,
        y = offY + point.y * scaledH
    )
}

private fun List<Offset>.toPath(): Path = Path().apply {
    if (size < 3) return@apply
    moveTo(this@toPath[0].x, this@toPath[0].y)
    for (index in 1 until size) {
        lineTo(this@toPath[index].x, this@toPath[index].y)
    }
    close()
}

private fun pointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        val intersects = ((current.y > point.y) != (previous.y > point.y)) &&
            (point.x < ((previous.x - current.x) * (point.y - current.y)) /
                ((previous.y - current.y).takeIf { it != 0f } ?: 1e-6f) + current.x)
        if (intersects) inside = !inside
        previous = current
    }
    return inside
}
