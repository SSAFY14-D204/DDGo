package com.ddgo.app.feature.climbing.record.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.NeutralBackground
import com.ddgo.app.core.ui.theme.NeutralSurface
import com.ddgo.app.core.ui.theme.Primary80
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.feature.climbing.record.presentation.RecordUiState
import com.ddgo.app.feature.climbing.upload.ChallengeCreationUiState
import com.ddgo.app.feature.climbing.upload.GymResolveUiState
import com.ddgo.app.feature.climbing.upload.GymSearchUiState
import com.ddgo.app.feature.climbing.upload.RealtimeSetupStep
import com.ddgo.app.feature.climbing.upload.UploadRealtimeOverlayUiState
import kotlin.math.roundToInt

@Composable
fun RealtimeRecordPage(
    uiState: RecordUiState,
    realtimeOverlayUiState: UploadRealtimeOverlayUiState,
    isTorchEnabled: Boolean,
    isTorchAvailable: Boolean,
    locationMessage: String?,
    isResolvingLocation: Boolean,
    previewContent: @Composable BoxScope.() -> Unit,
    overlayContent: @Composable BoxScope.() -> Unit,
    onNavigateBack: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onOpenGymSelector: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSelectGym: (NearbyPlace) -> Unit,
    onSelectDifficulty: (GymGrade) -> Unit,
    onTapShutter: () -> Unit,
    onLongPressShutter: () -> Unit,
    onTapFlash: () -> Unit,
    onSelectHoldColor: (String) -> Unit,
    onDismissHoldColorSheet: () -> Unit
) {
    val shouldShowSetupOverlay =
        realtimeOverlayUiState.setupStep != RealtimeSetupStep.Ready ||
            realtimeOverlayUiState.isSetupVisible

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        previewContent()
        overlayContent()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.76f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                TopChrome(onNavigateBack = onNavigateBack)

                if (!shouldShowSetupOverlay) {
                    Spacer(modifier = Modifier.height(14.dp))
                    ReadySummaryCard(
                        gymName = realtimeOverlayUiState.gymName,
                        grade = realtimeOverlayUiState.selectedGymGrade,
                        holdColor = realtimeOverlayUiState.holdColor
                    )
                }
            }

            BottomControlRow(
                uiState = uiState,
                overlayUiState = realtimeOverlayUiState,
                isTorchEnabled = isTorchEnabled,
                isTorchAvailable = isTorchAvailable,
                onTapFlash = onTapFlash,
                onTapShutter = onTapShutter,
                onLongPressShutter = onLongPressShutter
            )
        }

        SetupAndSheetOverlays(
            uiState = uiState,
            realtimeOverlayUiState = realtimeOverlayUiState,
            locationMessage = locationMessage,
            isResolvingLocation = isResolvingLocation,
            onRequestCameraPermission = onRequestCameraPermission,
            onOpenGymSelector = onOpenGymSelector,
            onSearchQueryChange = onSearchQueryChange,
            onSearchSubmit = onSearchSubmit,
            onSelectGym = onSelectGym,
            onSelectDifficulty = onSelectDifficulty,
            onSelectHoldColor = onSelectHoldColor,
            onDismissHoldColorSheet = onDismissHoldColorSheet
        )
    }
}

@Composable
private fun TopChrome(onNavigateBack: () -> Unit) {
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f)
            ) {
                Box(
                    modifier = Modifier
                        .clickable(onClick = onNavigateBack)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "닫기",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeaderChip(text = "9:16")
                HeaderChip(text = "720p")
            }
        }
    }
}

@Composable
private fun ReadySummaryCard(
    gymName: String,
    grade: GymGrade?,
    holdColor: String
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = gymName.ifBlank { "암장 선택 중" },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillTag(text = grade?.gradeLabel ?: "난이도 선택")
                PillTag(text = holdColor.ifBlank { "홀드 색 선택" })
            }
        }
    }
}

