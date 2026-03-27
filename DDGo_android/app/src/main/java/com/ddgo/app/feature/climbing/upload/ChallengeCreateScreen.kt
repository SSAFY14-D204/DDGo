package com.ddgo.app.feature.climbing.upload

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.R
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoPrimaryButtonVariant
import com.ddgo.app.core.ui.components.SafeAreaScreen
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoHoldColorPalette
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace

private enum class CreateStep {
    GYM_NAME,
    LEVEL,
    COLOR
}

enum class ChallengeCreatePresentationMode {
    UploadFullScreen,
    RealtimeEmbedded
}

enum class ChallengeCreateEntryStep {
    GYM_NAME,
    LEVEL,
    COLOR
}

@Composable
fun ChallengeCreateScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    initialStep: ChallengeCreateEntryStep = ChallengeCreateEntryStep.GYM_NAME,
    minimumStep: ChallengeCreateEntryStep = initialStep,
    presentationMode: ChallengeCreatePresentationMode = ChallengeCreatePresentationMode.UploadFullScreen,
    onNavigateToNext: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    ChallengeCreateFlowContent(
        viewModel = viewModel,
        initialStep = initialStep,
        minimumStep = minimumStep,
        presentationMode = presentationMode,
        onNavigateToNext = onNavigateToNext,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun ChallengeCreateFlowContent(
    viewModel: UploadViewModel,
    initialStep: ChallengeCreateEntryStep,
    minimumStep: ChallengeCreateEntryStep,
    presentationMode: ChallengeCreatePresentationMode,
    onNavigateToNext: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var step by rememberSaveable(initialStep) { mutableStateOf(initialStep.toCreateStep()) }
    val minimumAllowedStep = minimumStep.toCreateStep()

    val handleBack = {
        val previousStep = step.previousStep()
        if (previousStep == null || previousStep.ordinal < minimumAllowedStep.ordinal) {
            onNavigateBack()
        } else {
            step = previousStep
        }
    }

    BackHandler {
        handleBack()
    }

    when (step) {
        CreateStep.GYM_NAME -> GymNameStep(
            viewModel = viewModel,
            onNext = { step = CreateStep.LEVEL },
            onBack = handleBack
        )

        CreateStep.LEVEL -> GymLevelStep(
            viewModel = viewModel,
            onNext = { step = CreateStep.COLOR },
            onBack = handleBack,
            presentationMode = presentationMode
        )

        CreateStep.COLOR -> GymColorStep(
            viewModel = viewModel,
            onNext = onNavigateToNext,
            onBack = handleBack,
            presentationMode = presentationMode
        )
    }
}

private fun ChallengeCreateEntryStep.toCreateStep(): CreateStep = when (this) {
    ChallengeCreateEntryStep.GYM_NAME -> CreateStep.GYM_NAME
    ChallengeCreateEntryStep.LEVEL -> CreateStep.LEVEL
    ChallengeCreateEntryStep.COLOR -> CreateStep.COLOR
}

private fun CreateStep.previousStep(): CreateStep? = when (this) {
    CreateStep.GYM_NAME -> null
    CreateStep.LEVEL -> CreateStep.GYM_NAME
    CreateStep.COLOR -> CreateStep.LEVEL
}

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
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isResolvingLocation by remember { mutableStateOf(false) }
    var hasShownCachedLocationResult by remember { mutableStateOf(false) }

    val startIncrementalLocationSearch = {
        hasShownCachedLocationResult = false
        isResolvingLocation = true
        loadCurrentLocationIncrementally(
            context = context,
            onCachedLocation = { latitude, longitude ->
                hasShownCachedLocationResult = true
                locationMessage = null
                viewModel.searchNearbyPlaces(
                    latitude = latitude,
                    longitude = longitude,
                    query = "",
                    nearbyOnly = true
                )
            },
            onFreshLocation = { latitude, longitude, isSameAsCached ->
                isResolvingLocation = false
                locationMessage = null
                if (!isSameAsCached) {
                    viewModel.searchNearbyPlaces(
                        latitude = latitude,
                        longitude = longitude,
                        query = "",
                        nearbyOnly = true
                    )
                }
            },
            onError = {
                isResolvingLocation = false
                locationMessage = if (hasShownCachedLocationResult) {
                    "최근 위치 기준 결과를 먼저 보여주고 있어요."
                } else {
                    it
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            startIncrementalLocationSearch()
        } else {
            isResolvingLocation = false
            locationMessage = "위치 권한이 필요합니다."
        }
    }

    val searchAroundCurrentLocation = {
        if (hasLocationPermission(context)) {
            startIncrementalLocationSearch()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val submitKeywordSearch = {
        val query = searchQuery.trim()
        locationMessage = null
        val cachedLatitude = viewModel.lastSearchLatitude
        val cachedLongitude = viewModel.lastSearchLongitude

        if (cachedLatitude != null && cachedLongitude != null) {
            viewModel.searchNearbyPlaces(
                latitude = cachedLatitude,
                longitude = cachedLongitude,
                query = query
            )
        } else {
            searchAroundCurrentLocation()
        }
    }

    SafeAreaScreen(
        modifier = Modifier,
        containerColor = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CreateAppBar(onBack = onBack)

        // 스크롤 가능한 콘텐츠 영역: 하단 버튼 공간을 제외하고 남은 공간을 차지
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
        ) {
            SearchHeroSection(
                query = searchQuery,
                onQueryChange = {
                    searchQuery = it
                    locationMessage = null
                },
                onSearch = submitKeywordSearch,
                onCurrentLocationSearch = searchAroundCurrentLocation,
                isBusy = isResolvingLocation || gymSearchUiState is GymSearchUiState.Loading,
                locationMessage = locationMessage,
                isResolvingLocation = isResolvingLocation
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (gymSearchUiState) {
                GymSearchUiState.Idle -> {
                    SearchEmptyGuide()
                }

                GymSearchUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4A90E2))
                    }
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
                        // weight(1f)로 남은 공간을 유동적으로 차지 → 하단 버튼이 가려지지 않음
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
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
                        text = "선택한 암장 정보를 확인하는 중입니다...",
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
        }

        // "다음" 버튼: Column 하단에 고정 배치 (스크롤 콘텐츠 밖)
        Button(
            onClick = onNext,
            enabled = gymResolveUiState is GymResolveUiState.Success,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
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

@Composable
private fun NearbyPlaceItem(
    place: NearbyPlace,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF1A2744) else Color(0xFF1E1E1E))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF42A5F5) else Color(0xFF2A2A2A),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SearchResultLeadingBadge(selected = selected)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.placeName,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = place.roadAddressName ?: place.addressName ?: "주소 정보 없음",
                    color = Color(0xFFB0B0B0),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (place.distanceMeters != null) {
                    Text(
                        text = formatDistanceLabel(place.distanceMeters),
                        color = Color(0xFF82B1FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selected) {
                    SelectionPill()
                }
            }
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
    onBack: () -> Unit,
    presentationMode: ChallengeCreatePresentationMode = ChallengeCreatePresentationMode.UploadFullScreen
) {
    val isEmbedded = presentationMode == ChallengeCreatePresentationMode.RealtimeEmbedded
    val screenModifier = if (isEmbedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
    val contentModifier = if (isEmbedded) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
    val grades = viewModel.resolvedGymGrades
    val selectedLevelSortOrder = viewModel.selectedLevelSortOrder
    val selectedGymGradeId = viewModel.selectedGymGradeId
    val selectedLevelGrade = remember(
        grades,
        selectedLevelSortOrder,
        selectedGymGradeId,
        viewModel.selectedGymGrade
    ) {
        viewModel.selectedGymGrade
            ?.takeIf { selected -> grades.any { it.gymGradeId == selected.gymGradeId } }
            ?: selectedGymGradeId?.let { gradeId ->
                grades.firstOrNull { it.gymGradeId.toLong() == gradeId }
            }
            ?: selectedLevelSortOrder?.let { sortOrder ->
                grades.firstOrNull { it.sortOrder == sortOrder }
            }
    }

    SafeAreaScreen(
        modifier = screenModifier,
        containerColor = Color(0xFF0B0B0E)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectionStepHeader(
            progressFraction = 0.5f,
            onBack = onBack,
            presentationMode = presentationMode
        )

        Column(modifier = contentModifier) {
            Spacer(modifier = Modifier.height(27.dp))

            Text(
                text = if (isEmbedded) {
                    "문제 난이도를\n골라 주세요"
                } else {
                    "볼더링 문제의\n레벨을 선택해주세요"
                },
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
                GymGradeDifficultyReferenceBar(
                    grades = grades,
                    selectedGymGradeId = selectedGymGradeId,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 22.dp)
                )

                GymGradeColorSwatchPanel(
                    grades = grades,
                    selectedGymGradeId = selectedGymGradeId,
                    onSelect = { grade ->
                        viewModel.selectGymGrade(grade)
                        if (isEmbedded) {
                            onNext()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 191.dp)
                )
            }

            if (isEmbedded) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            when {
                grades.isEmpty() -> {
                    Text(
                        text = "선택 가능한 난이도 정보가 없습니다.",
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = Color(0xFF999999),
                        fontSize = 14.sp
                    )
                }

                selectedLevelGrade != null -> {
                    Text(
                        text = if (isEmbedded) {
                            "${resolveHoldColorDisplayName(selectedLevelGrade.colorName, selectedLevelGrade.colorHex)} 난이도로 준비할게요"
                        } else {
                            "${resolveHoldColorDisplayName(selectedLevelGrade.colorName, selectedLevelGrade.colorHex)} 난이도로 기록할게요"
                        },
                        modifier = Modifier.padding(horizontal = 22.dp),
                        color = resolveGymGradeAccentColor(selectedLevelGrade),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isEmbedded) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onNext,
                    enabled = selectedLevelGrade != null,
                    modifier = Modifier
                        .padding(start = 22.dp, end = 22.dp, bottom = 22.dp)
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedLevelGrade != null) Color(0xFF1D9BF0) else Color(0xFF505050),
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
}
}

@Composable
private fun GymColorStep(
    viewModel: UploadViewModel,
    onNext: () -> Unit,
    onBack: () -> Unit,
    presentationMode: ChallengeCreatePresentationMode = ChallengeCreatePresentationMode.UploadFullScreen
) {
    val isEmbedded = presentationMode == ChallengeCreatePresentationMode.RealtimeEmbedded
    val challengeCreationUiState by viewModel.challengeCreationUiState.collectAsState()
    val canBypassChallengeCreationForDev = viewModel.canBypassChallengeCreationForDev
    val selectedLevelSortOrder = viewModel.selectedLevelSortOrder
    val selectedPaletteKey = viewModel.selectedHoldColorKey
    val selectedHoldSlot = remember(selectedPaletteKey) {
        findHoldPaletteSlot(normalizeProblemHoldPaletteKey(selectedPaletteKey))
    }

    LaunchedEffect(challengeCreationUiState, canBypassChallengeCreationForDev, isEmbedded) {
        if (!isEmbedded && !canBypassChallengeCreationForDev && challengeCreationUiState is ChallengeCreationUiState.Success) {
            viewModel.consumeChallengeCreationResult()
            onNext()
        }
    }

    val actionEnabled = when {
        isEmbedded -> {
            selectedLevelSortOrder != null &&
                selectedPaletteKey != null &&
                challengeCreationUiState !is ChallengeCreationUiState.Loading
        }

        else -> {
            (canBypassChallengeCreationForDev || selectedLevelSortOrder != null) &&
                selectedPaletteKey != null &&
                challengeCreationUiState !is ChallengeCreationUiState.Loading
        }
    }

    val helperMessage = when {
        selectedLevelSortOrder == null && !canBypassChallengeCreationForDev -> {
            if (isEmbedded) {
                "먼저 난이도를 선택해 주세요." to Color(0xFFFF8A8A)
            } else {
                "먼저 레벨을 선택해주세요." to Color(0xFFFF8A8A)
            }
        }

        challengeCreationUiState is ChallengeCreationUiState.Loading -> {
            if (isEmbedded) {
                "촬영 준비를 마무리하고 있습니다..." to Color(0xFF999999)
            } else {
                "챌린지를 생성하고 있습니다..." to Color(0xFF999999)
            }
        }

        challengeCreationUiState is ChallengeCreationUiState.Error -> {
            (challengeCreationUiState as ChallengeCreationUiState.Error).message to Color(0xFFFF8A8A)
        }

        else -> null
    }

    SafeAreaScreen(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0B0B0E)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectionStepHeader(
            progressFraction = 1f,
            onBack = onBack,
            presentationMode = presentationMode
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val titleTopPadding = maxHeight * COLOR_STEP_TITLE_TOP_FRACTION
            val heroTopPadding = maxHeight * COLOR_STEP_HERO_TOP_FRACTION
            val heroWidth = maxWidth * COLOR_STEP_HERO_WIDTH_FRACTION
            val heroHeight = maxHeight * COLOR_STEP_HERO_AREA_HEIGHT_FRACTION
            val sheetHeight = maxHeight * (1f - COLOR_STEP_SHEET_TOP_FRACTION)
            val helperMessageBottomPadding = sheetHeight + (maxHeight * 0.02f)

            Text(
                text = if (isEmbedded) {
                    "문제 홀드 색을 골라주세요"
                } else {
                    "문제 홀드의 컬러를 골라주세요"
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 25.dp, top = titleTopPadding),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                lineHeight = 28.sp
            )

            HoldColorHero(
                previewSlot = selectedHoldSlot,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = heroTopPadding)
                    .width(heroWidth)
                    .height(heroHeight)
            )

            helperMessage?.let { (message, color) ->
                Text(
                    text = message,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 22.dp, end = 22.dp, bottom = helperMessageBottomPadding),
                    color = color,
                    fontSize = 14.sp
                )
            }

            ColorSelectionSheet(
                selectedPaletteKey = selectedPaletteKey,
                onSelect = viewModel::updateHoldColor,
                actionText = if (isEmbedded) {
                    "촬영 준비 완료"
                } else {
                    "홀드 찾기"
                },
                actionEnabled = actionEnabled,
                onAction = {
                    when {
                        isEmbedded -> viewModel.completeRealtimeChallengeSetup()
                        else -> {
                            viewModel.finalizeHoldDetectionColorSelection()
                            when {
                                canBypassChallengeCreationForDev -> onNext()
                                else -> viewModel.createChallengeFromSelection()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetHeight)
            )

            /*
            when {
                selectedLevelSortOrder == null && !canBypassChallengeCreationForDev -> {
                    Text(
                        text = if (isEmbedded) {
                            "먼저 난이도를 선택해 주세요."
                        } else {
                            "먼저 레벨을 선택해주세요."
                        },
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }

                false -> {
                    Text(
                        text = "선택한 레벨에 연결된 홀드 컬러가 없습니다.",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                        color = Color(0xFFFF8A8A),
                        fontSize = 14.sp
                    )
                }

                challengeCreationUiState is ChallengeCreationUiState.Loading -> {
                    Text(
                        text = if (isEmbedded) {
                            "촬영 준비를 마무리하고 있습니다..."
                        } else {
                            "챌린지를 생성하고 있습니다..."
                        },
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

            if (false) GradientActionButton(
                text = if (isEmbedded) {
                    "촬영 준비 완료"
                } else {
                    "홀드 찾기"
                },
                enabled = when {
                    isEmbedded -> {
                        selectedLevelSortOrder != null &&
                            selectedPaletteKey != null &&
                            challengeCreationUiState !is ChallengeCreationUiState.Loading
                    }

                    else -> {
                        (canBypassChallengeCreationForDev || selectedLevelSortOrder != null) &&
                            selectedPaletteKey != null &&
                            challengeCreationUiState !is ChallengeCreationUiState.Loading
                    }
                },
                onClick = {
                    when {
                        isEmbedded -> viewModel.completeRealtimeChallengeSetup()
                        else -> {
                            viewModel.finalizeHoldDetectionColorSelection()
                            when {
                                canBypassChallengeCreationForDev -> onNext()
                                else -> viewModel.createChallengeFromSelection()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .then(
                        if (isEmbedded) {
                            Modifier
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .padding(start = 22.dp, end = 22.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .height(58.dp)
            )
            */
        }
    }
}
}

@Composable
private fun SelectionStepHeader(
    progressFraction: Float,
    onBack: () -> Unit,
    presentationMode: ChallengeCreatePresentationMode = ChallengeCreatePresentationMode.UploadFullScreen
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
    grades: List<GymGrade>,
    selectedPaletteKey: String? = null,
    modifier: Modifier = Modifier
) {
    // API에서 받은 grade를 sortOrder 오름차순 → UI에서 위(어려움)→아래(쉬움)로 뒤집어 표시
    val slots = remember(grades) {
        if (grades.isNotEmpty()) {
            grades
                .sortedBy { it.sortOrder }
                .reversed() // 낮은 sortOrder = 쉬움 → 화면 아래에 위치
                .map { grade ->
                    val key = resolveHoldColorKey(grade.colorName, grade.colorHex)
                    val color = resolveGymGradeAccentColor(grade)
                    HoldPaletteSlot(key = key ?: "unknown", color = color)
                }
        } else {
            difficultyReferenceSlots
        }
    }

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
            slots.forEach { slot ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 1.dp)
                ) {
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
private fun DifficultyReferenceBar(
    grades: List<GymGrade>,
    selectedGymGradeId: Long?,
    modifier: Modifier = Modifier
) {
    val orderedGrades = remember(grades) {
        grades.sortedByDescending { it.sortOrder }
    }

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
                text = "?대젮?",
                color = Color(0xFF999999),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "?ъ? ",
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
            if (orderedGrades.isNotEmpty()) {
                orderedGrades.forEach { grade ->
                    DifficultyReferenceSegment(
                        color = resolveGymGradeAccentColor(grade),
                        selected = selectedGymGradeId == grade.gymGradeId.toLong(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 1.dp)
                    )
                }
            } else {
                difficultyReferenceSlots.forEach { slot ->
                    DifficultyReferenceSegment(
                        color = slot.color,
                        selected = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyReferenceSegment(
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, Color.White)
                    } else {
                        Modifier
                    }
                )
        )
    }
}

@Composable
private fun GymGradeDifficultyReferenceBar(
    grades: List<GymGrade>,
    selectedGymGradeId: Long?,
    modifier: Modifier = Modifier
) {
    val orderedGrades = remember(grades) {
        grades.sortedByDescending { it.sortOrder }
    }

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
            if (orderedGrades.isNotEmpty()) {
                orderedGrades.forEach { grade ->
                    DifficultyReferenceSegment(
                        color = resolveGymGradeAccentColor(grade),
                        selected = selectedGymGradeId == grade.gymGradeId.toLong(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 1.dp)
                    )
                }
            } else {
                difficultyReferenceSlots.forEach { slot ->
                    DifficultyReferenceSegment(
                        color = slot.color,
                        selected = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 1.dp)
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
    previewSlot: HoldPaletteSlot?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        HoldAssetGraphic(
            slot = previewSlot ?: defaultHoldPreviewSlot,
            tintColor = if (previewSlot == null) DdgoColorTokens.DisabledFill else null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .aspectRatio(HOLD_ASSET_ASPECT_RATIO)
        )
    }
}

@Composable
private fun ColorSelectionSheet(
    selectedPaletteKey: String?,
    onSelect: (String) -> Unit,
    actionText: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(COLOR_STEP_SHEET_SHAPE)
            .background(Color.White)
    ) {
        val paletteHorizontalPadding = maxWidth * 0.06f
        val paletteTopPadding = maxHeight * 0.07f
        val paletteBottomPadding = maxHeight * 0.07f
        val rowSpacing = maxHeight * 0.05f
        val chipSize = maxWidth * 0.16f
        val buttonWidth = maxWidth * COLOR_STEP_CTA_WIDTH_FRACTION

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = paletteHorizontalPadding,
                    end = paletteHorizontalPadding,
                    top = paletteTopPadding,
                    bottom = paletteBottomPadding
                )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                holdPickerRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    row.forEach { slot ->
                        ColorCircleButton(
                            slot = slot,
                            selected = slot.key == normalizeProblemHoldPaletteKey(selectedPaletteKey),
                            enabled = true,
                            size = chipSize,
                            onClick = { onSelect(slot.key) }
                        )
                    }
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.size(chipSize))
                    }
                }
            }
        }

            Spacer(modifier = Modifier.weight(1f))

            DdgoPrimaryButton(
                text = actionText,
                enabled = actionEnabled,
                onClick = onAction,
                variant = DdgoPrimaryButtonVariant.EmphasisGradient,
                enabledBackgroundBrush = Brush.horizontalGradient(
                    colors = listOf(
                        DdgoColorTokens.BrandGradientEnd,
                        DdgoColorTokens.BrandGradientStart
                    )
                ),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(buttonWidth)
            )
        }
    }
}

