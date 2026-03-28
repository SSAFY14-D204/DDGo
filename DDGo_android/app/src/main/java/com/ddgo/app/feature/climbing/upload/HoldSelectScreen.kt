package com.ddgo.app.feature.climbing.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.SafeAreaScreen
import kotlin.math.roundToInt

private val HoldSelectStartHeadlineAccent = Color(0xFF64FFB0)
private val HoldSelectGoalHeadlineAccent = Color(0xFFFF9A62)

@Composable
fun HoldSelectScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    allowAdditionalUpload: Boolean = true,
    onNavigateToAdditional: () -> Unit = {},
    onNavigateToNext: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val bitmap = viewModel.bestFrameBitmap
    val uploadUiState by viewModel.uiState.collectAsState()
    var showAdditionalUploadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(
        bitmap,
        viewModel.detectedHolds,
        viewModel.selectedStartHold,
        viewModel.selectedEndHold
    ) {
        if (bitmap != null) {
            viewModel.prepareHoldSelectionUiState()
        }
    }

    LaunchedEffect(bitmap) {
        if (bitmap == null) {
            viewModel.ensureHoldDetectionReadyForCurrentColor()
        }
    }

    if (showAdditionalUploadDialog) {
        AdditionalAttemptPromptDialog(
            onNavigateToAdditional = {
                showAdditionalUploadDialog = false
                onNavigateToAdditional()
            },
            onNavigateToNext = {
                showAdditionalUploadDialog = false
                onNavigateToNext()
            }
        )
    }

    SafeAreaScreen(containerColor = Color.Black) {
        if (bitmap == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = uploadUiState) {
                    is UploadUiState.Error -> {
                        Text(
                            text = state.message,
                            color = Color.White,
                            fontSize = 18.sp,
                            lineHeight = 28.sp
                        )
                    }

                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "홀드 정보를 준비하고 있어요.",
                                color = Color.White,
                                fontSize = 18.sp,
                                lineHeight = 28.sp
                            )
                        }
                    }
                }
            }
        } else {
            TwoPhaseHoldSelection(
                viewModel = viewModel,
                allowAdditionalUpload = allowAdditionalUpload,
                onNavigateBack = onNavigateBack,
                onNavigateToNext = onNavigateToNext,
                onShowAdditionalUploadDialog = { showAdditionalUploadDialog = true }
            )
        }
    }
}

@Composable
private fun TwoPhaseHoldSelection(
    viewModel: UploadViewModel,
    allowAdditionalUpload: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToNext: () -> Unit,
    onShowAdditionalUploadDialog: () -> Unit
) {
    val phase = viewModel.holdSelectionPhase
    val selectedStartIndex = viewModel.holdSelectionStartIndex
    val selectedEndIndex = viewModel.holdSelectionEndIndex

    val handleBack = {
        if (phase == UploadRecoveryHoldSelectionPhase.END) {
            viewModel.stepBackHoldSelection()
        } else {
            onNavigateBack()
        }
    }
    BackHandler(onBack = handleBack)

    HoldSelectionContent(
        viewModel = viewModel,
        phase = phase,
        startIndex = selectedStartIndex,
        endIndex = selectedEndIndex,
        onStartSelect = viewModel::selectHoldSelectionStartIndex,
        onEndSelect = viewModel::selectHoldSelectionEndIndex,
        onConfirm = {
            if (viewModel.confirmCurrentHoldSelection()) {
                if (allowAdditionalUpload) {
                    onShowAdditionalUploadDialog()
                } else {
                    onNavigateToNext()
                }
            }
        },
        onBack = handleBack
    )
}

