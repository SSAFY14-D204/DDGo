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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private val DetailPagePadding = 18.dp
private val DetailSectionSpacing = 18.dp
private val DetailCommentSpacing = 14.dp
private val DetailCommentReplyIndent = 14.dp
private val DetailCommentIconSize = 38.dp

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
        MainChromeDefaults.ContentBottomPadding + 138.dp
    } else {
        with(density) { commentDockHeightPx.intValue.toDp() } + 18.dp
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

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "\uB4A4\uB85C \uAC00\uAE30",
                tint = CommunityPalette.TextPrimary
            )
        }

        Text(
            text = "\uCEE4\uBBA4\uB2C8\uD2F0",
            color = CommunityPalette.TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (isMine) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "\uB354\uBCF4\uAE30",
                        tint = CommunityPalette.TextPrimary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = CommunityPalette.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "\uC218\uC815",
                                color = CommunityPalette.TextPrimary
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onEditPost()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "\uC0AD\uC81C",
                                color = CommunityPalette.Danger
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeletePost()
                        }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
private fun CommunityDetailPostSection(
    detail: CommunityPostDetail,
    onTogglePostLike: () -> Unit
) {
    val createdAtDisplay = formatCommunityFeedTimestamp(detail.createdAt)
    val authorName = detail.authorNickname.ifBlank { "\uC775\uBA85" }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CommunityDetailAuthorMetaRow(
            authorName = authorName,
            metaText = "$createdAtDisplay \u00B7 \uC870\uD68C ${detail.viewCount}",
            gymName = detail.gymName
        )

        Text(
            text = detail.title,
            color = CommunityPalette.TextPrimary,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 17.sp,
                lineHeight = 24.sp
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        CommunityDetailBodyText(text = detail.content)

        if (detail.videos.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

        CommunityDetailSectionDivider()
        CommunityDetailEngagementRow(
            likeCount = detail.likeCount,
            commentCount = detail.commentCount,
            isLiked = detail.isLiked,
            onToggleLike = onTogglePostLike
        )
    }
}

@Composable
private fun CommunityDetailAuthorMetaRow(
    authorName: String,
    metaText: String,
    gymName: String?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityDetailAvatar(
            name = authorName,
            size = 40.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = authorName,
                color = CommunityPalette.TextPrimary,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (!gymName.isNullOrBlank()) {
                    "$metaText \u00B7 $gymName"
                } else {
                    metaText
                },
                color = CommunityPalette.TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
            )
        }
    }
}

@Composable
private fun CommunityDetailAvatar(
    name: String,
    size: Dp
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(12.dp),
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = CommunityPalette.TextHint,
                modifier = Modifier.size(size * 0.56f)
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
    var isFullscreen by remember(video.playbackUrl) { mutableStateOf(false) }
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

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (player != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                this.player = player
                                useController = true
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        update = { playerView ->
                            playerView.player = player
                            playerView.useController = true
                            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            playerView.setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    )
                } else {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(40.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.52f),
                            shape = CircleShape
                        )
                        .clickable { isFullscreen = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "\uC804\uCCB4\uD654\uBA74 \uB2EB\uAE30",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                            contentDescription = "\uC601\uC0C1 \uC7AC\uC0DD",
                            tint = CommunityPalette.OnAccent.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.42f),
                                shape = CircleShape
                            )
                            .clickable {
                                isPlaying = true
                                isFullscreen = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInFull,
                            contentDescription = "\uC601\uC0C1 \uC804\uCCB4\uD654\uBA74",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
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
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.42f),
                            shape = CircleShape
                        )
                        .clickable { isFullscreen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = "\uC601\uC0C1 \uC804\uCCB4\uD654\uBA74",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityDetailMetricAction(
            modifier = Modifier.weight(1f),
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = "\uACF5\uAC10",
            count = likeCount,
            emphasized = true,
            selected = isLiked,
            onClick = onToggleLike
        )
        CommunityDetailMetricAction(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.ChatBubbleOutline,
            label = "\uB313\uAE00",
            count = commentCount
        )
    }
}

