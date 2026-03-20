package com.ddgo.app.domain.poseanalysis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class HandPeakAnalysisTest {

    @Test
    fun `extractBodyPartHeights computes landmarks source heights and front orientation`() {
        val points = extractBodyPartHeights(
            frames = listOf(
                PoseFrame(
                    frameTimeMs = 0L,
                    landmarks = listOf(
                        landmark(11, 0.7, 0.2),
                        landmark(12, 0.3, 0.2),
                        landmark(15, 0.7, 0.4),
                        landmark(16, 0.6, 0.5),
                        landmark(23, 0.68, 0.5),
                        landmark(24, 0.32, 0.5),
                        landmark(27, 0.55, 0.9),
                        landmark(28, 0.45, 0.8)
                    )
                )
            )
        )

        val point = points.single()
        assertEquals(0.55, point.handHeight!!, 1e-9)
        assertEquals(0.65, point.torsoHeight!!, 1e-9)
        assertEquals(0.15, point.footHeight!!, 1e-9)
        assertEquals(0.3, point.torsoScale!!, 1e-9)
        assertEquals(TorsoOrientation.FRONT, point.torsoOrientation)
        assertFalse(point.facingCamera)
    }

    @Test
    fun `extractBodyPartHeights supports world landmarks height conversion`() {
        val points = extractBodyPartHeights(
            frames = listOf(
                PoseFrame(
                    frameTimeMs = 0L,
                    landmarks = listOf(
                        landmark(11, 0.7, 0.2),
                        landmark(12, 0.3, 0.2),
                        landmark(23, 0.68, 0.5),
                        landmark(24, 0.32, 0.5)
                    ),
                    worldLandmarks = listOf(
                        landmark(15, 0.7, -0.4),
                        landmark(16, 0.6, -0.5),
                        landmark(11, 0.7, -0.2),
                        landmark(12, 0.3, -0.2),
                        landmark(23, 0.68, -0.5),
                        landmark(24, 0.32, -0.5),
                        landmark(27, 0.55, -0.9),
                        landmark(28, 0.45, -0.8)
                    )
                )
            ),
            config = HandPeakConfig(landmarkSource = LandmarkSource.WORLD_LANDMARKS)
        )

        val point = points.single()
        assertEquals(0.45, point.handHeight!!, 1e-9)
        assertEquals(0.35, point.torsoHeight!!, 1e-9)
        assertEquals(0.85, point.footHeight!!, 1e-9)
        assertEquals(TorsoOrientation.FRONT, point.torsoOrientation)
    }

    @Test
    fun `facing camera requires front segment lasting at least 500ms`() {
        val points = extractBodyPartHeights(
            frames = listOf(
                frontFrame(0L),
                frontFrame(300L),
                frontFrame(600L),
                backFrame(900L)
            )
        )

        assertTrue(points[0].facingCamera)
        assertTrue(points[1].facingCamera)
        assertTrue(points[2].facingCamera)
        assertFalse(points[3].facingCamera)
    }

    @Test
    fun `analyzeHandPeakAndEnd falls back from disallowed global top to supported local peak`() {
        val annotation = analyzeHandPeakAndEnd(
            points = listOf(
                bodyPoint(0L, 0.58, footHeight = 0.4),
                bodyPoint(250L, 0.60, footHeight = 0.4),
                bodyPoint(500L, 0.59, footHeight = 0.4),
                bodyPoint(750L, 0.90, footHeight = 0.2, facingCamera = true),
                bodyPoint(1_000L, 0.60, footHeight = 0.4),
                bodyPoint(1_250L, 0.59, footHeight = 0.4),
                bodyPoint(1_500L, 0.60, footHeight = 0.4)
            )
        )

        assertNotNull(annotation)
        assertTrue(annotation!!.validTopFound)
        assertEquals(0.9, annotation.globalTopHeight, 1e-9)
        assertEquals(1_500L, annotation.endTimeMs)
        assertEquals(6, annotation.supportCount)
    }

    @Test
    fun `analyzeHandPeakAndEnd returns false when no valid top exists`() {
        val annotation = analyzeHandPeakAndEnd(
            points = listOf(
                bodyPoint(0L, 0.80, footHeight = 0.2, facingCamera = true),
                bodyPoint(400L, 0.60, footHeight = 0.2, facingCamera = true),
                bodyPoint(800L, 0.40, footHeight = 0.2, facingCamera = true)
            )
        )

        assertNotNull(annotation)
        assertFalse(annotation!!.validTopFound)
        assertNull(annotation.selectedTopTimeMs)
        assertNull(annotation.endTimeMs)
    }

    @Test
    fun `python success sample matches expected hand peak annotation`() {
        val path = File("C:/ssafy/ref-codes/pose-timestamp/videos/export_성공1_1773988614180.json")
        assumeTrue(path.exists())

        val annotation = analyzeHandPeakAndEnd(extractBodyPartHeights(loadPoseFrames(path)))

        assertNotNull(annotation)
        assertEquals(57_708L, annotation!!.selectedTopTimeMs)
        assertEquals(0.73396178625, annotation.selectedTopHeight!!, 1e-9)
        assertEquals(61_708L, annotation.endTimeMs)
        assertEquals(0.68470515125, annotation.endHeight!!, 1e-9)
        assertTrue(annotation.validTopFound)
    }

    @Test
    fun `python failure sample still matches expected fallback top and end`() {
        val path = File("C:/ssafy/ref-codes/pose-timestamp/videos/export_실패1_1773808380322.json")
        assumeTrue(path.exists())

        val annotation = analyzeHandPeakAndEnd(extractBodyPartHeights(loadPoseFrames(path)))

        assertNotNull(annotation)
        assertEquals(8_900L, annotation!!.selectedTopTimeMs)
        assertEquals(0.49002588625, annotation.selectedTopHeight!!, 1e-9)
        assertEquals(18_867L, annotation.endTimeMs)
        assertEquals(0.44740777374999996, annotation.endHeight!!, 1e-9)
        assertTrue(annotation.validTopFound)
    }

    private fun loadPoseFrames(file: File): List<PoseFrame> {
        val root = Json.parseToJsonElement(file.readText())
        return root.jsonArray.map { element ->
            val frame = element.jsonObject
            PoseFrame(
                frameTimeMs = frame.getLong("frameTimeMs"),
                landmarks = frame.getLandmarks("landmarks"),
                worldLandmarks = frame.getLandmarks("worldLandmarks")
            )
        }
    }

    private fun JsonObject.getLandmarks(key: String): List<Landmark> =
        getValue(key).jsonArray.map { element ->
            val landmark = element.jsonObject
            Landmark(
                index = landmark.getInt("index"),
                x = landmark.getDouble("x") ?: error("Missing x"),
                y = landmark.getDouble("y") ?: error("Missing y"),
                z = landmark.getDouble("z") ?: 0.0,
                visibility = landmark.getDouble("visibility"),
                presence = landmark.getDouble("presence")
            )
        }

    private fun JsonObject.getInt(key: String): Int = getValue(key).jsonPrimitive.int

    private fun JsonObject.getLong(key: String): Long = getValue(key).jsonPrimitive.long

    private fun JsonObject.getDouble(key: String): Double? =
        get(key)?.jsonPrimitive?.doubleOrNull

    private fun landmark(index: Int, x: Double, y: Double): Landmark = Landmark(
        index = index,
        x = x,
        y = y,
        z = 0.0,
        visibility = 0.99,
        presence = 0.99
    )

    private fun frontFrame(frameTimeMs: Long): PoseFrame = PoseFrame(
        frameTimeMs = frameTimeMs,
        landmarks = listOf(
            landmark(11, 0.7, 0.2),
            landmark(12, 0.3, 0.2),
            landmark(23, 0.68, 0.5),
            landmark(24, 0.32, 0.5)
        )
    )

    private fun backFrame(frameTimeMs: Long): PoseFrame = PoseFrame(
        frameTimeMs = frameTimeMs,
        landmarks = listOf(
            landmark(11, 0.3, 0.2),
            landmark(12, 0.7, 0.2),
            landmark(23, 0.32, 0.5),
            landmark(24, 0.68, 0.5)
        )
    )

    private fun bodyPoint(
        frameTimeMs: Long,
        handHeight: Double,
        footHeight: Double?,
        facingCamera: Boolean = false
    ): FrameBodyPartHeights = FrameBodyPartHeights(
        frameTimeMs = frameTimeMs,
        handHeight = handHeight,
        footHeight = footHeight,
        facingCamera = facingCamera
    )
}