@Composable
private fun ColorCircleButton(
    slot: HoldPaletteSlot,
    selected: Boolean,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit
) {
    val selectionStrokeOuter = (size * 0.05f).coerceAtLeast(3.dp)
    val selectionStrokeInner = (size * 0.033f).coerceAtLeast(2.dp)
    val selectionPadding = (size * 0.066f).coerceAtLeast(4.dp)

    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.28f)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(selectionStrokeOuter, Color(0xFF66B6FF), CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(if (selected) selectionPadding else 0.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.border(selectionStrokeInner, Color.White, CircleShape)
                } else {
                    Modifier
                }
            )
            .padding(if (selected) selectionPadding else 0.dp)
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
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun GymGradeColorSwatchPanel(
    grades: List<GymGrade>,
    selectedGymGradeId: Long?,
    onSelect: (GymGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    val gradeRows = remember(grades) {
        grades
            .sortedByDescending { it.sortOrder }
            .chunked(2)
    }

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
        if (gradeRows.isEmpty()) {
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(
                    items = gradeRows,
                    key = { row -> row.firstOrNull()?.gymGradeId ?: -1 }
                ) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                        row.forEach { grade ->
                            GymGradeColorSwatch(
                                grade = grade,
                                selected = selectedGymGradeId == grade.gymGradeId.toLong(),
                                onClick = { onSelect(grade) }
                            )
                        }

                        if (row.size == 1) {
                            Spacer(modifier = Modifier.size(50.dp))
                        }
                    }
                }
            }
        }
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
                        val grade = availableGradeForProblemHoldSlot(
                            availableGradesByKey = availableGradesByKey,
                            slotKey = slot.key
                        )
                        HoldColorTile(
                            slot = slot,
                            enabled = grade != null,
                            selected = slot.key == normalizeProblemHoldPaletteKey(selectedPaletteKey),
                            onClick = { grade?.let(onSelect) }
                        )
                    }
                    repeat(2 - row.size) {
                        Spacer(modifier = Modifier.size(50.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldColorSelectionPanel(
    grades: List<GymGrade>,
    selectedGymGradeId: Long?,
    onSelect: (GymGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    val gradeRows = remember(grades) {
        grades
            .sortedBy { it.sortOrder }
            .chunked(2)
    }

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
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {
        if (gradeRows.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?쒖씠???뺣낫媛 ?놁뒿?덈떎.",
                    color = Color(0xFF767676),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = gradeRows,
                    key = { row -> row.firstOrNull()?.gymGradeId ?: -1 }
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { grade ->
                            GymGradeColorTile(
                                grade = grade,
                                selected = selectedGymGradeId == grade.gymGradeId.toLong(),
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(grade) }
                            )
                        }

                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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

@Composable
private fun GymGradeColorSwatch(
    grade: GymGrade,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = resolveGymGradeAccentColor(grade)
    val shape = RoundedCornerShape(5.dp)
    val needsOutline = accentColor.red + accentColor.green + accentColor.blue > 2.6f
    val borderWidth = if (selected) 2.dp else if (needsOutline) 1.dp else 0.dp
    val borderColor = when {
        selected -> Color(0xFF0B0B0E)
        needsOutline -> Color(0xFF767676)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(shape)
            .background(accentColor)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun GymGradeColorTile(
    grade: GymGrade,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val accentColor = resolveGymGradeAccentColor(grade)
    val secondaryLabel = grade.colorName
        .takeIf { it.isNotBlank() && !it.equals(grade.gradeLabel, ignoreCase = true) }
        ?: formatGymGradeLevelText(grade)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Color(0xFFF3F7FF) else Color(0xFFF7F7F8))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF4396FB) else Color(0xFFE4E8EF),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = formatGymGradeLevelText(grade),
                color = Color(0xFF16181D),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = secondaryLabel,
                color = Color(0xFF767676),
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

private data class HoldPaletteSlot(
    val key: String,
    val color: Color,
    val borderColor: Color? = null
)

private val difficultyReferenceSlots = listOf(
    HoldPaletteSlot(key = "black", color = DdgoHoldColorPalette.Black.color),
    HoldPaletteSlot(key = "gray", color = DdgoHoldColorPalette.Gray.color),
    HoldPaletteSlot(key = "white", color = DdgoHoldColorPalette.Ivory.color, borderColor = DdgoColorTokens.NeutralLightGray),
    HoldPaletteSlot(key = "brown", color = DdgoHoldColorPalette.Brown.color),
    HoldPaletteSlot(key = "purple", color = DdgoHoldColorPalette.Purple.color),
    HoldPaletteSlot(key = "navy", color = DdgoHoldColorPalette.Navy.color),
    HoldPaletteSlot(key = "blue", color = DdgoHoldColorPalette.Blue.color),
    HoldPaletteSlot(key = "skyblue", color = DdgoHoldColorPalette.Sky.color),
    HoldPaletteSlot(key = "green", color = DdgoHoldColorPalette.Green.color),
    HoldPaletteSlot(key = "orange", color = DdgoHoldColorPalette.Orange.color),
    HoldPaletteSlot(key = "red", color = DdgoHoldColorPalette.Red.color),
    HoldPaletteSlot(key = "yellow", color = DdgoHoldColorPalette.Yellow.color),
    HoldPaletteSlot(key = "pink", color = DdgoHoldColorPalette.Pink.color)
)

private val holdPaletteRows = listOf(
    listOf(
        HoldPaletteSlot(key = "black", color = DdgoHoldColorPalette.Black.color, borderColor = DdgoColorTokens.BrandGray),
        HoldPaletteSlot(key = "gray", color = DdgoHoldColorPalette.Gray.color)
    ),
    listOf(
        HoldPaletteSlot(key = "white", color = DdgoHoldColorPalette.Ivory.color, borderColor = DdgoColorTokens.NeutralLightGray),
        HoldPaletteSlot(key = "brown", color = DdgoHoldColorPalette.Brown.color)
    ),
    listOf(
        HoldPaletteSlot(key = "purple", color = DdgoHoldColorPalette.Purple.color),
        HoldPaletteSlot(key = "navy", color = DdgoHoldColorPalette.Navy.color)
    ),
    listOf(
        HoldPaletteSlot(key = "skyblue", color = DdgoHoldColorPalette.Sky.color),
        HoldPaletteSlot(key = "green", color = DdgoHoldColorPalette.Green.color)
    ),
    listOf(
        HoldPaletteSlot(key = "orange", color = DdgoHoldColorPalette.Orange.color),
        HoldPaletteSlot(key = "red", color = DdgoHoldColorPalette.Red.color)
    ),
    listOf(
        HoldPaletteSlot(key = "yellow", color = DdgoHoldColorPalette.Yellow.color),
        HoldPaletteSlot(key = "pink", color = DdgoHoldColorPalette.Pink.color)
    )
)

private val holdPickerRows = listOf(
    listOf(
        HoldPaletteSlot(key = "red", color = DdgoHoldColorPalette.Red.color),
        HoldPaletteSlot(key = "orange", color = DdgoHoldColorPalette.Orange.color),
        HoldPaletteSlot(key = "yellow", color = DdgoHoldColorPalette.Yellow.color),
        HoldPaletteSlot(key = "green", color = DdgoHoldColorPalette.Green.color)
    ),
    listOf(
        HoldPaletteSlot(key = "skyblue", color = DdgoHoldColorPalette.Sky.color),
        HoldPaletteSlot(key = "navy", color = DdgoHoldColorPalette.Navy.color),
        HoldPaletteSlot(key = "purple", color = DdgoHoldColorPalette.Purple.color),
        HoldPaletteSlot(key = "brown", color = DdgoHoldColorPalette.Brown.color)
    ),
    listOf(
        HoldPaletteSlot(key = "pink", color = DdgoHoldColorPalette.Pink.color),
        HoldPaletteSlot(key = "white", color = DdgoHoldColorPalette.Ivory.color, borderColor = DdgoColorTokens.NeutralLightGray),
        HoldPaletteSlot(key = "gray", color = DdgoHoldColorPalette.Gray.color),
        HoldPaletteSlot(key = "black", color = DdgoHoldColorPalette.Black.color)
    )
)

private const val DEFAULT_HOLD_COLOR_KEY = "pink"
private const val HOLD_ASSET_ASPECT_RATIO = 247f / 256f
private const val COLOR_STEP_SHEET_TOP_FRACTION = 0.53f
private const val COLOR_STEP_TITLE_TOP_FRACTION = 0.04f
private const val COLOR_STEP_HERO_TOP_FRACTION = 0.15f
private const val COLOR_STEP_HERO_WIDTH_FRACTION = 0.63f
private const val COLOR_STEP_HERO_AREA_HEIGHT_FRACTION = 0.34f
private const val COLOR_STEP_CTA_WIDTH_FRACTION = 0.85f
private val COLOR_STEP_SHEET_SHAPE = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val defaultHoldPreviewSlot = holdPickerRows.flatten().first { it.key == DEFAULT_HOLD_COLOR_KEY }

private fun normalizeProblemHoldPaletteKey(key: String?): String? {
    return when (key) {
        "blue" -> "navy"
        else -> key
    }
}

private fun availableGradeForProblemHoldSlot(
    availableGradesByKey: Map<String, GymGrade>,
    slotKey: String
): GymGrade? {
    return when (slotKey) {
        "navy" -> availableGradesByKey["navy"] ?: availableGradesByKey["blue"]
        else -> availableGradesByKey[slotKey]
    }
}

private fun findHoldPaletteSlot(key: String?): HoldPaletteSlot? {
    val normalizedKey = normalizeProblemHoldPaletteKey(key)
    if (normalizedKey == null) {
        return null
    }

    return sequenceOf(
        holdPickerRows.flatten(),
        holdPaletteRows.flatten(),
        difficultyReferenceSlots
    ).flatMap { it.asSequence() }
        .firstOrNull { it.key == normalizedKey }
}

private fun holdAssetPathForKey(key: String): String? {
    return when (key) {
        "black" -> "holds/hold_black.png"
        "brown" -> "holds/hold_brown.png"
        "blue" -> "holds/hold_blue.png"
        "gray" -> "holds/hold_gray.png"
        "green" -> "holds/hold_green.png"
        "white" -> "holds/hold_white.png"
        "navy" -> "holds/hold_blue.png"
        "orange" -> "holds/hold_orange.png"
        "pink" -> "holds/hold_pink.png"
        "purple" -> "holds/hold_purple.png"
        "red" -> "holds/hold_red.png"
        "skyblue" -> "holds/hold_sky.png"
        "yellow" -> "holds/hold_yellow.png"
        else -> null
    }
}

@Composable
private fun HoldAssetGraphic(
    slot: HoldPaletteSlot,
    modifier: Modifier = Modifier,
    tintColor: Color? = null
) {
    val context = LocalContext.current
    val assetPath = holdAssetPathForKey(slot.key)
    val fillColor = tintColor ?: slot.color

    if (assetPath == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(fillColor)
                .then(
                    if (slot.borderColor != null && tintColor == null) {
                        Modifier.border(1.dp, slot.borderColor, RoundedCornerShape(18.dp))
                    } else {
                        Modifier
                    }
                )
        )
        return
    }

    val holdBitmap = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }

    if (holdBitmap == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(fillColor)
        )
        return
    }

    Image(
        bitmap = holdBitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = tintColor?.let { ColorFilter.tint(it, BlendMode.Modulate) },
        modifier = modifier
    )
}

private fun buildAvailableGymGradeMap(grades: List<GymGrade>): Map<String, GymGrade> {
    return buildMap {
        grades.forEach { grade ->
            resolveHoldColorKey(
                colorName = grade.colorName,
                colorHex = grade.colorHex
            )?.let { put(it, grade) }
        }
    }
}

private fun buildAvailableLevelGradeMap(grades: List<GymGrade>): Map<String, GymGrade> {
    return grades
        .sortedBy { it.sortOrder }
        .distinctBy { it.sortOrder }
        .fold(linkedMapOf()) { acc, grade ->
            resolveHoldColorKey(
                colorName = grade.colorName,
                colorHex = grade.colorHex
            )?.let { acc[it] = grade }
            acc
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
                text = grade.colorName.ifBlank { grade.gradeLabel ?: "난이도" },
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
                text = "선택됨",
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
    val title = grade.colorName.ifBlank { grade.gradeLabel ?: "문제 색상" }

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
                text = "선택한 홀드 색상",
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
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF151517), Color(0xFF101114))
                )
            )
            .border(1.dp, Color(0xFF2A3C56), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "선택한 암장",
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
                label = "난이도 수",
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
            .getOrElse {
                fallbackColorByName(grade.colorName)
            }
    }

    return fallbackColorByName(grade.colorName)
}

private fun holdAssetColorOverride(colorName: String): Color? {
    return DdgoHoldColorPalette.colorForKey(
        resolveHoldColorKey(colorName = colorName, colorHex = null)
    )
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
    return resolveHoldColorDisplayName(colorName = colorName, colorHex = null)
}

private fun fallbackColorByName(colorName: String): Color {
    return holdAssetColorOverride(colorName) ?: DdgoColorTokens.BrandBlue
}

private fun holdPaletteKeyForColor(color: Color): String? {
    val colorInt = color.toArgb()
    return DdgoHoldColorPalette.all
        .firstOrNull { it.color.toArgb() == colorInt }
        ?.key
}
@Composable
private fun HoldAssetThumbnail(
    color: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val slot = remember(color) {
            holdPaletteRows.flatten().firstOrNull { it.color == color }
            ?: holdPickerRows.flatten().firstOrNull { it.color == color }
            ?: findHoldPaletteSlot(holdPaletteKeyForColor(color))
    }

    if (slot != null) {
        Box(
            modifier = modifier
                .clip(shape),
            contentAlignment = Alignment.Center
        ) {
            HoldAssetGraphic(
                slot = slot,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(HOLD_ASSET_ASPECT_RATIO)
            )
        }
        return
    }

    Box(
        modifier = modifier
            .rotate(90f)
            .clip(shape)
            .background(color)
            .then(
                if (color == DdgoHoldColorPalette.Ivory.color) {
                    Modifier.border(1.dp, DdgoColorTokens.NeutralLightGray, shape)
                } else if (color == DdgoHoldColorPalette.Black.color) {
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

@SuppressLint("MissingPermission")
private fun loadCurrentLocation(
    context: Context,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onError("위치 권한이 필요합니다.")
        return
    }

    val locationManager = context.getSystemService(LocationManager::class.java)
        ?: run {
            onError("위치 서비스를 사용할 수 없습니다.")
            return
        }

    val availableProviders = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter(locationManager::isProviderEnabled)

    if (availableProviders.isEmpty()) {
        onError("위치 서비스를 켜주세요.")
        return
    }

    try {
        // 실기기에서는 GPS 첫 고정이 느릴 수 있으므로, 최근 위치가 있으면 즉시 사용합니다.
        findBestLastKnownLocation(locationManager, availableProviders)?.let { cachedLocation ->
            onSuccess(cachedLocation.latitude, cachedLocation.longitude)
            return
        }

        requestCurrentLocation(
            context = context,
            locationManager = locationManager,
            providers = availableProviders,
            providerIndex = 0,
            onSuccess = onSuccess,
            onError = onError
        )
    } catch (_: SecurityException) {
        onError("위치 권한이 필요합니다.")
    }
}

@Composable
private fun GymSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCurrentLocationSearch: () -> Unit,
    isBusy: Boolean
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        singleLine = true,
        placeholder = {
            Text(
                text = "암장명을 입력해보세요",
                color = Color(0xFF8A94A3),
                fontSize = 15.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색",
                tint = Color(0xFF7F8EA3)
            )
        },
        trailingIcon = {
            SearchBarTrailingActions(
                onSearch = onSearch,
                onCurrentLocationSearch = onCurrentLocationSearch,
                isBusy = isBusy
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF22262D),
            unfocusedContainerColor = Color(0xFF22262D),
            disabledContainerColor = Color(0xFF22262D),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF1D9BF0),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = Color(0xFF7F8EA3),
            unfocusedLeadingIconColor = Color(0xFF7F8EA3),
            focusedTrailingIconColor = Color.White,
            unfocusedTrailingIconColor = Color.White
        )
    )
}

@Composable
private fun SearchBarTrailingActions(
    onSearch: () -> Unit,
    onCurrentLocationSearch: () -> Unit,
    isBusy: Boolean
) {
    Row(
        modifier = Modifier.padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchActionChip(
            icon = Icons.Default.MyLocation,
            contentDescription = "현재 위치 검색",
            tint = Color(0xFF1D9BF0),
            onClick = onCurrentLocationSearch,
            enabled = !isBusy,
            isBusy = isBusy
        )

        SearchActionChip(
            icon = Icons.Default.NearMe,
            contentDescription = "키워드 검색",
            tint = Color(0xFF1D9BF0),
            onClick = onSearch,
            enabled = !isBusy,
            isBusy = false
        )
    }
}

@SuppressLint("MissingPermission")
private fun loadCurrentLocationIncrementally(
    context: Context,
    onCachedLocation: (Double, Double) -> Unit,
    onFreshLocation: (Double, Double, Boolean) -> Unit,
    onError: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onError("위치 권한이 필요합니다.")
        return
    }

    val locationManager = context.getSystemService(LocationManager::class.java)
        ?: run {
            onError("위치 서비스를 사용할 수 없습니다.")
            return
        }

    val availableProviders = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter(locationManager::isProviderEnabled)

    if (availableProviders.isEmpty()) {
        onError("위치 서비스를 켜주세요.")
        return
    }

    try {
        val cachedLocation = findBestLastKnownLocation(locationManager, availableProviders)
        cachedLocation?.let { location ->
            onCachedLocation(location.latitude, location.longitude)
        }

        requestCurrentLocation(
            context = context,
            locationManager = locationManager,
            providers = availableProviders,
            providerIndex = 0,
            onSuccess = { latitude, longitude ->
                val isSameAsCached = cachedLocation?.let {
                    areLocationsEffectivelySame(
                        cachedLatitude = it.latitude,
                        cachedLongitude = it.longitude,
                        freshLatitude = latitude,
                        freshLongitude = longitude
                    )
                } ?: false

                onFreshLocation(latitude, longitude, isSameAsCached)
            },
            onError = onError
        )
    } catch (_: SecurityException) {
        onError("위치 권한이 필요합니다.")
    }
}

