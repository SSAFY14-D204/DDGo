package com.ddgo.app.feature.community

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.ddgo.app.core.ui.atom.DdgoChoiceChip
import com.ddgo.app.core.ui.atom.DdgoOutlinedButton
import com.ddgo.app.core.ui.atom.DdgoPrimaryButton
import com.ddgo.app.core.ui.atom.DdgoTextButton
import com.ddgo.app.core.ui.atom.DdgoTextButtonTone
import com.ddgo.app.core.ui.atom.DdgoTextField
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.feature.main.MainChromeDefaults

private val CommunityPagePadding = 20.dp
private val CommunitySectionSpacing = 18.dp
private val CommunityCardRadius = 28.dp
private val CommunityChipRadius = 999.dp
private val CommunityCardBorder = BorderStroke(1.dp, CommunityPalette.Border)
private val CommunitySubtleBorder = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.72f))

@Composable
private fun CommunityPageShell(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommunityPalette.PageGradient)
    ) {
        CommunityGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 54.dp, y = (-30).dp),
            colors = listOf(
                CommunityPalette.Accent.copy(alpha = 0.18f),
                CommunityPalette.Accent.copy(alpha = 0f)
            )
        )

        CommunityGlow(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-88).dp, y = 138.dp),
            colors = listOf(
                CommunityPalette.AccentStrong.copy(alpha = 0.12f),
                CommunityPalette.AccentStrong.copy(alpha = 0f)
            )
        )

        content()
    }
}

@Composable
private fun CommunityGlow(
    modifier: Modifier = Modifier,
    colors: List<Color>
) {
    Box(
        modifier = modifier
            .size(220.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Brush.radialGradient(colors = colors))
    )
}