@Composable
private fun HoldSelectionContent(
    viewModel: UploadViewModel,
    phase: UploadRecoveryHoldSelectionPhase,
    startIndex: Int,
    endIndex: Int,
    onStartSelect: (Int) -> Unit,
    onEndSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val bitmap = viewModel.bestFrameBitmap ?: return
    val holds = viewModel.detectedHolds
    val localDensity = LocalDensity.current

    val isStart = phase == UploadRecoveryHoldSelectionPhase.START
    val accentColor = if (isStart) COLOR_START else COLOR_END
    val selectedIndex = if (isStart) startIndex else endIndex
    val onSelect: (Int) -> Unit = if (isStart) onStartSelect else onEndSelect

    Column(modifier = Modifier.fillMaxSize()) {
        UploadFlowTopBar(
            title = if (isStart) "시작 홀드 선택" else "목표 홀드 선택",
            onNavigateBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = buildHoldSelectionHeadline(isStart = isStart),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    lineHeight = 28.6.sp,
                    letterSpacing = (-0.22).sp
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${holds.size}개의 홀드가 감지됐어요",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val containerWidthPx = constraints.maxWidth.toFloat()
            val containerHeightPx = constraints.maxHeight.toFloat()
            val bitmapAspectRatio = remember(bitmap.width, bitmap.height) {
                if (bitmap.width > 0 && bitmap.height > 0) {
                    bitmap.width.toFloat() / bitmap.height.toFloat()
                } else {
                    9f / 16f
                }
            }
            val rawCropBounds = remember(holds) {
                calculateExpandedVerticalCropBoundsFromRawHoldExtents(holds)
            }
            val cropSpec = remember(rawCropBounds, bitmapAspectRatio) {
                if (rawCropBounds == null) {
                    uncroppedVideoViewportCropSpec(bitmapAspectRatio)
                } else {
                    calculateVerticalVideoViewportCropSpecFromBounds(
                        topFraction = rawCropBounds.topFraction,
                        bottomFraction = rawCropBounds.bottomFraction,
                        videoAspectRatio = bitmapAspectRatio,
                        fullVideoHeightPx = bitmap.height.toFloat().coerceAtLeast(1f),
                        topSafeInsetPx = 0f,
                        bottomSafeInsetPx = 0f
                    )
                }
            }
            val viewportWidthPx = remember(
                containerWidthPx,
                containerHeightPx,
                cropSpec
            ) {
                val safeContainerWidthPx = containerWidthPx.coerceAtLeast(0f)
                val safeContainerHeightPx = containerHeightPx.coerceAtLeast(0f)
                if (safeContainerWidthPx <= 0f || safeContainerHeightPx <= 0f) {
                    0f
                } else {
                    val viewportAspectRatio = cropSpec.viewportAspectRatio
                        .takeIf { it > 0f }
                        ?: bitmapAspectRatio
                    minOf(
                        safeContainerWidthPx,
                        safeContainerHeightPx * viewportAspectRatio
                    ).coerceAtLeast(0f)
                }
            }
            val fullImageHeightPx = remember(viewportWidthPx, bitmapAspectRatio) {
                if (viewportWidthPx <= 0f || bitmapAspectRatio <= 0f) {
                    0f
                } else {
                    viewportWidthPx / bitmapAspectRatio
                }
            }
            val topCropOffsetPx = remember(cropSpec, fullImageHeightPx) {
                if (cropSpec.isActive) {
                    fullImageHeightPx * cropSpec.topCropFraction
                } else {
                    0f
                }
            }
            val cropPlacement = remember(fullImageHeightPx, cropSpec, topCropOffsetPx) {
                calculateCroppedVideoViewportPlacement(
                    fullVideoWidthPx = viewportWidthPx.roundToInt().coerceAtLeast(1),
                    fullVideoHeightPx = fullImageHeightPx.roundToInt().coerceAtLeast(1),
                    cropSpec = cropSpec,
                    topCropPx = topCropOffsetPx
                )
            }
            val displayRects = remember(holds, viewportWidthPx, cropPlacement) {
                holds.map { hold ->
                    hold.toScreenRect(
                        offX = 0f,
                        offY = 0f,
                        scaledW = viewportWidthPx,
                        scaledH = cropPlacement.fullVideoHeightPx.toFloat()
                    )
                }
            }
            val visibleRects = remember(displayRects, cropPlacement) {
                displayRects.map { rect ->
                    ScreenRect(
                        l = rect.l,
                        t = rect.t + cropPlacement.transformedLayerOffsetYPx,
                        r = rect.r,
                        b = rect.b + cropPlacement.transformedLayerOffsetYPx
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CroppedVideoViewport(
                    cropSpec = cropSpec,
                    fullVideoAspectRatio = bitmapAspectRatio,
                    topCropPx = topCropOffsetPx,
                    modifier = Modifier.width(with(localDensity) { viewportWidthPx.toDp() }),
                    transformedLayer = {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                        ) {
                            val uniformStrokePx = 1.5f * density

                            drawRect(
                                color = Color.Black.copy(alpha = 0.28f),
                                size = size
                            )

                            displayRects.forEach { rect ->
                                drawRect(
                                    color = Color.Transparent,
                                    topLeft = Offset(rect.l, rect.t),
                                    size = Size(rect.r - rect.l, rect.b - rect.t),
                                    blendMode = BlendMode.Clear
                                )
                            }

                            holds.forEachIndexed { idx, hold ->
                                val rect = displayRects[idx]

                                val isPersistedStartSelection = !isStart && idx == startIndex
                                val isOtherSelected = if (isStart) idx == endIndex else false
                                val isThisSelected = idx == selectedIndex
                                val isHighlighted = isThisSelected || isPersistedStartSelection || isOtherSelected
                                val color = when {
                                    isThisSelected -> accentColor
                                    isPersistedStartSelection -> COLOR_START
                                    isOtherSelected -> if (isStart) COLOR_END else COLOR_START
                                    else -> COLOR_INACTIVE
                                }
                                val alpha = when {
                                    isThisSelected -> 1.0f
                                    isPersistedStartSelection -> 1.0f
                                    isOtherSelected -> 0.5f
                                    else -> 0.7f
                                }
                                val strokeWidthPx = if (isHighlighted) {
                                    uniformStrokePx * 2f
                                } else {
                                    uniformStrokePx
                                }

                                drawRect(
                                    color = color.copy(alpha = if (isThisSelected || isPersistedStartSelection) 0.22f else 0.07f),
                                    topLeft = Offset(rect.l, rect.t),
                                    size = Size(rect.r - rect.l, rect.b - rect.t)
                                )
                                drawRect(
                                    color = color.copy(alpha = alpha),
                                    topLeft = Offset(rect.l, rect.t),
                                    size = Size(rect.r - rect.l, rect.b - rect.t),
                                    style = Stroke(width = strokeWidthPx)
                                )
                                drawConfidenceLabel(
                                    label = "${(hold.confidence * 100).toInt()}%",
                                    boxLeft = rect.l,
                                    boxTop = rect.t,
                                    boxBottom = rect.b,
                                    color = color,
                                    isSelected = isThisSelected || isPersistedStartSelection
                                )
                            }
                        }
                    },
                    overlayLayer = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(visibleRects, phase, selectedIndex, startIndex, endIndex, isStart) {
                                    detectTapGestures { tap ->
                                        val tapX = tap.x
                                        val tapY = tap.y

                                        for (idx in visibleRects.indices.reversed()) {
                                            val rect = visibleRects[idx]
                                            if (tapX in rect.l..rect.r && tapY in rect.t..rect.b) {
                                                val otherIndex = if (isStart) endIndex else startIndex
                                                if (idx != otherIndex) {
                                                    onSelect(if (selectedIndex == idx) -1 else idx)
                                                }
                                                break
                                            }
                                        }
                                    }
                                }
                        )
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onConfirm,
                enabled = selectedIndex >= 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                    disabledContentColor = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text = when {
                        selectedIndex < 0 && isStart -> "시작 홀드 위치를 선택해 주세요"
                        selectedIndex < 0 -> "목표 홀드 위치를 선택해 주세요"
                        isStart -> "다음 단계로 이동하기"
                        else -> "선택 완료"
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun buildHoldSelectionHeadline(isStart: Boolean): AnnotatedString {
    val accentColor = if (isStart) HoldSelectStartHeadlineAccent else HoldSelectGoalHeadlineAccent
    return buildAnnotatedString {
        append("분석 정확도를 위해\n")
        withStyle(
            style = SpanStyle(
                color = accentColor,
                fontWeight = FontWeight.Bold
            )
        ) {
            append(if (isStart) "시작 홀드" else "목표 홀드")
        }
        append(if (isStart) "를 지정해 주세요" else "를 선택해 주세요")
    }
}
