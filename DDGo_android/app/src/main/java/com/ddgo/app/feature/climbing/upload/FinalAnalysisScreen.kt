package com.ddgo.app.feature.climbing.upload

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// ── 색상 상수 ─────────────────────────────────────────────────────────────────
private val FA_BG         = Color(0xFF0D0D0D)
private val FA_CARD       = Color(0xFF1A1A2E)
private val FA_ACCENT1    = Color(0xFF7B2FFF)  // 보라
private val FA_ACCENT2    = Color(0xFF00C2FF)  // 파랑
private val FA_SUCCESS    = Color(0xFF7B2FFF)  // 성공: 보라
private val FA_FAIL       = Color(0xFFFF4C61)  // 실패: 빨강
private val FA_TEXT_MUTED = Color(0xFF9E9E9E)
private val FA_DIVIDER    = Color(0xFF2A2A2A)

/**
 * 최종 분석 결과 데이터 모델.
 * 외부(ViewModel 또는 서버 응답)에서 주입받습니다.
 *
 * @param isSuccess         문제 풀이 성공 여부
 * @param reachedHolds      평균 도달 홀드 수 (예: 9)
 * @param totalHolds        전체 홀드 수 (예: 14)
 * @param balanceRatio      무게중심 안정 비율 (0~100, 예: 68)
 * @param stabilityTimeline 무게중심 안정 시계열 값 (연속 float 값, 범위 0~1)
 *                          예) [0.3f, 0.5f, 0.6f, 0.4f, 0.8f, ...]
 * @param attemptCount      현재 시도 수 (그래프 X축 라벨링용)
 * @param currentAttempt    현재 강조할 시도 번호 (1-based)
 */
data class FinalAnalysisData(
    val isSuccess: Boolean = true,
    val reachedHolds: Int = 9,
    val totalHolds: Int = 14,
    val balanceRatio: Int = 68,
    val stabilityTimeline: List<Float> = emptyList(),
    val attemptCount: Int = 4,
    val currentAttempt: Int = 4
)

/**
 * 최종 분석 결과 화면.
 *
 * @param data              분석 결과 데이터 (외부 주입)
 * @param onNavigateBack    뒤로가기 콜백
 * @param onNavigateCompare "다음 시도와 비교분석 하기" 버튼 콜백
 */
@Composable
fun FinalAnalysisScreen(
    data: FinalAnalysisData = FinalAnalysisData(),
    onNavigateBack: () -> Unit = {},
    onNavigateToMain: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FA_BG)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 96.dp)
        ) {
            // 상단 앱바
            FinalAnalysisTopBar(onNavigateBack = onNavigateBack)

            Spacer(Modifier.height(16.dp))

            // 문제 풀이 여부
            SolvedStatusSection(isSuccess = data.isSuccess)

            FaDivider()

            // 평균 도달 홀드
            ReachedHoldsSection(
                reachedHolds = data.reachedHolds,
                totalHolds = data.totalHolds
            )

            FaDivider()

            // 무게중심 안정 비율
            BalanceRatioSection(balanceRatio = data.balanceRatio)

            Spacer(Modifier.height(8.dp))

            // 안정성 그래프
            StabilityGraphSection(
                timeline = data.stabilityTimeline,
                attemptCount = data.attemptCount,
                currentAttempt = data.currentAttempt
            )

            Spacer(Modifier.height(16.dp))
        }

        // 하단 고정 버튼
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, FA_BG.copy(alpha = 0.95f), FA_BG)
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = onNavigateToMain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FA_ACCENT1
                )
            ) {
                Text(
                    text = "분석 완료",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── 상단 앱바 ────────────────────────────────────────────────────────────────

@Composable
private fun FinalAnalysisTopBar(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color.White
            )
        }
        Text(
            text = "문제 분석",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

// ── 구분선 ────────────────────────────────────────────────────────────────────

@Composable
private fun FaDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FA_DIVIDER)
    )
}

// ── 문제 풀이 여부 섹션 ───────────────────────────────────────────────────────

@Composable
private fun SolvedStatusSection(isSuccess: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "문제 풀이 여부",
            fontSize = 16.sp,
            color = FA_TEXT_MUTED,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isSuccess) "성공" else "실패",
            fontSize = 48.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isSuccess) FA_SUCCESS else FA_FAIL
        )
    }
}

// ── 평균 도달 홀드 섹션 ───────────────────────────────────────────────────────

@Composable
private fun ReachedHoldsSection(reachedHolds: Int, totalHolds: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "평균 도달 홀드",
            fontSize = 16.sp,
            color = FA_TEXT_MUTED,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "${reachedHolds}번",
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                color = FA_ACCENT1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/${totalHolds}번",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = FA_TEXT_MUTED,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

// ── 무게중심 안정 비율 섹션 ──────────────────────────────────────────────────

@Composable
private fun BalanceRatioSection(balanceRatio: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 소제목 줄
        Text(
            text = "최근 인접도",
            fontSize = 13.sp,
            color = FA_TEXT_MUTED,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "무게중심 안정 비율",
            fontSize = 16.sp,
            color = FA_TEXT_MUTED,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))

        // 애니메이션 퍼센트
        var targetRatio by remember { mutableIntStateOf(0) }
        val animatedRatio by animateFloatAsState(
            targetValue = targetRatio.toFloat(),
            animationSpec = tween(durationMillis = 800),
            label = "balanceRatio"
        )
        LaunchedEffect(balanceRatio) { targetRatio = balanceRatio }

        Text(
            text = "${animatedRatio.toInt()}%",
            fontSize = 52.sp,
            fontWeight = FontWeight.ExtraBold,
            color = FA_ACCENT1
        )
    }
}

