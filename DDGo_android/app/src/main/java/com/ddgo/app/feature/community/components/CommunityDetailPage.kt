package com.ddgo.app.feature.community.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityVideoAttachment
import com.ddgo.app.feature.community.CommunityPalette
import com.ddgo.app.feature.community.CommunityUiState
import com.ddgo.app.feature.main.MainChromeDefaults
import androidx.compose.foundation.Image as FoundationImage

private val DetailPagePadding = 20.dp
private val DetailSectionSpacing = 24.dp
private val DetailCommentSpacing = 18.dp
private val DetailCommentReplyIndent = 18.dp
private val DetailCommentIconSize = 46.dp

@Composable
internal fun CommunityDetailPage(
    uiState: CommunityUiState,
    onBack: () -> Unit,
    onTogglePostLike: () -> Unit,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit,
    onCommentChanged: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onCancelComment: () -> Unit,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    val detail = uiState.detail
    val density = LocalDensity.current
    val commentDockHeightPx = remember { mutableIntStateOf(0) }
    val commentDockBottomPadding = if (commentDockHeightPx.intValue == 0) {
        MainChromeDefaults.ContentBottomPadding + 110.dp
    } else {
        with(density) { commentDockHeightPx.intValue.toDp() } + 10.dp
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommunityPalette.Surface)
    ) {
        when {
            uiState.isLoadingDetail -> CommunityDetailLoadingState()

            uiState.detailError != null -> CommunityDetailErrorState(
                message = uiState.detailError
            )

            detail != null -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(
                        start = DetailPagePadding,
                        end = DetailPagePadding,
                        top = 8.dp,
                        bottom = commentDockBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(DetailSectionSpacing)
                ) {
                    item {
                        CommunityDetailHeaderBar(
                            isMine = detail.isMine,
                            onBack = onBack,
                            onEditPost = onEditPost,
                            onDeletePost = onDeletePost
                        )
                    }

                    item {
                        CommunityDetailPostSection(
                            detail = detail,
                            onTogglePostLike = onTogglePostLike
                        )
                    }

                    item {
                        CommunityDetailSectionDivider()
                    }

                    item {
                        CommunityCommentsSection(
                            comments = uiState.comments,
                            onReply = onReply,
                            onEditComment = onEditComment,
                            onDeleteComment = onDeleteComment,
                            onToggleCommentLike = onToggleCommentLike
                        )
                    }
                }
            }
        }

        if (detail != null) {
            CommunityDetailCommentInputDock(
                commentInput = uiState.commentInput,
                replyingToNickname = uiState.replyingToNickname,
                editingCommentId = uiState.editingCommentId,
                onCommentChanged = onCommentChanged,
                onSubmitComment = onSubmitComment,
                onCancelComment = onCancelComment,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { commentDockHeightPx.intValue = it.height }
            )
        }
    }
}

@Composable
private fun CommunityDetailHeaderBar(
    isMine: Boolean,
    onBack: () -> Unit,
    onEditPost: () -> Unit,
    onDeletePost: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        CommunityDetailTopBarChrome(
            title = "커뮤니티",
            onBack = onBack,
            onMore = if (isMine) {
                { menuExpanded = true }
            } else {
                null
            }
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            DropdownMenuItem(
                text = { Text("수정") },
                onClick = {
                    menuExpanded = false
                    onEditPost()
                }
            )
            DropdownMenuItem(
                text = { Text("삭제", color = CommunityPalette.Danger) },
                onClick = {
                    menuExpanded = false
                    onDeletePost()
                }
            )
        }
    }
}

@Composable
private fun CommunityDetailPostSection(
    detail: CommunityPostDetail,
    onTogglePostLike: () -> Unit
) {
    val gymName = detail.gymName?.takeIf { it.isNotBlank() }
    val createdAtDisplay = formatCommunityFeedTimestamp(detail.createdAt)

    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = detail.title,
                modifier = if (gymName != null) Modifier.weight(1f) else Modifier,
                color = CommunityPalette.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            gymName?.let {
                CommunityDetailTagChip(
                    text = it,
                    highlighted = true
                )
            }
        }

        CommunityDetailAuthorRowChrome(
            authorName = detail.authorNickname,
            metaText = "$createdAtDisplay | 조회 ${detail.viewCount}회",
            avatarContent = {
                CommunityDetailAvatar(
                    name = detail.authorNickname,
                    size = CommunityDetailAvatarSize
                )
            }
        )

        CommunityDetailBodyText(text = detail.content)

        if (detail.videos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                detail.videos.forEachIndexed { index, video ->
                    CommunityDetailPlayableMediaCard(
                        video = video,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (index != detail.videos.lastIndex) {
                        CommunityDetailSectionDivider()
                    }
                }
            }
        }

        CommunityDetailEngagementRow(
            likeCount = detail.likeCount,
            commentCount = detail.commentCount,
            isLiked = detail.isLiked,
            onToggleLike = onTogglePostLike
        )
    }
}

