package com.ddgo.app.feature.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityDraftVideoStatus
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostUpsertRequest
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.domain.usecase.CreateCommunityCommentUseCase
import com.ddgo.app.domain.usecase.CreateCommunityDraftVideoUseCase
import com.ddgo.app.domain.usecase.CreateCommunityPostUseCase
import com.ddgo.app.domain.usecase.DeleteCommunityCommentUseCase
import com.ddgo.app.domain.usecase.DeleteCommunityPostUseCase
import com.ddgo.app.domain.usecase.GetCommunityChallengeReferencesUseCase
import com.ddgo.app.domain.usecase.GetCommunityCommentsUseCase
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
    private val getCommunityCommentsUseCase: GetCommunityCommentsUseCase,
    private val createCommunityCommentUseCase: CreateCommunityCommentUseCase,
    private val updateCommunityCommentUseCase: UpdateCommunityCommentUseCase,
    private val deleteCommunityCommentUseCase: DeleteCommunityCommentUseCase,
    private val toggleCommunityPostLikeUseCase: ToggleCommunityPostLikeUseCase,
    private val toggleCommunityCommentLikeUseCase: ToggleCommunityCommentLikeUseCase,
    private val getCommunityChallengeReferencesUseCase: GetCommunityChallengeReferencesUseCase,
    private val createCommunityDraftVideoUseCase: CreateCommunityDraftVideoUseCase
) : ViewModel() {

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

    fun selectSort(sort: CommunitySort) {
        _uiState.update { it.copy(selectedSort = sort) }
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFeed = true, feedError = null) }
            getCommunityPostsUseCase(
                page = 0,
                size = 20,
                keyword = _uiState.value.searchKeyword,
                sort = _uiState.value.selectedSort,
                gymId = _uiState.value.selectedGymId
            ).onSuccess { page ->
                _uiState.update {
                    it.copy(
                        posts = page.items,
                        availableGyms = buildGymOptions(page.items, it.availableGyms),
                        isLoadingFeed = false
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingFeed = false,
                        feedError = throwable.message ?: "커뮤니티 피드를 불러오지 못했어요."
                    )
                }
            }
        }
    }

    fun openPostDetail(postId: Long) {
        _uiState.update {
            it.copy(
                destination = CommunityDestination.Detail(postId),
                isLoadingDetail = true,
                detailError = null,
                comments = emptyList()
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
                _uiState.update { it.copy(destination = CommunityDestination.Feed, detail = null, comments = emptyList()) }
            }

            is CommunityDestination.Compose -> {
                val nextDestination = if (destination.editingPostId != null) {
                    CommunityDestination.Detail(destination.editingPostId)
                } else {
                    CommunityDestination.Feed
                }
                _uiState.update { it.copy(destination = nextDestination, isChallengeSheetVisible = false) }
            }
        }
    }

    fun updateComposeTitle(value: String) {
        _uiState.update { it.copy(composeState = it.composeState.copy(title = value)) }
    }

    fun updateComposeContent(value: String) {
        _uiState.update { it.copy(composeState = it.composeState.copy(content = value)) }
    }

    fun clearComposeGym() {
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    gymId = null,
                    gymName = null
                )
            )
        }
    }

    fun addSelectedVideos(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
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
                                    }
                                )
                            )
                        }
                    }
                    .onFailure { throwable ->
                        emitMessage(throwable.message ?: "선택한 영상을 준비하지 못했어요.")
                    }
            }
        }
    }

    fun removeComposeVideo(videoId: String) {
        _uiState.update {
            it.copy(
                composeState = it.composeState.copy(
                    videos = it.composeState.videos
                        .filterNot { item -> item.id == videoId }
                        .mapIndexed { index, item -> item.copy(sortOrder = index) }
                )
            )
        }
    }

    fun moveComposeVideo(videoId: String, direction: Int) {
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
                    videos = videos.mapIndexed { index, video -> video.copy(sortOrder = index) }
                )
            )
        }
    }

    fun submitPost() {
        val composeState = _uiState.value.composeState
        val title = composeState.title.trim()
        val content = composeState.content.trim()
        if (title.isBlank() || content.isBlank()) {
            emitMessage("제목과 내용을 입력해 주세요.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(composeState = it.composeState.copy(isSubmitting = true, submitError = null))
            }

            val request = CommunityPostUpsertRequest(
                title = title,
                content = content,
                gymId = composeState.gymId,
                videos = composeState.videos
            )

            val editingPostId = (_uiState.value.destination as? CommunityDestination.Compose)?.editingPostId
            val result = if (editingPostId == null) {
                createCommunityPostUseCase(request)
            } else {
                updateCommunityPostUseCase(editingPostId, request)
            }

            result.onSuccess { detail ->
                _uiState.update {
                    it.copy(
                        destination = CommunityDestination.Detail(detail.id),
                        detail = detail,
                        composeState = CommunityComposeState(),
                        isLoadingDetail = false
                    )
                }
                refreshFeed()
                loadComments(detail.id)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        composeState = it.composeState.copy(
                            isSubmitting = false,
                            submitError = throwable.message ?: "게시글을 저장하지 못했어요."
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
                        it.copy(
                            destination = CommunityDestination.Feed,
                            detail = null,
                            comments = emptyList()
                        )
                    }
                    refreshFeed()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "게시글을 삭제하지 못했어요.")
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
        _uiState.update {
            it.copy(
                replyingToCommentId = null,
                replyingToNickname = null,
                editingCommentId = null,
                commentInput = ""
            )
        }
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
                emitMessage(throwable.message ?: "댓글을 저장하지 못했어요.")
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
                    emitMessage(throwable.message ?: "댓글을 삭제하지 못했어요.")
                }
        }
    }

    fun togglePostLike() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            toggleCommunityPostLikeUseCase(detail.id, !detail.isLiked)
                .onSuccess {
                    loadDetail(detail.id)
                    refreshFeed()
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "좋아요를 반영하지 못했어요.")
                }
        }
    }

    fun toggleCommentLike(comment: CommunityComment) {
        viewModelScope.launch {
            toggleCommunityCommentLikeUseCase(comment.id, !comment.isLiked)
                .onSuccess {
                    loadComments(comment.postId)
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "댓글 좋아요를 반영하지 못했어요.")
                }
        }
    }

    fun openChallengeReferenceSheet() {
        _uiState.update { it.copy(isChallengeSheetVisible = true) }
        if (_uiState.value.challengeReferences.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChallengeReferences = true) }
            getCommunityChallengeReferencesUseCase()
                .onSuccess { references ->
                    _uiState.update {
                        it.copy(
                            challengeReferences = references,
                            isLoadingChallengeReferences = false,
                            availableGyms = buildGymOptions(it.posts, it.availableGyms)
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { it.copy(isLoadingChallengeReferences = false) }
                    emitMessage(throwable.message ?: "챌린지 참고 목록을 불러오지 못했어요.")
                }
        }
    }

    fun closeChallengeReferenceSheet() {
        _uiState.update { it.copy(isChallengeSheetVisible = false) }
    }

    fun selectChallengeReference(reference: CommunityChallengeReference) {
        if (reference.gymId == null) {
            emitMessage("이 챌린지는 암장 정보가 없어 태그에 바로 사용할 수 없어요.")
            return
        }

        _uiState.update {
            it.copy(
                isChallengeSheetVisible = false,
                composeState = it.composeState.copy(
                    gymId = reference.gymId,
                    gymName = reference.gymName
                )
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadDetail(postId: Long) {
        viewModelScope.launch {
            getCommunityPostDetailUseCase(postId)
                .onSuccess { detail ->
                    _uiState.update {
                        it.copy(
                            detail = detail,
                            isLoadingDetail = false,
                            detailError = null
                        )
                    }
                    loadComments(postId)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            detailError = throwable.message ?: "게시글 상세를 불러오지 못했어요."
                        )
                    }
                }
        }
    }

    private fun loadComments(postId: Long) {
        viewModelScope.launch {
            getCommunityCommentsUseCase(postId)
                .onSuccess { comments ->
                    updateComments(postId, comments)
                }
                .onFailure { throwable ->
                    emitMessage(throwable.message ?: "댓글을 불러오지 못했어요.")
                }
        }
    }

    private fun updateComments(postId: Long, comments: List<CommunityComment>) {
        _uiState.update {
            it.copy(
                comments = comments,
                detail = it.detail?.takeIf { detail -> detail.id == postId }?.copy(
                    commentCount = comments.sumOf { root -> 1 + root.replies.size }
                )
            )
        }
        refreshFeed()
    }

    private fun emitMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun buildGymOptions(
        posts: List<CommunityPostSummary>,
        existing: List<Pair<Long, String>>
    ): List<Pair<Long, String>> {
        val fromPosts = posts.mapNotNull { post ->
            val gymId = post.gymId ?: return@mapNotNull null
            val gymName = post.gymName ?: return@mapNotNull null
            gymId to gymName
        }
        return (existing + fromPosts)
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
}
