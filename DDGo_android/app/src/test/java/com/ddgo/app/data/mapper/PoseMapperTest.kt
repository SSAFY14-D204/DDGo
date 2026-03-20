package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PosePixelPoint
import com.ddgo.app.domain.model.PoseWorldPoint
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
    fun `pose list is converted into pose sequence dto`() {
        val poses = listOf(
            Pose(
                frameTimeMs = 500L,
                landmarks = listOf(
                    PoseLandmark(index = 15, x = 0.1f, y = 0.2f, z = -0.3f),
                    PoseLandmark(index = 19, x = 0.4f, y = 0.5f, z = -0.6f)
                ),
                landmarksPx = mapOf(
                    "15" to PosePixelPoint(x = 100f, y = 200f),
                    "19" to PosePixelPoint(x = 400f, y = 500f)
                ),
                worldLandmarksSample = mapOf(
                    "15" to PoseWorldPoint(x = 0.1f, y = 0.2f, z = -0.3f)
                )
            )
        )

        val dto = poses.toPoseSequenceDto()

        assertEquals(1, dto.poses.size)
        assertEquals(500L, dto.poses.first().frameTimeMs)
        assertEquals(2, dto.poses.first().landmarksPx.size)
        assertEquals(100f, dto.poses.first().landmarksPx.getValue("15").x)
        assertEquals(1, dto.poses.first().worldLandmarksSample.size)
        assertEquals(-0.3f, dto.poses.first().worldLandmarksSample.getValue("15").z)
    }

    @Test
    fun `pose dto is serialized to json`() {
        val poses = listOf(
            Pose(
                frameTimeMs = 750L,
                landmarks = listOf(
                    PoseLandmark(index = 16, x = 0.11f, y = 0.22f, z = -0.33f)
                ),
                landmarksPx = mapOf(
                    "16" to PosePixelPoint(x = 110f, y = 220f)
                )
            )
        )

        val jsonString = poses.toPoseJson(json)
        val decoded = json.decodeFromString<PoseSequenceDto>(jsonString)

        assertTrue(jsonString.contains("\"frame_time_ms\":750"))
        assertTrue(jsonString.contains("\"landmarks_px\""))
        assertEquals(1, decoded.poses.size)
        assertEquals(110f, decoded.poses.first().landmarksPx.getValue("16").x)
    }
}
