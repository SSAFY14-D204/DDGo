package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `pose 리스트를 직렬화 가능한 dto로 변환한다`() {
        val poses = listOf(
            Pose(
                frameTimeMs = 500L,
                landmarks = listOf(
                    PoseLandmark(index = 15, x = 0.1f, y = 0.2f, z = -0.3f),
                    PoseLandmark(index = 19, x = 0.4f, y = 0.5f, z = -0.6f)
                )
            )
        )

        val dto = poses.toPoseSequenceDto()

        assertEquals(1, dto.poses.size)
        assertEquals(500L, dto.poses.first().frameTimeMs)
        assertEquals(2, dto.poses.first().landmarks.size)
        assertEquals(15, dto.poses.first().landmarks.first().index)
    }

    @Test
    fun `pose dto를 json 문자열로 변환할 수 있다`() {
        val poses = listOf(
            Pose(
                frameTimeMs = 750L,
                landmarks = listOf(
                    PoseLandmark(index = 16, x = 0.11f, y = 0.22f, z = -0.33f)
                )
            )
        )

        val jsonString = poses.toPoseJson(json)
        val decoded = json.decodeFromString<PoseSequenceDto>(jsonString)

        assertTrue(jsonString.contains("\"frame_time_ms\":750"))
        assertEquals(1, decoded.poses.size)
        assertEquals(16, decoded.poses.first().landmarks.first().index)
    }
}
