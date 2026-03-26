package com.ddgo.app.feature.community.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.feature.community.CommunityComposeMode
import com.ddgo.app.feature.community.CommunityDestination
import com.ddgo.app.feature.community.CommunityPalette
import com.ddgo.app.feature.community.CommunityUiState
import com.ddgo.app.feature.main.MainChromeDefaults

@Composable
internal fun CommunityComposePage(
    uiState: CommunityUiState,
    onBack: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onClearGym: () -> Unit,
    onOpenChallengeSheet: () -> Unit,
    onPickVideos: () -> Unit,
    onRemoveVideo: (String) -> Unit,
    onMoveVideoUp: (String) -> Unit,
    onMoveVideoDown: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val composeState = uiState.composeState
    val isSubmitting = composeState.isSubmitting
    val isEditMode = (uiState.destination as? CommunityDestination.Compose)?.editingPostId != null
    val isAnalysisShareMode = composeState.mode == CommunityComposeMode.AnalysisShare
    val showGymSection = !isAnalysisShareMode || composeState.gymName != null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CommunityPagePadding,
            end = CommunityPagePadding,
            top = 14.dp,
            bottom = MainChromeDefaults.ContentBottomPadding + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            CommunityComposeHeaderBar(
                title = if (isEditMode) "커뮤니티 글 수정" else "커뮤니티 글쓰기",
                isSubmitting = isSubmitting,
                onBack = onBack,
                onSubmit = onSubmit
            )
        }

        item {
            Spacer(Modifier.height(34.dp))
            CommunityComposeTitleInput(
                value = composeState.title,
                onValueChange = onTitleChanged,
                readOnly = isSubmitting
            )
            Spacer(Modifier.height(18.dp))
            CommunityComposeContentInput(
                value = composeState.content,
                onValueChange = onContentChanged,
                readOnly = isSubmitting
            )
            Spacer(Modifier.height(40.dp))
            if (showGymSection) {
                CommunityComposeSectionDivider()
                Spacer(Modifier.height(32.dp))
                CommunityComposeTagSection(
                    selectedGymName = composeState.gymName,
                    editable = !isAnalysisShareMode,
                    enabled = !isSubmitting,
                    onOpenChallengeSheet = onOpenChallengeSheet,
                    onClearGym = onClearGym
                )
                Spacer(Modifier.height(42.dp))
            }
            CommunityComposeSectionDivider()
            Spacer(Modifier.height(28.dp))
            CommunityComposeVideoSection(
                videos = composeState.videos,
                editable = !isAnalysisShareMode,
                enabled = !isSubmitting,
                onPickVideos = onPickVideos,
                onRemoveVideo = onRemoveVideo,
                onMoveVideoUp = onMoveVideoUp,
                onMoveVideoDown = onMoveVideoDown
            )
        }

        composeState.submitError?.let { error ->
            item {
                Spacer(Modifier.height(28.dp))
                CommunityMessageCard(
                    message = error,
                    background = CommunityPalette.DangerSoft,
                    contentColor = CommunityPalette.Danger
                )
            }
        }
    }
}

@Composable
private fun CommunityComposeHeaderBar(
    title: String,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onSubmit: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        CommunityCloseIconButton(
            onClick = onBack,
            enabled = !isSubmitting,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = CommunityPalette.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = CommunityPalette.AccentStrong
                )
            }
            CommunityHeaderActionTextButton(
                text = "완료",
                onClick = onSubmit,
                enabled = !isSubmitting,
                contentColor = CommunityPalette.AccentStrong
            )
        }
    }
}

@Composable
private fun CommunityComposeTitleInput(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        readOnly = readOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = CommunityPalette.TextPrimary,
            fontWeight = FontWeight.Bold
        ),
        cursorBrush = SolidColor(CommunityPalette.AccentStrong),
        decorationBox = { innerTextField ->
            if (value.isBlank()) {
                Text(
                    text = "제목을 입력하세요.",
                    color = CommunityPalette.TextSecondary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            innerTextField()
        }
    )
}

