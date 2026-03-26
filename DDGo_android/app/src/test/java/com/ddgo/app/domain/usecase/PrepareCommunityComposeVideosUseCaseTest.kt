package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.CommunityVideoDraft
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrepareCommunityComposeVideosUseCaseTest {

    @Test
    fun `succeeds with a single uri`() = runTest {
        val createCommunityDraftVideoUseCase = mockk<CreateCommunityDraftVideoUseCase>()
        coEvery {
            createCommunityDraftVideoUseCase("file:///single.mp4", 0)
        } returns Result.success(draft("file:///single.mp4", 0))

        val useCase = PrepareCommunityComposeVideosUseCase(createCommunityDraftVideoUseCase)

        val result = useCase(listOf("file:///single.mp4"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("file:///single.mp4"), result.getOrThrow().map { it.id })
        assertEquals(listOf(0), result.getOrThrow().map { it.sortOrder })
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///single.mp4", 0) }
    }

    @Test
    fun `dedupes uris preserves first seen order and reindexes drafts`() = runTest {
        val createCommunityDraftVideoUseCase = mockk<CreateCommunityDraftVideoUseCase>()
        coEvery {
            createCommunityDraftVideoUseCase("file:///first.mp4", 0)
        } returns Result.success(draft("file:///first.mp4", 0))
        coEvery {
            createCommunityDraftVideoUseCase("file:///second.mp4", 1)
        } returns Result.success(draft("file:///second.mp4", 1))

        val useCase = PrepareCommunityComposeVideosUseCase(createCommunityDraftVideoUseCase)

        val result = useCase(
            listOf(
                "file:///first.mp4",
                "file:///second.mp4",
                "file:///first.mp4"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("file:///first.mp4", "file:///second.mp4"),
            result.getOrThrow().map { it.id }
        )
        assertEquals(listOf(0, 1), result.getOrThrow().map { it.sortOrder })
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///first.mp4", 0) }
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///second.mp4", 1) }
    }

    @Test
    fun `succeeds with exactly three unique uris`() = runTest {
        val createCommunityDraftVideoUseCase = mockk<CreateCommunityDraftVideoUseCase>()
        coEvery {
            createCommunityDraftVideoUseCase("file:///one.mp4", 0)
        } returns Result.success(draft("file:///one.mp4", 0))
        coEvery {
            createCommunityDraftVideoUseCase("file:///two.mp4", 1)
        } returns Result.success(draft("file:///two.mp4", 1))
        coEvery {
            createCommunityDraftVideoUseCase("file:///three.mp4", 2)
        } returns Result.success(draft("file:///three.mp4", 2))

        val useCase = PrepareCommunityComposeVideosUseCase(createCommunityDraftVideoUseCase)

        val result = useCase(
            listOf(
                "file:///one.mp4",
                "file:///two.mp4",
                "file:///three.mp4"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("file:///one.mp4", "file:///two.mp4", "file:///three.mp4"),
            result.getOrThrow().map { it.id }
        )
        assertEquals(listOf(0, 1, 2), result.getOrThrow().map { it.sortOrder })
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///one.mp4", 0) }
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///two.mp4", 1) }
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///three.mp4", 2) }
    }

    @Test
    fun `fails when uri list is empty or exceeds limit`() = runTest {
        val createCommunityDraftVideoUseCase = mockk<CreateCommunityDraftVideoUseCase>()
        val useCase = PrepareCommunityComposeVideosUseCase(createCommunityDraftVideoUseCase)

        val emptyResult = useCase(emptyList())
        val overflowResult = useCase(
            listOf(
                "file:///1.mp4",
                "file:///2.mp4",
                "file:///3.mp4",
                "file:///4.mp4"
            )
        )

        assertTrue(emptyResult.isFailure)
        assertTrue(overflowResult.isFailure)
        coVerify(exactly = 0) { createCommunityDraftVideoUseCase(any(), any()) }
    }

    @Test
    fun `propagates draft preparation failure`() = runTest {
        val createCommunityDraftVideoUseCase = mockk<CreateCommunityDraftVideoUseCase>()
        coEvery {
            createCommunityDraftVideoUseCase("file:///ok.mp4", 0)
        } returns Result.success(draft("file:///ok.mp4", 0))
        coEvery {
            createCommunityDraftVideoUseCase("file:///fail.mp4", 1)
        } returns Result.failure(IllegalStateException("broken"))

        val useCase = PrepareCommunityComposeVideosUseCase(createCommunityDraftVideoUseCase)

        val result = useCase(listOf("file:///ok.mp4", "file:///fail.mp4"))

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///ok.mp4", 0) }
        coVerify(exactly = 1) { createCommunityDraftVideoUseCase("file:///fail.mp4", 1) }
    }

    private fun draft(uri: String, sortOrder: Int): CommunityVideoDraft {
        return CommunityVideoDraft(
            id = uri,
            localUri = uri,
            originalFileName = "video_$sortOrder.mp4",
            contentType = "video/mp4",
            fileSize = 1_024L,
            durationMs = 5_000L,
            sortOrder = sortOrder
        )
    }
}
