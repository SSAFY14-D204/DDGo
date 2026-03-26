package com.ddgo.app.data.remote.community

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunityPostPageResponseDto(
    val items: List<CommunityPostSummaryDto>,
    val page: Int,
    val size: Int,
    val totalPages: Int = 0,
    val hasNext: Boolean,
    val totalElements: Long
)

@Serializable
data class CommunityPostSummaryDto(
    val id: Long,
    val title: String,
    @SerialName("excerpt")
    val contentPreview: String,
    val authorNickname: String,
    val gymId: Long? = null,
    val gymName: String? = null,
    val createdAt: String,
    val viewCount: Int = 0,
    val likeCount: Int,
    val commentCount: Int,
    val videoCount: Int,
    val thumbnailUrl: String? = null,
    @SerialName("liked")
    val isLiked: Boolean = false,
    @SerialName("mine")
    val isMine: Boolean = false
)

@Serializable
data class CommunityPostDetailResponseDto(
    val id: Long,
    val title: String,
    val content: String,
    val authorNickname: String,
    val gymId: Long? = null,
    val gymName: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
    val likeCount: Int,
    val commentCount: Int,
    val viewCount: Int,
    val videoCount: Int = 0,
    @SerialName("liked")
    val isLiked: Boolean = false,
    @SerialName("mine")
    val isMine: Boolean = false,
    val videos: List<CommunityVideoDto> = emptyList(),
    val comments: List<CommunityCommentDto> = emptyList()
)

@Serializable
data class CommunityVideoDto(
    val id: Long? = null,
    val objectKey: String,
    val playbackUrl: String,
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long,
    val durationMs: Long? = null,
    val sortOrder: Int
)

@Serializable
data class CommunityPostUpsertRequestDto(
    val title: String,
    val content: String,
    val gymId: Long? = null,
    val videos: List<CommunityVideoPayloadDto>
)

@Serializable
data class CommunityVideoPayloadDto(
    val objectKey: String,
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long,
    val durationMs: Long? = null,
    val sortOrder: Int
)

@Serializable
data class CommunityCommentDto(
    val id: Long,
    val parentCommentId: Long? = null,
    val depth: Int,
    val authorNickname: String,
    val content: String,
    val createdAt: String,
    val likeCount: Int,
    @SerialName("liked")
    val isLiked: Boolean = false,
    @SerialName("mine")
    val isMine: Boolean = false,
    val replies: List<CommunityCommentDto> = emptyList()
)

@Serializable
data class CommunityCommentRequestDto(
    val content: String,
    val parentCommentId: Long? = null
)

@Serializable
data class CommunityLikeResponseDto(
    val targetId: Long,
    val liked: Boolean,
    val likeCount: Int
)

@Serializable
data class CommunityVideoUploadUrlRequestDto(
    val videos: List<CommunityVideoUploadRequestItemDto>
)

@Serializable
data class CommunityVideoUploadRequestItemDto(
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long
)

@Serializable
data class CommunityVideoUploadUrlResponseDto(
    @SerialName("videos")
    val tickets: List<CommunityVideoUploadTicketDto> = emptyList()
)

@Serializable
data class CommunityVideoUploadTicketDto(
    val originalFileName: String? = null,
    val uploadUrl: String,
    val objectKey: String
)
