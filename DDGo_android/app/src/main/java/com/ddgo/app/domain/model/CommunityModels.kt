package com.ddgo.app.domain.model

enum class CommunitySort {
    LATEST,
    POPULAR
}

enum class CommunityDraftVideoStatus {
    LOCAL,
    UPLOADING,
    UPLOADED,
    FAILED
}

data class CommunityFeedPage(
    val items: List<CommunityPostSummary>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
    val totalElements: Long
)

data class CommunityPostSummary(
    val id: Long,
    val title: String,
    val contentPreview: String,
    val authorNickname: String,
    val gymId: Long? = null,
    val gymName: String? = null,
    val createdAt: String,
    val viewCount: Int,
    val likeCount: Int,
    val commentCount: Int,
    val videoCount: Int,
    val isLiked: Boolean,
    val isMine: Boolean
)

data class CommunityPostDetail(
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
    val isLiked: Boolean,
    val isMine: Boolean,
    val videos: List<CommunityVideoAttachment>,
    val comments: List<CommunityComment> = emptyList()
)

data class CommunityVideoAttachment(
    val objectKey: String,
    val playbackUrl: String,
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long,
    val durationMs: Long? = null,
    val sortOrder: Int
)

data class CommunityComment(
    val id: Long,
    val postId: Long,
    val parentCommentId: Long? = null,
    val depth: Int,
    val authorNickname: String,
    val content: String,
    val createdAt: String,
    val likeCount: Int,
    val isLiked: Boolean,
    val isMine: Boolean,
    val replies: List<CommunityComment> = emptyList()
)

data class CommunityVideoUploadTicket(
    val uploadUrl: String,
    val objectKey: String
)

data class CommunityLikeResult(
    val targetId: Long,
    val liked: Boolean,
    val likeCount: Int
)

class CommunityVideoUploadFailureException(
    val draftId: String,
    override val message: String
) : IllegalStateException(message)

data class CommunityVideoDraft(
    val id: String,
    val localUri: String? = null,
    val objectKey: String? = null,
    val playbackUrl: String? = null,
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long,
    val durationMs: Long? = null,
    val sortOrder: Int,
    val status: CommunityDraftVideoStatus = CommunityDraftVideoStatus.LOCAL,
    val errorMessage: String? = null
)

data class CommunityVideoUploadRequest(
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long
)

data class CommunityPostUpsertRequest(
    val title: String,
    val content: String,
    val gymId: Long? = null,
    val videos: List<CommunityVideoDraft>
)

data class CommunityChallengeReference(
    val challengeId: Long,
    val gymId: Long? = null,
    val gymName: String,
    val problemColor: String,
    val gradeLabel: String? = null,
    val challengeStatus: String,
    val challengeResult: String? = null,
    val createdAt: String,
    val attempts: List<CommunityAttemptReference> = emptyList()
)

data class CommunityAttemptReference(
    val challengeId: Long,
    val attemptId: Long,
    val attemptNo: Int,
    val attemptStatus: String,
    val attemptResult: String? = null,
    val createdAt: String
)