@Composable
private fun CommunityHeroCard(
    title: String,
    description: String,
    chips: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityCardRadius),
        color = Color.Transparent,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .background(CommunityPalette.HeroGradient)
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = title,
                    color = CommunityPalette.OnAccent,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = CommunityPalette.OnAccent.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.take(3).forEach { chip ->
                        CommunityAccentChip(text = chip)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityAccentChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val containerColor = if (selected) CommunityPalette.AccentSoft else CommunityPalette.Surface
    val contentColor = if (selected) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary
    val borderColor = if (selected) CommunityPalette.AccentStrong else CommunityPalette.Border
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        color = containerColor,
        shape = RoundedCornerShape(CommunityChipRadius),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CommunitySectionCard(
    modifier: Modifier = Modifier,
    background: Color = CommunityPalette.Surface,
    border: BorderStroke = CommunityCardBorder,
    contentPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityCardRadius),
        color = background,
        border = border,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun CommunityScreen(viewModel: CommunityViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addSelectedVideos(uris.map(Uri::toString))
    }

    BackHandler(enabled = uiState.destination != CommunityDestination.Feed || uiState.isChallengeSheetVisible) {
        if (uiState.isChallengeSheetVisible) {
            viewModel.closeChallengeReferenceSheet()
        } else {
            viewModel.navigateBack()
        }
    }

    CommunityPageShell {
        when (uiState.destination) {
            CommunityDestination.Feed -> FeedScreen(
                uiState = uiState,
                onKeywordChanged = viewModel::onSearchKeywordChanged,
                onSearchSubmit = viewModel::submitSearch,
                onSortSelected = viewModel::selectSort,
                onSelectGym = viewModel::selectGymFilter,
                onOpenPost = { viewModel.openPostDetail(it.id) },
                onOpenCompose = viewModel::openCompose
            )

            is CommunityDestination.Detail -> DetailScreen(
                uiState = uiState,
                onBack = viewModel::navigateBack,
                onTogglePostLike = viewModel::togglePostLike,
                onEditPost = viewModel::openEdit,
                onDeletePost = viewModel::deleteCurrentPost,
                onCommentChanged = viewModel::updateCommentInput,
                onSubmitComment = viewModel::submitComment,
                onCancelComment = viewModel::cancelCommentDraft,
                onReply = viewModel::beginReply,
                onEditComment = viewModel::beginCommentEdit,
                onDeleteComment = { viewModel.deleteComment(it.id) },
                onToggleCommentLike = viewModel::toggleCommentLike
            )

            is CommunityDestination.Compose -> ComposeScreen(
                uiState = uiState,
                onBack = viewModel::navigateBack,
                onTitleChanged = viewModel::updateComposeTitle,
                onContentChanged = viewModel::updateComposeContent,
                onClearGym = viewModel::clearComposeGym,
                onOpenChallengeSheet = viewModel::openChallengeReferenceSheet,
                onPickVideos = { videoPickerLauncher.launch(arrayOf("video/*")) },
                onRemoveVideo = viewModel::removeComposeVideo,
                onMoveVideoUp = { viewModel.moveComposeVideo(it, -1) },
                onMoveVideoDown = { viewModel.moveComposeVideo(it, 1) },
                onSubmit = viewModel::submitPost
            )
        }

        uiState.message?.let { message ->
            MessageCard(
                message = message,
                background = CommunityPalette.NeutralBlack,
                contentColor = CommunityPalette.NeutralWhite,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = CommunityPagePadding, vertical = 16.dp)
                    .padding(bottom = MainChromeDefaults.ContentBottomPadding)
            )
        }
    }

    if (uiState.isChallengeSheetVisible) {
        ChallengeReferenceSheet(
            references = uiState.challengeReferences,
            isLoading = uiState.isLoadingChallengeReferences,
            onDismiss = viewModel::closeChallengeReferenceSheet,
            onSelect = viewModel::selectChallengeReference
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedScreen(
    uiState: CommunityUiState,
    onKeywordChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSortSelected: (CommunitySort) -> Unit,
    onSelectGym: (Long?, String?) -> Unit,
    onOpenPost: (CommunityPostSummary) -> Unit,
    onOpenCompose: () -> Unit
) {
    val feedBottomPadding = MainChromeDefaults.OverlayFabBottomPadding + 56.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = CommunityPagePadding,
                end = CommunityPagePadding,
                top = 14.dp,
                bottom = feedBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "커뮤니티 게시판",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = CommunityPalette.TextPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DdgoTextField(
                            value = uiState.searchKeyword,
                            onValueChange = onKeywordChanged,
                            modifier = Modifier.weight(1f),
                            placeholder = "검색어를 입력하세요",
                            leadingIcon = Icons.Default.Search,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() })
                        )
                        DdgoPrimaryButton(
                            text = "조회",
                            onClick = onSearchSubmit,
                            modifier = Modifier.width(96.dp)
                        )
                    }
                }
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        SortButton("최신순", uiState.selectedSort == CommunitySort.LATEST) {
                            onSortSelected(CommunitySort.LATEST)
                        }
                    }
                    item {
                        SortButton("인기순", uiState.selectedSort == CommunitySort.POPULAR) {
                            onSortSelected(CommunitySort.POPULAR)
                        }
                    }
                    item {
                        FilterPill("전체", uiState.selectedGymId == null) { onSelectGym(null, null) }
                    }
                    items(uiState.availableGyms, key = { it.first }) { (gymId, gymName) ->
                        FilterPill(gymName, uiState.selectedGymId == gymId) { onSelectGym(gymId, gymName) }
                    }
                }
            }

            item {
                Surface(
                    color = CommunityPalette.NeutralWhite,
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CommunityPalette.NeutralBackground)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "번호",
                            modifier = Modifier.width(42.dp),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "제목",
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "작성자",
                            modifier = Modifier.width(66.dp),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                }
            }

            when {
                uiState.isLoadingFeed -> item {
                    Surface(
                        color = CommunityPalette.NeutralWhite,
                        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = CommunityPalette.BrandBlue)
                        }
                    }
                }

                uiState.feedError != null -> item {
                    Surface(
                        color = CommunityPalette.NeutralWhite,
                        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                    ) {
                        MessageCard(
                            message = uiState.feedError,
                            background = CommunityPalette.NeutralWhite,
                            contentColor = Color(0xFFD84A4A),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                uiState.posts.isEmpty() -> item {
                    Surface(
                        color = CommunityPalette.NeutralWhite,
                        shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                    ) {
                        MessageCard(
                            message = "등록된 게시글이 없습니다.",
                            background = CommunityPalette.NeutralWhite,
                            contentColor = CommunityPalette.BrandGray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                else -> itemsIndexed(uiState.posts, key = { _, post -> post.id }) { index, post ->
                    Surface(
                        color = CommunityPalette.NeutralWhite,
                        shape = when (index) {
                            uiState.posts.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                            else -> RoundedCornerShape(0.dp)
                        },
                        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                    ) {
                        Column {
                            BoardListItem(
                                post = post,
                                index = index,
                                onOpenPost = onOpenPost
                            )
                            if (index != uiState.posts.lastIndex) {
                                HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.12f))
                            }
                        }
                    }
                }
            }
        }

        DdgoPrimaryButton(
            text = "글쓰기",
            onClick = onOpenCompose,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .width(120.dp)
                .padding(
                    end = CommunityPagePadding,
                    bottom = MainChromeDefaults.OverlayFabBottomPadding
                ),
            leadingIcon = Icons.Default.Add
        )
    }
}

