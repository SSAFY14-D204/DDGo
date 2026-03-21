package com.ddgo.app.feature.community

import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoDraft

sealed interface CommunityDestination {
    data object Feed : CommunityDestination
    data class Detail(val postId: Long) : CommunityDestination
    data class Compose(val editingPostId: Long? = null) : CommunityDestination
}

data class CommunityComposeState(
    val title: String = "",
    val content: String = "",
    val gymId: Long? = null,
    val gymName: String? = null,
    val videos: List<CommunityVideoDraft> = emptyList(),
    val isSubmitting: Boolean = false,
    val submitError: String? = null
)

data class CommunityUiState(
    val destination: CommunityDestination = CommunityDestination.Feed,
    val posts: List<CommunityPostSummary> = emptyList(),
    val selectedSort: CommunitySort = CommunitySort.LATEST,
    val searchKeyword: String = "",
    val selectedGymId: Long? = null,
    val selectedGymName: String? = null,
    val availableGyms: List<Pair<Long, String>> = emptyList(),
    val isLoadingFeed: Boolean = true,
    val feedError: String? = null,
    val detail: CommunityPostDetail? = null,
    val comments: List<CommunityComment> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
    val commentInput: String = "",
    val replyingToCommentId: Long? = null,
    val replyingToNickname: String? = null,
    val editingCommentId: Long? = null,
    val composeState: CommunityComposeState = CommunityComposeState(),
    val isChallengeSheetVisible: Boolean = false,
    val isLoadingChallengeReferences: Boolean = false,
    val challengeReferences: List<CommunityChallengeReference> = emptyList(),
    val message: String? = null
)
