package com.ddgo.app.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityDraftVideoStatus
import com.ddgo.app.domain.model.CommunityPostUpsertRequest
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.domain.model.CommunityVideoUploadFailureException
import com.ddgo.app.domain.usecase.CreateCommunityCommentUseCase
import com.ddgo.app.domain.usecase.CreateCommunityDraftVideoUseCase
import com.ddgo.app.domain.usecase.CreateCommunityPostUseCase
import com.ddgo.app.domain.usecase.DeleteCommunityCommentUseCase
import com.ddgo.app.domain.usecase.DeleteCommunityPostUseCase
import com.ddgo.app.domain.usecase.GetCommunityChallengeReferencesUseCase
import com.ddgo.app.domain.usecase.GetCommunityPostDetailUseCase
import com.ddgo.app.domain.usecase.GetCommunityPostsUseCase
import com.ddgo.app.domain.usecase.ToggleCommunityCommentLikeUseCase
import com.ddgo.app.domain.usecase.ToggleCommunityPostLikeUseCase
import com.ddgo.app.domain.usecase.UpdateCommunityCommentUseCase
import com.ddgo.app.domain.usecase.UpdateCommunityPostUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val getCommunityPostsUseCase: GetCommunityPostsUseCase,
    private val getCommunityPostDetailUseCase: GetCommunityPostDetailUseCase,
    private val createCommunityPostUseCase: CreateCommunityPostUseCase,
    private val updateCommunityPostUseCase: UpdateCommunityPostUseCase,
    private val deleteCommunityPostUseCase: DeleteCommunityPostUseCase,
    private val createCommunityCommentUseCase: CreateCommunityCommentUseCase,
    private val updateCommunityCommentUseCase: UpdateCommunityCommentUseCase,
    private val deleteCommunityCommentUseCase: DeleteCommunityCommentUseCase,
    private val toggleCommunityPostLikeUseCase: ToggleCommunityPostLikeUseCase,
    private val toggleCommunityCommentLikeUseCase: ToggleCommunityCommentLikeUseCase,
    private val getCommunityChallengeReferencesUseCase: GetCommunityChallengeReferencesUseCase,
    private val createCommunityDraftVideoUseCase: CreateCommunityDraftVideoUseCase
) : ViewModel() {

    private companion object {
        const val FeedPageSize = 20
    }

    private val _uiState = MutableStateFlow(CommunityUiState())
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        refreshFeed()
    }

    fun onSearchKeywordChanged(keyword: String) {
        _uiState.update { it.copy(searchKeyword = keyword) }
    }

    fun submitSearch() {
        refreshFeed()
    }

    fun selectFeedTab(tab: CommunityFeedTab) {
        _uiState.update {
            it.copy(
                selectedFeedTab = tab,
                selectedSort = tab.toSort()
            )
        }
        refreshFeed()
    }

    fun selectGymFilter(gymId: Long?, gymName: String?) {
        _uiState.update {
            it.copy(
                selectedGymId = gymId,
                selectedGymName = gymName
            )
        }
        refreshFeed()
    }

    fun refreshFeed() {
        loadFeed(page = 0, append = false)
    }

    fun loadMoreFeed() {
        val state = _uiState.value
        if (state.isLoadingFeed || state.isLoadingMoreFeed || !state.hasMoreFeed || state.posts.isEmpty()) {
            return
        }
        loadFeed(page = state.currentFeedPage + 1, append = true)
    }

    fun showNotificationsNotReadyMessage() {
        emitMessage("알림 기능은 아직 준비 중입니다.")
    }

    fun notifyNotificationsUnavailable() {
        showNotificationsNotReadyMessage()
    }

    fun showVideoPermissionIssueMessage() {
        emitMessage("선택한 영상 중 일부는 접근 권한을 유지하지 못해 제외되었어요.")
    }

    fun openPostDetail(postId: Long) {
        _uiState.update {
            it.withClearedCommentDraft().copy(
                destination = CommunityDestination.Detail(postId),
                detail = null,
                comments = emptyList(),
                isLoadingDetail = true,
                detailError = null
            )
        }
        loadDetail(postId)
    }

    fun openCompose() {
        _uiState.update {
            it.copy(
                destination = CommunityDestination.Compose(),
                composeState = CommunityComposeState()
            )
        }
    }

    fun openEdit() {
        val detail = _uiState.value.detail ?: return
        val draftVideos = detail.videos.mapIndexed { index, video ->
            CommunityVideoDraft(
                id = video.objectKey,
                objectKey = video.objectKey,
                playbackUrl = video.playbackUrl,
                originalFileName = video.originalFileName,
                contentType = video.contentType,
                fileSize = video.fileSize,
                durationMs = video.durationMs,
                sortOrder = index,
                status = CommunityDraftVideoStatus.UPLOADED
            )
        }
        _uiState.update {
            it.copy(
                destination = CommunityDestination.Compose(editingPostId = detail.id),
                composeState = CommunityComposeState(
                    title = detail.title,
                    content = detail.content,
                    gymId = detail.gymId,
                    gymName = detail.gymName,
                    videos = draftVideos
                )
            )
        }
    }

    fun navigateBack() {
        when (val destination = _uiState.value.destination) {
            CommunityDestination.Feed -> Unit
            is CommunityDestination.Detail -> {
                _uiState.update {
                    it.withClearedCommentDraft().copy(
                        destination = CommunityDestination.Feed,
                        detail = null,
                        comments = emptyList(),
                        detailError = null
                    )
                }
            }

            is CommunityDestination.Compose -> {
                val nextDestination = if (destination.editingPostId != null) {
                    CommunityDestination.Detail(destination.editingPostId)
                } else {
                    CommunityDestination.Feed
                }
                _uiState.update {
                    it.copy(
                        destination = nextDestination,
                        isChallengeSheetVisible = false
                    )
                }
            }
        }
    }

    fun updateComposeTitle(value: String) {
        if (_uiState.value.composeState.isSubmitting) return
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    title = value,
                    submitError = null
                )
            )
        }
    }

    fun updateComposeContent(value: String) {
        if (_uiState.value.composeState.isSubmitting) return
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    content = value,
                    submitError = null
                )
            )
        }
    }

    fun clearComposeGym() {
        if (_uiState.value.composeState.isSubmitting) return
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    gymId = null,
                    gymName = null,
                    submitError = null
                )
            )
        }
    }

    fun addSelectedVideos(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        if (_uiState.value.composeState.isSubmitting) return
        val remainingSlots = 3 - _uiState.value.composeState.videos.size
        if (remainingSlots <= 0) {
            emitMessage("영상은 최대 3개까지 첨부할 수 있어요.")
            return
        }

        viewModelScope.launch {
            uriStrings.take(remainingSlots).forEachIndexed { index, uri ->
                createCommunityDraftVideoUseCase(uri, _uiState.value.composeState.videos.size + index)
                    .onSuccess { draft ->
                        _uiState.update {
                            it.copy(
                                composeState = it.composeState.copy(
                                    videos = (it.composeState.videos + draft).mapIndexed { sort, item ->
                                        item.copy(sortOrder = sort)
                                    },
                                    submitError = null
                                )
                            )
                        }
                    }
                    .onFailure { throwable ->
                        emitMessage(throwable.orNetworkMessage("선택한 영상을 준비하지 못했어요."))
                    }
            }
        }
    }

    fun removeComposeVideo(videoId: String) {
        if (_uiState.value.composeState.isSubmitting) return
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    videos = it.composeState.videos
                        .filterNot { item -> item.id == videoId }
                        .mapIndexed { index, item -> item.copy(sortOrder = index) },
                    submitError = null
                )
            )
        }
    }

    fun moveComposeVideo(videoId: String, direction: Int) {
        if (_uiState.value.composeState.isSubmitting) return
        val videos = _uiState.value.composeState.videos.toMutableList()
        val currentIndex = videos.indexOfFirst { it.id == videoId }
        if (currentIndex == -1) return
        val targetIndex = currentIndex + direction
        if (targetIndex !in videos.indices) return
        val current = videos[currentIndex]
        videos[currentIndex] = videos[targetIndex]
        videos[targetIndex] = current
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    videos = videos.mapIndexed { index, video -> video.copy(sortOrder = index) },
                    submitError = null
                )
            )
        }
    }

    fun submitPost() {
        if (_uiState.value.composeState.isSubmitting) return
        val composeState = _uiState.value.composeState
        val title = composeState.title.trim()
        val content = composeState.content.trim()
        if (title.isBlank() || content.isBlank()) {
            emitMessage("제목과 내용을 입력해 주세요.")
            return
        }

        val preparedVideos = composeState.videos.map { draft ->
            if (draft.objectKey == null) {
                draft.copy(
                    status = CommunityDraftVideoStatus.UPLOADING,
                    errorMessage = null
                )
            } else {
                draft.copy(errorMessage = null)
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    composeState = it.composeState.copy(
                        videos = preparedVideos,
                        isSubmitting = true,
                        submitError = null
                    )
                )
            }

            val request = CommunityPostUpsertRequest(
                title = title,
                content = content,
                gymId = composeState.gymId,
                videos = preparedVideos
            )

            val editingPostId = (_uiState.value.destination as? CommunityDestination.Compose)?.editingPostId
            val result = if (editingPostId == null) {
                createCommunityPostUseCase(request)
            } else {
                updateCommunityPostUseCase(editingPostId, request)
            }

            result.onSuccess { detail ->
                _uiState.update {
                    it.withClearedCommentDraft().copy(
                        destination = CommunityDestination.Detail(detail.id),
                        detail = detail,
                        comments = detail.comments,
                        composeState = CommunityComposeState(),
                        isLoadingDetail = false,
                        detailError = null
                    )
                }
                refreshFeed()
            }.onFailure { throwable ->
                val message = throwable.orNetworkMessage("게시글을 저장하지 못했어요.")
                _uiState.update {
                    it.copy(
                        composeState = it.composeState.copy(
                            isSubmitting = false,
                            submitError = message,
                            videos = resolveComposeFailureVideos(
                                videos = it.composeState.videos,
                                throwable = throwable,
                                defaultMessage = message
                            )
                        )
                    )
                }
            }
        }
    }

    fun deleteCurrentPost() {
        val postId = _uiState.value.detail?.id ?: return
        viewModelScope.launch {
            deleteCommunityPostUseCase(postId)
                .onSuccess {
                    _uiState.update {
                        it.withClearedCommentDraft().copy(
                            destination = CommunityDestination.Feed,
                            detail = null,
                            comments = emptyList(),
                            detailError = null
                        )
                    }
                    refreshFeed()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.orNetworkMessage("게시글을 삭제하지 못했어요."))
                }
        }
    }

    fun updateCommentInput(value: String) {
        _uiState.update { it.copy(commentInput = value) }
    }

    fun beginReply(comment: CommunityComment) {
        _uiState.update {
            it.copy(
                replyingToCommentId = comment.id,
                replyingToNickname = comment.authorNickname,
                editingCommentId = null,
                commentInput = ""
            )
        }
    }

    fun beginCommentEdit(comment: CommunityComment) {
        _uiState.update {
            it.copy(
                editingCommentId = comment.id,
                replyingToCommentId = null,
                replyingToNickname = null,
                commentInput = comment.content
            )
        }
    }

    fun cancelCommentDraft() {
        _uiState.update { it.withClearedCommentDraft() }
    }

    fun submitComment() {
        val postId = _uiState.value.detail?.id ?: return
        val content = _uiState.value.commentInput.trim()
        if (content.isBlank()) {
            emitMessage("댓글 내용을 입력해 주세요.")
            return
        }

        viewModelScope.launch {
            val editingCommentId = _uiState.value.editingCommentId
            val result = if (editingCommentId != null) {
                updateCommunityCommentUseCase(postId, editingCommentId, content)
            } else {
                createCommunityCommentUseCase(postId, content, _uiState.value.replyingToCommentId)
            }

            result.onSuccess { comments ->
                updateComments(postId, comments)
                cancelCommentDraft()
            }.onFailure { throwable ->
                emitMessage(throwable.orNetworkMessage("댓글을 저장하지 못했어요."))
            }
        }
    }

    fun deleteComment(commentId: Long) {
        val postId = _uiState.value.detail?.id ?: return
        viewModelScope.launch {
            deleteCommunityCommentUseCase(postId, commentId)
                .onSuccess { comments ->
                    updateComments(postId, comments)
                    cancelCommentDraft()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.orNetworkMessage("댓글을 삭제하지 못했어요."))
                }
        }
    }

    fun togglePostLike() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            toggleCommunityPostLikeUseCase(detail.id, !detail.isLiked)
                .onSuccess { result ->
                    applyPostLikeResult(result.targetId, result.liked, result.likeCount)
                }
                .onFailure { throwable ->
                    emitMessage(throwable.orNetworkMessage("게시글 좋아요를 반영하지 못했어요."))
                }
        }
    }

    fun toggleCommentLike(comment: CommunityComment) {
        viewModelScope.launch {
            toggleCommunityCommentLikeUseCase(comment.id, !comment.isLiked)
                .onSuccess { result ->
                    applyCommentLikeResult(
                        postId = comment.postId,
                        commentId = result.targetId,
                        liked = result.liked,
                        likeCount = result.likeCount
                    )
                }
                .onFailure { throwable ->
                    emitMessage(throwable.orNetworkMessage("댓글 좋아요를 반영하지 못했어요."))
                }
        }
    }

    fun openChallengeReferenceSheet() {
        if (_uiState.value.composeState.isSubmitting) return
        _uiState.update { it.copy(isChallengeSheetVisible = true) }
        if (_uiState.value.challengeReferences.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChallengeReferences = true) }
            getCommunityChallengeReferencesUseCase()
                .onSuccess { references ->
                    _uiState.update {
                        it.copy(
                            challengeReferences = references,
                            isLoadingChallengeReferences = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoadingChallengeReferences = false) }
                    emitMessage(throwable.orNetworkMessage("챌린지 참고 목록을 불러오지 못했어요."))
                }
        }
    }

    fun closeChallengeReferenceSheet() {
        _uiState.update { it.copy(isChallengeSheetVisible = false) }
    }

    fun selectChallengeReference(reference: CommunityChallengeReference) {
        if (_uiState.value.composeState.isSubmitting) return
        if (reference.gymId == null) {
            _uiState.update { it.copy(isChallengeSheetVisible = false) }
            emitMessage("이 챌린지에서 연결할 암장 정보를 찾지 못했어요.")
            return
        }

        _uiState.update {
            it.copy(
                isChallengeSheetVisible = false,
                composeState = it.composeState.copy(
                    gymId = reference.gymId,
                    gymName = reference.gymName,
                    submitError = null
                )
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadFeed(page: Int, append: Boolean) {
        val snapshot = _uiState.value
        if (append && (snapshot.isLoadingFeed || snapshot.isLoadingMoreFeed || !snapshot.hasMoreFeed)) {
            return
        }

        viewModelScope.launch {
            if (append) {
                _uiState.update { it.copy(isLoadingMoreFeed = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingFeed = true,
                        isLoadingMoreFeed = false,
                        feedError = null
                    )
                }
            }

            getCommunityPostsUseCase(
                page = page,
                size = FeedPageSize,
                keyword = snapshot.searchKeyword,
                sort = snapshot.selectedSort,
                gymId = snapshot.selectedGymId
            ).onSuccess { feedPage ->
                _uiState.update { state ->
                    val mergedPosts = if (append) {
                        (state.posts + feedPage.items).distinctBy { it.id }
                    } else {
                        feedPage.items
                    }
                    state.copy(
                        posts = mergedPosts,
                        availableGyms = buildGymOptions(mergedPosts),
                        isLoadingFeed = false,
                        isLoadingMoreFeed = false,
                        currentFeedPage = feedPage.page,
                        hasMoreFeed = feedPage.hasNext,
                        feedError = null
                    )
                }
            }.onFailure { throwable ->
                if (append) {
                    _uiState.update { it.copy(isLoadingMoreFeed = false) }
                    emitMessage(throwable.orNetworkMessage("게시글을 더 불러오지 못했어요."))
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingFeed = false,
                            isLoadingMoreFeed = false,
                            feedError = throwable.orNetworkMessage("커뮤니티 게시글을 불러오지 못했어요.")
                        )
                    }
                }
            }
        }
    }

    private fun loadDetail(postId: Long) {
        viewModelScope.launch {
            getCommunityPostDetailUseCase(postId)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            comments = detail.comments,
                            isLoadingDetail = false,
                            detailError = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            detailError = throwable.orNetworkMessage("게시글 상세를 불러오지 못했어요.")
                        )
                    }
                }
        }
    }

    private fun updateComments(postId: Long, comments: List<CommunityComment>) {
        val commentCount = countComments(comments)
        _uiState.update { state ->
            state.copy(
                comments = comments,
                detail = state.detail?.takeIf { detail -> detail.id == postId }?.copy(
                    commentCount = commentCount,
                    comments = comments
                ),
                posts = state.posts.map { post ->
                    if (post.id == postId) post.copy(commentCount = commentCount) else post
                }
            )
        }
    }

    private fun applyPostLikeResult(postId: Long, liked: Boolean, likeCount: Int) {
        _uiState.update { state ->
            state.copy(
                detail = state.detail?.takeIf { it.id == postId }?.copy(
                    isLiked = liked,
                    likeCount = likeCount
                ),
                posts = state.posts.map { post ->
                    if (post.id == postId) {
                        post.copy(
                            isLiked = liked,
                            likeCount = likeCount
                        )
                    } else {
                        post
                    }
                }
            )
        }
    }

    private fun applyCommentLikeResult(
        postId: Long,
        commentId: Long,
        liked: Boolean,
        likeCount: Int
    ) {
        val updatedComments = _uiState.value.comments.updateCommentLike(
            commentId = commentId,
            liked = liked,
            likeCount = likeCount
        )
        _uiState.update { state ->
            state.copy(
                comments = updatedComments,
                detail = state.detail?.takeIf { it.id == postId }?.copy(comments = updatedComments)
            )
        }
    }

    private fun resolveComposeFailureVideos(
        videos: List<CommunityVideoDraft>,
        throwable: Throwable,
        defaultMessage: String
    ): List<CommunityVideoDraft> {
        return when (throwable) {
            is CommunityVideoUploadFailureException -> {
                videos.map { draft ->
                    when {
                        draft.id == throwable.draftId -> draft.copy(
                            status = CommunityDraftVideoStatus.FAILED,
                            errorMessage = throwable.orNetworkMessage(defaultMessage)
                        )

                        draft.objectKey == null && draft.status == CommunityDraftVideoStatus.UPLOADING -> draft.copy(
                            status = CommunityDraftVideoStatus.LOCAL,
                            errorMessage = null
                        )

                        else -> draft
                    }
                }
            }

            else -> {
                videos.map { draft ->
                    if (draft.objectKey == null && draft.status == CommunityDraftVideoStatus.UPLOADING) {
                        draft.copy(
                            status = CommunityDraftVideoStatus.FAILED,
                            errorMessage = defaultMessage
                        )
                    } else {
                        draft
                    }
                }
            }
        }
    }

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun Throwable.orNetworkMessage(fallback: String): String {
        return toUserFacingNetworkMessageOrNull() ?: message ?: fallback
    }

    private fun buildGymOptions(posts: List<com.ddgo.app.domain.model.CommunityPostSummary>): List<Pair<Long, String>> {
        return posts.mapNotNull { post ->
            val gymId = post.gymId ?: return@mapNotNull null
            val gymName = post.gymName ?: return@mapNotNull null
            gymId to gymName
        }.distinctBy { it.first }
            .sortedBy { it.second }
    }

    private fun countComments(comments: List<CommunityComment>): Int {
        return comments.sumOf { comment ->
            1 + countComments(comment.replies)
        }
    }

    private fun List<CommunityComment>.updateCommentLike(
        commentId: Long,
        liked: Boolean,
        likeCount: Int
    ): List<CommunityComment> {
        return map { comment ->
            if (comment.id == commentId) {
                comment.copy(
                    isLiked = liked,
                    likeCount = likeCount
                )
            } else {
                comment.copy(
                    replies = comment.replies.updateCommentLike(
                        commentId = commentId,
                        liked = liked,
                        likeCount = likeCount
                    )
                )
            }
        }
    }

    private fun CommunityUiState.withClearedCommentDraft(): CommunityUiState {
        return copy(
            commentInput = "",
            replyingToCommentId = null,
            replyingToNickname = null,
            editingCommentId = null
        )
    }

    private fun CommunityFeedTab.toSort(): CommunitySort {
        return when (this) {
            CommunityFeedTab.Recommended -> CommunitySort.LATEST
            CommunityFeedTab.Popular -> CommunitySort.POPULAR
            CommunityFeedTab.Latest -> CommunitySort.LATEST
        }
    }
}