@Composable
private fun SearchActionChip(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    isBusy: Boolean
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = Color(0xFF1D9BF0)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun SearchHeroSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onCurrentLocationSearch: () -> Unit,
    isBusy: Boolean,
    locationMessage: String?,
    isResolvingLocation: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF161A20), Color(0xFF0E1116))
                )
            )
            .border(1.dp, Color(0xFF252D38), RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "암장 탐색",
            color = Color(0xFF82B1FF),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "지금 가고 싶은 클라이밍장을\n빠르게 찾아보세요",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        GymSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            onCurrentLocationSearch = onCurrentLocationSearch,
            isBusy = isBusy
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "암장명으로 찾거나, 현재 위치 버튼으로 주변 암장만 바로 확인할 수 있어요.",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        if (isResolvingLocation) {
            Spacer(modifier = Modifier.height(12.dp))
            SearchStatusChip(
                text = "현재 위치를 확인하는 중입니다",
                background = Color(0xFF173657),
                content = Color(0xFF9CCCFF)
            )
        }

        if (locationMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            SearchStatusChip(
                text = locationMessage,
                background = Color(0xFF351A20),
                content = Color(0xFFFFA4AF)
            )
        }
    }
}

@Composable
private fun SearchStatusChip(
    text: String,
    background: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SearchEmptyGuide() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF16181D))
            .border(1.dp, Color(0xFF252A33), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Text(
            text = "아직 검색한 암장이 없어요",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "암장명을 입력해 찾거나, 현재 위치 버튼으로 가까운 클라이밍장을 불러오세요.",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun SearchResultLeadingBadge(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color(0xFF2979FF) else Color(0xFF2A2F38)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.NearMe,
            contentDescription = null,
            tint = if (selected) Color.White else Color(0xFF82B1FF)
        )
    }
}

