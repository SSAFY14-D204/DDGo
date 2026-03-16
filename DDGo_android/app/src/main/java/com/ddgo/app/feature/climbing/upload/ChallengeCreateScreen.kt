package com.ddgo.app.feature.climbing.upload

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// ─────────────────────────────────────────────
// 내부 데이터
// ─────────────────────────────────────────────

private enum class CreateStep { GYM_NAME, LEVEL, COLOR }

private data class ClimbingLevel(val label: String, val color: Color)

private val LEVELS = listOf(
    ClimbingLevel("V0",  Color(0xFF757575)),  // 회색
    ClimbingLevel("V1",  Color(0xFFBDBDBD)),  // 밝은 회색
    ClimbingLevel("V2",  Color(0xFFF5F5F5)),  // 흰색
    ClimbingLevel("V3",  Color(0xFF795548)),  // 갈색
    ClimbingLevel("V4",  Color(0xFF5C6BC0)),  // 남색
    ClimbingLevel("V5",  Color(0xFF8E24AA)),  // 보라
    ClimbingLevel("V6",  Color(0xFF43A047)),  // 초록
    ClimbingLevel("V7",  Color(0xFFCDDC39)),  // 연두
    ClimbingLevel("V8",  Color(0xFFFDD835)),  // 노랑
    ClimbingLevel("V9",  Color(0xFFFF9800)),  // 주황
    ClimbingLevel("V10", Color(0xFFE53935)),  // 빨강
)

private data class HoldColor(val name: String, val color: Color)

private val HOLD_COLORS = listOf(
    HoldColor("red",    Color(0xFFE53935)),
    HoldColor("orange", Color(0xFFFF6F00)),
    HoldColor("yellow", Color(0xFFFFD600)),
    HoldColor("green",  Color(0xFF43A047)),
    HoldColor("cyan",   Color(0xFF00B8D4)),
    HoldColor("blue",   Color(0xFF1565C0)),
    HoldColor("purple", Color(0xFF7B1FA2)),
    HoldColor("brown",  Color(0xFF5D4037)),
    HoldColor("pink",   Color(0xFFF16698)),
    HoldColor("white",  Color(0xFFFFFFFF)),
    HoldColor("gray",   Color(0xFF757575)),
    HoldColor("black",  Color(0xFF212121)),
)

private val GYM_MOCK_LIST = listOf(
    "노보 클라이밍", "더클라이밍 강남", "더클라이밍 홍대",
    "클라임웍스", "피커스 클라이밍", "블랙야크 클라이밍"
)

// ─────────────────────────────────────────────
// 메인 화면
// ─────────────────────────────────────────────

@Composable
fun ChallengeCreateScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToNext: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var step by remember { mutableStateOf(CreateStep.GYM_NAME) }

    BackHandler {
        when (step) {
            CreateStep.GYM_NAME -> onNavigateBack()
            CreateStep.LEVEL    -> step = CreateStep.GYM_NAME
            CreateStep.COLOR    -> step = CreateStep.LEVEL
        }
    }

    when (step) {
        CreateStep.GYM_NAME -> GymNameStep(
            initialName = viewModel.gymName,
            onConfirm   = { viewModel.updateGymInfo(0, it) },
            onNext      = { step = CreateStep.LEVEL },
            onBack      = onNavigateBack
        )
        CreateStep.LEVEL -> LevelStep(
            gymName      = viewModel.gymName,
            initialIndex = LEVELS.indexOfFirst { it.label == viewModel.difficultyLevel }
                .takeIf { it >= 0 } ?: 5,
            onConfirm    = { viewModel.updateDifficulty(LEVELS[it].label) },
            onNext       = { step = CreateStep.COLOR },
            onBack       = { step = CreateStep.GYM_NAME }
        )
        CreateStep.COLOR -> ColorStep(
            initialColorIndex = HOLD_COLORS.indexOfFirst { it.name == viewModel.holdColor }
                .takeIf { it >= 0 } ?: 8,
            onConfirm         = { viewModel.updateHoldColor(HOLD_COLORS[it].name) },
            onNext            = onNavigateToNext,
            onBack            = { step = CreateStep.LEVEL }
        )
    }
}

