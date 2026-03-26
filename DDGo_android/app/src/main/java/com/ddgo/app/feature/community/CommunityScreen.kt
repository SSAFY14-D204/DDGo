package com.ddgo.app.feature.community

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import com.ddgo.app.feature.community.components.CommunityChallengeReferenceSheet
import com.ddgo.app.feature.community.components.CommunityComposePage
import com.ddgo.app.feature.community.components.CommunityDetailPage
import com.ddgo.app.feature.community.components.CommunityFeedPage
import com.ddgo.app.feature.community.components.CommunityMessageCard
import com.ddgo.app.feature.community.components.CommunityPagePadding
import com.ddgo.app.feature.community.components.CommunityPageShell
import com.ddgo.app.feature.main.MainChromeDefaults
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import kotlinx.coroutines.delay

@Composable
fun CommunityScreen(
    pendingAnalysisShareRequest: PendingCommunityComposeRequest? = null,
    onPendingAnalysisShareHandled: () -> Unit = {},
    viewModel: CommunityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isPreparingAnalysisShare = uiState.composeState.isPreparingAnalysisShare
    val isComposeSubmitting = uiState.destination is CommunityDestination.Compose &&
        uiState.composeState.isSubmitting
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val uriStrings = uris.mapNotNull { uri ->
            uri.takeIf { persistReadPermission(context.contentResolver, it) }?.toString()
        }
        if (uriStrings.size != uris.size) {
            viewModel.showVideoPermissionIssueMessage()
        }
        viewModel.addSelectedVideos(uriStrings)
    }

    val handleBack = {
        when {
            uiState.isChallengeSheetVisible -> viewModel.closeChallengeReferenceSheet()
            isPreparingAnalysisShare -> Unit
            isComposeSubmitting -> Unit
            else -> viewModel.navigateBack()
        }
    }

    LaunchedEffect(pendingAnalysisShareRequest?.requestId) {
        val request = pendingAnalysisShareRequest ?: return@LaunchedEffect
        onPendingAnalysisShareHandled()
        viewModel.consumePendingAnalysisShare(
            requestId = request.requestId,
            gymId = request.gymId,
            gymName = request.gymName,
            videoUris = request.videos.map { it.videoUri }
        )
    }

    LaunchedEffect(uiState.message) {
        if (uiState.message != null) {
            delay(3000)
            viewModel.clearMessage()
        }
    }

    BackHandler(
        enabled = isPreparingAnalysisShare ||
            uiState.destination != CommunityDestination.Feed ||
            uiState.isChallengeSheetVisible
    ) { handleBack() }

    CommunityPageShell {
        when (uiState.destination) {
            CommunityDestination.Feed -> CommunityFeedPage(
                uiState = uiState,
                onSelectTab = viewModel::selectFeedTab,
                onKeywordChanged = viewModel::onSearchKeywordChanged,
                onSearchSubmit = viewModel::submitSearch,
                onSelectGym = viewModel::selectGymFilter,
                onOpenPost = { viewModel.openPostDetail(it.id) },
                onOpenCompose = viewModel::openCompose,
                onNotificationClick = viewModel::notifyNotificationsUnavailable,
                onLoadMore = viewModel::loadMoreFeed
            )

            is CommunityDestination.Detail -> CommunityDetailPage(
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

            is CommunityDestination.Compose -> CommunityComposePage(
                uiState = uiState,
                onBack = handleBack,
                onTitleChanged = viewModel::updateComposeTitle,
                onContentChanged = viewModel::updateComposeContent,
                onClearGym = viewModel::clearComposeGym,
                onOpenChallengeSheet = {
                    if (!isComposeSubmitting) {
                        viewModel.openChallengeReferenceSheet()
                    }
                },
                onPickVideos = {
                    if (!isComposeSubmitting) {
                        videoPickerLauncher.launch(arrayOf("video/*"))
                    }
                },
                onRemoveVideo = viewModel::removeComposeVideo,
                onMoveVideoUp = { viewModel.moveComposeVideo(it, -1) },
                onMoveVideoDown = { viewModel.moveComposeVideo(it, 1) },
                onSubmit = {
                    if (!isComposeSubmitting) {
                        viewModel.submitPost()
                    }
                }
            )
        }

        uiState.message?.let { message ->
            CommunityMessageCard(
                message = message,
                background = CommunityPalette.TextPrimary,
                contentColor = CommunityPalette.OnAccent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = CommunityPagePadding, vertical = 16.dp)
                    .padding(bottom = MainChromeDefaults.ContentBottomPadding)
            )
        }

        if (isPreparingAnalysisShare) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CommunityPalette.Surface.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CommunityPalette.AccentStrong)
            }
        }
    }

    if (uiState.isChallengeSheetVisible) {
        CommunityChallengeReferenceSheet(
            references = uiState.challengeReferences,
            isLoading = uiState.isLoadingChallengeReferences,
            onDismiss = viewModel::closeChallengeReferenceSheet,
            onSelect = viewModel::selectChallengeReference
        )
    }
}

private fun persistReadPermission(contentResolver: android.content.ContentResolver, uri: Uri): Boolean {
    return runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }.isSuccess
}
