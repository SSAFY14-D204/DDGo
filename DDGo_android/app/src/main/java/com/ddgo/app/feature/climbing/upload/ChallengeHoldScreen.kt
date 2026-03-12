package com.ddgo.app.feature.climbing.upload

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.domain.model.Hold

// ── 홀드 선택 단계 ─────────────────────────────────────────────────────────────
private enum class SelectionPhase { START, END }

// ── 로딩 애니메이션용 더미 홀드 ─────────────────────────────────────────────────
private data class AnimHoldItem(
    val color: Color,
    val center: Offset,
    val sizeFraction: Float,
    val rotation: Float,
    val phaseOffset: Float
)

private val ANIM_HOLDS = listOf(
    AnimHoldItem(Color(0xFFFFD600), Offset(0.72f, 0.28f), 1.00f, -30f,    0f),
    AnimHoldItem(Color(0xFF43A047), Offset(0.80f, 0.52f), 0.75f,  15f,  600f),
    AnimHoldItem(Color(0xFFE91E63), Offset(0.38f, 0.65f), 0.85f, -10f, 1200f),
)

// ── 색상 상수 ───────────────────────────────────────────────────────────────────
private val COLOR_START    = Color(0xFF00E676)   // 초록 — 시작 홀드
private val COLOR_END      = Color(0xFFFF5252)   // 빨강 — 끝 홀드
private val COLOR_INACTIVE = Color.White.copy(alpha = 0.65f)

@Composable
fun ChallengeHoldScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToNext: () -> Unit = {}
) {
    val uiState  by viewModel.uiState.collectAsState()
    val videoUri  = viewModel.videoUri

    // videoUri 준비 완료 시 탐지 파이프라인 시작
    LaunchedEffect(videoUri) {
        if (videoUri != null) viewModel.runHoldDetection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── 로딩 ↔ 홀드 선택 크로스페이드 ─────────────────────────────────────
        AnimatedContent(
            targetState    = uiState is UploadUiState.Success,
            transitionSpec = {
                fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(300))
            },
            label = "hold_screen_transition"
        ) { isSuccess ->
            if (isSuccess) {
                TwoPhaseHoldSelection(
                    viewModel        = viewModel,
                    onNavigateToNext = onNavigateToNext
                )
            } else {
                HoldLoadingContent(uiState = uiState)
            }
        }
    }
}

// ── 2단계 홀드 선택 래퍼 ─────────────────────────────────────────────────────────

@Composable
private fun TwoPhaseHoldSelection(
    viewModel: UploadViewModel,
    onNavigateToNext: () -> Unit
) {
    var phase              by remember { mutableStateOf(SelectionPhase.START) }
    var selectedStartIndex by remember { mutableIntStateOf(-1) }
    var selectedEndIndex   by remember { mutableIntStateOf(-1) }

    // START → END 슬라이드 인, END → START 슬라이드 아웃
    AnimatedContent(
        targetState    = phase,
        transitionSpec = {
            val forward = targetState == SelectionPhase.END
            val enter   = slideInHorizontally(tween(380)) { if (forward)  it else -it } +
                          fadeIn(tween(300))
            val exit    = slideOutHorizontally(tween(300)) { if (forward) -it else  it } +
                          fadeOut(tween(200))
            enter togetherWith exit
        },
        label = "phase_transition"
    ) { currentPhase ->
        HoldSelectionContent(
            viewModel     = viewModel,
            phase         = currentPhase,
            startIndex    = selectedStartIndex,
            endIndex      = selectedEndIndex,
            onStartSelect = { selectedStartIndex = it },
            onEndSelect   = { selectedEndIndex   = it },
            onConfirm     = {
                when (currentPhase) {
                    SelectionPhase.START -> {
                        viewModel.updateSelectedHoldInfo(
                            viewModel.detectedHolds[selectedStartIndex].toString()
                        )
                        phase = SelectionPhase.END
                    }
                    SelectionPhase.END -> {
                        viewModel.updateSelectedEndHoldInfo(
                            viewModel.detectedHolds[selectedEndIndex].toString()
                        )
                        viewModel.resetState()
                        onNavigateToNext()
                    }
                }
            },
            onBack = {
                // END 단계에서 뒤로가기 시 START 단계로 복귀
                if (currentPhase == SelectionPhase.END) {
                    selectedEndIndex = -1
                    phase = SelectionPhase.START
                }
            }
        )
    }
}