@Composable
private fun BottomControlRow(
    uiState: RecordUiState,
    overlayUiState: UploadRealtimeOverlayUiState,
    isTorchEnabled: Boolean,
    isTorchAvailable: Boolean,
    onTapFlash: () -> Unit,
    onTapShutter: () -> Unit,
    onLongPressShutter: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val canCaptureAttempt =
            overlayUiState.setupStep == RealtimeSetupStep.Ready &&
                !overlayUiState.isSetupVisible

        Spacer(modifier = Modifier.width(96.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShutterButton(
                isRecording = uiState.isRecording,
                enabled = (uiState.canStartRecording || uiState.isRecording) &&
                    canCaptureAttempt,
                onClick = onTapShutter,
                onLongClick = onLongPressShutter
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (uiState.isRecording) "탭해서 종료" else "길게 눌러 녹화 시작",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )
        }

        CircleActionButton(
            label = if (isTorchEnabled) "켜짐" else "꺼짐",
            subLabel = "플래시",
            icon = if (isTorchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
            containerColor = Color.Black.copy(alpha = 0.56f),
            contentColor = Color.White,
            enabled = isTorchAvailable && !uiState.isRecording,
            onClick = onTapFlash
        )
    }
}

@Composable
private fun SetupAndSheetOverlays(
    uiState: RecordUiState,
    realtimeOverlayUiState: UploadRealtimeOverlayUiState,
    locationMessage: String?,
    isResolvingLocation: Boolean,
    onRequestCameraPermission: () -> Unit,
    onOpenGymSelector: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSelectGym: (NearbyPlace) -> Unit,
    onSelectDifficulty: (GymGrade) -> Unit,
    onSelectHoldColor: (String) -> Unit,
    onDismissHoldColorSheet: () -> Unit
) {
    val shouldShowSetupOverlay =
        realtimeOverlayUiState.setupStep != RealtimeSetupStep.Ready ||
            realtimeOverlayUiState.isSetupVisible

    when {
        !uiState.hasCameraPermission -> {
            Scrim()
            CenterCard(
                title = "카메라 권한이 필요해요",
                body = "실시간 분석을 시작하려면 카메라 접근을 허용해주세요.",
                actionLabel = "권한 허용",
                onAction = onRequestCameraPermission
            )
        }

        uiState.cameraErrorMessage != null -> {
            Scrim()
            CenterCard(
                title = "카메라를 열 수 없어요",
                body = uiState.cameraErrorMessage.orEmpty(),
                actionLabel = "다시 시도",
                onAction = onRequestCameraPermission
            )
        }

        shouldShowSetupOverlay -> {
            Scrim()
            when (realtimeOverlayUiState.setupStep) {
                RealtimeSetupStep.GymPrompt -> GymPromptCard(
                    selectedGymName = realtimeOverlayUiState.gymName,
                    locationMessage = locationMessage,
                    onOpenGymSelector = onOpenGymSelector
                )

                RealtimeSetupStep.GymList -> GymListSheet(
                    uiState = realtimeOverlayUiState,
                    isResolvingLocation = isResolvingLocation,
                    locationMessage = locationMessage,
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchSubmit = onSearchSubmit,
                    onSelectGym = onSelectGym
                )

                RealtimeSetupStep.Difficulty -> DifficultySheet(
                    uiState = realtimeOverlayUiState,
                    onSelectDifficulty = onSelectDifficulty
                )

                RealtimeSetupStep.Ready -> Unit
            }
        }
    }

    if (realtimeOverlayUiState.isHoldColorSheetVisible) {
        Scrim(alpha = 0.78f, onClick = onDismissHoldColorSheet)
        HoldColorSheet(
            uiState = realtimeOverlayUiState,
            onSelectHoldColor = onSelectHoldColor
        )
    }
}

@Composable
private fun GymPromptCard(
    selectedGymName: String,
    locationMessage: String?,
    onOpenGymSelector: () -> Unit
) {
    CenterCard(
        title = "오늘은 여기서 클라이밍 고!",
        body = if (selectedGymName.isBlank()) {
            "가까운 암장을 먼저 보여드릴게요."
        } else {
            selectedGymName
        },
        actionLabel = "암장 선택",
        onAction = onOpenGymSelector,
        footer = locationMessage
    )
}

@Composable
private fun GymListSheet(
    uiState: UploadRealtimeOverlayUiState,
    isResolvingLocation: Boolean,
    locationMessage: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSelectGym: (NearbyPlace) -> Unit
) {
    BottomSheet {
        Text(
            text = "가까운 암장을 골라볼까요?",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = "암장 검색") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "근처 재검색",
                    modifier = Modifier.clickable { onSearchSubmit("") }
                )
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onSearchSubmit(uiState.searchQuery.trim()) },
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary80,
                contentColor = NeutralBackground
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "검색", fontWeight = FontWeight.SemiBold)
        }

        if (!locationMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = locationMessage,
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        when {
            isResolvingLocation || uiState.gymSearchUiState is GymSearchUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Primary80)
                }
            }

            uiState.gymSearchUiState is GymSearchUiState.Error -> {
                Text(
                    text = (uiState.gymSearchUiState as GymSearchUiState.Error).message,
                    color = Color(0xFFFFB2B2),
                    fontSize = 14.sp
                )
            }

            uiState.nearbyPlaces.isEmpty() -> {
                Text(
                    text = "표시할 암장이 없어요. 위치를 다시 확인하거나 검색어를 바꿔보세요.",
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.nearbyPlaces, key = { it.externalPlaceId }) { place ->
                        NearbyGymItem(
                            place = place,
                            isSelected = uiState.selectedNearbyPlace?.externalPlaceId == place.externalPlaceId,
                            isResolving = uiState.gymResolveUiState is GymResolveUiState.Loading &&
                                uiState.selectedNearbyPlace?.externalPlaceId == place.externalPlaceId,
                            onClick = { onSelectGym(place) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultySheet(
    uiState: UploadRealtimeOverlayUiState,
    onSelectDifficulty: (GymGrade) -> Unit
) {
    BottomSheet {
        Text(
            text = uiState.gymName.ifBlank { "난이도 선택" },
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "선택한 난이도 색으로 기본 홀드 색도 맞춰드릴게요.",
            color = Color.White.copy(alpha = 0.74f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.challengeCreationUiState is ChallengeCreationUiState.Error) {
            Text(
                text = uiState.challengeCreationUiState.message,
                color = Color(0xFFFFB2B2),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (uiState.challengeCreationUiState is ChallengeCreationUiState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary80)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.resolvedGymGrades, key = { it.gymGradeId }) { grade ->
                    DifficultyItem(
                        grade = grade,
                        selected = uiState.selectedGymGrade?.gymGradeId == grade.gymGradeId,
                        onClick = { onSelectDifficulty(grade) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldColorSheet(
    uiState: UploadRealtimeOverlayUiState,
    onSelectHoldColor: (String) -> Unit
) {
    BottomSheet(
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        Text(
            text = "홀드 색상 선택",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(14.dp))
        uiState.holdColorOptions.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                row.forEach { option ->
                    HoldColorItem(
                        label = option.label,
                        color = Color(option.colorInt),
                        selected = option.key == uiState.selectedHoldColorKey,
                        onClick = { onSelectHoldColor(option.key) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CenterCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    footer: String? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = NeutralSurface.copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = body,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
                if (!footer.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = footer,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = NeutralBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BottomSheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = NeutralSurface.copy(alpha = 0.98f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NearbyGymItem(
    place: NearbyPlace,
    isSelected: Boolean,
    isResolving: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Primary80.copy(alpha = 0.18f) else NeutralBackground.copy(alpha = 0.82f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.72f)
            ) {
                Text(
                    text = place.placeName,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = place.roadAddressName ?: place.addressName.orEmpty(),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 13.sp
                )
            }

            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Primary80,
                    strokeWidth = 2.dp
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = place.distanceMeters.formatDistance(),
                        color = Primary80,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyItem(
    grade: GymGrade,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Primary80.copy(alpha = 0.18f) else NeutralBackground.copy(alpha = 0.82f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = grade.gradeLabel ?: "V${grade.sortOrder}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = grade.colorName.replaceFirstChar { it.uppercase() },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun HoldColorItem(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(color)
                .alpha(if (selected) 1f else 0.9f)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.88f))
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = if (selected) 1f else 0.72f),
            fontSize = 12.sp
        )
    }
}

@Composable
private fun CircleActionButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    subLabel: String? = null,
    icon: ImageVector? = null
) {
    Surface(
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        shape = CircleShape,
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .size(96.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            subLabel?.let {
                Text(
                    text = it,
                    color = contentColor.copy(alpha = 0.74f),
                    fontSize = 11.sp
                )
            }

            Text(
                text = label,
                color = contentColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ShutterButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val scale = animateFloatAsState(
        targetValue = if (isRecording) 0.94f else 1f,
        label = "realtime-shutter"
    )

    Box(
        modifier = Modifier
            .size(118.dp)
            .scale(scale.value)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.16f else 0.08f))
            .alpha(if (enabled) 1f else 0.42f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.White else NeutralBackground),
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE25757))
                )
            } else {
                Text(
                    text = "탭 촬영",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.42f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PillTag(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = NeutralSurface.copy(alpha = 0.92f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun Scrim(
    alpha: Float = 0.66f,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    )
}

private fun Int?.formatDistance(): String {
    val meters = this ?: return "근처"
    if (meters < 1000) {
        return "${meters}m"
    }
    val km = (meters / 1000f * 10f).roundToInt() / 10f
    return "${km}km"
}