@Composable
private fun DetailScreen(
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
    val commentInputBarHeightPx = remember { mutableIntStateOf(0) }
    val measuredCommentInputHeight = with(density) { commentInputBarHeightPx.intValue.toDp() }
    val commentInputBottomPadding = if (commentInputBarHeightPx.intValue == 0) {
        MainChromeDefaults.ContentBottomPadding + 120.dp
    } else {
        measuredCommentInputHeight + 12.dp
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoadingDetail -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CommunityPalette.AccentStrong)
            }

            uiState.detailError != null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                MessageCard(
                    uiState.detailError,
                    CommunityPalette.Surface,
                    Color(0xFFD84A4A),
                    modifier = Modifier.padding(horizontal = CommunityPagePadding)
                )
            }

            detail != null -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    start = CommunityPagePadding,
                    end = CommunityPagePadding,
                    top = CommunityPagePadding,
                    bottom = commentInputBottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(CommunitySectionSpacing)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                            }
                            Text(
                                "게시글 상세",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (detail.isMine) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                DdgoTextButton(
                                    text = "수정",
                                    onClick = onEditPost
                                )
                                DdgoTextButton(
                                    text = "삭제",
                                    onClick = onDeletePost,
                                    tone = DdgoTextButtonTone.Danger
                                )
                            }
                        }
                    }
                }

                item {
                    CommunitySectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CommunityAccentChip(text = "#${detail.id}")
                                detail.gymName?.let { CommunityAccentChip(text = it, selected = true) }
                            }
                            Text(
                                detail.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = CommunityPalette.TextPrimary
                            )
                            Text(
                                detail.content,
                                color = CommunityPalette.TextSecondary,
                                lineHeight = 22.sp
                            )
                            HorizontalDivider(color = CommunityPalette.Border.copy(alpha = 0.9f))
                            DetailMeta("작성자", detail.authorNickname)
                            DetailMeta("작성일", detail.createdAt)
                            detail.updatedAt?.takeIf { it.isNotBlank() }?.let { updatedAt ->
                                DetailMeta("수정일", updatedAt)
                            }
                            DetailMeta("암장", detail.gymName ?: "미지정")
                            DetailMeta("조회", detail.viewCount.toString())
                            DetailMeta("좋아요", detail.likeCount.toString())
                            DetailMeta("댓글", detail.commentCount.toString(), showDivider = false)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                DdgoPrimaryButton(
                                    text = if (detail.isLiked) "좋아요 취소" else "좋아요",
                                    onClick = onTogglePostLike,
                                    modifier = Modifier.width(148.dp),
                                    leadingIcon = if (detail.isLiked) {
                                        Icons.Default.Favorite
                                    } else {
                                        Icons.Default.FavoriteBorder
                                    }
                                )
                            }
                        }
                    }
                }

                if (detail.videos.isNotEmpty()) {
                    item {
                        CommunitySectionCard {
                            Text(
                                "첨부 영상",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            detail.videos.forEach { video ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = CommunityPalette.AccentSoft,
                                    shape = RoundedCornerShape(22.dp),
                                    border = CommunitySubtleBorder
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp)
                                                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                                        ) {
                                            AndroidView(
                                                factory = { context ->
                                                    PlayerView(context).apply {
                                                        player = ExoPlayer.Builder(context).build().also { player ->
                                                            player.setMediaItem(MediaItem.fromUri(video.playbackUrl))
                                                            player.prepare()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Text(
                                            video.originalFileName,
                                            modifier = Modifier.padding(14.dp),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                item {
                    CommunitySectionCard {
                        Text(
                            "댓글 ${detail.commentCount}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        if (uiState.comments.isEmpty()) {
                            MessageCard(
                                "등록된 댓글이 없습니다.",
                                CommunityPalette.AccentSoft,
                                CommunityPalette.TextSecondary
                            )
                        } else {
                            uiState.comments.forEach { comment ->
                                CommentRow(comment, false, onReply, onEditComment, onDeleteComment, onToggleCommentLike)
                                comment.replies.forEach { reply ->
                                    Spacer(Modifier.height(8.dp))
                                    CommentRow(reply, true, onReply, onEditComment, onDeleteComment, onToggleCommentLike)
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }

        if (detail != null) {
            CommunityCommentInputBar(
                commentInput = uiState.commentInput,
                replyingToNickname = uiState.replyingToNickname,
                editingCommentId = uiState.editingCommentId,
                onCommentChanged = onCommentChanged,
                onSubmitComment = onSubmitComment,
                onCancelComment = onCancelComment,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = CommunityPagePadding,
                        end = CommunityPagePadding,
                        bottom = MainChromeDefaults.ContentBottomPadding
                    )
                    .onSizeChanged { commentInputBarHeightPx.intValue = it.height }
            )
        }
    }
}

@Composable
private fun ComposeScreen(
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
    val isEditMode = (uiState.destination as? CommunityDestination.Compose)?.editingPostId != null
    CommunityPageShell {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = CommunityPagePadding,
                end = CommunityPagePadding,
                top = CommunityPagePadding,
                bottom = MainChromeDefaults.ContentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(CommunitySectionSpacing)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                        }
                        Text(
                            if (isEditMode) "게시글 수정" else "게시글 작성",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DdgoPrimaryButton(
                        text = if (isEditMode) "수정" else "등록",
                        onClick = onSubmit,
                        enabled = !composeState.isSubmitting,
                        isLoading = composeState.isSubmitting,
                        modifier = Modifier.width(108.dp)
                    )
                }
            }

            item {
                CommunityHeroCard(
                    title = "기록을 공유해요",
                    description = "시도 후기, 팁, 암장 정보를 같은 카드 스타일로 정리할 수 있어요.",
                    chips = listOf("제목", "내용", "영상")
                )
            }

            item {
                CommunitySectionCard {
                    Text("제목", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    DdgoTextField(
                        value = composeState.title,
                        onValueChange = onTitleChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "제목을 입력하세요"
                    )
                }
            }

            item {
                CommunitySectionCard {
                    Text("내용", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    DdgoTextField(
                        value = composeState.content,
                        onValueChange = onContentChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        minLines = 8,
                        placeholder = "내용을 입력하세요"
                    )
                }
            }

            item {
                CommunitySectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("암장 태그", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (composeState.gymName != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CommunityAccentChip(text = composeState.gymName, selected = true)
                            DdgoTextButton(
                                text = "해제",
                                onClick = onClearGym,
                                tone = DdgoTextButtonTone.Neutral
                            )
                        }
                    } else {
                        Text(
                            "선택 사항입니다. 챌린지 기록을 참고해 암장을 지정할 수 있어요.",
                            color = CommunityPalette.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    DdgoOutlinedButton(
                        text = "내 챌린지 기록에서 선택",
                        onClick = onOpenChallengeSheet,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                CommunitySectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("첨부 영상", fontWeight = FontWeight.Bold)
                        Text("${composeState.videos.size} / 3", color = CommunityPalette.TextSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    DdgoOutlinedButton(
                        text = "영상 선택",
                        onClick = onPickVideos,
                        enabled = composeState.videos.size < 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    if (composeState.videos.isEmpty()) {
                        Text("첨부된 영상이 없습니다.", color = CommunityPalette.TextSecondary)
                    } else {
                        composeState.videos.forEach { video ->
                            VideoDraftRow(
                                video,
                                { onMoveVideoUp(video.id) },
                                { onMoveVideoDown(video.id) },
                                { onRemoveVideo(video.id) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            composeState.submitError?.let { error ->
                item {
                    MessageCard(
                        error,
                        CommunityPalette.Surface,
                        Color(0xFFD84A4A)
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardListItem(
    post: CommunityPostSummary,
    index: Int,
    onOpenPost: (CommunityPostSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPost(post) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                post.id.toString(),
                modifier = Modifier.width(42.dp),
                textAlign = TextAlign.Center,
                color = CommunityPalette.BrandGray
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    post.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = CommunityPalette.TextPrimary
                )
                Text(
                    boardListMeta(post),
                    color = CommunityPalette.NeutralGray,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.width(66.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    post.authorNickname,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    compactDate(post.createdAt, index),
                    style = MaterialTheme.typography.bodySmall,
                    color = CommunityPalette.NeutralGray
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommunityComment,
    isReply: Boolean,
    onReply: (CommunityComment) -> Unit,
    onEdit: (CommunityComment) -> Unit,
    onDelete: (CommunityComment) -> Unit,
    onToggleLike: (CommunityComment) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isReply) 18.dp else 0.dp),
        color = if (isReply) CommunityPalette.AccentSoft else CommunityPalette.Surface,
        shape = RoundedCornerShape(22.dp),
        border = CommunitySubtleBorder
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (isReply) "ㄴ ${comment.authorNickname}" else comment.authorNickname,
                        fontWeight = FontWeight.SemiBold,
                        color = CommunityPalette.TextPrimary
                    )
                    Text(comment.createdAt, color = CommunityPalette.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!isReply) {
                        DdgoTextButton(
                            text = "답글",
                            onClick = { onReply(comment) },
                            tone = DdgoTextButtonTone.Neutral
                        )
                    }
                    DdgoTextButton(
                        text = if (comment.isLiked) "좋아요 취소" else "좋아요",
                        onClick = { onToggleLike(comment) },
                        tone = DdgoTextButtonTone.Primary
                    )
                    if (comment.isMine) {
                        DdgoTextButton(
                            text = "수정",
                            onClick = { onEdit(comment) },
                            tone = DdgoTextButtonTone.Neutral
                        )
                        DdgoTextButton(
                            text = "삭제",
                            onClick = { onDelete(comment) },
                            tone = DdgoTextButtonTone.Danger
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(comment.content, color = CommunityPalette.TextSecondary, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            CommunityAccentChip(text = "좋아요 ${comment.likeCount}")
        }
    }
}

@Composable
private fun VideoDraftRow(
    video: CommunityVideoDraft,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = CommunityPalette.AccentSoft,
        shape = RoundedCornerShape(22.dp),
        border = CommunitySubtleBorder
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                video.originalFileName,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = CommunityPalette.TextPrimary
            )
            Text(
                "${video.fileSize / 1024 / 1024} MB",
                style = MaterialTheme.typography.bodySmall,
                color = CommunityPalette.TextSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                DdgoTextButton(
                    text = "위로",
                    onClick = onMoveUp,
                    tone = DdgoTextButtonTone.Neutral
                )
                DdgoTextButton(
                    text = "아래로",
                    onClick = onMoveDown,
                    tone = DdgoTextButtonTone.Neutral
                )
                DdgoTextButton(
                    text = "제거",
                    onClick = onRemove,
                    tone = DdgoTextButtonTone.Danger
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChallengeReferenceSheet(
    references: List<CommunityChallengeReference>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (CommunityChallengeReference) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CommunityPalette.Surface) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(CommunityPagePadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("내 챌린지 참고", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            when {
                isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CommunityPalette.AccentStrong)
                    }
                }

                references.isEmpty() -> item {
                    MessageCard("참고할 챌린지 기록이 없습니다.", CommunityPalette.AccentSoft, CommunityPalette.TextSecondary)
                }

                else -> items(references, key = { it.challengeId }) { reference ->
                    CommunitySectionCard(modifier = Modifier.clickable { onSelect(reference) }) {
                        Text(reference.gymName, fontWeight = FontWeight.Bold, color = CommunityPalette.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "색상 ${reference.problemColor} · 난이도 ${reference.gradeLabel ?: "-"}",
                            color = CommunityPalette.TextSecondary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "시도 ${reference.attempts.size}회 · ${reference.createdAt}",
                            color = CommunityPalette.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CommunityPalette.NeutralWhite,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
private fun SortButton(label: String, selected: Boolean, onClick: () -> Unit) {
    DdgoChoiceChip(
        label = label,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    DdgoChoiceChip(
        label = label,
        selected = selected,
        onClick = onClick
    )
}

@Composable
private fun DetailMeta(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, modifier = Modifier.width(72.dp), fontWeight = FontWeight.SemiBold, color = CommunityPalette.TextSecondary)
            Text(value, color = CommunityPalette.TextPrimary)
        }
        if (showDivider) HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.12f))
    }
}

@Composable
private fun CommunityCommentInputBar(
    commentInput: String,
    replyingToNickname: String?,
    editingCommentId: Long?,
    onCommentChanged: (String) -> Unit,
    onSubmitComment: () -> Unit,
    onCancelComment: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommunitySectionCard(modifier = modifier) {
        if (replyingToNickname != null || editingCommentId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    replyingToNickname?.let { "${it}님에게 답글 작성 중" } ?: "댓글 수정 중",
                    color = CommunityPalette.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                DdgoTextButton(
                    text = "취소",
                    onClick = onCancelComment,
                    tone = DdgoTextButtonTone.Neutral
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        DdgoTextField(
            value = commentInput,
            onValueChange = onCommentChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "댓글을 입력하세요"
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DdgoPrimaryButton(
                text = if (editingCommentId != null) "수정" else "등록",
                onClick = onSubmitComment,
                modifier = Modifier.width(104.dp)
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CommunityPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), color = contentColor)
    }
}

private fun compactDate(createdAt: String, fallbackIndex: Int): String {
    val trimmed = createdAt.trim()
    if (trimmed.isBlank()) return (fallbackIndex + 1).toString()
    return if (trimmed.length >= 10) trimmed.substring(0, 10) else trimmed
}

private fun boardListMeta(post: CommunityPostSummary): String {
    return buildList {
        post.gymName?.takeIf { it.isNotBlank() }?.let(::add)
        if (post.isMine) add("내 글")
        add("조회 ${post.viewCount}")
        add("좋아요 ${post.likeCount}")
        add("댓글 ${post.commentCount}")
        if (post.videoCount > 0) add("영상 ${post.videoCount}")
    }.joinToString(" · ")
}