// ── 단일 단계 홀드 선택 UI ───────────────────────────────────────────────────────

@Composable
private fun HoldSelectionContent(
    viewModel: UploadViewModel,
    phase: SelectionPhase,
    startIndex: Int,
    endIndex: Int,
    onStartSelect: (Int) -> Unit,
    onEndSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    val bitmap = viewModel.bestFrameBitmap
    val holds  = viewModel.detectedHolds

    val isStart       = phase == SelectionPhase.START
    val accentColor   = if (isStart) COLOR_START else COLOR_END
    val selectedIndex = if (isStart) startIndex  else endIndex
    val onSelect: (Int) -> Unit = if (isStart) onStartSelect else onEndSelect

    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("프레임을 불러올 수 없어요.\n다시 시도해주세요.",
                 color = Color.White, fontSize = 18.sp, lineHeight = 28.sp)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── 헤더 ─────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // 단계 뱃지 (○1 시작 / ○2 끝)
            StepBadgeRow(phase = phase)
            Spacer(Modifier.height(10.dp))

            Text(
                text       = if (isStart) "시작 홀드를\n선택해주세요"
                             else "끝 홀드를\n선택해주세요",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = "${holds.size}개의 홀드가 감지됐어요",
                fontSize = 13.sp,
                color    = Color.White.copy(alpha = 0.5f)
            )
        }

        // ── 이미지 + 바운딩박스 오버레이 ─────────────────────────────────────
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val cW      = constraints.maxWidth.toFloat()
            val cH      = constraints.maxHeight.toFloat()
            val scale   = minOf(cW / bitmap.width, cH / bitmap.height)
            val scaledW = bitmap.width  * scale
            val scaledH = bitmap.height * scale
            val offX    = (cW - scaledW) / 2f
            val offY    = (cH - scaledH) / 2f

            Image(
                bitmap             = bitmap.asImageBitmap(),
                contentDescription = "홀드 탐지 프레임",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Fit
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(holds, phase) {
                        detectTapGestures { tap ->
                            holds.forEachIndexed { idx, hold ->
                                val r = hold.toScreenRect(offX, offY, scaledW, scaledH)
                                if (tap.x in r.l..r.r && tap.y in r.t..r.b) {
                                    // 이미 다른 단계에서 확정된 홀드는 재선택 불가
                                    val otherIndex = if (isStart) endIndex else startIndex
                                    if (idx != otherIndex) onSelect(
                                        if (selectedIndex == idx) -1 else idx
                                    )
                                }
                            }
                        }
                    }
            ) {
                holds.forEachIndexed { idx, hold ->
                    val r = hold.toScreenRect(offX, offY, scaledW, scaledH)

                    // 이미 확정된 반대쪽 홀드는 흐릿하게 표시
                    val isOtherSelected = if (isStart) idx == endIndex else idx == startIndex
                    val isThisSelected  = idx == selectedIndex

                    val color = when {
                        isThisSelected  -> accentColor
                        isOtherSelected -> if (isStart) COLOR_END else COLOR_START
                        else            -> COLOR_INACTIVE
                    }
                    val alpha = when {
                        isThisSelected  -> 1.0f
                        isOtherSelected -> 0.5f
                        else            -> 0.7f
                    }
                    val strokePx = (if (isThisSelected) 4f else 2f) * density

                    // 반투명 채우기
                    drawRect(
                        color   = color.copy(alpha = if (isThisSelected) 0.22f else 0.07f),
                        topLeft = Offset(r.l, r.t),
                        size    = Size(r.r - r.l, r.b - r.t)
                    )
                    // 외곽선
                    drawRect(
                        color   = color.copy(alpha = alpha),
                        topLeft = Offset(r.l, r.t),
                        size    = Size(r.r - r.l, r.b - r.t),
                        style   = Stroke(width = strokePx)
                    )
                    // 모서리 강조
                    val cr  = minOf(r.r - r.l, r.b - r.t) * 0.18f
                    val cPx = strokePx * 2f
                    listOf(
                        listOf(Offset(r.l, r.t + cr), Offset(r.l, r.t), Offset(r.l + cr, r.t)),
                        listOf(Offset(r.r - cr, r.t), Offset(r.r, r.t), Offset(r.r, r.t + cr)),
                        listOf(Offset(r.l, r.b - cr), Offset(r.l, r.b), Offset(r.l + cr, r.b)),
                        listOf(Offset(r.r - cr, r.b), Offset(r.r, r.b), Offset(r.r, r.b - cr))
                    ).forEach { pts ->
                        drawLine(color.copy(alpha = alpha), pts[0], pts[1], strokeWidth = cPx)
                        drawLine(color.copy(alpha = alpha), pts[1], pts[2], strokeWidth = cPx)
                    }
                    // confidence 라벨
                    drawConfidenceLabel(
                        label      = "${(hold.confidence * 100).toInt()}%",
                        boxLeft    = r.l,
                        boxTop     = r.t,
                        boxBottom  = r.b,
                        color      = color,
                        isSelected = isThisSelected
                    )
                }
            }
        }

        // ── 버튼 영역 ─────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick  = onConfirm,
                enabled  = selectedIndex >= 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = accentColor,
                    contentColor           = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.15f),
                    disabledContentColor   = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text       = when {
                        selectedIndex < 0 -> if (isStart) "시작 홀드를 터치해 선택해주세요"
                                             else "끝 홀드를 터치해 선택해주세요"
                        isStart           -> "다음 — 끝 홀드 선택하기"
                        else              -> "선택 완료"
                    },
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // END 단계에서만 "이전으로" 버튼 노출
            if (!isStart) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.10f),
                        contentColor   = Color.White
                    )
                ) {
                    Text("← 시작 홀드 다시 선택", fontSize = 14.sp)
                }
            }
        }
    }
}

