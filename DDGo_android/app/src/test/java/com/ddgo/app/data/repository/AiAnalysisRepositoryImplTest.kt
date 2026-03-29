package com.ddgo.app.data.repository

import com.ddgo.app.core.config.AiAnalysisVariant
import com.ddgo.app.data.remote.ai.AiAnalysisApi
import com.ddgo.app.data.remote.ai.AiAnalysisRequestDto
import com.ddgo.app.data.remote.ai.AiAnalysisResponseDto
import com.ddgo.app.data.remote.ai.AiAnalysisVideoMetadataDto
import com.ddgo.app.data.remote.ai.AiCruxCandidateDto
import com.ddgo.app.data.remote.ai.AiCruxResultDto
import com.ddgo.app.data.remote.ai.AiCruxSegmentDto
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Hold
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AiAnalysisRepositoryImplTest {

    @Test
    fun `fast analyze builds expected payload and uses v1 plain endpoint`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi()
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V1
        )

        val result = repository.analyze(sampleContext(mode = AiAnalysisMode.FAST))

        assertTrue(result.isSuccess)
        assertNotNull(plainApi.lastFastRequest)
        assertNull(gzipApi.lastFastRequest)
        assertEquals("api/v1/mujoco-complete/analyze/fast", plainApi.lastFastUrl)

        val request = requireNotNull(plainApi.lastFastRequest)
        val holds = request.holdsJson.getArray("holds")
        val firstHold = holds[0].jsonObject
        val bbox = firstHold.getObject("bbox_px")
        val polygon = firstHold.getArray("polygon_px")
        val poseSequence = request.pose3dSequenceJson
        val frames = poseSequence.getArray("frames")
        val firstFrame = frames[0].jsonObject
        val userBody = request.userBodyJson
        val userProfile = userBody.getObject("user_profile")
        val calibrationCompat = userBody.getObject("calibration_compat")

        assertEquals(1, plainApi.fastCallCount)
        assertEquals(1, firstHold.getInt("hold_id"))
        assertEquals(100f, bbox.getFloat("x1"), 0.0001f)
        assertEquals(100f, bbox.getFloat("y1"), 0.0001f)
        assertEquals(400f, bbox.getFloat("x2"), 0.0001f)
        assertEquals(300f, bbox.getFloat("y2"), 0.0001f)
        assertEquals(2, polygon.size)
        assertEquals(100f, polygon[0].jsonObject.getFloat("x"), 0.0001f)
        assertEquals(125f, polygon[0].jsonObject.getFloat("y"), 0.0001f)
        assertEquals("file:///attempt.mp4", poseSequence.getObject("source").getString("video_uri"))
        val metadata = poseSequence.getObject("video_metadata")
        assertEquals(1920, metadata.getInt("frame_width"))
        assertEquals(10, metadata.getInt("analysis_fps_limit"))
        assertEquals(10f, metadata.getFloat("fps"), 0.0001f)
        assertTrue(firstFrame.containsKey("pose_world_landmarks"))
        assertEquals(1.8f, userProfile.getFloat("height_m"), 0.0001f)
        assertEquals(1.8f, userProfile.getFloat("wingspan_m"), 0.0001f)
        assertEquals(0f, userProfile.getFloat("weight_kg"), 0.0001f)
        assertEquals(0.32070857f, calibrationCompat.getFloat("upper_arm_m"), 0.0001f)
        assertEquals(1.8f, calibrationCompat.getFloat("wingspan_m"), 0.0001f)
        assertEquals(3, request.topKCrux)
        assertEquals(2, request.frameStep)
    }

    @Test
    fun `physics analyze uses v1 plain endpoint and keeps response payload`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi()
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V1
        )

        val result = repository.analyze(
            sampleContext(
                mode = AiAnalysisMode.PHYSICS,
                heightCm = 180f,
                weightKg = 70f,
                wingspanCm = 185f
            )
        ).getOrThrow()

        assertEquals(1, plainApi.physicsCallCount)
        assertEquals("api/v1/mujoco-complete/analyze/physics", plainApi.lastPhysicsUrl)
        assertNull(gzipApi.lastPhysicsRequest)
        assertEquals(AiAnalysisMode.PHYSICS, result.mode)
        assertNotNull(result.physicsSummary)
        assertNotNull(result.physicsResult)

        val request = requireNotNull(plainApi.lastPhysicsRequest)
        val userProfile = request.userBodyJson.getObject("user_profile")
        val calibrationCompat = request.userBodyJson.getObject("calibration_compat")

        assertEquals(1.85f, userProfile.getFloat("wingspan_m"), 0.0001f)
        assertEquals(70f, userProfile.getFloat("weight_kg"), 0.0001f)
        assertEquals(0.35942858f, calibrationCompat.getFloat("shoulder_width_m"), 0.0001f)
        assertEquals(
            1234L,
            result.cruxResult.topCandidates.first().bestSegment?.startTimeMs
        )
    }

    @Test
    fun `v2 gzip 10fps analyze uses gzip endpoint and api v2 path`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi()
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V2_GZIP_10FPS
        )

        repository.analyze(sampleContext(mode = AiAnalysisMode.FAST)).getOrThrow()

        assertEquals(0, plainApi.fastCallCount)
        assertEquals(1, gzipApi.fastCallCount)
        assertEquals("api/v2/mujoco-complete/analyze/fast", gzipApi.lastFastUrl)
    }

    @Test
    fun `v1 primary request respects frame cap`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi()
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V1
        )

        repository.analyze(
            sampleContext(
                mode = AiAnalysisMode.FAST,
                frameCount = 240,
                frameStep = 2
            )
        ).getOrThrow()

        val request = requireNotNull(plainApi.lastFastRequest)
        val frames = request.pose3dSequenceJson.getArray("frames")
        val metadata = request.pose3dSequenceJson.getObject("video_metadata")

        assertEquals(4, request.frameStep)
        assertEquals(60, frames.size)
        assertEquals(60, metadata.getInt("processed_frames"))
    }

    @Test
    fun `v2 gzip 10fps primary request keeps full normalized frame rate`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi()
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V2_GZIP_10FPS
        )

        repository.analyze(
            sampleContext(
                mode = AiAnalysisMode.FAST,
                frameCount = 240,
                frameStep = 1
            )
        ).getOrThrow()

        val request = requireNotNull(gzipApi.lastFastRequest)
        val frames = request.pose3dSequenceJson.getArray("frames")
        val metadata = request.pose3dSequenceJson.getObject("video_metadata")

        assertNull(plainApi.lastFastRequest)
        assertEquals(1, request.frameStep)
        assertEquals(240, frames.size)
        assertEquals(240, metadata.getInt("processed_frames"))
        assertEquals(10, metadata.getInt("analysis_fps_limit"))
        assertEquals(10f, metadata.getFloat("fps"), 0.0001f)
    }

    @Test
    fun `invalid pose frames are removed before request is sent`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi()
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V1
        )

        repository.analyze(
            sampleContext(
                mode = AiAnalysisMode.FAST,
                frameCount = 4,
                frameStep = 1,
                invalidFrameIndexes = setOf(0, 2)
            )
        ).getOrThrow()

        val request = requireNotNull(plainApi.lastFastRequest)
        val frames = request.pose3dSequenceJson.getArray("frames")
        val metadata = request.pose3dSequenceJson.getObject("video_metadata")

        assertEquals(2, frames.size)
        assertEquals(2, metadata.getInt("processed_frames"))
        assertEquals(10, metadata.getInt("analysis_fps_limit"))
        assertEquals(10f, metadata.getFloat("fps"), 0.0001f)
        assertEquals(1, frames[0].jsonObject.getInt("frame_index"))
        assertEquals(3, frames[1].jsonObject.getInt("frame_index"))
    }

    @Test
    fun `v2 gzip 10fps request entity too large retries with fewer sampled frames`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi().apply {
            enqueueFastFailure(requestEntityTooLargeException())
        }
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V2_GZIP_10FPS
        )

        val result = repository.analyze(
            sampleContext(
                mode = AiAnalysisMode.FAST,
                frameCount = 200,
                frameStep = 1
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(0, plainApi.fastCallCount)
        assertEquals(2, gzipApi.fastCallCount)
        assertEquals(2, gzipApi.fastRequests.size)
        assertEquals(1, gzipApi.fastRequests[0].frameStep)
        assertEquals(5, gzipApi.fastRequests[1].frameStep)
        assertTrue(
            gzipApi.fastRequests[1].pose3dSequenceJson.getArray("frames").size <
                gzipApi.fastRequests[0].pose3dSequenceJson.getArray("frames").size
        )
    }

    @Test
    fun `413 retry keeps fallback frame reachable when valid frames are off stride`() = runBlocking {
        val plainApi = RecordingAiAnalysisApi()
        val gzipApi = RecordingAiAnalysisApi().apply {
            enqueueFastFailure(requestEntityTooLargeException())
        }
        val repository = AiAnalysisRepositoryImpl(
            aiAnalysisApi = plainApi,
            aiAnalysisGzipApi = gzipApi,
            aiAnalysisVariant = AiAnalysisVariant.V2_GZIP_10FPS
        )

        val validFrameIndexes = (1 until 200 step 5).toSet()
        val invalidFrameIndexes = (0 until 200).filterNot(validFrameIndexes::contains).toSet()

        val result = repository.analyze(
            sampleContext(
                mode = AiAnalysisMode.FAST,
                frameCount = 200,
                frameStep = 1,
                invalidFrameIndexes = invalidFrameIndexes
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(0, plainApi.fastCallCount)
        assertEquals(2, gzipApi.fastCallCount)
        assertEquals(40, gzipApi.fastRequests[0].pose3dSequenceJson.getArray("frames").size)
        assertEquals(1, gzipApi.fastRequests[1].pose3dSequenceJson.getArray("frames").size)
        assertEquals(1, gzipApi.fastRequests[1].frameStep)
        assertEquals(
            1,
            gzipApi.fastRequests[1]
                .pose3dSequenceJson
                .getArray("frames")[0]
                .jsonObject
                .getInt("frame_index")
        )
    }

    private fun sampleContext(
        mode: AiAnalysisMode,
        heightCm: Float = 180f,
        weightKg: Float? = null,
        wingspanCm: Float? = null,
        frameCount: Int = 1,
        frameStep: Int = 2,
        invalidFrameIndexes: Set<Int> = emptySet()
    ): AiAnalysisRequestContext {
        return AiAnalysisRequestContext(
            mode = mode,
            holds = listOf(
                Hold(
                    holdNo = 1,
                    boundingBox = Hold.BoundingBox(
                        left = 0.1f,
                        top = 0.2f,
                        right = 0.4f,
                        bottom = 0.6f
                    ),
                    confidence = 0.92f,
                    polygon = listOf(
                        Hold.Point(x = 0.1f, y = 0.25f),
                        Hold.Point(x = 0.4f, y = 0.55f)
                    )
                )
            ),
            poseSequence = AiPoseSequence(
                source = AiPayloadSource(
                    uri = "file:///attempt.mp4",
                    videoUri = "file:///attempt.mp4",
                    generator = "test",
                    exportedAtIso = "2026-03-19T00:00:00Z"
                ),
                videoMetadata = AiVideoMetadata(
                    frameWidth = 1920,
                    frameHeight = 1080,
                    fps = 30f,
                    totalFrames = frameCount,
                    processedFrames = frameCount,
                    analysisFpsLimit = 10
                ),
                frames = List(frameCount) { frameIndex ->
                    val isInvalidFrame = frameIndex in invalidFrameIndexes
                    AiPoseFrame(
                        frameIndex = frameIndex,
                        timestampMs = 1234L + (frameIndex * 100L),
                        poseDetected = !isInvalidFrame,
                        poseLandmarks = if (isInvalidFrame) {
                            emptyList()
                        } else {
                            sampleLandmarks(offset = 0f)
                        },
                        poseWorldLandmarks = if (isInvalidFrame) {
                            emptyList()
                        } else {
                            sampleLandmarks(offset = 1f)
                        }
                    )
                }
            ),
            frameWidthPx = 1000,
            frameHeightPx = 500,
            heightCm = heightCm,
            weightKg = weightKg,
            wingspanCm = wingspanCm,
            topKCrux = 3,
            frameStep = frameStep
        )
    }

    private class RecordingAiAnalysisApi : AiAnalysisApi {
        var fastCallCount = 0
        var physicsCallCount = 0
        var lastFastUrl: String? = null
        var lastPhysicsUrl: String? = null
        var lastFastRequest: AiAnalysisRequestDto? = null
        var lastPhysicsRequest: AiAnalysisRequestDto? = null
        val fastRequests = mutableListOf<AiAnalysisRequestDto>()
        private val fastFailures = ArrayDeque<Throwable>()

        fun enqueueFastFailure(throwable: Throwable) {
            fastFailures.addLast(throwable)
        }

        override suspend fun analyzeFast(
            url: String,
            request: AiAnalysisRequestDto
        ): AiAnalysisResponseDto {
            fastCallCount += 1
            lastFastUrl = url
            lastFastRequest = request
            fastRequests += request
            if (fastFailures.isNotEmpty()) {
                throw fastFailures.removeFirst()
            }
            return successResponse(mode = "fast", includePhysicsPayload = false)
        }

        override suspend fun analyzePhysics(
            url: String,
            request: AiAnalysisRequestDto
        ): AiAnalysisResponseDto {
            physicsCallCount += 1
            lastPhysicsUrl = url
            lastPhysicsRequest = request
            return successResponse(mode = "physics", includePhysicsPayload = true)
        }
    }
}

private fun sampleLandmarks(offset: Float): List<AiLandmark3D> {
    return List(33) { index ->
        AiLandmark3D(
            index = index,
            x = offset + (index * 0.01f),
            y = offset + (index * 0.02f),
            z = offset + (index * 0.03f),
            visibility = 0.9f,
            presence = 0.8f
        )
    }
}

private fun successResponse(
    mode: String,
    includePhysicsPayload: Boolean
): AiAnalysisResponseDto {
    return AiAnalysisResponseDto(
        schemaVersion = "2026-03-19",
        mode = mode,
        videoMetadata = AiAnalysisVideoMetadataDto(
            frameWidth = 1920,
            frameHeight = 1080,
            fps = 30f,
            totalFrames = 120,
            processedFrames = 60,
            frameStep = 2
        ),
        cruxResult = AiCruxResultDto(
            candidateCount = 1,
            topCandidates = listOf(
                AiCruxCandidateDto(
                    holdId = 7,
                    segmentCount = 1,
                    engagementCount = 2,
                    totalActiveTimeSeconds = 1.2,
                    longestContinuousDwellSeconds = 0.8,
                    reasonTags = listOf("long_dwell"),
                    bestSegment = AiCruxSegmentDto(
                        startFrame = 3,
                        endFrame = 5,
                        startTimeMs = 1234L,
                        endTimeMs = 2234L,
                        durationSeconds = 1.0,
                        dominantLimbs = listOf("right_hand"),
                        dominantModes = listOf("pull")
                    ),
                    fastCruxScore = 0.75,
                    physicsCruxScore = if (includePhysicsPayload) 0.82 else null
                )
            )
        ),
        physicsSummary = if (includePhysicsPayload) {
            JsonObject(mapOf("status" to kotlinx.serialization.json.JsonPrimitive("ok")))
        } else {
            null
        },
        physicsResult = if (includePhysicsPayload) {
            JsonObject(mapOf("frames" to JsonArray(emptyList())))
        } else {
            null
        }
    )
}

private fun JsonObject.getArray(key: String): JsonArray = getValue(key).jsonArray

private fun JsonObject.getFloat(key: String): Float = getValue(key).jsonPrimitive.float

private fun JsonObject.getInt(key: String): Int = getValue(key).jsonPrimitive.int

private fun JsonObject.getObject(key: String): JsonObject = getValue(key).jsonObject

private fun JsonObject.getString(key: String): String = getValue(key).jsonPrimitive.content

private fun requestEntityTooLargeException(): HttpException {
    return HttpException(
        Response.error<AiAnalysisResponseDto>(
            413,
            "<html><body>413 Request Entity Too Large</body></html>"
                .toResponseBody("text/html".toMediaType())
        )
    )
}
