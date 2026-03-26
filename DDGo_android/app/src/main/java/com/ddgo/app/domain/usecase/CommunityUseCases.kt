package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityFeedPage
import com.ddgo.app.domain.model.CommunityLikeResult
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostUpsertRequest
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.domain.model.CommunityVideoUploadRequest
import com.ddgo.app.domain.model.CommunityVideoUploadTicket
import com.ddgo.app.domain.repository.CommunityRepository
import javax.inject.Inject

class GetCommunityPostsUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        page: Int,
        size: Int,
        keyword: String,
        sort: CommunitySort,
        gymId: Long?
    ): Result<CommunityFeedPage> = communityRepository.getPosts(page, size, keyword, sort, gymId)
}

class GetCommunityPostDetailUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(postId: Long): Result<CommunityPostDetail> =
        communityRepository.getPostDetail(postId)
}

class CreateCommunityPostUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(request: CommunityPostUpsertRequest): Result<CommunityPostDetail> =
        communityRepository.createPost(request)
}

class UpdateCommunityPostUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        postId: Long,
        request: CommunityPostUpsertRequest
    ): Result<CommunityPostDetail> = communityRepository.updatePost(postId, request)
}

class DeleteCommunityPostUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(postId: Long): Result<Unit> = communityRepository.deletePost(postId)
}

class GetCommunityCommentsUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(postId: Long): Result<List<CommunityComment>> =
        communityRepository.getComments(postId)
}

class CreateCommunityCommentUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        postId: Long,
        content: String,
        parentCommentId: Long?
    ): Result<List<CommunityComment>> =
        communityRepository.createComment(postId, content, parentCommentId)
}

class UpdateCommunityCommentUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        postId: Long,
        commentId: Long,
        content: String
    ): Result<List<CommunityComment>> =
        communityRepository.updateComment(postId, commentId, content)
}

class DeleteCommunityCommentUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        postId: Long,
        commentId: Long
    ): Result<List<CommunityComment>> =
        communityRepository.deleteComment(postId, commentId)
}

class ToggleCommunityPostLikeUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(postId: Long, shouldLike: Boolean): Result<CommunityLikeResult> {
        return if (shouldLike) {
            communityRepository.likePost(postId)
        } else {
            communityRepository.unlikePost(postId)
        }
    }
}

class ToggleCommunityCommentLikeUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(commentId: Long, shouldLike: Boolean): Result<CommunityLikeResult> {
        return if (shouldLike) {
            communityRepository.likeComment(commentId)
        } else {
            communityRepository.unlikeComment(commentId)
        }
    }
}

class IssueCommunityVideoUploadTicketsUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        videos: List<CommunityVideoUploadRequest>
    ): Result<List<CommunityVideoUploadTicket>> =
        communityRepository.issueVideoUploadTickets(videos)
}

class UploadCommunityVideoUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        ticket: CommunityVideoUploadTicket,
        draft: CommunityVideoDraft
    ): Result<CommunityVideoDraft> = communityRepository.uploadVideo(ticket, draft)
}

class GetCommunityChallengeReferencesUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(): Result<List<CommunityChallengeReference>> =
        communityRepository.getChallengeReferences()
}

class CreateCommunityDraftVideoUseCase @Inject constructor(
    private val communityRepository: CommunityRepository
) {
    suspend operator fun invoke(
        uriString: String,
        sortOrder: Int
    ): Result<CommunityVideoDraft> = communityRepository.createDraftVideo(uriString, sortOrder)
}

class PrepareCommunityComposeVideosUseCase @Inject constructor(
    private val createCommunityDraftVideoUseCase: CreateCommunityDraftVideoUseCase
) {
    suspend operator fun invoke(uriStrings: List<String>): Result<List<CommunityVideoDraft>> = runCatching {
        val uniqueUris = uriStrings
            .filter(String::isNotBlank)
            .distinct()

        require(uniqueUris.isNotEmpty()) { "공유할 영상을 선택해 주세요." }
        require(uniqueUris.size <= 3) { "영상은 최대 3개까지 첨부할 수 있어요." }

        uniqueUris.mapIndexed { index, uri ->
            createCommunityDraftVideoUseCase(uri, index).getOrThrow()
        }
    }
}
