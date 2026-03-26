package com.ddgo.app.feature.community

import com.ddgo.app.domain.model.CommunityChallengeReference
import com.ddgo.app.domain.model.CommunityComment
import com.ddgo.app.domain.model.CommunityFeedPage
import com.ddgo.app.domain.model.CommunityPostDetail
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.domain.model.CommunitySort
import com.ddgo.app.domain.model.CommunityVideoAttachment
import com.ddgo.app.domain.model.CommunityVideoDraft
import com.ddgo.app.domain.usecase.CreateCommunityCommentUseCase
import com.ddgo.app.domain.usecase.CreateCommunityDraftVideoUseCase
import com.ddgo.app.domain.usecase.CreateCommunityPostUseCase
import com.ddgo.app.domain.usecase.DeleteCommunityCommentUseCase
import com.ddgo.app.domain.usecase.DeleteCommunityPostUseCase
import com.ddgo.app.domain.usecase.GetCommunityChallengeReferencesUseCase
import com.ddgo.app.domain.usecase.GetCommunityPostDetailUseCase
import com.ddgo.app.domain.usecase.GetCommunityPostsUseCase
import com.ddgo.app.domain.usecase.PrepareCommunityComposeVideosUseCase
import com.ddgo.app.domain.usecase.ToggleCommunityCommentLikeUseCase
import com.ddgo.app.domain.usecase.ToggleCommunityPostLikeUseCase
import com.ddgo.app.domain.usecase.UpdateCommunityCommentUseCase
import com.ddgo.app.domain.usecase.UpdateCommunityPostUseCase
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `open compose enters create mode`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openCompose()

        val composeDestination = viewModel.uiState.value.destination as CommunityDestination.Compose
        assertEquals(null, composeDestination.editingPostId)
        assertEquals(CommunityComposeMode.Create, viewModel.uiState.value.composeState.mode)
    }

    @Test
    fun `initial feed load keeps thumbnail url`() = runTest {
        val viewModel = createViewModel(
            initialFeedPage = CommunityFeedPage(
                items = listOf(sampleSummary()),
                page = 0,
                size = 20,
                hasNext = false,
                totalElements = 1L
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.posts.size)
        assertEquals(
            "https://example.com/thumbnail.jpg",
            viewModel.uiState.value.posts.first().thumbnailUrl
        )
    }

    @Test
    fun `open edit enters edit mode with existing videos`() = runTest {
        val detail = sampleDetail()
        val getCommunityPostDetailUseCase = mockk<GetCommunityPostDetailUseCase>()

        val viewModel = createViewModel(
            getCommunityPostDetailUseCase = getCommunityPostDetailUseCase
        )
        advanceUntilIdle()
        coEvery { getCommunityPostDetailUseCase(detail.id) } returns Result.success(detail)

        viewModel.openPostDetail(detail.id)
        advanceUntilIdle()
        viewModel.openEdit()

        val composeDestination = viewModel.uiState.value.destination as CommunityDestination.Compose
        assertEquals(detail.id, composeDestination.editingPostId)
        assertEquals(CommunityComposeMode.Edit, viewModel.uiState.value.composeState.mode)
        assertEquals(detail.videos.size, viewModel.uiState.value.composeState.videos.size)
    }

    @Test
    fun `consume pending analysis share enters share mode once and blocks video editing`() = runTest {
        val prepareCommunityComposeVideosUseCase = mockk<PrepareCommunityComposeVideosUseCase>()
        val createCommunityDraftVideoUseCase = mockk<CreateCommunityDraftVideoUseCase>()
        val preparedDraft = CommunityVideoDraft(
            id = "file:///attempt.mp4",
            localUri = "file:///attempt.mp4",
            originalFileName = "attempt.mp4",
            contentType = "video/mp4",
            fileSize = 1_024L,
            durationMs = 4_000L,
            sortOrder = 0
        )
        coEvery {
            prepareCommunityComposeVideosUseCase(listOf("file:///attempt.mp4"))
        } returns Result.success(listOf(preparedDraft))

        val getCommunityChallengeReferencesUseCase = mockk<GetCommunityChallengeReferencesUseCase>()
        val viewModel = createViewModel(
            prepareCommunityComposeVideosUseCase = prepareCommunityComposeVideosUseCase,
            getCommunityChallengeReferencesUseCase = getCommunityChallengeReferencesUseCase,
            createCommunityDraftVideoUseCase = createCommunityDraftVideoUseCase
        )
        advanceUntilIdle()

        viewModel.consumePendingAnalysisShare(
            requestId = 101L,
            gymId = 7L,
            gymName = "DDGo Gym",
            videoUris = listOf("file:///attempt.mp4")
        )
        advanceUntilIdle()

        val composeState = viewModel.uiState.value.composeState
        assertEquals(CommunityComposeMode.AnalysisShare, composeState.mode)
        assertEquals(101L, composeState.shareRequestId)
        assertEquals(7L, composeState.gymId)
        assertEquals("DDGo Gym", composeState.gymName)
        assertEquals(1, composeState.videos.size)

        viewModel.clearComposeGym()
        viewModel.addSelectedVideos(listOf("file:///extra.mp4"))
        viewModel.removeComposeVideo(preparedDraft.id)
        viewModel.moveComposeVideo(preparedDraft.id, 1)
        viewModel.openChallengeReferenceSheet()
        viewModel.selectChallengeReference(sampleChallengeReference())
        advanceUntilIdle()

        assertEquals(7L, viewModel.uiState.value.composeState.gymId)
        assertEquals("DDGo Gym", viewModel.uiState.value.composeState.gymName)
        assertEquals(1, viewModel.uiState.value.composeState.videos.size)
        assertFalse(viewModel.uiState.value.isChallengeSheetVisible)

        viewModel.consumePendingAnalysisShare(
            requestId = 101L,
            gymId = 99L,
            gymName = "Ignored",
            videoUris = listOf("file:///attempt.mp4")
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            prepareCommunityComposeVideosUseCase(listOf("file:///attempt.mp4"))
        }
        coVerify(exactly = 0) {
            createCommunityDraftVideoUseCase(any(), any())
        }
        coVerify(exactly = 0) {
            getCommunityChallengeReferencesUseCase()
        }
    }

    private fun createViewModel(
        getCommunityPostsUseCase: GetCommunityPostsUseCase = mockk(),
        getCommunityPostDetailUseCase: GetCommunityPostDetailUseCase = mockk(),
        createCommunityPostUseCase: CreateCommunityPostUseCase = mockk(relaxed = true),
        updateCommunityPostUseCase: UpdateCommunityPostUseCase = mockk(relaxed = true),
        deleteCommunityPostUseCase: DeleteCommunityPostUseCase = mockk(relaxed = true),
        createCommunityCommentUseCase: CreateCommunityCommentUseCase = mockk(relaxed = true),
        updateCommunityCommentUseCase: UpdateCommunityCommentUseCase = mockk(relaxed = true),
        deleteCommunityCommentUseCase: DeleteCommunityCommentUseCase = mockk(relaxed = true),
        toggleCommunityPostLikeUseCase: ToggleCommunityPostLikeUseCase = mockk(relaxed = true),
        toggleCommunityCommentLikeUseCase: ToggleCommunityCommentLikeUseCase = mockk(relaxed = true),
        getCommunityChallengeReferencesUseCase: GetCommunityChallengeReferencesUseCase = mockk(relaxed = true),
        createCommunityDraftVideoUseCase: CreateCommunityDraftVideoUseCase = mockk(relaxed = true),
        prepareCommunityComposeVideosUseCase: PrepareCommunityComposeVideosUseCase = mockk(relaxed = true),
        initialFeedPage: CommunityFeedPage = CommunityFeedPage(
            items = emptyList(),
            page = 0,
            size = 20,
            hasNext = false,
            totalElements = 0L
        )
    ): CommunityViewModel {
        coEvery {
            getCommunityPostsUseCase(any(), any(), any(), any(), any())
        } returns Result.success(initialFeedPage)
        coEvery { getCommunityPostDetailUseCase(any()) } returns Result.failure(
            IllegalStateException("missing detail")
        )

        return CommunityViewModel(
            getCommunityPostsUseCase = getCommunityPostsUseCase,
            getCommunityPostDetailUseCase = getCommunityPostDetailUseCase,
            createCommunityPostUseCase = createCommunityPostUseCase,
            updateCommunityPostUseCase = updateCommunityPostUseCase,
            deleteCommunityPostUseCase = deleteCommunityPostUseCase,
            createCommunityCommentUseCase = createCommunityCommentUseCase,
            updateCommunityCommentUseCase = updateCommunityCommentUseCase,
            deleteCommunityCommentUseCase = deleteCommunityCommentUseCase,
            toggleCommunityPostLikeUseCase = toggleCommunityPostLikeUseCase,
            toggleCommunityCommentLikeUseCase = toggleCommunityCommentLikeUseCase,
            getCommunityChallengeReferencesUseCase = getCommunityChallengeReferencesUseCase,
            createCommunityDraftVideoUseCase = createCommunityDraftVideoUseCase,
            prepareCommunityComposeVideosUseCase = prepareCommunityComposeVideosUseCase
        )
    }

    private fun sampleDetail(): CommunityPostDetail {
        return CommunityPostDetail(
            id = 7L,
            title = "title",
            content = "content",
            authorNickname = "tester",
            gymId = 5L,
            gymName = "DDGo Gym",
            createdAt = "2026-03-26T09:00:00",
            updatedAt = null,
            likeCount = 0,
            commentCount = 0,
            viewCount = 0,
            isLiked = false,
            isMine = true,
            videos = listOf(
                CommunityVideoAttachment(
                    objectKey = "object-1",
                    playbackUrl = "https://example.com/video.mp4",
                    originalFileName = "video.mp4",
                    contentType = "video/mp4",
                    fileSize = 2_048L,
                    durationMs = 3_000L,
                    sortOrder = 0
                )
            ),
            comments = emptyList<CommunityComment>()
        )
    }

    private fun sampleSummary(): CommunityPostSummary {
        return CommunityPostSummary(
            id = 3L,
            title = "summary",
            contentPreview = "preview",
            authorNickname = "tester",
            gymId = 5L,
            gymName = "DDGo Gym",
            createdAt = "2026-03-26T09:00:00",
            viewCount = 10,
            likeCount = 4,
            commentCount = 2,
            videoCount = 1,
            thumbnailUrl = "https://example.com/thumbnail.jpg",
            isLiked = false,
            isMine = true
        )
    }

    private fun sampleChallengeReference(): CommunityChallengeReference {
        return CommunityChallengeReference(
            challengeId = 11L,
            gymId = 99L,
            gymName = "Other Gym",
            problemColor = "blue",
            gradeLabel = "V4",
            challengeStatus = "COMPLETED",
            challengeResult = "SUCCESS",
            createdAt = "2026-03-26T08:30:00"
        )
    }
}