// ─────────────────────────────────────────────
// 공통 AppBar
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "클라이밍 기록",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
    )
}

// ─────────────────────────────────────────────
// Step 1 : 클라이밍장 이름 입력
// ─────────────────────────────────────────────

@Composable
private fun GymNameStep(
    initialName: String,
    onConfirm: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf(initialName) }
    var confirmed by remember { mutableStateOf(initialName.isNotBlank()) }

    val suggestions = remember(inputText) {
        if (inputText.isBlank()) emptyList()
        else GYM_MOCK_LIST.filter { it.contains(inputText, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CreateAppBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "클라이밍장 이름을\n입력해주세요",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 32.sp
                )

                Spacer(Modifier.height(32.dp))

                // 텍스트 입력
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it; confirmed = false },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("클라이밍장 이름", color = Color(0xFF555555))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor    = Color.White,
                        unfocusedTextColor  = Color.White,
                        focusedBorderColor  = Color.White,
                        unfocusedBorderColor = Color(0xFF555555),
                        cursorColor         = Color.White
                    )
                )

                // 검색 결과 칩 목록
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { gym ->
                            SuggestionChip(
                                onClick = {
                                    inputText = gym
                                    confirmed = true
                                    onConfirm(gym)
                                },
                                label = { Text(gym, color = Color.White) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (confirmed && inputText == gym)
                                        Color(0xFF1C3A5E) else Color(0xFF1C1C1E)
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = if (confirmed && inputText == gym)
                                        Color(0xFF4A90E2) else Color(0xFF555555)
                                )
                            )
                        }
                    }
                }
            }

            // 다음 버튼
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onConfirm(inputText)
                        onNext()
                    }
                },
                enabled = inputText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A90E2),
                    contentColor   = Color.White,
                    disabledContainerColor = Color(0xFF333333),
                    disabledContentColor   = Color(0xFF666666)
                )
            ) {
                Text(text = "다음", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Step 2 : 레벨 선택 (수직 드래그 바)
// ─────────────────────────────────────────────

@Composable
private fun LevelStep(
    gymName: String,
    initialIndex: Int,
    onConfirm: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }
    var hasInteracted by remember { mutableStateOf(false) }
    var barHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    val selectedLevel = LEVELS[selectedIndex]
    val buttonTextColor = if (selectedLevel.color.luminance() > 0.5f) Color.Black else Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CreateAppBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 헤더 영역
            Column {
                Text(
                    text = "도전하는 볼더링 문제의\n레벨을 골라주세요",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 30.sp
                )
                Spacer(Modifier.height(12.dp))
                // 선택된 클라이밍장 칩
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(gymName, color = Color.White, fontSize = 13.sp)
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF1C1C1E)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF555555)
                    )
                )
            }

            // 레벨 바 + 화살표 + 툴팁
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 컬러 레벨 바
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .fillMaxHeight(0.85f)
                            .onSizeChanged { barHeightPx = it.height.toFloat() }
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        hasInteracted = true
                                        val i = ((offset.y / barHeightPx) * LEVELS.size)
                                            .toInt()
                                            .coerceIn(0, LEVELS.lastIndex)
                                        selectedIndex = i
                                        onConfirm(i)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val y = change.position.y.coerceIn(0f, barHeightPx)
                                        val i = ((y / barHeightPx) * LEVELS.size)
                                            .toInt()
                                            .coerceIn(0, LEVELS.lastIndex)
                                        selectedIndex = i
                                        onConfirm(i)
                                    }
                                )
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LEVELS.forEach { level ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .background(level.color)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    // 화살표 인디케이터
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .fillMaxHeight(0.85f)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val arrowH = 22.dp.toPx()
                            val arrowW = 18.dp.toPx()
                            val arrowY = ((selectedIndex + 0.5f) / LEVELS.size) * size.height

                            // 왼쪽을 향하는 삼각형 ◄
                            val path = Path().apply {
                                moveTo(arrowW, arrowY - arrowH / 2f)
                                lineTo(0f,     arrowY)
                                lineTo(arrowW, arrowY + arrowH / 2f)
                                close()
                            }
                            drawPath(path = path, color = Color(0xFF5C9EFF))
                        }
                    }

                    // 첫 방문 툴팁 말풍선
                    if (!hasInteracted) {
                        Spacer(Modifier.width(8.dp))
                        DragTooltip()
                    }
                }
            }

            // 다음 버튼 (선택된 레벨 색상)
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = selectedLevel.color,
                    contentColor   = buttonTextColor
                )
            ) {
                Text(text = "다음", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DragTooltip() {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = "위 아래로\n드래그 해주세요!",
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp
        )
    }
}

