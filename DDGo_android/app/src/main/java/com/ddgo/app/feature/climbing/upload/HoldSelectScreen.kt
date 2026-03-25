package com.ddgo.app.feature.climbing.upload

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ── 홀드 선택 단계 ────────────────────────────────────────────────────────────────
private enum class SelectionPhase { START, END }

/**
 * 시작 홀드 → 끝 홀드를 순서대로 선택하는 화면.
 * [ChallengeHoldScreen]에서 누락 홀드 추가 완료 후 진입합니다.
 */
@Composable
fun HoldSelectScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    allowAdditionalUpload: Boolean = true,
    onNavigateToAdditional: () -> Unit = {},
    onNavigateToNext: () -> Unit = {}
) {
    val bitmap = viewModel.bestFrameBitmap
    var showAdditionalUploadDialog by remember { mutableStateOf(false) }

    if (showAdditionalUploadDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "이 문제의 추가 시도 영상이 있나요?",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "시도 별로 비교해서 분석을 보여줄게요",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            containerColor = Color(0xFF2E2E2E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdditionalUploadDialog = false
                        onNavigateToAdditional()
                    }
                ) {
                    Text(
                        text = "더 있어요!",
                        color = Color(0xFF42A5F5),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdditionalUploadDialog = false
                        onNavigateToNext()
                    }
                ) {
                    Text(
                        text = "없어요",
                        color = Color.White
                    )
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bitmap == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "프레임을 불러올 수 없어요.\n다시 시도해주세요.",
                    color = Color.White, fontSize = 18.sp, lineHeight = 28.sp
                )
            }
        } else {
            TwoPhaseHoldSelection(
                viewModel = viewModel,
                allowAdditionalUpload = allowAdditionalUpload,
                onNavigateToNext = onNavigateToNext,
                onShowAdditionalUploadDialog = {
                    showAdditionalUploadDialog = true
                }
            )
        }
    }
}

// ── 2단계 홀드 선택 래퍼 ─────────────────────────────────────────────────────────

@Composable
private fun TwoPhaseHoldSelection(
    viewModel: UploadViewModel,
    allowAdditionalUpload: Boolean,
    onNavigateToNext: () -> Unit,
    onShowAdditionalUploadDialog: () -> Unit
) {
    var phase              by remember { mutableStateOf(SelectionPhase.START) }
    var selectedStartIndex by remember { mutableIntStateOf(-1) }
    var selectedEndIndex   by remember { mutableIntStateOf(-1) }

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
                        val selectedHold = viewModel.detectedHolds[selectedStartIndex]
                        UploadAiTraceLogger.log(
                            event = "HOLD_SELECT_CONFIRM_START",
                            playbackUri = viewModel.videoUri,
                            phase = "HoldSelect",
                            details = mapOf(
                                "selectedIndex" to selectedStartIndex,
                                "bbox" to selectedHold.boundingBox.toString()
                            )
                        )
                        viewModel.updateSelectedStartHold(
                            selectedHold
                        )
                        phase = SelectionPhase.END
                    }
                    SelectionPhase.END -> {
                        val selectedHold = viewModel.detectedHolds[selectedEndIndex]
                        UploadAiTraceLogger.log(
                            event = "HOLD_SELECT_CONFIRM_END",
                            playbackUri = viewModel.videoUri,
                            phase = "HoldSelect",
                            details = mapOf(
                                "selectedIndex" to selectedEndIndex,
                                "bbox" to selectedHold.boundingBox.toString()
                            )
                        )
                        viewModel.updateSelectedEndHold(
                            selectedHold
                        )
                        viewModel.resetState()
                        if (allowAdditionalUpload) {
                            onShowAdditionalUploadDialog()
                        } else {
                            onNavigateToNext()
                        }
                    }
                }
            },
            onBack = {
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
    val bitmap = viewModel.bestFrameBitmap ?: return
    val holds  = viewModel.detectedHolds

    val isStart       = phase == SelectionPhase.START
    val accentColor   = if (isStart) COLOR_START else COLOR_END
    val selectedIndex = if (isStart) startIndex  else endIndex
    val onSelect: (Int) -> Unit = if (isStart) onStartSelect else onEndSelect

    Column(modifier = Modifier.fillMaxSize()) {

        // ── 헤더 ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            StepBadgeRow(phase = phase)
            Spacer(Modifier.height(10.dp))
            Text(
                text       = if (isStart) "분석 정확도를 위해\n시작 홀드를 지정해주세요"
                             else "목표 홀드를 선택해주세요",
                color      = Color.White,
                style      = MaterialTheme.typography.headlineMedium.copy(
                    lineHeight = 28.6.sp,
                    letterSpacing = (-0.22).sp
                )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = "${holds.size}개의 홀드가 감지됐어요",
                fontSize = 13.sp,
                color    = Color.White.copy(alpha = 0.5f)
            )
        }

        // ── 이미지 + 바운딩박스 오버레이 ──────────────────────────────────────
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
                                    val otherIndex = if (isStart) endIndex else startIndex
                                    if (idx != otherIndex) {
                                        onSelect(if (selectedIndex == idx) -1 else idx)
                                    }
                                }
                            }
                        }
                    }
            ) {
                holds.forEachIndexed { idx, hold ->
                    val r = hold.toScreenRect(offX, offY, scaledW, scaledH)

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

                    drawRect(
                        color   = color.copy(alpha = if (isThisSelected) 0.22f else 0.07f),
                        topLeft = Offset(r.l, r.t),
                        size    = Size(r.r - r.l, r.b - r.t)
                    )
                    drawRect(
                        color   = color.copy(alpha = alpha),
                        topLeft = Offset(r.l, r.t),
                        size    = Size(r.r - r.l, r.b - r.t),
                        style   = Stroke(width = strokePx)
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

// ── 단계 뱃지 행 ──────────────────────────────────────────────────────────────────

@Composable
private fun StepBadgeRow(phase: SelectionPhase) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepBadge(
            step   = "1",
            label  = "시작 홀드",
            active = phase == SelectionPhase.START,
            done   = phase == SelectionPhase.END,
            color  = COLOR_START
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
