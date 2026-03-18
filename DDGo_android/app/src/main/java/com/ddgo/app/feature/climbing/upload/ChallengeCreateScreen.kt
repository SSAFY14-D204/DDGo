package com.ddgo.app.feature.climbing.upload

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace

private enum class CreateStep {
    GYM_NAME,
    LEVEL,
    COLOR
}

@Composable
fun ChallengeCreateScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateToNext: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    var step by rememberSaveable { mutableStateOf(CreateStep.GYM_NAME) }

    BackHandler {
        when (step) {
            CreateStep.GYM_NAME -> onNavigateBack()
            CreateStep.LEVEL -> step = CreateStep.GYM_NAME
            CreateStep.COLOR -> step = CreateStep.LEVEL
        }
    }

    when (step) {
        CreateStep.GYM_NAME -> GymNameStep(
            viewModel = viewModel,
            onNext = { step = CreateStep.LEVEL },
            onBack = onNavigateBack
        )

        CreateStep.LEVEL -> GymLevelStep(
            viewModel = viewModel,
            onNext = { step = CreateStep.COLOR },
            onBack = { step = CreateStep.GYM_NAME }
        )

        CreateStep.COLOR -> GymColorStep(
            viewModel = viewModel,
            onNext = onNavigateToNext,
            onBack = { step = CreateStep.LEVEL }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = "\uD074\uB77C\uC774\uBC0D \uAE30\uB85D",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "\uB4A4\uB85C",
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
    )
}

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
            locationMessage = "\uC704\uCE58 \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4."
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
                text = "\uC8FC\uBCC0 \uC554\uC7A5\uC744 \uAC80\uC0C9\uD574\n\uC120\uD0DD\uD574\uC8FC\uC138\uC694",
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
                Text(
                    text = "\uD604\uC7AC \uC704\uCE58\uB85C \uC554\uC7A5 \uAC80\uC0C9",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
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
                        text = "\uBC84\uD2BC\uC744 \uB20C\uB7EC \uC8FC\uBCC0 \uC554\uC7A5\uC744 \uCC3E\uC544\uBCFC\uAC8C\uC694.",
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
                            text = "\uAC80\uC0C9 \uACB0\uACFC\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.",
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
                                    onClick = { viewModel.resolveSelectedPlace(place) }
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
                        text = "\uC120\uD0DD\uD55C \uC554\uC7A5 \uC815\uBCF4\uB97C \uD655\uC778\uD558\uB294 \uC911\uC785\uB2C8\uB2E4...",
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
                    SelectedGymSummaryCard(
                        gymName = formatGymDisplayName(resolved.gym.displayName),
                        gradeCount = resolved.grades.size
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
                Text(text = "\uB2E4\uC74C", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

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
            text = place.roadAddressName ?: place.addressName ?: "\uC8FC\uC18C \uC815\uBCF4 \uC5C6\uC74C",
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

private data class LevelChoice(
    val sortOrder: Int,
    val label: String,
    val accentColor: Color
)

@Composable
private fun GymLevelStep(
    viewModel: UploadViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val grades = viewModel.resolvedGymGrades
    val selectedGrade = viewModel.selectedGymGrade
    val availableGradesByKey = remember(grades) { buildAvailableGymGradeMap(grades) }
    val selectedPaletteKey = selectedGrade?.let {
        normalizeHoldColorKey(
            colorName = it.colorName,
            colorHex = it.colorHex
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
    ) {
        SelectionStepHeader(
            progressFraction = 0.5f,
            onBack = onBack
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(27.dp))

            Text(
                text = "볼더링 문제의\n레벨을 선택해주세요",
                modifier = Modifier.padding(start = 25.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            GymReferenceSubtitle(
                gymName = formatGymDisplayName(viewModel.gymName),
                modifier = Modifier.padding(start = 25.dp)
            )

            Spacer(modifier = Modifier.height(37.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(415.dp)
            ) {
                DifficultyReferenceBar(
                    selectedPaletteKey = selectedPaletteKey,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 22.dp)
                )

                HoldColorSelectionPanel(
                    availableGradesByKey = availableGradesByKey,
                    selectedPaletteKey = selectedPaletteKey,
                    onSelect = viewModel::selectGymGrade,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 191.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            when {
                grades.isEmpty() -> {
                    Text(
                        text = "선택 가능한 난이도 정보가 없습니다.",
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = Color(0xFF999999),
                        fontSize = 14.sp
                    )
                }

                selectedGrade != null -> {
                    Text(
                        text = "${formatHoldColorDisplayName(selectedGrade.colorName)} 난이도로 기록할게요",
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = resolveGymGradeAccentColor(selectedGrade),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNext,
                enabled = selectedGrade != null,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedGrade != null) Color(0xFF1D9BF0) else Color(0xFF505050),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF505050).copy(alpha = 0.55f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = "다음",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun GymColorStep(
    viewModel: UploadViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val grades = viewModel.resolvedGymGrades
    val selectedGrade = viewModel.selectedGymGrade
    val challengeCreationUiState by viewModel.challengeCreationUiState.collectAsState()
    val selectedLevelSortOrder = viewModel.selectedLevelSortOrder
    val levelMatchedGrades = remember(grades, selectedLevelSortOrder) {
        grades.filter { it.sortOrder == selectedLevelSortOrder }
    }
    val availableGradesByKey = remember(levelMatchedGrades) {
        buildAvailableGymGradeMap(levelMatchedGrades)
    }
    val selectedPaletteKey = selectedGrade?.let {
        normalizeHoldColorKey(
            colorName = it.colorName,
            colorHex = it.colorHex
        )
    }

    LaunchedEffect(challengeCreationUiState) {
        if (challengeCreationUiState is ChallengeCreationUiState.Success) {
            viewModel.consumeChallengeCreationResult()
            onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0E))
    ) {
        SelectionStepHeader(
            progressFraction = 1f,
            onBack = onBack
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(27.dp))

            Text(
                text = "문제 홀드의\n컬러를 선택해주세요",
                modifier = Modifier.padding(start = 25.dp),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(22.dp))

            HoldColorHero(
                previewColor = selectedGrade?.let(::resolveGymGradeAccentColor) ?: Color(0xFFFF56A8),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            ColorSelectionSheet(
                availableGradesByKey = availableGradesByKey,
                selectedPaletteKey = selectedPaletteKey,
                onSelect = viewModel::selectGymGrade,
                modifier = Modifier
                    .fillMaxWidth()
            )

            when {
                selectedLevelSortOrder == null -> {
                    Text(
                        text = "먼저 레벨을 선택해주세요.",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }

                levelMatchedGrades.isEmpty() -> {
                    Text(
                        text = "선택한 레벨에 연결된 홀드 컬러가 없습니다.",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }

                challengeCreationUiState is ChallengeCreationUiState.Loading -> {
                    Text(
                        text = "챌린지를 생성하고 있습니다...",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        color = Color(0xFF999999),
                        fontSize = 14.sp
                    )
                }

                challengeCreationUiState is ChallengeCreationUiState.Error -> {
                    Text(
                        text = (challengeCreationUiState as ChallengeCreationUiState.Error).message,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }
            }

            GradientActionButton(
                text = "홀드 찾기",
                enabled = selectedGrade != null &&
                    challengeCreationUiState !is ChallengeCreationUiState.Loading,
                onClick = { viewModel.createChallengeFromSelection() },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 22.dp, end = 22.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .height(58.dp)
            )
        }
    }
}

@Composable
private fun SelectionStepHeader(
    progressFraction: Float,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = Color.White
                )
            }

            Text(
                text = "클라이밍 기록",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF505050))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .height(2.dp)
                    .background(Color(0xFF4396FB))
            )
        }
    }
}

@Composable
private fun GymReferenceSubtitle(
    gymName: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = buildAnnotatedString {
            if (gymName.isBlank()) {
                withStyle(SpanStyle(color = Color(0xFF999999))) {
                    append("기준 난이도표")
                }
            } else {
                withStyle(
                    SpanStyle(
                        color = Color(0xFF4396FB),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(gymName)
                }
                withStyle(SpanStyle(color = Color(0xFF999999))) {
                    append(" 기준 난이도표")
                }
            }
        },
        modifier = modifier,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )
}

@Composable
private fun DifficultyReferenceBar(
    selectedPaletteKey: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(401.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            modifier = Modifier.height(401.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "어려움",
                color = Color(0xFF999999),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "쉬움",
                color = Color(0xFF999999),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .width(35.dp)
                .height(401.dp)
        ) {
            difficultyReferenceSlots.forEach { slot ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 1.dp)
                )
                {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(slot.color)
                            .then(
                                if (slot.key == selectedPaletteKey) {
                                    Modifier.border(2.dp, Color.White)
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelSelectionPanel(
    levels: List<LevelChoice>,
    selectedSortOrder: Int?,
    onSelect: (LevelChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 244.dp, height = 415.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x1A6A707C),
                spotColor = Color(0x1A6A707C)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        if (levels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "난이도 정보가 없습니다.",
                    color = Color(0xFF767676),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(levels, key = { it.sortOrder }) { level ->
                    LevelChoiceCard(
                        level = level,
                        selected = level.sortOrder == selectedSortOrder,
                        onClick = { onSelect(level) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LevelChoiceCard(
    level: LevelChoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0xFFF3F7FF) else Color(0xFFF7F7F8))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF4396FB) else Color(0xFFE4E8EF),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HoldAssetThumbnail(
            color = level.accentColor,
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(10.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = level.label,
                color = Color(0xFF16181D),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "문제 레벨",
                color = Color(0xFF767676),
                fontSize = 12.sp
            )
        }

        if (selected) {
            Text(
                text = "선택됨",
                color = Color(0xFF4396FB),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LevelSummaryCard(
    title: String,
    level: LevelChoice,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF151517))
            .border(1.dp, Color(0xFF2A3C56), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HoldAssetThumbnail(
            color = level.accentColor,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(14.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = Color(0xFF9CCCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = level.label,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "다음 단계에서 홀드 컬러를 선택할 수 있어요",
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun HoldColorHero(
    previewColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .offset(y = 54.dp)
                .size(width = 174.dp, height = 48.dp)
        ) {
            drawOval(
                color = previewColor.copy(alpha = 0.35f),
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.18f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.58f)
            )
        }

        Canvas(modifier = Modifier.size(width = 244.dp, height = 190.dp)) {
            val width = size.width
            val height = size.height
            val path = Path().apply {
                moveTo(width * 0.46f, height * 0.10f)
                cubicTo(width * 0.68f, height * 0.02f, width * 0.94f, height * 0.10f, width * 0.94f, height * 0.40f)
                cubicTo(width * 0.95f, height * 0.72f, width * 0.73f, height * 0.94f, width * 0.45f, height * 0.94f)
                cubicTo(width * 0.19f, height * 0.93f, width * 0.03f, height * 0.70f, width * 0.03f, height * 0.45f)
                cubicTo(width * 0.03f, height * 0.22f, width * 0.21f, height * 0.10f, width * 0.46f, height * 0.10f)
                close()
            }
            drawPath(path = path, color = previewColor)
            drawPath(path = path, color = Color.White.copy(alpha = 0.06f))
        }
    }
}

@Composable
private fun ColorSelectionSheet(
    availableGradesByKey: Map<String, GymGrade>,
    selectedPaletteKey: String?,
    onSelect: (GymGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.White)
            .padding(horizontal = 28.dp, vertical = 26.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            holdPickerRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { slot ->
                        val grade = availableGradesByKey[slot.key]
                        ColorCircleButton(
                            slot = slot,
                            selected = slot.key == selectedPaletteKey,
                            enabled = grade != null,
                            onClick = { grade?.let(onSelect) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorCircleButton(
    slot: HoldPaletteSlot,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .alpha(if (enabled) 1f else 0.28f)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(3.dp, Color(0xFF66B6FF), CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(5.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, Color.White, CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(5.dp)
            .clip(CircleShape)
            .background(slot.color)
            .then(
                if (slot.borderColor != null) {
                    Modifier.border(1.dp, slot.borderColor, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun GradientActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1D9BF0),
                            Color(0xFF855AF7)
                        )
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF505050),
                            Color(0xFF505050)
                        )
                    )
                }
            )
            .alpha(if (enabled) 1f else 0.65f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HoldColorSelectionPanel(
    availableGradesByKey: Map<String, GymGrade>,
    selectedPaletteKey: String?,
    onSelect: (GymGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 244.dp, height = 415.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color(0x1A6A707C),
                spotColor = Color(0x1A6A707C)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 26.dp, vertical = 28.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            holdPaletteRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    row.forEach { slot ->
                        val grade = availableGradesByKey[slot.key]
                        HoldColorTile(
                            slot = slot,
                            enabled = grade != null,
                            selected = slot.key == selectedPaletteKey,
                            onClick = { grade?.let(onSelect) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldColorTile(
    slot: HoldPaletteSlot,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(5.dp)
    val borderWidth = if (selected) 2.dp else if (slot.borderColor != null) 1.dp else 0.dp
    val borderColor = when {
        selected -> Color(0xFF0B0B0E)
        slot.borderColor != null -> slot.borderColor
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(50.dp)
            .alpha(if (enabled) 1f else 0.28f)
            .clip(shape)
            .background(slot.color)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
    )
}

private data class HoldPaletteSlot(
    val key: String,
    val color: Color,
    val borderColor: Color? = null
)

private val difficultyReferenceSlots = listOf(
    HoldPaletteSlot(key = "slate", color = Color(0xFF20272D)),
    HoldPaletteSlot(key = "gray", color = Color(0xFF505050)),
    HoldPaletteSlot(key = "white", color = Color.White),
    HoldPaletteSlot(key = "brown", color = Color(0xFF6B3E1C)),
    HoldPaletteSlot(key = "purple", color = Color(0xFF876FFF)),
    HoldPaletteSlot(key = "navy", color = Color(0xFF373FD7)),
    HoldPaletteSlot(key = "blue", color = Color(0xFF4396FB)),
    HoldPaletteSlot(key = "green", color = Color(0xFF65B969)),
    HoldPaletteSlot(key = "orange", color = Color(0xFFFF7700)),
    HoldPaletteSlot(key = "red", color = Color(0xFFFF0000)),
    HoldPaletteSlot(key = "yellow", color = Color(0xFFFED500))
)

private val holdPaletteRows = listOf(
    listOf(
        HoldPaletteSlot(key = "black", color = Color(0xFF292929), borderColor = Color(0xFF535353)),
        HoldPaletteSlot(key = "gray", color = Color(0xFF505050))
    ),
    listOf(
        HoldPaletteSlot(key = "white", color = Color.White, borderColor = Color(0xFF767676)),
        HoldPaletteSlot(key = "brown", color = Color(0xFF6B3E1C))
    ),
    listOf(
        HoldPaletteSlot(key = "purple", color = Color(0xFF876FFF)),
        HoldPaletteSlot(key = "navy", color = Color(0xFF373FD7))
    ),
    listOf(
        HoldPaletteSlot(key = "blue", color = Color(0xFF4396FB)),
        HoldPaletteSlot(key = "green", color = Color(0xFF65B969))
    ),
    listOf(
        HoldPaletteSlot(key = "orange", color = Color(0xFFFF7700)),
        HoldPaletteSlot(key = "red", color = Color(0xFFFF0000))
    ),
    listOf(
        HoldPaletteSlot(key = "yellow", color = Color(0xFFFED500)),
        HoldPaletteSlot(key = "pink", color = Color(0xFFFF56A8))
    )
)

private val holdPickerRows = listOf(
    listOf(
        HoldPaletteSlot(key = "red", color = Color(0xFFFF1208)),
        HoldPaletteSlot(key = "orange", color = Color(0xFFFF7A00)),
        HoldPaletteSlot(key = "yellow", color = Color(0xFFFFCB12)),
        HoldPaletteSlot(key = "green", color = Color(0xFF48BE5C))
    ),
    listOf(
        HoldPaletteSlot(key = "blue", color = Color(0xFF1FC4E2)),
        HoldPaletteSlot(key = "navy", color = Color(0xFF3F43DB)),
        HoldPaletteSlot(key = "purple", color = Color(0xFF8265EE)),
        HoldPaletteSlot(key = "brown", color = Color(0xFF8A4B16))
    ),
    listOf(
        HoldPaletteSlot(key = "pink", color = Color(0xFFFF43AC)),
        HoldPaletteSlot(key = "white", color = Color(0xFFF5F1F1), borderColor = Color(0xFFE0D9D9)),
        HoldPaletteSlot(key = "gray", color = Color(0xFF5C5C5C)),
        HoldPaletteSlot(key = "black", color = Color(0xFF0A0A12))
    )
)

private fun buildAvailableGymGradeMap(grades: List<GymGrade>): Map<String, GymGrade> {
    return buildMap {
        grades.forEach { grade ->
            normalizeHoldColorKey(
                colorName = grade.colorName,
                colorHex = grade.colorHex
            )?.let { put(it, grade) }
        }
    }
}

private fun buildAvailableLevelChoices(grades: List<GymGrade>): List<LevelChoice> {
    return grades
        .sortedBy { it.sortOrder }
        .distinctBy { it.sortOrder }
        .map { grade ->
            LevelChoice(
                sortOrder = grade.sortOrder,
                label = formatGymGradeLevelText(grade),
                accentColor = resolveGymGradeAccentColor(grade)
            )
        }
}

private fun normalizeHoldColorKey(
    colorName: String,
    colorHex: String?
): String? {
    val normalizedHex = colorHex
        ?.trim()
        ?.removePrefix("#")
        ?.uppercase()

    when (normalizedHex) {
        "20272D" -> return "slate"
        "292929" -> return "black"
        "505050" -> return "gray"
        "FFFFFF" -> return "white"
        "6B3E1C" -> return "brown"
        "876FFF" -> return "purple"
        "373FD7" -> return "navy"
        "4396FB" -> return "blue"
        "65B969" -> return "green"
        "FF7700" -> return "orange"
        "FF0000" -> return "red"
        "FED500" -> return "yellow"
        "FF56A8" -> return "pink"
    }

    return when (colorName.trim().lowercase()) {
        "\uAC80\uC815", "black" -> "black"
        "\uD68C\uC0C9", "gray", "grey" -> "gray"
        "\uD770\uC0C9", "white" -> "white"
        "\uAC08\uC0C9", "brown" -> "brown"
        "\uBCF4\uB77C", "purple" -> "purple"
        "\uB0A8\uC0C9", "navy", "indigo" -> "navy"
        "\uD30C\uB791", "blue", "cyan", "skyblue" -> "blue"
        "\uCD08\uB85D", "green" -> "green"
        "\uC8FC\uD669", "orange" -> "orange"
        "\uBE68\uAC15", "red" -> "red"
        "\uB178\uB791", "yellow" -> "yellow"
        "\uD551\uD06C", "pink" -> "pink"
        else -> null
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
        HoldAssetThumbnail(
            color = accentColor,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = grade.colorName.ifBlank { grade.gradeLabel ?: "\uB09C\uC774\uB3C4" },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatGymGradeLevelText(grade),
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp
            )
        }

        if (selected) {
            Text(
                text = "\uC120\uD0DD\uB428",
                color = Color(0xFF9CCCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SelectedHoldPreviewCard(
    grade: GymGrade,
    modifier: Modifier = Modifier
) {
    val accentColor = resolveGymGradeAccentColor(grade)
    val title = grade.colorName.ifBlank { grade.gradeLabel ?: "\uBB38\uC81C \uC0C9\uC0C1" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF151517))
            .border(1.dp, Color(0xFF2A3C56), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HoldAssetThumbnail(
            color = accentColor,
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(16.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "\uC120\uD0DD\uD55C \uD640\uB4DC \uC0C9\uC0C1",
                color = Color(0xFF9CCCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatGymGradeLevelText(grade),
                color = Color(0xFFB0B0B0),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SelectedGymSummaryCard(
    gymName: String,
    gradeCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF151517))
            .border(1.dp, Color(0xFF2B4F77), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "\uC120\uD0DD\uD55C \uC554\uC7A5",
            color = Color(0xFF9CCCFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = gymName,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryBadge(
                label = "\uB09C\uC774\uB3C4 \uC218",
                value = gradeCount.toString()
            )
        }
    }
}

@Composable
private fun SummaryBadge(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF1F2630))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFB0B0B0),
            fontSize = 12.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun resolveGymGradeAccentColor(grade: GymGrade): Color {
    holdAssetColorOverride(grade.colorName)?.let { return it }

    val colorHex = grade.colorHex?.takeIf { it.isNotBlank() }
    if (colorHex != null) {
        return runCatching { Color(AndroidColor.parseColor(colorHex)) }
            .getOrElse { fallbackColorByName(grade.colorName) }
    }

    return fallbackColorByName(grade.colorName)
}

private fun holdAssetColorOverride(colorName: String): Color? {
    return when (colorName.trim().lowercase()) {
        "\uBE68\uAC15", "red" -> Color(0xFFFF0000)
        "\uC8FC\uD669", "orange" -> Color(0xFFFF7700)
        "\uB178\uB791", "yellow" -> Color(0xFFFED500)
        "\uCD08\uB85D", "green" -> Color(0xFF65B969)
        "\uD30C\uB791", "blue", "cyan" -> Color(0xFF4396FB)
        "\uB0A8\uC0C9", "navy" -> Color(0xFF373FD7)
        "\uBCF4\uB77C", "purple" -> Color(0xFF876FFF)
        "\uAC08\uC0C9", "brown" -> Color(0xFF6B3E1C)
        "\uD551\uD06C", "pink" -> Color(0xFFFF56A8)
        "\uD770\uC0C9", "white" -> Color.White
        "\uD68C\uC0C9", "gray", "grey" -> Color(0xFF505050)
        "\uAC80\uC815", "black" -> Color(0xFF292929)
        else -> null
    }
}

private fun formatGymDisplayName(displayName: String): String {
    return displayName.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
}

private fun formatGymGradeLevelText(grade: GymGrade): String {
    return grade.gradeLabel
        ?.takeIf { it.isNotBlank() }
        ?: "V${grade.sortOrder}"
}

private fun formatHoldColorDisplayName(colorName: String): String {
    return when (colorName.trim().lowercase()) {
        "black", "검정" -> "검정"
        "gray", "grey", "회색" -> "회색"
        "white", "흰색" -> "흰색"
        "brown", "갈색" -> "갈색"
        "purple", "보라" -> "보라"
        "navy", "indigo", "남색" -> "남색"
        "blue", "cyan", "skyblue", "파랑" -> "파랑"
        "green", "초록" -> "초록"
        "orange", "주황" -> "주황"
        "red", "빨강" -> "빨강"
        "yellow", "노랑" -> "노랑"
        "pink", "핑크" -> "핑크"
        else -> colorName
    }
}

private fun fallbackColorByName(colorName: String): Color {
    return when (colorName.trim().lowercase()) {
        "\uBE68\uAC15", "red" -> Color(0xFFFF0000)
        "\uC8FC\uD669", "orange" -> Color(0xFFFF7700)
        "\uB178\uB791", "yellow" -> Color(0xFFFED500)
        "\uCD08\uB85D", "green" -> Color(0xFF65B969)
        "\uD30C\uB791", "blue", "cyan" -> Color(0xFF4396FB)
        "\uB0A8\uC0C9", "navy" -> Color(0xFF373FD7)
        "\uBCF4\uB77C", "purple" -> Color(0xFF876FFF)
        "\uAC08\uC0C9", "brown" -> Color(0xFF6B3E1C)
        "\uD551\uD06C", "pink" -> Color(0xFFFF56A8)
        "\uD770\uC0C9", "white" -> Color.White
        "\uD68C\uC0C9", "gray", "grey" -> Color(0xFF505050)
        "\uAC80\uC815", "black" -> Color(0xFF292929)
        else -> Color(0xFF4A90E2)
    }
}

@Composable
private fun HoldAssetThumbnail(
    color: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .rotate(90f)
            .clip(shape)
            .background(color)
            .then(
                if (color == Color.White) {
                    Modifier.border(1.dp, Color(0xFF767676), shape)
                } else if (color == Color(0xFF292929)) {
                    Modifier.border(1.dp, Color(0xFF535353), shape)
                } else {
                    Modifier
                }
            )
    )
}

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

private fun loadCurrentLocation(
    context: Context,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    val locationManager = context.getSystemService(LocationManager::class.java)
        ?: run {
            onError("\uC704\uCE58 \uC11C\uBE44\uC2A4\uB97C \uC0AC\uC6A9\uD560 \uC218 \uC5C6\uC2B5\uB2C8\uB2E4.")
            return
        }

    val provider = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    } ?: run {
        onError("\uC704\uCE58 \uC11C\uBE44\uC2A4\uB97C \uCF1C\uC8FC\uC138\uC694.")
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
                onError("\uD604\uC7AC \uC704\uCE58\uB97C \uAC00\uC838\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.")
            }
        }
    } catch (_: SecurityException) {
        onError("\uC704\uCE58 \uAD8C\uD55C\uC774 \uD544\uC694\uD569\uB2C8\uB2E4.")
    }
}