@Composable
private fun CommunityComposeContentInput(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 154.dp),
        readOnly = readOnly,
        textStyle = MaterialTheme.typography.headlineSmall.copy(
            color = CommunityPalette.TextPrimary,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(CommunityPalette.AccentStrong),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 154.dp)
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "내용을 자유롭게 적어주세요.",
                        color = CommunityPalette.TextHint,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun CommunityComposeSectionLabel(text: String) {
    Text(
        text = text,
        color = CommunityPalette.TextSecondary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun CommunityComposeTagSection(
    selectedGymName: String?,
    editable: Boolean,
    enabled: Boolean,
    onOpenChallengeSheet: () -> Unit,
    onClearGym: () -> Unit
) {
    if (!editable) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CommunityComposeSectionLabel(text = "암장 태그")
            selectedGymName?.let { gymName ->
                CommunityComposeChip(text = gymName)
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CommunityComposeSectionLabel(text = "암장 태그")
        CommunityComposeSearchFieldChrome(
            enabled = enabled,
            onClick = onOpenChallengeSheet,
            placeholder = "암장 검색",
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "암장 검색 열기",
                    tint = CommunityPalette.TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        )
        selectedGymName?.let { gymName ->
            CommunityComposeChip(
                text = gymName,
                onClick = if (enabled) onOpenChallengeSheet else null,
                onRemove = if (enabled) onClearGym else null
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityComposeVideoSection(
    videos: List<CommunityVideoDraft>,
    editable: Boolean,
    enabled: Boolean,
    onPickVideos: () -> Unit,
    onRemoveVideo: (String) -> Unit,
    onMoveVideoUp: (String) -> Unit,
    onMoveVideoDown: (String) -> Unit
) {
    if (!editable) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CommunityComposeSectionLabel(text = "첨부 영상")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                videos.forEach { video ->
                    CommunityComposeVideoPreviewTile(
                        video = video,
                        editable = false,
                        enabled = enabled,
                        isFirst = true,
                        isLast = true,
                        onMoveLeft = {},
                        onMoveRight = {},
                        onRemove = {}
                    )
                }
            }
            Text(
                text = "${videos.size} / 3",
                color = CommunityPalette.TextHint,
                style = MaterialTheme.typography.bodySmall
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CommunityComposeSectionLabel(text = "첨부 영상")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CommunityVideoPickerTile(
                enabled = enabled && videos.size < 3,
                onPickVideos = onPickVideos
            )
            videos.forEachIndexed { index, video ->
                CommunityComposeVideoPreviewTile(
                    video = video,
                    editable = true,
                    enabled = enabled,
                    isFirst = index == 0,
                    isLast = index == videos.lastIndex,
                    onMoveLeft = { onMoveVideoUp(video.id) },
                    onMoveRight = { onMoveVideoDown(video.id) },
                    onRemove = { onRemoveVideo(video.id) }
                )
            }
        }
        Text(
            text = "${videos.size} / 3",
            color = CommunityPalette.TextHint,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CommunityVideoPickerTile(
    enabled: Boolean,
    onPickVideos: () -> Unit
) {
    CommunityComposeAttachmentTile(
        modifier = Modifier.size(102.dp),
        onClick = if (enabled) onPickVideos else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = CommunityPalette.TextPrimary,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "영상선택",
                color = CommunityPalette.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CommunityComposeVideoPreviewTile(
    video: CommunityVideoDraft,
    editable: Boolean,
    enabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val previewSource = video.localUri ?: video.playbackUrl
    val previewRequest = remember(previewSource) {
        previewSource?.let { source ->
            ImageRequest.Builder(context)
                .data(source)
                .crossfade(true)
                .videoFrameMillis(1_000)
                .build()
        }
    }

    Box {
        CommunityComposeAttachmentTile(
            modifier = Modifier.size(102.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (previewRequest != null) {
                    AsyncImage(
                        model = previewRequest,
                        contentDescription = video.originalFileName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = CommunityPalette.TextHint,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = CommunityPalette.OnAccent.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(30.dp)
                )

                if (editable && !isFirst) {
                    CommunityVideoOverlayButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "앞으로 이동",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                        enabled = enabled,
                        onClick = onMoveLeft
                    )
                }

                if (editable && !isLast) {
                    CommunityVideoOverlayButton(
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "뒤로 이동",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        enabled = enabled,
                        onClick = onMoveRight
                    )
                }
            }
        }
        if (editable) {
            Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-8).dp),
            shape = CircleShape,
            color = CommunityPalette.TextSecondary.copy(alpha = 0.78f)
        ) {
            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "영상 제거",
                    tint = CommunityPalette.OnAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun CommunityVideoOverlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = CommunityPalette.Surface.copy(alpha = 0.9f)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(26.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = CommunityPalette.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CommunityChallengeReferenceSheet(
    references: List<CommunityChallengeReference>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CommunityChallengeReference) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CommunityPalette.Surface
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = CommunityPagePadding,
                end = CommunityPagePadding,
                top = 8.dp,
                bottom = CommunityPagePadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CommunitySectionHeader(
                    title = "내 챌린지 참고",
                    subtitle = "최근 기록에서 암장과 문제 정보를 바로 가져올 수 있어요."
                )
            }

            when {
                isLoading -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CommunityPalette.AccentStrong)
                    }
                }

                references.isEmpty() -> item {
                    CommunityMessageCard(
                        message = "참고할 챌린지 기록이 없습니다.",
                        background = CommunityPalette.SurfaceMuted,
                        contentColor = CommunityPalette.TextSecondary
                    )
                }

                else -> items(references, key = { it.challengeId }) { reference ->
                    CommunitySectionCard(
                        modifier = Modifier.clickable { onSelect(reference) }
                    ) {
                        Text(
                            text = reference.gymName,
                            color = CommunityPalette.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CommunityStatChip(
                                text = "색상 ${reference.problemColor}",
                                tone = CommunityChipTone.Accent
                            )
                            reference.gradeLabel?.let {
                                CommunityStatChip(
                                    text = "난이도 $it",
                                    tone = CommunityChipTone.Success
                                )
                            }
                            CommunityStatChip(
                                text = "시도 ${reference.attempts.size}회",
                                tone = CommunityChipTone.Neutral
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = reference.createdAt,
                            color = CommunityPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
