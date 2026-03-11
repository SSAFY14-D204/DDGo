package com.ddgo.app.feature.climbing.upload

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// 화면에 표시할 홀드 정의
private data class HoldItem(
    val color: Color,
    val center: Offset,     // 정규화 좌표 (0f~1f)
    val sizeFraction: Float, // 홀드 크기 (1f 기준)
    val rotation: Float,
    val floatPhaseOffset: Float // 떠다니는 애니메이션 위상 차이
)

private val DISPLAY_HOLDS = listOf(
    HoldItem(
        color          = Color(0xFFFFD600),  // 노랑
        center         = Offset(0.72f, 0.28f),
        sizeFraction   = 1.0f,
        rotation       = -30f,
        floatPhaseOffset = 0f
    ),
    HoldItem(
        color          = Color(0xFF43A047),  // 초록
        center         = Offset(0.80f, 0.52f),
        sizeFraction   = 0.75f,
        rotation       = 15f,
        floatPhaseOffset = 600f
    ),
    HoldItem(
        color          = Color(0xFFE91E63),  // 핑크
        center         = Offset(0.38f, 0.65f),
        sizeFraction   = 0.85f,
        rotation       = -10f,
        floatPhaseOffset = 1200f
    ),
)

@Composable
fun ChallengeHoldScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToNext: () -> Unit = {}
) {
    // 홀드들이 천천히 위아래로 떠다니는 애니메이션
    val infiniteTransition = rememberInfiniteTransition(label = "hold_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue  =  12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 홀드 캔버스 (전체 화면)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseHoldSizePx = 140.dp.toPx()

            DISPLAY_HOLDS.forEach { hold ->
                val cx = size.width  * hold.center.x
                val cy = size.height * hold.center.y +
                        (floatOffset * (1f + hold.floatPhaseOffset / 2400f))

                drawHoldShape(
                    color    = hold.color,
                    center   = Offset(cx, cy),
                    holdSize = baseHoldSizePx * hold.sizeFraction,
                    rotation = hold.rotation
                )
            }
        }

        // 텍스트 + 버튼
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "디디고가\n홀드를 찾고 있어요",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 36.sp
            )

            Spacer(Modifier.weight(1f))

            // 개발 단계용: 수동으로 다음 화면 이동
            // TODO: 실제 홀드 탐지 완료 시 자동 이동으로 변경
            Button(
                onClick = onNavigateToNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = Color.Black
                )
            ) {
                Text(
                    text = "다음",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 클라이밍 홀드 특유의 유기적 blob 형태를 Canvas DrawScope에 그립니다.
 *
 * PersonDetectorImpl의 프레임 탐색 방식과 동일하게,
 * 계산된 좌표 대신 실제 비율 기반 Path를 사용해 정확한 형태를 보장합니다.
 */
private fun DrawScope.drawHoldShape(
    color: Color,
    center: Offset,
    holdSize: Float,
    rotation: Float
) {
    val w = holdSize * 1.4f
    val h = holdSize

    rotate(degrees = rotation, pivot = center) {
        val left   = center.x - w / 2f
        val top    = center.y - h / 2f

        val path = Path().apply {
            // 상단 중앙에서 시작
            moveTo(left + w * 0.50f, top + h * 0.05f)
            // 오른쪽 곡선
            cubicTo(
                left + w * 0.85f, top + h * 0.00f,
                left + w * 1.00f, top + h * 0.38f,
                left + w * 0.88f, top + h * 0.68f
            )
            // 하단 곡선
            cubicTo(
                left + w * 0.75f, top + h * 0.97f,
                left + w * 0.40f, top + h * 1.00f,
                left + w * 0.22f, top + h * 0.86f
            )
            // 왼쪽 곡선
            cubicTo(
                left + w * 0.00f, top + h * 0.68f,
                left + w * 0.02f, top + h * 0.38f,
                left + w * 0.14f, top + h * 0.22f
            )
            // 시작점으로 복귀
            cubicTo(
                left + w * 0.24f, top + h * 0.05f,
                left + w * 0.36f, top + h * 0.08f,
                left + w * 0.50f, top + h * 0.05f
            )
            close()
        }
        drawPath(path = path, color = color)

        // 하이라이트 (홀드 상단 광택 효과)
        val hlPath = Path().apply {
            val hx = center.x - w * 0.05f
            val hy = center.y - h * 0.20f
            moveTo(hx, hy)
            cubicTo(
                hx + w * 0.15f, hy - h * 0.10f,
                hx + w * 0.25f, hy + h * 0.05f,
                hx + w * 0.10f, hy + h * 0.12f
            )
            cubicTo(
                hx - w * 0.05f, hy + h * 0.12f,
                hx - w * 0.10f, hy + h * 0.00f,
                hx, hy
            )
            close()
        }
        drawPath(path = hlPath, color = Color.White.copy(alpha = 0.25f))
    }
}
