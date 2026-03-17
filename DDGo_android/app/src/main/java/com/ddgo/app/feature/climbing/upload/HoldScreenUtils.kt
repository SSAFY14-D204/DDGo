package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ddgo.app.domain.model.Hold

// ── 색상 상수 ────────────────────────────────────────────────────────────────────
internal val COLOR_START    = Color(0xFF00E676)   // 초록 — 시작 홀드
internal val COLOR_END      = Color(0xFFFF5252)   // 빨강 — 끝 홀드
internal val COLOR_INACTIVE = Color.White.copy(alpha = 0.65f)

// ── 정규화 좌표 → 화면 픽셀 좌표 변환 헬퍼 ─────────────────────────────────────
internal data class ScreenRect(val l: Float, val t: Float, val r: Float, val b: Float)

internal fun Hold.toScreenRect(
    offX: Float, offY: Float, scaledW: Float, scaledH: Float
) = ScreenRect(
    l = offX + boundingBox.left   * scaledW,
    t = offY + boundingBox.top    * scaledH,
    r = offX + boundingBox.right  * scaledW,
    b = offY + boundingBox.bottom * scaledH
)

// ── DrawScope 헬퍼: confidence 라벨 ─────────────────────────────────────────────
internal fun DrawScope.drawConfidenceLabel(
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

// ── 수동 홀드 추가: 확대 팝업 (다중 선택) ────────────────────────────────────────
@Composable
internal fun CandidateHoldPopup(
    bitmap: Bitmap,
    candidates: List<Hold>,
    accentColor: Color,
    onSelect: (List<Hold>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedHolds by remember { mutableStateOf(emptySet<Hold>()) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1C1C1E))
                .padding(20.dp)
        ) {
            Text(
                text       = "홀드 수동 추가",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (selectedHolds.isEmpty()) {
                    "근처에서 ${candidates.size}개의 후보가 감지됐어요\n추가할 홀드를 모두 선택해주세요"
                } else {
                    "근처에서 ${candidates.size}개의 후보가 감지됐어요\n${selectedHolds.size}개 선택됨"
                },
                fontSize   = 13.sp,
                color      = Color.White.copy(alpha = 0.5f),
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                candidates.forEach { hold ->
                    CandidateHoldCard(
                        bitmap      = bitmap,
                        hold        = hold,
                        accentColor = accentColor,
                        isSelected  = hold in selectedHolds,
                        onToggle    = {
                            selectedHolds = if (hold in selectedHolds) {
                                selectedHolds - hold
                            } else {
                                selectedHolds + hold
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (selectedHolds.isNotEmpty()) {
                        onSelect(selectedHolds.toList())
                        onDismiss()
                    }
                },
                enabled  = selectedHolds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = accentColor,
                    contentColor           = Color.Black,
                    disabledContainerColor = Color.White.copy(alpha = 0.10f),
                    disabledContentColor   = Color.White.copy(alpha = 0.35f)
                )
            ) {
                Text(
                    text       = if (selectedHolds.isEmpty()) "홀드를 선택해주세요" else "${selectedHolds.size}개 추가하기",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick  = onDismiss,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor   = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text("취소", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CandidateHoldCard(
    bitmap: Bitmap,
    hold: Hold,
    accentColor: Color,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val cropped   = remember(hold.boundingBox) { cropHoldRegion(bitmap, hold) }
    val holdColor = holdLabelToComposeColor(hold.colorLabel)

    Box {
        Column(
            modifier = Modifier
                .width(130.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) Color(0xFF1E2E1E) else Color(0xFF2C2C2E)
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) accentColor else accentColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onToggle() }
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap             = cropped.asImageBitmap(),
                contentDescription = "후보 홀드",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).background(holdColor, CircleShape))
                Text(hold.colorLabel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text     = "${(hold.confidence * 100).toInt()}%",
                fontSize = 10.sp,
                color    = Color.White.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = if (isSelected) "✓ 선택됨" else "탭하여 선택",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = if (isSelected) accentColor else Color.White.copy(alpha = 0.35f)
            )
        }

        // 선택 완료 뱃지 (우상단)
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "✓",
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.Black
                )
            }
        }
    }
}

// ── 헬퍼 함수 ────────────────────────────────────────────────────────────────────

internal fun cropHoldRegion(
    bitmap: Bitmap,
    hold: Hold,
    paddingFraction: Float = 0.6f
): Bitmap {
    val iw = bitmap.width
    val ih = bitmap.height

    val holdL = (hold.boundingBox.left   * iw).toInt()
    val holdT = (hold.boundingBox.top    * ih).toInt()
    val holdR = (hold.boundingBox.right  * iw).toInt()
    val holdB = (hold.boundingBox.bottom * ih).toInt()
    val holdW = (holdR - holdL).coerceAtLeast(1)
    val holdH = (holdB - holdT).coerceAtLeast(1)

    val padX  = (holdW * paddingFraction).toInt()
    val padY  = (holdH * paddingFraction).toInt()

    val cropL = (holdL - padX).coerceAtLeast(0)
    val cropT = (holdT - padY).coerceAtLeast(0)
    val cropR = (holdR + padX).coerceAtMost(iw)
    val cropB = (holdB + padY).coerceAtMost(ih)
    val cropW = (cropR - cropL).coerceAtLeast(1)
    val cropH = (cropB - cropT).coerceAtLeast(1)

    return Bitmap.createBitmap(bitmap, cropL, cropT, cropW, cropH)
}

internal fun holdLabelToComposeColor(label: String): Color = when (label) {
    "red"    -> Color(0xFFE53935)
    "orange" -> Color(0xFFFF6F00)
    "yellow" -> Color(0xFFFFD600)
    "green"  -> Color(0xFF43A047)
    "cyan"   -> Color(0xFF00B8D4)
    "blue"   -> Color(0xFF1565C0)
    "purple" -> Color(0xFF7B1FA2)
    "brown"  -> Color(0xFF5D4037)
    "pink"   -> Color(0xFFF16698)
    "white"  -> Color(0xFFFFFFFF)
    "gray"   -> Color(0xFF757575)
    "black"  -> Color(0xFF212121)
    else     -> Color(0xFF888888)
}
