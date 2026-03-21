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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoDraft

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommunityPalette.NeutralBackground)
    ) {
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
                    .navigationBarsPadding()
                    .padding(16.dp)
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
    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FilledTonalButton(onClick = onOpenCompose, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("\uae00\uc4f0\uae30")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                BoardCard {
                    Text(
                        "\ucee4\ubba4\ub2c8\ud2f0 \uac8c\uc2dc\ud310",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "\uce74\ub4dc \ud53c\ub4dc \ub300\uc2e0 \uc804\ud1b5\uc801\uc778 \uac8c\uc2dc\ud310 \ud615\uc2dd\uc73c\ub85c \uae00\uc744 \ud655\uc778\ud560 \uc218 \uc788\uc5b4\uc694.",
                        color = CommunityPalette.BrandGray
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            item {
                BoardCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = uiState.searchKeyword,
                            onValueChange = onKeywordChanged,
                            modifier = Modifier.weight(1f),
                            label = { Text("\uac80\uc0c9") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true
                        )
                        OutlinedButton(onClick = onSearchSubmit) { Text("\uc870\ud68c") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortButton("\ucd5c\uc2e0\uc21c", uiState.selectedSort == CommunitySort.LATEST) { onSortSelected(CommunitySort.LATEST) }
                        SortButton("\uc778\uae30\uc21c", uiState.selectedSort == CommunitySort.POPULAR) { onSortSelected(CommunitySort.POPULAR) }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill("\uc804\uccb4 \uc554\uc7a5", uiState.selectedGymId == null) { onSelectGym(null, null) }
                        uiState.availableGyms.forEach { (gymId, gymName) ->
                            FilterPill(gymName, uiState.selectedGymId == gymId) { onSelectGym(gymId, gymName) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
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
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("\ubc88\ud638", modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("\uc81c\ubaa9", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("\uc791\uc131\uc790", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
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
                            message = "\ub4f1\ub85d\ub41c \uac8c\uc2dc\uae00\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.",
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
                            BoardListItem(post = post, index = index, onOpenPost = onOpenPost)
                            if (index != uiState.posts.lastIndex) {
                                HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.12f))
                            }
                        }
                    }
                }
            }
        }
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
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\ub4a4\ub85c \uac00\uae30")
                    }
                    Text("\uac8c\uc2dc\uae00 \uc0c1\uc138", fontWeight = FontWeight.SemiBold)
                }
                if (detail?.isMine == true) {
                    Row {
                        TextButton(onClick = onEditPost) { Text("\uc218\uc815") }
                        TextButton(onClick = onDeletePost) { Text("\uc0ad\uc81c", color = Color(0xFFD84A4A)) }
                    }
                }
            }
        },
        bottomBar = {
            if (detail != null) {
                BoardCard(
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (uiState.replyingToNickname != null || uiState.editingCommentId != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                uiState.replyingToNickname?.let { "${it}\ub2d8\uc5d0\uac8c \ub2f5\uae00 \uc791\uc131 \uc911" } ?: "\ub313\uae00 \uc218\uc815 \uc911",
                                color = CommunityPalette.NeutralGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = onCancelComment) { Text("\ucde8\uc18c") }
                        }
                    }
                    OutlinedTextField(
                        value = uiState.commentInput,
                        onValueChange = onCommentChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("\ub313\uae00\uc744 \uc785\ub825\ud558\uc138\uc694") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(onClick = onSubmitComment) {
                            Text(if (uiState.editingCommentId != null) "\uc218\uc815" else "\ub4f1\ub85d")
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            uiState.isLoadingDetail -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CommunityPalette.BrandBlue)
            }

            uiState.detailError != null -> Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                MessageCard(uiState.detailError, Color(0xFFFFEEEE), Color(0xFFD84A4A))
            }

            detail != null -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    BoardCard(padding = 0.dp) {
                        Column {
                            Text(detail.title, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                            DetailMeta("\uc791\uc131\uc790", detail.authorNickname)
                            DetailMeta("\uc791\uc131\uc77c", detail.createdAt)
                            DetailMeta("\uc554\uc7a5", detail.gymName ?: "\ubbf8\uc9c0\uc815")
                            DetailMeta("\uc870\ud68c", detail.viewCount.toString())
                            DetailMeta("\uc88b\uc544\uc694", detail.likeCount.toString())
                            DetailMeta("\ub313\uae00", detail.commentCount.toString(), showDivider = false)
                            HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                            Text(detail.content, modifier = Modifier.padding(16.dp), color = CommunityPalette.BrandGray, lineHeight = 22.sp)
                            HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.18f))
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                                FilledTonalButton(onClick = onTogglePostLike) {
                                    Icon(if (detail.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (detail.isLiked) "\uc88b\uc544\uc694 \ucde8\uc18c" else "\uc88b\uc544\uc694")
                                }
                            }
                        }
                    }
                }

                if (detail.videos.isNotEmpty()) {
                    item {
                        BoardCard {
                            Text("\ucca8\ubd80 \uc601\uc0c1", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            detail.videos.forEach { video ->
                                Surface(
                                    color = CommunityPalette.NeutralBackground,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.16f))
                                ) {
                                    Column {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp)
                                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
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
                                        Text(video.originalFileName, modifier = Modifier.padding(14.dp), fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }

                item {
                    BoardCard {
                        Text("\ub313\uae00 ${detail.commentCount}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        if (uiState.comments.isEmpty()) {
                            MessageCard("\ub4f1\ub85d\ub41c \ub313\uae00\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.", CommunityPalette.NeutralBackground, CommunityPalette.BrandGray)
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
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "\ub4a4\ub85c \uac00\uae30")
                    }
                    Text(if (isEditMode) "\uac8c\uc2dc\uae00 \uc218\uc815" else "\uac8c\uc2dc\uae00 \uc791\uc131", fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(onClick = onSubmit, enabled = !composeState.isSubmitting) {
                    Text(if (composeState.isSubmitting) "\uc800\uc7a5 \uc911..." else "\uc800\uc7a5")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BoardCard {
                    Text("\uc81c\ubaa9", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(composeState.title, onTitleChanged, modifier = Modifier.fillMaxWidth(), placeholder = { Text("\uc81c\ubaa9\uc744 \uc785\ub825\ud558\uc138\uc694") })
                }
            }
            item {
                BoardCard {
                    Text("\ub0b4\uc6a9", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(composeState.content, onContentChanged, modifier = Modifier.fillMaxWidth(), minLines = 8, placeholder = { Text("\ub0b4\uc6a9\uc744 \uc785\ub825\ud558\uc138\uc694") })
                }
            }
            item {
                BoardCard {
                    Text("\uc554\uc7a5 \ud0dc\uadf8", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (composeState.gymName != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(onClick = {}, label = { Text(composeState.gymName) }, enabled = false)
                            TextButton(onClick = onClearGym) { Text("\ud574\uc81c") }
                        }
                    } else {
                        Text(
                            "\uc120\ud0dd \uc0ac\ud56d\uc785\ub2c8\ub2e4. \ucc4c\ub9b0\uc9c0 \uae30\ub85d\uc744 \ucc38\uace0\ud574 \uc554\uc7a5\uc744 \uc9c0\uc815\ud560 \uc218 \uc788\uc5b4\uc694.",
                            color = CommunityPalette.BrandGray
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenChallengeSheet) { Text("\ub0b4 \ucc4c\ub9b0\uc9c0 \uae30\ub85d\uc5d0\uc11c \uc120\ud0dd") }
                }
            }
            item {
                BoardCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("\ucca8\ubd80 \uc601\uc0c1", fontWeight = FontWeight.Bold)
                        Text("${composeState.videos.size} / 3", color = CommunityPalette.BrandGray)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onPickVideos, enabled = composeState.videos.size < 3) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("\uc601\uc0c1 \uc120\ud0dd")
                    }
                    Spacer(Modifier.height(10.dp))
                    if (composeState.videos.isEmpty()) {
                        Text("\ucca8\ubd80\ub41c \uc601\uc0c1\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.", color = CommunityPalette.BrandGray)
                    } else {
                        composeState.videos.forEach { video ->
                            VideoDraftRow(video, { onMoveVideoUp(video.id) }, { onMoveVideoDown(video.id) }, { onRemoveVideo(video.id) })
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            composeState.submitError?.let { error ->
                item { MessageCard(error, Color(0xFFFFEEEE), Color(0xFFD84A4A)) }
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
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(post.id.toString(), modifier = Modifier.width(48.dp), textAlign = TextAlign.Center, color = CommunityPalette.BrandGray)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(post.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    post.gymName?.let { AssistChip(onClick = {}, enabled = false, label = { Text(it) }) }
                }
                Text(post.contentPreview, color = CommunityPalette.BrandGray, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(
                    "\uc601\uc0c1 ${post.videoCount} \u00b7 \uc88b\uc544\uc694 ${post.likeCount} \u00b7 \ub313\uae00 ${post.commentCount}",
                    color = CommunityPalette.NeutralGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(modifier = Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(post.authorNickname, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(compactDate(post.createdAt, index), style = MaterialTheme.typography.bodySmall, color = CommunityPalette.NeutralGray)
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
        color = if (isReply) CommunityPalette.LightBlue.copy(alpha = 0.08f) else CommunityPalette.NeutralWhite,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.16f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(if (isReply) "\u3134 ${comment.authorNickname}" else comment.authorNickname, fontWeight = FontWeight.SemiBold)
                    Text(comment.createdAt, color = CommunityPalette.NeutralGray, style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    if (!isReply) TextButton(onClick = { onReply(comment) }) { Text("\ub2f5\uae00") }
                    TextButton(onClick = { onToggleLike(comment) }) { Text(if (comment.isLiked) "\uc88b\uc544\uc694 \ucde8\uc18c" else "\uc88b\uc544\uc694") }
                    if (comment.isMine) {
                        TextButton(onClick = { onEdit(comment) }) { Text("\uc218\uc815") }
                        TextButton(onClick = { onDelete(comment) }) { Text("\uc0ad\uc81c", color = Color(0xFFD84A4A)) }
                    }
                }
            }
            Text(comment.content, color = CommunityPalette.BrandGray, lineHeight = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text("\uc88b\uc544\uc694 ${comment.likeCount}", color = CommunityPalette.NeutralGray, style = MaterialTheme.typography.bodySmall)
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
        color = CommunityPalette.NeutralBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.16f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(video.originalFileName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${video.fileSize / 1024 / 1024} MB", style = MaterialTheme.typography.bodySmall, color = CommunityPalette.NeutralGray)
            Row {
                TextButton(onClick = onMoveUp) { Text("\uc704\ub85c") }
                TextButton(onClick = onMoveDown) { Text("\uc544\ub798\ub85c") }
                TextButton(onClick = onRemove) { Text("\uc81c\uac70", color = Color(0xFFD84A4A)) }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CommunityPalette.NeutralWhite) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("\ub0b4 \ucc4c\ub9b0\uc9c0 \ucc38\uace0", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            when {
                isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CommunityPalette.BrandBlue)
                    }
                }

                references.isEmpty() -> item {
                    MessageCard("\ucc38\uace0\ud560 \ucc4c\ub9b0\uc9c0 \uae30\ub85d\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.", CommunityPalette.NeutralBackground, CommunityPalette.BrandGray)
                }

                else -> items(references, key = { it.challengeId }) { reference ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(reference) },
                        color = CommunityPalette.NeutralBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CommunityPalette.NeutralGray.copy(alpha = 0.16f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(reference.gymName, fontWeight = FontWeight.Bold)
                            Text(
                                "\uc0c9\uc0c1 ${reference.problemColor} \u00b7 \ub09c\uc774\ub3c4 ${reference.gradeLabel ?: "-"}",
                                color = CommunityPalette.BrandGray
                            )
                            Text(
                                "\uc2dc\ub3c4 ${reference.attempts.size}\ud68c \u00b7 ${reference.createdAt}",
                                color = CommunityPalette.NeutralGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) CommunityPalette.BrandBlue.copy(alpha = 0.12f) else CommunityPalette.NeutralWhite,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) CommunityPalette.BrandBlue else CommunityPalette.NeutralGray.copy(alpha = 0.22f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (selected) CommunityPalette.BrandBlue else CommunityPalette.BrandGray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) CommunityPalette.BrandGreen.copy(alpha = 0.18f) else CommunityPalette.NeutralWhite,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) CommunityPalette.BrandGreen.copy(alpha = 0.7f) else CommunityPalette.NeutralGray.copy(alpha = 0.18f))
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DetailMeta(label: String, value: String, showDivider: Boolean = true) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, modifier = Modifier.width(72.dp), fontWeight = FontWeight.SemiBold, color = CommunityPalette.BrandGray)
            Text(value)
        }
        if (showDivider) HorizontalDivider(color = CommunityPalette.NeutralGray.copy(alpha = 0.12f))
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), color = contentColor)
    }
}

private fun compactDate(createdAt: String, fallbackIndex: Int): String {
    val trimmed = createdAt.trim()
    if (trimmed.isBlank()) return (fallbackIndex + 1).toString()
    return if (trimmed.length >= 10) trimmed.substring(0, 10) else trimmed
}