// ─────────────────────────────────────────────
// Step 3 : 홀드 컬러 선택
// ─────────────────────────────────────────────

@Composable
private fun ColorStep(
    initialColorIndex: Int,
    onConfirm: (Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(initialColorIndex) }
    val selectedHoldColor = HOLD_COLORS[selectedIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CreateAppBar(onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp)
        ) {
            // 제목
            Text(
                text = "문제 홀드의\n컬러를 골라주세요",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 30.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            // 홀드 이미지 영역
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // 블러 그림자 (아래)
                Canvas(
                    modifier = Modifier
                        .offset(y = 30.dp)
                        .size(180.dp, 60.dp)
                ) {
                    drawOval(
                        color = selectedHoldColor.color.copy(alpha = 0.35f),
                        topLeft = Offset(size.width * 0.1f, size.height * 0.2f),
                        size = androidx.compose.ui.geometry.Size(
                            size.width * 0.8f, size.height * 0.6f
                        )
                    )
                }

                // 홀드 본체 (유기적 blob 형태)
                Canvas(
                    modifier = Modifier.size(220.dp, 160.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // 키드니/빈 모양의 패스
                    val path = Path().apply {
                        moveTo(w * 0.50f, h * 0.05f)
                        cubicTo(
                            w * 0.85f, h * 0.00f,
                            w * 1.00f, h * 0.35f,
                            w * 0.90f, h * 0.65f
                        )
                        cubicTo(
                            w * 0.78f, h * 0.95f,
                            w * 0.45f, h * 1.00f,
                            w * 0.25f, h * 0.88f
                        )
                        cubicTo(
                            w * 0.00f, h * 0.72f,
                            w * 0.00f, h * 0.40f,
                            w * 0.12f, h * 0.22f
                        )
                        cubicTo(
                            w * 0.22f, h * 0.05f,
                            w * 0.35f, h * 0.08f,
                            w * 0.50f, h * 0.05f
                        )
                        close()
                    }
                    drawPath(path = path, color = selectedHoldColor.color)
                }
            }

            // 컬러 팔레트 카드
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                // 4열 3행 컬러 그리드
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HOLD_COLORS.chunked(4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            row.forEach { holdColor ->
                                val idx = HOLD_COLORS.indexOf(holdColor)
                                val isSelected = idx == selectedIndex
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (holdColor.name == "white") Color(0xFFF0F0F0)
                                            else holdColor.color
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(
                                                width = 3.dp,
                                                color = Color(0xFF4A90E2),
                                                shape = CircleShape
                                            ) else Modifier
                                        )
                                        .clickable {
                                            selectedIndex = idx
                                            onConfirm(idx)
                                        }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            // 현재 선택된 색상을 반드시 ViewModel에 저장한 뒤 이동
                            onConfirm(selectedIndex)
                            onNext()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = selectedHoldColor.color,
                            contentColor   = if (selectedHoldColor.color.luminance() > 0.5f)
                                Color.Black else Color.White
                        )
                    ) {
                        Text(text = "다음", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
