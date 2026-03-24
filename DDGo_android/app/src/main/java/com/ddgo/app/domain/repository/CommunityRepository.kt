package com.ddgo.app.domain.repository

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

interface CommunityRepository {
    suspend fun getPosts(
        page: Int,
        size: Int,
        keyword: String,
        sort: CommunitySort,
        gymId: Long?
    ): Result<CommunityFeedPage>

    suspend fun getPostDetail(postId: Long): Result<CommunityPostDetail>

    suspend fun createPost(request: CommunityPostUpsertRequest): Result<CommunityPostDetail>

    suspend fun updatePost(
        postId: Long,
        request: CommunityPostUpsertRequest
    ): Result<CommunityPostDetail>

    suspend fun deletePost(postId: Long): Result<Unit>

    suspend fun getComments(postId: Long): Result<List<CommunityComment>>

    suspend fun createComment(
        postId: Long,
        content: String,
        parentCommentId: Long?
    ): Result<List<CommunityComment>>

    suspend fun updateComment(
        postId: Long,
        commentId: Long,
        content: String
    ): Result<List<CommunityComment>>

    suspend fun deleteComment(
        postId: Long,
        commentId: Long
    ): Result<List<CommunityComment>>

    suspend fun likePost(postId: Long): Result<CommunityLikeResult>

    suspend fun unlikePost(postId: Long): Result<CommunityLikeResult>

    suspend fun likeComment(commentId: Long): Result<CommunityLikeResult>

    suspend fun unlikeComment(commentId: Long): Result<CommunityLikeResult>

    suspend fun issueVideoUploadTickets(
        videos: List<CommunityVideoUploadRequest>
    ): Result<List<CommunityVideoUploadTicket>>

    suspend fun uploadVideo(
        ticket: CommunityVideoUploadTicket,
        draft: CommunityVideoDraft
    ): Result<CommunityVideoDraft>

    suspend fun getChallengeReferences(): Result<List<CommunityChallengeReference>>

    suspend fun createDraftVideo(
        uriString: String,
        sortOrder: Int
    ): Result<CommunityVideoDraft>
}
