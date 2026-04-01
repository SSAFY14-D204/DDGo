package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.attempt.AttemptDetailResponseDto
import com.ddgo.app.data.remote.challenge.ChallengeListResponseDto
import com.ddgo.app.data.remote.common.GymNameFormatter
import com.ddgo.app.data.remote.community.CommunityCommentDto
import com.ddgo.app.data.remote.community.CommunityLikeResponseDto
import com.ddgo.app.data.remote.community.CommunityPostDetailResponseDto
import com.ddgo.app.data.remote.community.CommunityPostPageResponseDto
import com.ddgo.app.data.remote.community.CommunityPostSummaryDto
import com.ddgo.app.data.remote.community.CommunityVideoDto
import com.ddgo.app.data.remote.community.CommunityVideoPayloadDto
import com.ddgo.app.data.remote.community.CommunityVideoUploadRequestItemDto
import com.ddgo.app.data.remote.community.CommunityVideoUploadTicketDto
import com.ddgo.app.domain.model.CommunityAttemptReference
import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityFeedPage
import com.ddgo.app.domain.model.CommunityLikeResult
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.domain.model.CommunityVideoAttachment
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.domain.model.CommunityVideoUploadRequest
import com.ddgo.app.domain.model.CommunityVideoUploadTicket

object CommunityMapper {

    fun CommunityPostPageResponseDto.toDomain(): CommunityFeedPage = CommunityFeedPage(
        items = items.map { it.toDomain() },
        page = page,
        size = size,
        hasNext = hasNext,
        totalElements = totalElements
    )

    fun CommunityPostSummaryDto.toDomain(): CommunityPostSummary = CommunityPostSummary(
        id = id,
        title = title,
        contentPreview = contentPreview,
        authorNickname = authorNickname,
        gymId = gymId,
        gymName = GymNameFormatter.sanitize(gymName),
        createdAt = createdAt,
        viewCount = viewCount,
        likeCount = likeCount,
        commentCount = commentCount,
        videoCount = videoCount,
        thumbnailUrl = thumbnailUrl,
        isLiked = isLiked,
        isMine = isMine
    )

    fun CommunityPostDetailResponseDto.toDomain(): CommunityPostDetail = CommunityPostDetail(
        id = id,
        title = title,
        content = content,
        authorNickname = authorNickname,
        gymId = gymId,
        gymName = GymNameFormatter.sanitize(gymName),
        createdAt = createdAt,
        updatedAt = updatedAt,
        likeCount = likeCount,
        commentCount = commentCount,
        viewCount = viewCount,
        isLiked = isLiked,
        isMine = isMine,
        videos = videos.sortedBy { it.sortOrder }.map { it.toDomain() },
        comments = comments.sortedBy { it.createdAt }.map { it.toDomain(id) }
    )

    fun CommunityVideoDto.toDomain(): CommunityVideoAttachment = CommunityVideoAttachment(
        objectKey = objectKey,
        playbackUrl = playbackUrl,
        thumbnailUrl = thumbnailUrl,
        originalFileName = originalFileName,
        contentType = contentType,
        fileSize = fileSize,
        durationMs = durationMs,
        sortOrder = sortOrder
    )

    fun CommunityCommentDto.toDomain(postId: Long): CommunityComment = CommunityComment(
        id = id,
        postId = postId,
        parentCommentId = parentCommentId,
        depth = depth,
        authorNickname = authorNickname,
        content = content,
        createdAt = createdAt,
        likeCount = likeCount,
        isLiked = isLiked,
        isMine = isMine,
        replies = replies.sortedBy { it.createdAt }.map { it.toDomain(postId) }
    )

    fun CommunityLikeResponseDto.toDomain(): CommunityLikeResult = CommunityLikeResult(
        targetId = targetId,
        liked = liked,
        likeCount = likeCount
    )

    fun CommunityVideoUploadTicketDto.toDomain(): CommunityVideoUploadTicket = CommunityVideoUploadTicket(
        uploadUrl = uploadUrl,
        objectKey = objectKey
    )

    fun CommunityVideoUploadRequest.toDto(): CommunityVideoUploadRequestItemDto =
        CommunityVideoUploadRequestItemDto(
            originalFileName = originalFileName,
            contentType = contentType,
            fileSize = fileSize
        )

    fun CommunityVideoDraft.toPayload(sortOrder: Int): CommunityVideoPayloadDto =
        CommunityVideoPayloadDto(
            objectKey = requireNotNull(objectKey),
            originalFileName = originalFileName,
            contentType = contentType,
            fileSize = fileSize,
            durationMs = durationMs,
            sortOrder = sortOrder
        )

    fun ChallengeListResponseDto.toReference(
        attempts: List<CommunityAttemptReference>
    ): CommunityChallengeReference = CommunityChallengeReference(
        challengeId = id,
        gymId = gymId,
        gymName = GymNameFormatter.sanitize(gymName),
        problemColor = problemColor,
        gradeLabel = gradeLabel,
        challengeStatus = challengeStatus,
        challengeResult = challengeResult,
        createdAt = createdAt,
        attempts = attempts
    )

    fun AttemptDetailResponseDto.toReference(challengeId: Long): CommunityAttemptReference =
        CommunityAttemptReference(
            challengeId = challengeId,
            attemptId = attemptId,
            attemptNo = attemptNo,
            attemptStatus = attemptStatus,
            attemptResult = attemptResult,
            createdAt = createdAt
        )
}