// ── 안정성 그래프 섹션 ───────────────────────────────────────────────────────

/**
 * 연속 float 값 배열을 받아 두 개의 곡선(파랑/보라)으로 그래프를 그립니다.
 *
 * [timeline] 이 비어 있는 경우 사인 함수 기반 데모 데이터를 사용합니다.
 */
@Composable
private fun StabilityGraphSection(
    timeline: List<Float>,
    attemptCount: Int,
    currentAttempt: Int
) {
    // 실제 데이터가 없으면 데모 곡선 생성
    val displayData = remember(timeline) {
        if (timeline.isNotEmpty()) timeline
        else generateDemoData()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
    ) {
        // 그래프 캔버스
        StabilityLineChart(
            data = displayData,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // X축 라벨 (1차 ~ N차, 현재 강조)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            for (i in 1..attemptCount) {
                AttemptLabel(
                    number = i,
                    isHighlighted = i == currentAttempt
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 실제 연속 데이터를 매끄러운 베지어 곡선으로 렌더링하는 캔버스 컴포넌트.
 *
 * - 파란 선: 입력 데이터 (원본 값)
 * - 보라 선: 이동평균 스무딩 값 (5-point)
 * - 끝부분에 원형 마커 + 세모 포인터 표시
 */
@Composable
private fun StabilityLineChart(
    data: List<Float>,
    modifier: Modifier = Modifier
) {
    // 애니메이션: 그래프가 서서히 나타남
    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000),
        label = "chartProgress"
    )
    LaunchedEffect(Unit) { progress = 1f }

    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val padTop    = 16.dp.toPx()
        val padBottom = 20.dp.toPx()
        val chartH = h - padTop - padBottom

        // 표시할 데이터 포인트 수 (애니메이션)
        val visibleCount = (data.size * animatedProgress).coerceAtLeast(2f).toInt()
            .coerceAtMost(data.size)

        // 시각화할 min/max
        val minV = data.min()
        val maxV = data.max()
        val range = (maxV - minV).coerceAtLeast(0.001f)

        fun xAt(i: Int): Float = if (visibleCount <= 1) w / 2f
            else i.toFloat() / (data.size - 1) * w

        fun yAt(v: Float): Float = padTop + chartH * (1f - (v - minV) / range)

        // 이동평균 (보라 선)
        val smoothed = data.movingAverage(5)

        // ── 파란 선 (원본) ─────────────────────────────────────────────────
        val bluePath = Path().apply {
            moveTo(xAt(0), yAt(data[0]))
            for (i in 1 until visibleCount) {
                val x0 = xAt(i - 1); val y0 = yAt(data[i - 1])
                val x1 = xAt(i);     val y1 = yAt(data[i])
                val cpX = (x0 + x1) / 2f
                cubicTo(cpX, y0, cpX, y1, x1, y1)
            }
        }
        drawPath(
            path = bluePath,
            color = FA_ACCENT2,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // ── 보라 선 (스무딩) ──────────────────────────────────────────────
        val purplePath = Path().apply {
            moveTo(xAt(0), yAt(smoothed[0]))
            for (i in 1 until visibleCount) {
                val x0 = xAt(i - 1); val y0 = yAt(smoothed[i - 1])
                val x1 = xAt(i);     val y1 = yAt(smoothed[i])
                val cpX = (x0 + x1) / 2f
                cubicTo(cpX, y0, cpX, y1, x1, y1)
            }
        }
        drawPath(
            path = purplePath,
            color = FA_ACCENT1,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        // ── 끝 마커 (보라 선 끝) ─────────────────────────────────────────
        val lastIdx = visibleCount - 1
        val endX = xAt(lastIdx)
        val endY = yAt(smoothed[lastIdx])

        // 흰 원형 마커
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(endX, endY)
        )
        drawCircle(
            color = FA_ACCENT1,
            radius = 3.dp.toPx(),
            center = Offset(endX, endY)
        )

        // 세모 포인터 (오른쪽 방향)
        val triSize = 8.dp.toPx()
        val triPath = Path().apply {
            moveTo(endX + triSize, endY)
            lineTo(endX - triSize / 2f, endY - triSize * 0.75f)
            lineTo(endX - triSize / 2f, endY + triSize * 0.75f)
            close()
        }
        drawPath(triPath, color = FA_ACCENT1)
    }
}

// ── X축 라벨 ─────────────────────────────────────────────────────────────────

@Composable
private fun AttemptLabel(number: Int, isHighlighted: Boolean) {
    if (isHighlighted) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFD600))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${number}차",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
    } else {
        Text(
            text = "${number}차",
            fontSize = 13.sp,
            color = FA_TEXT_MUTED
        )
    }
}

// ── 데모 데이터 생성 ──────────────────────────────────────────────────────────

private fun generateDemoData(): List<Float> {
    return (0 until 80).map { i ->
        val t = i / 80f
        // 두 사인 파형의 합산 + 약한 노이즈
        (0.5f + 0.25f * sin(t * Math.PI.toFloat() * 4f) +
                0.15f * sin(t * Math.PI.toFloat() * 9f + 1f))
            .coerceIn(0f, 1f)
    }
}

// ── List<Float> 이동 평균 확장 함수 ──────────────────────────────────────────

private fun List<Float>.movingAverage(windowSize: Int): List<Float> {
    if (isEmpty()) return emptyList()
    return mapIndexed { i, _ ->
        val from = maxOf(0, i - windowSize / 2)
        val to   = minOf(size - 1, i + windowSize / 2)
        subList(from, to + 1).average().toFloat()
    }
}
