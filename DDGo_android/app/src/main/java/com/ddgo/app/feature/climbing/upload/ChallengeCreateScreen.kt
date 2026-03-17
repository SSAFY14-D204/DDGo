package com.ddgo.app.feature.climbing.upload

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace

// ─────────────────────────────────────────────
// 내부 데이터
// ─────────────────────────────────────────────

private enum class CreateStep { GYM_NAME, GRADE }

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
            CreateStep.GRADE    -> step = CreateStep.GYM_NAME
        }
    }

    when (step) {
        CreateStep.GYM_NAME -> GymNameStep(
            viewModel = viewModel,
            onNext = { step = CreateStep.GRADE },
            onBack = onNavigateBack
        )
        CreateStep.GRADE -> GymGradeStep(
            viewModel = viewModel,
            onNext = onNavigateToNext,
            onBack = { step = CreateStep.GYM_NAME }
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
    viewModel: UploadViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gymSearchUiState by viewModel.gymSearchUiState.collectAsState()
    val gymResolveUiState by viewModel.gymResolveUiState.collectAsState()
    var locationMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            loadCurrentLocation(
                context = context,
                onSuccess = { latitude, longitude ->
                    locationMessage = null
                    viewModel.searchNearbyPlaces(latitude, longitude)
                },
                onError = { locationMessage = it }
            )
        } else {
            locationMessage = "위치 권한이 필요합니다."
        }
    }

    val searchAroundCurrentLocation = {
        if (hasLocationPermission(context)) {
            loadCurrentLocation(
                context = context,
                onSuccess = { latitude, longitude ->
                    locationMessage = null
                    viewModel.searchNearbyPlaces(latitude, longitude)
                },
                onError = { locationMessage = it }
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
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
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                text = "주변 암장을 검색해서 선택해 주세요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = searchAroundCurrentLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A90E2),
                    contentColor = Color.White
                )
            ) {
                Text("현재 위치로 주변 암장 검색", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            if (locationMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = locationMessage.orEmpty(),
                    color = Color(0xFFFF8A8A),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (gymSearchUiState) {
                GymSearchUiState.Idle -> {
                    Text(
                        text = "버튼을 눌러 주변 암장을 검색하세요.",
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                }

                GymSearchUiState.Loading -> {
                    CircularProgressIndicator(color = Color(0xFF4A90E2))
                }

                is GymSearchUiState.Error -> {
                    Text(
                        text = (gymSearchUiState as GymSearchUiState.Error).message,
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }

                is GymSearchUiState.Success -> {
                    val places = (gymSearchUiState as GymSearchUiState.Success).places

                    if (places.isEmpty()) {
                        Text(
                            text = "검색 결과가 없습니다.",
                            color = Color(0xFFB0B0B0),
                            fontSize = 14.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(places, key = { it.externalPlaceId }) { place ->
                                NearbyPlaceItem(
                                    place = place,
                                    selected = place.externalPlaceId ==
                                        viewModel.selectedNearbyPlace?.externalPlaceId,
                                    onClick = {
                                        viewModel.selectNearbyPlaceForNextStep(place)
                                        onNext()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (gymResolveUiState) {
                GymResolveUiState.Idle -> Unit

                GymResolveUiState.Loading -> {
                    Text(
                        text = "선택한 장소를 확인하는 중입니다...",
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                }

                is GymResolveUiState.Error -> {
                    Text(
                        text = (gymResolveUiState as GymResolveUiState.Error).message,
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }

                is GymResolveUiState.Success -> {
                    val resolved = (gymResolveUiState as GymResolveUiState.Success).resolvedGym
                    Text(
                        text = "선택된 암장: ${resolved.gym.displayName}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "불러온 난이도 수: ${resolved.grades.size}",
                        color = Color(0xFFB0B0B0),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNext,
                enabled = gymResolveUiState is GymResolveUiState.Success,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A90E2),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF333333),
                    disabledContentColor = Color(0xFF666666)
                )
            ) {
                Text(text = "다음", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Kakao 검색 결과 장소 1건을 표시하는 아이템.
 */
@Composable
private fun NearbyPlaceItem(
    place: NearbyPlace,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1C3A5E) else Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            text = place.placeName,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = place.roadAddressName ?: place.addressName ?: "주소 정보 없음",
            color = Color(0xFFB0B0B0),
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        if (place.distanceMeters != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${place.distanceMeters}m",
                color = Color(0xFF9CCCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GymGradeStep(
    viewModel: UploadViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val grades = viewModel.resolvedGymGrades
    val selectedGrade = viewModel.selectedGymGrade
    val challengeCreationUiState by viewModel.challengeCreationUiState.collectAsState()

    LaunchedEffect(challengeCreationUiState) {
        if (challengeCreationUiState is ChallengeCreationUiState.Success) {
            viewModel.consumeChallengeCreationResult()
            onNext()
        }
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "해당 암장의 난이도를 선택해 주세요",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(viewModel.gymName, color = Color.White, fontSize = 13.sp)
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = Color(0xFF1C1C1E)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = Color(0xFF555555)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (grades.isEmpty()) {
                    Text(
                        text = "선택 가능한 난이도가 없습니다.",
                        color = Color(0xFFB0B0B0),
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(grades, key = { it.gymGradeId }) { grade ->
                            GymGradeItem(
                                grade = grade,
                                selected = selectedGrade?.gymGradeId == grade.gymGradeId,
                                onClick = { viewModel.selectGymGrade(grade) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (challengeCreationUiState) {
                    ChallengeCreationUiState.Idle -> Unit
                    ChallengeCreationUiState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color(0xFF4A90E2),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "챌린지를 생성하고 있습니다...",
                                color = Color(0xFFB0B0B0),
                                fontSize = 14.sp
                            )
                        }
                    }

                    is ChallengeCreationUiState.Error -> {
                        Text(
                            text = (challengeCreationUiState as ChallengeCreationUiState.Error).message,
                            color = Color(0xFFFF8A8A),
                            fontSize = 14.sp
                        )
                    }

                    is ChallengeCreationUiState.Success -> Unit
                }
            }

            Button(
                onClick = {
                    val currentGymId = viewModel.gymId
                    if (currentGymId == null || currentGymId <= 0 || selectedGrade == null) {
                        onNext()
                    } else {
                        viewModel.createChallengeFromSelection()
                    }
                },
                enabled = challengeCreationUiState !is ChallengeCreationUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4A90E2),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF333333),
                    disabledContentColor = Color(0xFF666666)
                )
            ) {
                Text(
                    text = "챌린지 생성 후 다음",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GymGradeItem(
    grade: GymGrade,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = resolveGymGradeAccentColor(grade)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color(0xFF1C3A5E) else Color(0xFF1C1C1E))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(accentColor)
                .then(
                    if (accentColor == Color.White) {
                        Modifier.border(1.dp, Color(0xFF444444), CircleShape)
                    } else {
                        Modifier
                    }
                )
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = grade.gradeLabel ?: grade.colorName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${grade.colorName} · 순서 ${grade.sortOrder}",
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp
            )
        }

        if (selected) {
            Text(
                text = "선택됨",
                color = Color(0xFF9CCCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun resolveGymGradeAccentColor(grade: GymGrade): Color {
    val colorHex = grade.colorHex?.takeIf { it.isNotBlank() }
    if (colorHex != null) {
        return runCatching { Color(AndroidColor.parseColor(colorHex)) }
            .getOrElse { fallbackColorByName(grade.colorName) }
    }

    return fallbackColorByName(grade.colorName)
}

private fun fallbackColorByName(colorName: String): Color {
    return when (colorName.trim().lowercase()) {
        "빨강", "red" -> Color(0xFFFF3B30)
        "주황", "orange" -> Color(0xFFFF9500)
        "노랑", "yellow" -> Color(0xFFFFD60A)
        "초록", "green" -> Color(0xFF34C759)
        "파랑", "blue" -> Color(0xFF007AFF)
        "남색", "navy" -> Color(0xFF5856D6)
        "보라", "purple" -> Color(0xFFAF52DE)
        "갈색", "brown" -> Color(0xFFA2845E)
        "핑크", "pink" -> Color(0xFFFF2D55)
        "흰색", "white" -> Color.White
        "회색", "gray", "grey" -> Color(0xFF8E8E93)
        "검정", "black" -> Color(0xFF1C1C1E)
        else -> Color(0xFF4A90E2)
    }
}

@Composable
private fun LegacyGymNameStep(
    viewModel: UploadViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val gymSearchUiState by viewModel.gymSearchUiState.collectAsState()
    val gymResolveUiState by viewModel.gymResolveUiState.collectAsState()
    var locationMessage by remember { mutableStateOf<String?>(null) }
    var inputText by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    val suggestions = remember(inputText) {
        GYM_MOCK_LIST.filter { it.contains(inputText, ignoreCase = true) }
    }
    val onConfirm: (String) -> Unit = {}

    /**
     * 위치 권한 요청 런처.
     *
     * 역할:
     * - 런타임 권한을 Compose 화면에서 요청합니다.
     * - 권한 허용 시 현재 위치를 읽고 검색을 시작합니다.
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            loadCurrentLocation(
                context = context,
                onSuccess = { latitude, longitude ->
                    locationMessage = null
                    viewModel.searchNearbyPlaces(latitude, longitude)
                },
                onError = { locationMessage = it }
            )
        } else {
            locationMessage = "위치 권한이 필요합니다."
        }
    }

    /**
     * 현재 위치 기준 검색 시작.
     *
     * 규칙:
     * - 권한이 있으면 바로 위치 조회
     * - 없으면 권한 요청
     */
    val searchAroundCurrentLocation = {
        if (hasLocationPermission(context)) {
            loadCurrentLocation(
                context = context,
                onSuccess = { latitude, longitude ->
                    locationMessage = null
                    viewModel.searchNearbyPlaces(latitude, longitude)
                },
                onError = { locationMessage = it }
            )
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
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

/**
 * 현재 위치 권한이 있는지 확인합니다.
 *
 * 규칙:
 * - FINE 또는 COARSE 둘 중 하나라도 있으면 true로 처리합니다.
 */
private fun hasLocationPermission(context: Context): Boolean {
    val fineGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarseGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    return fineGranted || coarseGranted
}

/**
 * 현재 위치를 1회 조회합니다.
 *
 * 동작:
 * - GPS provider가 가능하면 우선 사용
 * - 아니면 NETWORK provider 사용
 * - 그래도 실패하면 lastKnownLocation fallback
 */
private fun loadCurrentLocation(
    context: Context,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    val locationManager = context.getSystemService(LocationManager::class.java)
        ?: run {
            onError("위치 서비스를 사용할 수 없습니다.")
            return
        }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    } ?: run {
        onError("위치 서비스를 켜 주세요.")
        return
    }

    try {
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            CancellationSignal(),
            ContextCompat.getMainExecutor(context)
        ) { location ->
            if (location != null) {
                onSuccess(location.latitude, location.longitude)
                return@getCurrentLocation
            }

            val lastKnownLocation = locationManager.getLastKnownLocation(provider)
            if (lastKnownLocation != null) {
                onSuccess(lastKnownLocation.latitude, lastKnownLocation.longitude)
            } else {
                onError("현재 위치를 가져오지 못했습니다.")
            }
        }
    } catch (_: SecurityException) {
        onError("위치 권한이 필요합니다.")
    }
}