@Composable
private fun CommunityDetailAvatar(
    name: String,
    size: Dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.firstOrNull()?.toString() ?: "?",
                color = CommunityPalette.TextSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CommunityDetailMediaCard(
    video: CommunityVideoAttachment,
    modifier: Modifier = Modifier
) {
    val thumbnailState = rememberCommunityVideoThumbnailState(video.playbackUrl, video.thumbnailUrl)

    CommunityDetailMediaSurface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            CommunityDetailVideoThumbnailContent(
                thumbnailState = thumbnailState,
                contentDescription = video.originalFileName,
                modifier = Modifier.fillMaxSize()
            )
            if (thumbnailState !is CommunityVideoThumbnailState.Loading) {
                Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "동영상 미리보기",
                tint = CommunityPalette.OnAccent.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                )
            }
        }
    }
}

@Composable
private fun CommunityDetailPlayableMediaCard(
    video: CommunityVideoAttachment,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember(video.playbackUrl) { mutableStateOf(false) }
    val thumbnailState = rememberCommunityVideoThumbnailState(video.playbackUrl, video.thumbnailUrl)
    var isPortraitVideo by remember(video.playbackUrl) { mutableStateOf(false) }

    LaunchedEffect(thumbnailState) {
        thumbnailState.isPortrait?.let { isPortraitVideo = it }
    }
    val player = remember(context, video.playbackUrl, isPlaying) {
        if (!isPlaying) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(video.playbackUrl))
                prepare()
                playWhenReady = true
            }
        }
    }

    DisposableEffect(player) {
        if (player == null) {
            onDispose { }
        } else {
            resolveCommunityVideoIsPortrait(player.videoSize)?.let { isPortraitVideo = it }
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    resolveCommunityVideoIsPortrait(videoSize)?.let { isPortraitVideo = it }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }
    }

    CommunityDetailMediaSurface(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (player == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isPlaying = true }
                ) {
                    CommunityDetailVideoThumbnailContent(
                        thumbnailState = thumbnailState,
                        contentDescription = video.originalFileName,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (thumbnailState !is CommunityVideoThumbnailState.Loading) {
                        Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "동영상 재생",
                        tint = CommunityPalette.OnAccent.copy(alpha = 0.92f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                        )
                    }
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            resizeMode = if (isPortraitVideo) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            setShutterBackgroundColor(CommunityPalette.SurfaceMuted.toArgb())
                            this.player = player
                        }
                    },
                    update = {
                        it.player = player
                        it.resizeMode = if (isPortraitVideo) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                        it.setShutterBackgroundColor(CommunityPalette.SurfaceMuted.toArgb())
                    }
                )
            }
        }
    }
}

@Composable
private fun CommunityDetailVideoThumbnailContent(
    thumbnailState: CommunityVideoThumbnailState,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(CommunityPalette.SurfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        when (thumbnailState) {
            CommunityVideoThumbnailState.Loading -> {
                CircularProgressIndicator(
                    color = CommunityPalette.AccentStrong,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            }

            is CommunityVideoThumbnailState.Success -> {
                FoundationImage(
                    bitmap = thumbnailState.bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (thumbnailState.isPortrait) {
                        ContentScale.Fit
                    } else {
                        ContentScale.Crop
                    }
                )
            }

            is CommunityVideoThumbnailState.Error -> Unit
        }
    }
}

@Composable
private fun CommunityDetailEngagementRow(
    likeCount: Int,
    commentCount: Int,
    isLiked: Boolean,
    onToggleLike: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityDetailMetricAction(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = "좋아요",
            count = likeCount,
            emphasized = true,
            selected = isLiked,
            onClick = onToggleLike
        )
        CommunityDetailMetricAction(
            icon = Icons.Outlined.ChatBubbleOutline,
            label = "댓글",
            count = commentCount
        )
    }
}

