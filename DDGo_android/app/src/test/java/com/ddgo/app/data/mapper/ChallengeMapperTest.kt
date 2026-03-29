package com.ddgo.app.data.mapper

import com.ddgo.app.data.mapper.ChallengeMapper.toDomain
import com.ddgo.app.data.mapper.ChallengeMapper.toRequestDto
import com.ddgo.app.data.remote.challenge.HoldSaveRequestDto
import com.ddgo.app.data.remote.challenge.HoldSaveResponseDto
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.HoldBoundingBox
import com.ddgo.app.domain.model.HoldPoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `hold save request serializes holdNo boundingBox and polygon`() {
        val holds = listOf(
            ChallengeHoldCoordinate(
                holdNo = 1,
                boundingBox = HoldBoundingBox(x1 = 0.40f, x2 = 0.52f, y1 = 0.30f, y2 = 0.38f),
                polygon = listOf(
                    HoldPoint(x = 0.45f, y = 0.32f),
                    HoldPoint(x = 0.48f, y = 0.30f),
                    HoldPoint(x = 0.52f, y = 0.33f)
                )
            )
        )

        val requestJson = json.encodeToString(
            HoldSaveRequestDto(
                holds = holds.map { it.toRequestDto() }
            )
        )

        assertTrue(requestJson.contains("\"holdNo\":1"))
        assertTrue(requestJson.contains("\"boundingBox\""))
        assertTrue(requestJson.contains("\"x1\":0.4"))
        assertTrue(requestJson.contains("\"polygon\""))
    }

    @Test
    fun `hold save response without boundingBox still maps to domain`() {
        val responseJson = """
            {
              "challengeId": 94,
              "holdCount": 1,
              "holds": [
                {
                  "holdNo": 1,
                  "polygon": [
                    { "x": 0.45, "y": 0.32 },
                    { "x": 0.48, "y": 0.30 },
                    { "x": 0.52, "y": 0.33 },
                    { "x": 0.50, "y": 0.38 }
                  ]
                }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<HoldSaveResponseDto>(responseJson)
        val domain = dto.toDomain()

        assertEquals(94L, domain.challengeId)
        assertEquals(1, domain.holdCount)
        assertEquals(1, domain.holds.first().holdNo)
        assertEquals(0.45f, domain.holds.first().boundingBox.x1)
        assertEquals(0.52f, domain.holds.first().boundingBox.x2)
        assertEquals(0.30f, domain.holds.first().boundingBox.y1)
        assertEquals(0.38f, domain.holds.first().boundingBox.y2)
    }
}
