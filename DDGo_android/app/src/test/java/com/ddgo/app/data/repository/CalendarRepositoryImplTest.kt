package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeListResponseDto
import com.ddgo.app.data.remote.common.ApiResponse
import com.ddgo.app.domain.model.CalendarEntryResult
import io.mockk.coEvery
import io.mockk.mockk
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarRepositoryImplTest {

    @Test
    fun `getCalendarEntries maps challenge result and status to calendar entry result`() = runTest {
        val challengeApi = mockk<ChallengeApi>()
        val repository = CalendarRepositoryImpl(challengeApi)
        val targetMonth = YearMonth.of(2026, 3)

        coEvery { challengeApi.getChallenges() } returns ApiResponse(
            success = true,
            data = listOf(
                challenge(
                    id = 1L,
                    startedAt = "2026-03-02T20:00:00",
                    challengeResult = "SUCCESS",
                    challengeStatus = "CLOSED"
                ),
                challenge(
                    id = 2L,
                    startedAt = "2026-03-02T19:00:00",
                    challengeResult = "FAIL",
                    challengeStatus = "CLOSED"
                ),
                challenge(
                    id = 3L,
                    startedAt = "2026-03-02T18:00:00",
                    challengeResult = null,
                    challengeStatus = "ACTIVE"
                ),
                challenge(
                    id = 4L,
                    startedAt = "2026-03-02T17:00:00",
                    challengeResult = null,
                    challengeStatus = "CLOSED"
                )
            )
        )

        val entries = repository.getCalendarEntries(targetMonth).getOrThrow()

        assertEquals(
            listOf(
                CalendarEntryResult.SUCCESS,
                CalendarEntryResult.FAIL,
                CalendarEntryResult.ACTIVE,
                CalendarEntryResult.PENDING
            ),
            entries.map { it.result }
        )
    }

    private fun challenge(
        id: Long,
        startedAt: String,
        challengeResult: String?,
        challengeStatus: String
    ): ChallengeListResponseDto {
        return ChallengeListResponseDto(
            id = id,
            gymId = 1L,
            gymName = "더클라임 강남",
            problemColor = "보라",
            gradeLabel = "V4",
            challengeStatus = challengeStatus,
            challengeResult = challengeResult,
            startedAt = startedAt,
            endedAt = null,
            createdAt = startedAt
        )
    }
}
