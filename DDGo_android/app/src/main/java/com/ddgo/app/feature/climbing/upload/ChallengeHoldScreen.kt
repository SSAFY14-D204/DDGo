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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ── 로딩 애니메이션용 더미 홀드 ──────────────────────────────────────────────────
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

/**
 * 홀드 탐지 로딩 → 완료 후 누락된 홀드를 추가하는 화면.
 * 탐지 완료 후 [onNavigateToHoldSelect]로 시작/끝 홀드 선택 화면(HoldSelectScreen)으로 이동합니다.
 * 이동 전 추가 영상 업로드 여부를 묻는 팝업을 띄웁니다.
 */
@Composable
fun ChallengeHoldScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToAdditional: () -> Unit = {},
    onNavigateToHoldSelect: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val videoUri = viewModel.videoUri

    var showDialog by remember { mutableStateOf(false) }

    // 다이얼로그 처리 (HEAD 브랜치 로직 유지)
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(text = "추가 시도 영상 업로드", fontWeight = FontWeight.Bold, color = Color.White)
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
                TextButton(onClick = {
                    showDialog = false
                    onNavigateToAdditional()
                }) {
                    Text("네, 더 올릴게요", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    onNavigateToHoldSelect()
                }) {
                    Text("아니오, 여기서 끝", color = Color.White)
                }
            }
        )
    }

    LaunchedEffect(videoUri) {
        if (videoUri != null) viewModel.runHoldDetection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AnimatedContent(
            targetState    = uiState is UploadUiState.Success,
            transitionSpec = {
                fadeIn(tween(600, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(300))
            },
            label = "hold_screen_transition"
        ) { isSuccess ->
            if (isSuccess) {
                HoldAddContent(
                    viewModel    = viewModel,
                    onShowDialog = { showDialog = true }
                )
            } else {
                HoldLoadingContent(uiState = uiState)
            }
        }
    }
}

// ── 누락된 홀드 추가 화면 (dev 브랜치 AI 로직 우선) ──────────────────────────────────

@Composable
private fun HoldAddContent(
    viewModel: UploadViewModel,
    onShowDialog: () -> Unit
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
                            viewModel.detectedHolds[selectedStartIndex].toSelectionSummary()
                        )
                        phase = SelectionPhase.END
                    }
                    SelectionPhase.END -> {
                        viewModel.updateSelectedEndHoldInfo(
                            viewModel.detectedHolds[selectedEndIndex].toSelectionSummary()
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
            Text(
                "프레임을 불러올 수 없어요.\n다시 시도해주세요.",
                color = Color.White, fontSize = 18.sp, lineHeight = 28.sp
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── 헤더 ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text       = "탐지된 홀드를\n확인해주세요",
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
            if (allRawHolds.size > holds.size) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = "빈 영역을 터치하면 누락된 홀드를 추가할 수 있어요",
                    fontSize = 11.sp,
                    color    = Color.White.copy(alpha = 0.35f)
                )
            }
        }

        // ── 이미지 + 홀드 경계 오버레이 ───────────────────────────────────────
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
                    .pointerInput(holds) {
                        detectTapGestures { tap ->
                            var tappedExisting = false
                            holds.forEach { hold ->
                                val r = hold.toScreenRect(offX, offY, scaledW, scaledH)
                                val polygon = hold.toScreenPolygon(offX, offY, scaledW, scaledH)
                                val hit = if (polygon.size >= 3) {
                                    pointInPolygon(tap, polygon)
                                } else {
                                    tap.x in r.l..r.r && tap.y in r.t..r.b
                                }

                                if (hit) {
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
                    val polygon = hold.toScreenPolygon(offX, offY, scaledW, scaledH)
                    val polygonPath = polygon.toPath()
                    val hasPolygon = polygon.size >= 3

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
                    val fillAlpha = if (isThisSelected) 0.22f else 0.07f

                    if (hasPolygon) {
                        drawPath(
                            path = polygonPath,
                            color = color.copy(alpha = fillAlpha)
                        )
                        drawPath(
                            path = polygonPath,
                            color = color.copy(alpha = alpha),
                            style = Stroke(
                                width = strokePx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        drawRect(
                            color = color.copy(alpha = fillAlpha),
                            topLeft = Offset(r.l, r.t),
                            size = Size(r.r - r.l, r.b - r.t)
                        )
                        drawRect(
                            color = color.copy(alpha = alpha),
                            topLeft = Offset(r.l, r.t),
                            size = Size(r.r - r.l, r.b - r.t),
                            style = Stroke(width = strokePx)
                        )

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
                    }

                    // confidence 라벨
                    drawConfidenceLabel(
                        label      = "${(hold.confidence * 100).toInt()}%",
                        boxLeft    = r.l,
                        boxTop     = r.t,
                        boxBottom  = r.b,
                        color      = COLOR_INACTIVE,
                        isSelected = false
                    )
                }
            }
        }

        // ── 버튼 ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Button(
                onClick  = onShowDialog,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = COLOR_START,
                    contentColor   = Color.Black
                )
            ) {
                Text(
                    text       = "다음 — 시작 홀드 선택하기",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // ── 후보 홀드 팝업 ─────────────────────────────────────────────────────────
    if (showCandidatePopup && candidateHolds.isNotEmpty()) {
        CandidateHoldPopup(
            bitmap      = bitmap,
            candidates  = candidateHolds,
            accentColor = COLOR_START,
            onSelect    = { hold -> viewModel.selectManualHold(hold) },
            onDismiss   = { viewModel.dismissCandidatePopup() }
        )
    }
}

// ── 로딩 콘텐츠 ──────────────────────────────────────────────────────────────────

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

// ── DrawScope 헬퍼: 로딩용 blob 홀드 형태 ───────────────────────────────────────

private fun DrawScope.drawHoldShape(
    color: Color, center: Offset, holdSize: Float, rotation: Float
) {
    val w = holdSize * 1.4f
    val h = holdSize

private fun Hold.toScreenRect(
    offX: Float, offY: Float, scaledW: Float, scaledH: Float
) = ScreenRect(
    l = offX + boundingBox.left   * scaledW,
    t = offY + boundingBox.top    * scaledH,
    r = offX + boundingBox.right  * scaledW,
    b = offY + boundingBox.bottom * scaledH
)

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

private fun Hold.toSelectionSummary(): String =
    buildString {
        append("bbox=(")
        append(String.format("%.3f, %.3f, %.3f, %.3f", boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom))
        append("), conf=")
        append(String.format("%.3f", confidence))
        if (polygon.isNotEmpty()) {
            append(", polygon=")
            append(polygon.size)
            append("pts")
        }
    }