@Composable
private fun CommunityDetailMetricAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    emphasized: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick)
        } else {
            Modifier
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary,
            modifier = Modifier.size(30.dp)
        )
        Text(
            text = label,
            color = CommunityPalette.TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal
        )
        Text(
            text = count.toString(),
            color = if (selected || emphasized) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CommunityDetailBodyText(text: String) {
    Text(
        text = text,
        color = CommunityPalette.TextPrimary.copy(alpha = 0.96f),
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = 30.sp
        ),
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun CommunityCommentsSection(
    comments: List<CommunityComment>,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    if (comments.isEmpty()) {
        CommunityMessageCard(
            message = "첫 댓글을 남겨보세요.",
            background = CommunityPalette.SurfaceMuted,
            contentColor = CommunityPalette.TextSecondary
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        comments.forEachIndexed { index, comment ->
            CommunityCommentThread(
                comment = comment,
                onReply = onReply,
                onEditComment = onEditComment,
                onDeleteComment = onDeleteComment,
                onToggleCommentLike = onToggleCommentLike
            )
            if (index != comments.lastIndex) {
                Spacer(Modifier.height(DetailCommentSpacing))
                CommunityDetailSectionDivider()
                Spacer(Modifier.height(DetailCommentSpacing))
            }
        }
    }
}

@Composable
private fun CommunityCommentThread(
    comment: CommunityComment,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CommunityCommentRow(
            comment = comment,
            isReply = false,
            onReply = onReply,
            onEditComment = onEditComment,
            onDeleteComment = onDeleteComment,
            onToggleCommentLike = onToggleCommentLike
        )
        comment.replies.forEach { reply ->
            CommunityCommentRow(
                comment = reply,
                isReply = true,
                onReply = onReply,
                onEditComment = onEditComment,
                onDeleteComment = onDeleteComment,
                onToggleCommentLike = onToggleCommentLike
            )
        }
    }
}

@Composable
private fun CommunityCommentRow(
    comment: CommunityComment,
    isReply: Boolean,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    val createdAtDisplay = formatCommunityFeedTimestamp(comment.createdAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) DetailCommentReplyIndent else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        CommunityDetailAvatar(
            name = comment.authorNickname,
            size = if (isReply) 40.dp else DetailCommentIconSize
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorNickname,
                    color = CommunityPalette.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = createdAtDisplay,
                    color = CommunityPalette.TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                text = comment.content,
                color = CommunityPalette.TextPrimary.copy(alpha = 0.96f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = 26.sp
                ),
                fontWeight = FontWeight.Medium
            )

            CommunityCommentActionRow(
                comment = comment,
                isReply = isReply,
                onReply = onReply,
                onEditComment = onEditComment,
                onDeleteComment = onDeleteComment,
                onToggleCommentLike = onToggleCommentLike
            )
        }
    }
}

@Composable
private fun CommunityCommentActionRow(
    comment: CommunityComment,
    isReply: Boolean,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityCommentActionText(
            text = "좋아요 ${comment.likeCount}",
            onClick = { onToggleCommentLike(comment) },
            highlighted = comment.isLiked
        )
        if (!isReply) {
            CommunityCommentActionText(
                text = "답글",
                onClick = { onReply(comment) }
            )
        }
        if (comment.isMine) {
            CommunityCommentActionText(
                text = "수정",
                onClick = { onEditComment(comment) }
            )
            CommunityCommentActionText(
                text = "삭제",
                onClick = { onDeleteComment(comment) },
                color = CommunityPalette.Danger
            )
        }
    }
}

@Composable
private fun CommunityCommentActionText(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    color: Color = if (highlighted) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary
) {
    Text(
        text = text,
        modifier = Modifier.clickable(onClick = onClick),
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium
    )
}

@Composable
private fun CommunityDetailCommentInputDock(
    commentInput: String,
    replyingToNickname: String?,
    editingCommentId: Long?,
    onCommentChanged: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onCancelComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CommunityPalette.Surface,
        shadowElevation = 8.dp
    ) {
        Column {
            HorizontalDivider(color = CommunityPalette.Border.copy(alpha = 0.78f))
            Column(
                modifier = Modifier.padding(
                    start = DetailPagePadding,
                    end = DetailPagePadding,
                    top = 12.dp,
                    bottom = MainChromeDefaults.ContentBottomPadding + 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (replyingToNickname != null || editingCommentId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = replyingToNickname?.let { "${it}님에게 답글 작성 중" } ?: "댓글 수정 중",
                            color = CommunityPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onCancelComment) {
                            Text("취소", color = CommunityPalette.AccentStrong)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = CommunityPalette.SurfaceMuted,
                        border = BorderStroke(1.dp, CommunityPalette.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "이미지 첨부",
                                tint = CommunityPalette.TextHint,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    CommunityDetailCommentField(
                        value = commentInput,
                        onValueChange = onCommentChanged,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        modifier = Modifier
                            .width(88.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onSubmitComment),
                        color = CommunityPalette.AccentStrong
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (editingCommentId != null) "수정" else "등록",
                                color = CommunityPalette.OnAccent,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityDetailCommentField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isBlank()) {
                Text(
                    text = "댓글을 입력하세요",
                    color = CommunityPalette.TextHint,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = CommunityPalette.TextPrimary,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(CommunityPalette.AccentStrong)
            )
        }
    }
}

@Composable
private fun CommunityDetailLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = CommunityPalette.AccentStrong)
    }
}

@Composable
private fun CommunityDetailErrorState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentAlignment = Alignment.Center
    ) {
        CommunityMessageCard(
            message = message,
            background = CommunityPalette.SurfaceMuted,
            contentColor = CommunityPalette.Danger
        )
    }
}
