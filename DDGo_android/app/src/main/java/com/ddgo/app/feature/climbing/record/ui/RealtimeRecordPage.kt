package com.ddgo.app.feature.climbing.record.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
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
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.feature.climbing.record.presentation.RecordUiState
import com.ddgo.app.feature.climbing.upload.GymResolveUiState
import com.ddgo.app.feature.climbing.upload.GymSearchUiState
import com.ddgo.app.feature.climbing.upload.RealtimeSetupStep
import com.ddgo.app.feature.climbing.upload.UploadRealtimeOverlayUiState
import kotlin.math.roundToInt

private val RealtimeSheetMaxWidth = 560.dp

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
    onTapShutter: () -> Unit,
    onLongPressShutter: () -> Unit,
    onTapFlash: () -> Unit,
    onSelectHoldColor: (String) -> Unit,
    onDismissHoldColorSheet: () -> Unit,
    challengeCreateCardContent: @Composable () -> Unit
) {
    val shouldShowSetupOverlay =
        realtimeOverlayUiState.setupStep != RealtimeSetupStep.Ready ||
            realtimeOverlayUiState.isSetupVisible

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RecordBackgroundTop,
                        RecordBackgroundBottom,
                        RecordBackgroundTop
                    )
                )
            )
    ) {
        previewContent()
        overlayContent()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            RecordScrim.copy(alpha = 0.46f),
                            Color.Transparent,
                            RecordScrim.copy(alpha = 0.78f)
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
                        difficultyLabel = realtimeOverlayUiState.difficultyLabel,
                        selectedHoldColorKey = realtimeOverlayUiState.selectedHoldColorKey
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
            onSelectHoldColor = onSelectHoldColor,
            onDismissHoldColorSheet = onDismissHoldColorSheet,
            challengeCreateCardContent = challengeCreateCardContent
        )
    }
}

@Composable
private fun TopChrome(onNavigateBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = CircleShape,
            color = RecordBackdrop.copy(alpha = 0.78f)
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onNavigateBack)
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "닫기",
                    tint = RecordTextPrimary
                )
            }
        }

    }
}

@Composable
private fun ReadySummaryCard(
    gymName: String,
    difficultyLabel: String,
    selectedHoldColorKey: String?
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = RecordBackdrop.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = gymName.ifBlank { "?붿옣 ?좏깮 以?" },
                color = RecordTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillTag(text = difficultyLabel.ifBlank { "?쒖씠???좏깮" })
                PillTag(text = resolveHoldColorTagLabel(selectedHoldColorKey))
            }
        }
    }
}