@Composable
private fun SelectionPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF204B72))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = "선택됨",
            color = Color(0xFFB7DCFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDistanceLabel(distanceMeters: Int): String {
    return if (distanceMeters >= 1000) {
        String.format("%.1fkm", distanceMeters / 1000f)
    } else {
        "${distanceMeters}m"
    }
}

@SuppressLint("MissingPermission")
private fun requestCurrentLocation(
    context: Context,
    locationManager: LocationManager,
    providers: List<String>,
    providerIndex: Int,
    onSuccess: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    if (!hasLocationPermission(context)) {
        onError("위치 권한이 필요합니다.")
        return
    }

    if (providerIndex >= providers.size) {
        onError("현재 위치를 가져오지 못했습니다.")
        return
    }

    val provider = providers[providerIndex]
    val cancellationSignal = CancellationSignal()
    val timeoutHandler = Handler(Looper.getMainLooper())
    var completed = false

    fun completeWithFallback() {
        if (completed) {
            return
        }
        completed = true
        cancellationSignal.cancel()
        timeoutHandler.removeCallbacksAndMessages(null)
        requestCurrentLocation(
            context = context,
            locationManager = locationManager,
            providers = providers,
            providerIndex = providerIndex + 1,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    timeoutHandler.postDelayed(
        { completeWithFallback() },
        6_000L
    )

    val locationConsumer = Consumer<Location> { location ->
        if (completed) {
            return@Consumer
        }

        completed = true
        timeoutHandler.removeCallbacksAndMessages(null)

        if (location != null) {
            onSuccess(location.latitude, location.longitude)
        } else {
            requestCurrentLocation(
                context = context,
                locationManager = locationManager,
                providers = providers,
                providerIndex = providerIndex + 1,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    LocationManagerCompat.getCurrentLocation(
        locationManager,
        provider,
        cancellationSignal,
        ContextCompat.getMainExecutor(context),
        locationConsumer
    )
}

private fun findBestLastKnownLocation(
    locationManager: LocationManager,
    providers: List<String>
): Location? {
    return providers
        .mapNotNull(locationManager::getLastKnownLocation)
        .maxByOrNull { it.time }
}

private fun areLocationsEffectivelySame(
    cachedLatitude: Double,
    cachedLongitude: Double,
    freshLatitude: Double,
    freshLongitude: Double
): Boolean {
    return kotlin.math.abs(cachedLatitude - freshLatitude) < 0.0001 &&
        kotlin.math.abs(cachedLongitude - freshLongitude) < 0.0001
}
