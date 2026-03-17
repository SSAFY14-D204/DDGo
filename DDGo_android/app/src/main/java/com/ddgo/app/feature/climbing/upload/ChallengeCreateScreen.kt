package com.ddgo.app.feature.climbing.upload

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    GRADE
}

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
            CreateStep.GRADE -> step = CreateStep.GYM_NAME
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
                    text = "\uD574\uB2F9 \uC554\uC7A5\uC758 \uB09C\uC774\uB3C4\uB97C \uC120\uD0DD\uD574\uC8FC\uC138\uC694",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 32.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = formatGymDisplayName(viewModel.gymName),
                            color = Color.White,
                            fontSize = 13.sp
                        )
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

                selectedGrade?.let { grade ->
                    SelectedHoldPreviewCard(grade = grade)
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (grades.isEmpty()) {
                    Text(
                        text = "\uC120\uD0DD \uAC00\uB2A5\uD55C \uB09C\uC774\uB3C4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.",
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
                                text = "\uCC4C\uB9B0\uC9C0\uB97C \uC0DD\uC131\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4...",
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
                onClick = { viewModel.createChallengeFromSelection() },
                enabled = selectedGrade != null &&
                    challengeCreationUiState !is ChallengeCreationUiState.Loading,
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
                    text = "\uCC4C\uB9B0\uC9C0 \uC0DD\uC131 \uD6C4 \uB2E4\uC74C",
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
private fun SelectedHoldPreviewCard(grade: GymGrade) {
    val accentColor = resolveGymGradeAccentColor(grade)
    val title = grade.colorName.ifBlank { grade.gradeLabel ?: "\uBB38\uC81C \uC0C9\uC0C1" }

    Row(
        modifier = Modifier
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
        ?: "V-${grade.sortOrder}"
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