// ── 단계 뱃지 행 ─────────────────────────────────────────────────────────────────

@Composable
private fun StepBadgeRow(phase: SelectionPhase) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBadge(
            step    = "1",
            label   = "시작 홀드",
            active  = phase == SelectionPhase.START,
            done    = phase == SelectionPhase.END,
            color   = COLOR_START
        )
        Spacer(Modifier.size(8.dp))
        Text("→", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
        Spacer(Modifier.size(8.dp))
        StepBadge(
            step   = "2",
            label  = "끝 홀드",
            active = phase == SelectionPhase.END,
            done   = false,
            color  = COLOR_END
        )
    }
}

@Composable
private fun StepBadge(
    step: String, label: String,
    active: Boolean, done: Boolean,
    color: Color
) {
    val bgColor   = when { active -> color; done -> color.copy(alpha = 0.5f); else -> Color.White.copy(alpha = 0.12f) }
    val textColor = if (active || done) Color.Black else Color.White.copy(alpha = 0.4f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(18.dp)
                .background(textColor.copy(alpha = 0.2f), CircleShape)
        ) {
            Text(step, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
        Spacer(Modifier.size(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
    }
}

// ── DrawScope 헬퍼: confidence 라벨 ──────────────────────────────────────────────

private fun DrawScope.drawConfidenceLabel(
    label: String,
    boxLeft: Float,
    boxTop: Float,
    boxBottom: Float,
    color: Color,
    isSelected: Boolean
) {
    drawIntoCanvas { canvas ->
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize    = 11.sp.toPx()
            typeface    = android.graphics.Typeface.DEFAULT_BOLD
        }
        val padH  = 4.dp.toPx()
        val padV  = 2.dp.toPx()
        val textW = textPaint.measureText(label)

        val rectL = boxLeft
        val rectT = if (boxTop > 20.dp.toPx()) boxTop - textPaint.textSize - padV * 2
                    else boxBottom + padV
        val rectR = rectL + textW + padH * 2
        val rectB = rectT + textPaint.textSize + padV * 2

        val bgPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color  = if (isSelected) color.toArgb()
                          else android.graphics.Color.argb(180, 0, 0, 0)
        }
        canvas.nativeCanvas.drawRoundRect(rectL, rectT, rectR, rectB, 6f, 6f, bgPaint)

        textPaint.color = if (isSelected) android.graphics.Color.BLACK
                          else android.graphics.Color.WHITE
        canvas.nativeCanvas.drawText(label, rectL + padH, rectB - padV, textPaint)
    }
}

// ── DrawScope 헬퍼: 로딩용 blob 홀드 형태 ────────────────────────────────────────

private fun DrawScope.drawHoldShape(
    color: Color, center: Offset, holdSize: Float, rotation: Float
) {
    val w = holdSize * 1.4f
    val h = holdSize

    rotate(degrees = rotation, pivot = center) {
        val left = center.x - w / 2f
        val top  = center.y - h / 2f

        val path = Path().apply {
            moveTo(left + w * 0.50f, top + h * 0.05f)
            cubicTo(left + w * 0.85f, top + h * 0.00f, left + w * 1.00f, top + h * 0.38f, left + w * 0.88f, top + h * 0.68f)
            cubicTo(left + w * 0.75f, top + h * 0.97f, left + w * 0.40f, top + h * 1.00f, left + w * 0.22f, top + h * 0.86f)
            cubicTo(left + w * 0.00f, top + h * 0.68f, left + w * 0.02f, top + h * 0.38f, left + w * 0.14f, top + h * 0.22f)
            cubicTo(left + w * 0.24f, top + h * 0.05f, left + w * 0.36f, top + h * 0.08f, left + w * 0.50f, top + h * 0.05f)
            close()
        }
        drawPath(path = path, color = color)

        val hlPath = Path().apply {
            val hx = center.x - w * 0.05f
            val hy = center.y - h * 0.20f
            moveTo(hx, hy)
            cubicTo(hx + w * 0.15f, hy - h * 0.10f, hx + w * 0.25f, hy + h * 0.05f, hx + w * 0.10f, hy + h * 0.12f)
            cubicTo(hx - w * 0.05f, hy + h * 0.12f, hx - w * 0.10f, hy + h * 0.00f, hx, hy)
            close()
        }
        drawPath(path = hlPath, color = Color.White.copy(alpha = 0.25f))
    }
}

// ── 로딩 콘텐츠 ───────────────────────────────────────────────────────────────────

@Composable
private fun HoldLoadingContent(uiState: UploadUiState) {
    val infiniteTransition = rememberInfiniteTransition(label = "hold_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue  = -12f,
        targetValue   =  12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val base = 140.dp.toPx()
            ANIM_HOLDS.forEach { hold ->
                val cx = size.width  * hold.center.x
                val cy = size.height * hold.center.y + floatOffset * (1f + hold.phaseOffset / 2400f)
                drawHoldShape(hold.color, Offset(cx, cy), base * hold.sizeFraction, hold.rotation)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.Start
        ) {
            when (uiState) {
                is UploadUiState.Error -> {
                    Text(
                        text       = "홀드 탐지 중\n오류가 발생했어요",
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        lineHeight = 36.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text     = uiState.message,
                        fontSize = 14.sp,
                        color    = Color.White.copy(alpha = 0.6f)
                    )
                }
                else -> {
                    Text(
                        text       = "디디고가\n홀드를 찾고 있어요",
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                        lineHeight = 36.sp
                    )
                }
            }
        }
    }
}

// ── 정규화 좌표 → 화면 픽셀 좌표 변환 헬퍼 ──────────────────────────────────────

private data class ScreenRect(val l: Float, val t: Float, val r: Float, val b: Float)

private fun Hold.toScreenRect(
    offX: Float, offY: Float, scaledW: Float, scaledH: Float
) = ScreenRect(
    l = offX + boundingBox.left   * scaledW,
    t = offY + boundingBox.top    * scaledH,
    r = offX + boundingBox.right  * scaledW,
    b = offY + boundingBox.bottom * scaledH
)
