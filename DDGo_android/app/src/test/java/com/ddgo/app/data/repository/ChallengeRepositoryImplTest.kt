package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.HoldItemDto
import com.ddgo.app.data.remote.challenge.HoldSaveRequestDto
import com.ddgo.app.data.remote.challenge.HoldSaveResponseDto
import com.ddgo.app.data.remote.challenge.PointItemDto
import com.ddgo.app.data.remote.common.ApiResponse
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.HoldBoundingBox
import com.ddgo.app.domain.model.HoldPoint
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeRepositoryImplTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `saveChallengeHolds sends holdNo boundingBox and polygon request body`() = runBlocking {
        val challengeApi = mockk<ChallengeApi>()
        val requestSlot = slot<HoldSaveRequestDto>()
        val repository = ChallengeRepositoryImpl(challengeApi)
        val holds = listOf(
            ChallengeHoldCoordinate(
                holdNo = 1,
                boundingBox = HoldBoundingBox(0.44f, 0.52f, 0.31f, 0.38f),
                polygon = listOf(
                    HoldPoint(0.45f, 0.32f),
                    HoldPoint(0.48f, 0.30f),
                    HoldPoint(0.52f, 0.33f)
                )
            ),
            ChallengeHoldCoordinate(
                holdNo = 2,
                boundingBox = HoldBoundingBox(0.60f, 0.68f, 0.48f, 0.55f),
                polygon = listOf(
                    HoldPoint(0.60f, 0.50f),
                    HoldPoint(0.65f, 0.48f),
                    HoldPoint(0.68f, 0.53f)
                )
            )
        )

        coEvery {
            challengeApi.saveChallengeHolds(94L, capture(requestSlot))
        } returns ApiResponse(
            success = true,
            data = HoldSaveResponseDto(
                challengeId = 94L,
                holdCount = 2,
                holds = listOf(
                    HoldItemDto(
                        holdNo = 1,
                        polygon = listOf(
                            PointItemDto(0.45f, 0.32f),
                            PointItemDto(0.48f, 0.30f),
                            PointItemDto(0.52f, 0.33f)
                        )
                    ),
                    HoldItemDto(
                        holdNo = 2,
                        polygon = listOf(
                            PointItemDto(0.60f, 0.50f),
                            PointItemDto(0.65f, 0.48f),
                            PointItemDto(0.68f, 0.53f)
                        )
                    )
                )
            )
        )

        val result = repository.saveChallengeHolds(
            challengeId = 94L,
            holds = holds
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2), requestSlot.captured.holds.map { it.holdNo })
        assertEquals(3, requestSlot.captured.holds.first().polygon.size)
        assertEquals(0.44f, requestSlot.captured.holds.first().boundingBox.x1)
        assertTrue(json.encodeToString(requestSlot.captured).contains("boundingBox"))
    }
}