private fun resolveHoldColorTagLabel(selectedHoldColorKey: String?): String {
    if (selectedHoldColorKey.isNullOrBlank()) {
        return "??????좏깮"
    }

    return resolveHoldColorDisplayName(selectedHoldColorKey, null)
        .ifBlank { selectedHoldColorKey }
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
                enabled = (uiState.canStartRecording || uiState.isRecording) && canCaptureAttempt,
                onClick = onTapShutter,
                onLongClick = onLongPressShutter
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (uiState.isRecording) "촬영 종료" else "촬영 시작",
                color = RecordTextSecondary,
                fontSize = 12.sp
            )
        }

        CircleActionButton(
            label = if (isTorchEnabled) "켜짐" else "꺼짐",
            subLabel = "플래시",
            icon = if (isTorchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
            containerColor = if (isTorchEnabled) RecordAccentSoft else RecordBackdrop.copy(alpha = 0.82f),
            contentColor = if (isTorchEnabled) RecordAccent else RecordTextPrimary,
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
    onSelectHoldColor: (String) -> Unit,
    onDismissHoldColorSheet: () -> Unit,
    challengeCreateCardContent: @Composable () -> Unit
) {
    val shouldShowSetupOverlay =
        realtimeOverlayUiState.setupStep != RealtimeSetupStep.Ready ||
            realtimeOverlayUiState.isSetupVisible

    when {
        !uiState.hasCameraPermission -> {
            Scrim()
            CenterCard(
                title = "카메라 권한이 필요해요",
                body = "실시간 촬영을 시작하려면 카메라 접근을 허용해주세요.",
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

                RealtimeSetupStep.ChallengeCreate -> ChallengeCreateSheet(
                    content = challengeCreateCardContent
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
        title = "오늘은 어디서 클라이밍할까요?",
        body = if (selectedGymName.isBlank()) {
            "실시간 기록을 시작하기 전에 먼저 암장을 선택해주세요."
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
            color = RecordTextPrimary,
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
                    contentDescription = null,
                    tint = RecordTextSecondary
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = "근처 암장 다시 검색",
                    tint = RecordAccent,
                    modifier = Modifier.clickable { onSearchSubmit("") }
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RecordAccent,
                unfocusedBorderColor = RecordBorder,
                focusedLabelColor = RecordAccent,
                unfocusedLabelColor = RecordTextSecondary,
                focusedTextColor = RecordTextPrimary,
                unfocusedTextColor = RecordTextPrimary,
                cursorColor = RecordAccent,
                focusedContainerColor = RecordSurfaceMuted,
                unfocusedContainerColor = RecordSurfaceMuted
            )
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onSearchSubmit(uiState.searchQuery.trim()) },
            colors = ButtonDefaults.buttonColors(
                containerColor = RecordAccent,
                contentColor = RecordOnAccent
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "검색", fontWeight = FontWeight.SemiBold)
        }

        if (!locationMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = locationMessage,
                color = RecordTextSecondary,
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
                    CircularProgressIndicator(color = RecordAccent)
                }
            }

            uiState.gymSearchUiState is GymSearchUiState.Error -> {
                Text(
                    text = (uiState.gymSearchUiState as GymSearchUiState.Error).message,
                    color = RecordError,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }

            uiState.nearbyPlaces.isEmpty() -> {
                Text(
                    text = "표시할 암장이 없어요. 위치를 다시 확인하거나 검색어를 바꿔보세요.",
                    color = RecordTextSecondary,
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
private fun ChallengeCreateSheet(
    content: @Composable () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val sheetMaxHeight = maxHeight * 0.86f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = RealtimeSheetMaxWidth)
                    .heightIn(max = sheetMaxHeight)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0B0B0E)
            ) {
                key("embedded-challenge-create") {
                    content()
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
            text = "홀드 색 선택",
            color = RecordTextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "실시간 분석 중에도 셔터를 길게 눌러 홀드 색을 다시 선택할 수 있어요.",
            color = RecordTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        HoldColorPaletteGrid(
            options = uiState.holdColorOptions,
            selectedHoldColorKey = uiState.selectedHoldColorKey,
            onSelectHoldColor = onSelectHoldColor
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp),
                shape = RoundedCornerShape(30.dp),
                color = RecordSurface.copy(alpha = 0.98f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = RecordTextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = body,
                        color = RecordTextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!footer.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = footer,
                            color = RecordTextHint,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RecordAccent,
                            contentColor = RecordOnAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = actionLabel, fontWeight = FontWeight.SemiBold)
                    }
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
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = RealtimeSheetMaxWidth)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(28.dp),
                color = RecordSurface.copy(alpha = 0.98f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    content()
                }
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
        color = if (isSelected) RecordAccentSoft else RecordSurfaceMuted
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
                    color = RecordTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = place.roadAddressName ?: place.addressName.orEmpty(),
                    color = RecordTextSecondary,
                    fontSize = 13.sp
                )
            }

            if (isResolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = RecordAccent,
                    strokeWidth = 2.dp
                )
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = place.distanceMeters.formatDistance(),
                        color = RecordAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = RecordAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
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
            .background(RecordAccent.copy(alpha = if (enabled) 0.24f else 0.10f))
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
                .background(if (isRecording) Color.White else RecordSurfaceMuted),
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
                    text = "촬영 시작",
                    color = RecordOnAccent,
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
        color = RecordBackdrop.copy(alpha = 0.82f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            color = RecordTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PillTag(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RecordSurface.copy(alpha = 0.96f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = RecordTextPrimary,
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
            .background(RecordScrim.copy(alpha = alpha))
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