@Composable
private fun CommunityDetailMetricAction(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: Int,
    emphasized: Boolean = false,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Row(
        modifier = rowModifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = CommunityPalette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                color = if (selected || emphasized) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun CommunityDetailBodyText(text: String) {
    Text(
        text = text,
        color = CommunityPalette.TextPrimary.copy(alpha = 0.96f),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp
        ),
        fontWeight = FontWeight.Normal
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = CommunityPalette.TextHint.copy(alpha = 0.82f),
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = "\uCCAB \uB313\uAE00\uC744 \uB0A8\uACA8\uC8FC\uC138\uC694.",
                color = CommunityPalette.TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)
            )
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    val authorName = comment.authorNickname.ifBlank { "\uC775\uBA85" }

    if (isReply) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DetailCommentReplyIndent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "\u21B3",
                color = CommunityPalette.TextHint,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                modifier = Modifier.padding(top = 10.dp)
            )
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = CommunityPalette.SurfaceMuted,
                border = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.86f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    CommunityDetailAvatar(
                        name = authorName,
                        size = 30.dp
                    )
                    CommunityCommentBody(
                        modifier = Modifier.weight(1f),
                        authorName = authorName,
                        createdAtDisplay = createdAtDisplay,
                        content = comment.content,
                        comment = comment,
                        isReply = true,
                        onReply = onReply,
                        onEditComment = onEditComment,
                        onDeleteComment = onDeleteComment,
                        onToggleCommentLike = onToggleCommentLike
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            CommunityDetailAvatar(
                name = authorName,
                size = DetailCommentIconSize
            )
            CommunityCommentBody(
                modifier = Modifier.weight(1f),
                authorName = authorName,
                createdAtDisplay = createdAtDisplay,
                content = comment.content,
                comment = comment,
                isReply = false,
                onReply = onReply,
                onEditComment = onEditComment,
                onDeleteComment = onDeleteComment,
                onToggleCommentLike = onToggleCommentLike
            )
        }
    }
}

@Composable
private fun CommunityCommentBody(
    modifier: Modifier,
    authorName: String,
    createdAtDisplay: String,
    content: String,
    comment: CommunityComment,
    isReply: Boolean,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = authorName,
                color = CommunityPalette.TextPrimary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold
            )
            CommunityCommentActionPill(
                comment = comment,
                isReply = isReply,
                onReply = onReply,
                onEditComment = onEditComment,
                onDeleteComment = onDeleteComment,
                onToggleCommentLike = onToggleCommentLike
            )
        }

        Text(
            text = content,
            color = CommunityPalette.TextPrimary.copy(alpha = 0.96f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 22.sp
            ),
            fontWeight = FontWeight.Normal
        )

        Text(
            text = createdAtDisplay,
            color = CommunityPalette.TextSecondary,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
        )
    }
}

@Composable
private fun CommunityCommentActionPill(
    comment: CommunityComment,
    isReply: Boolean,
    onReply: (CommunityComment) -> Unit,
    onEditComment: (CommunityComment) -> Unit,
    onDeleteComment: (CommunityComment) -> Unit,
    onToggleCommentLike: (CommunityComment) -> Unit
) {
    var menuExpanded by remember(comment.id) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.82f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (comment.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "\uC88B\uC544\uC694",
                tint = if (comment.isLiked) CommunityPalette.AccentStrong else CommunityPalette.TextHint,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onToggleCommentLike(comment) }
            )

            if (!isReply) {
                CommunityCommentActionDivider()
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "\uB2F5\uAE00",
                    tint = CommunityPalette.TextHint,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onReply(comment) }
                )
            }

            if (comment.isMine) {
                CommunityCommentActionDivider()
                Box {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "\uB354\uBCF4\uAE30",
                        tint = CommunityPalette.TextHint,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { menuExpanded = true }
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = CommunityPalette.Surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 10.dp,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "\uC218\uC815",
                                    color = CommunityPalette.TextPrimary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditComment(comment)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "\uC0AD\uC81C",
                                    color = CommunityPalette.Danger
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteComment(comment)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityCommentActionDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(12.dp)
            .background(CommunityPalette.Border)
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
                    top = 10.dp,
                    bottom = MainChromeDefaults.ContentBottomPadding + 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (replyingToNickname != null || editingCommentId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = replyingToNickname?.let {
                                "$it\uB2D8\uC5D0\uAC8C \uB2F5\uAE00 \uC791\uC131 \uC911"
                            } ?: "\uB313\uAE00 \uC218\uC815 \uC911",
                            color = CommunityPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                        )
                        TextButton(onClick = onCancelComment) {
                            Text("\uCDE8\uC18C", color = CommunityPalette.AccentStrong)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { },
                        shape = RoundedCornerShape(14.dp),
                        color = CommunityPalette.SurfaceMuted,
                        border = BorderStroke(1.dp, CommunityPalette.Border)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "\uCCA8\uBD80\uD30C\uC77C",
                                tint = CommunityPalette.TextHint,
                                modifier = Modifier.size(22.dp)
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
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSubmitComment),
                        color = CommunityPalette.AccentStrong
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (editingCommentId != null) {
                                    "\uB313\uAE00 \uC218\uC815"
                                } else {
                                    "\uB313\uAE00 \uB4F1\uB85D"
                                },
                                tint = CommunityPalette.OnAccent,
                                modifier = Modifier.size(20.dp)
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
        shape = RoundedCornerShape(16.dp),
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isBlank()) {
                Text(
                    text = "\uB313\uAE00\uC744 \uC785\uB825\uD558\uC138\uC694.",
                    color = CommunityPalette.TextHint,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
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
