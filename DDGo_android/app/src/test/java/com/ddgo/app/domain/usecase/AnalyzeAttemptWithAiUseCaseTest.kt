package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AiAnalysisFallbackReason
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisRequestContext
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.repository.AiAnalysisRepository
import com.ddgo.app.domain.repository.AiPoseSequenceProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeAttemptWithAiUseCaseTest {

    @Test
    fun `physics mode without weight falls back to fast`() = runBlocking {
        val provider = FakeAiPoseSequenceProvider()
        val repository = FakeAiAnalysisRepository()
        val useCase = AnalyzeAttemptWithAiUseCase(provider, repository)

        val result = useCase(
            mode = AiAnalysisMode.PHYSICS,
            videoUri = "file:///attempt.mp4",
            holds = listOf(sampleHold()),
            frameWidthPx = 1000,
            frameHeightPx = 500,
            heightCm = 180f,
            weightKg = null,
            wingspanCm = 181f
        ).getOrThrow()

        assertEquals(1, provider.callCount)
        assertEquals(1, repository.callCount)
        assertEquals(AiAnalysisMode.FAST, repository.contexts.single().mode)
        assertEquals(AiAnalysisMode.FAST, result.mode)
        assertEquals(AiAnalysisMode.PHYSICS, result.requestedMode)
        assertEquals(AiAnalysisFallbackReason.MISSING_WEIGHT, result.fallbackReason)
    }

    @Test
    fun `physics failure falls back to fast`() = runBlocking {
        val provider = FakeAiPoseSequenceProvider()
        val repository = FakeAiAnalysisRepository().apply {
            enqueue(
                AiAnalysisMode.PHYSICS,
                Result.failure(IllegalStateException("physics failed"))
            )
            enqueue(
                AiAnalysisMode.FAST,
                Result.success(sampleResult(mode = AiAnalysisMode.FAST))
            )
        }
        val useCase = AnalyzeAttemptWithAiUseCase(provider, repository)

        val result = useCase(
            mode = AiAnalysisMode.PHYSICS,
            videoUri = "file:///attempt.mp4",
            holds = listOf(sampleHold()),
            frameWidthPx = 1000,
            frameHeightPx = 500,
            heightCm = 180f,
            weightKg = 70f,
            wingspanCm = 181f
        ).getOrThrow()

        assertEquals(1, provider.callCount)
        assertEquals(2, repository.callCount)
        assertEquals(listOf(AiAnalysisMode.PHYSICS, AiAnalysisMode.FAST), repository.contexts.map { it.mode })
        assertEquals(AiAnalysisMode.FAST, result.mode)
        assertEquals(AiAnalysisMode.PHYSICS, result.requestedMode)
        assertEquals(AiAnalysisFallbackReason.PHYSICS_REQUEST_FAILED, result.fallbackReason)
    }

    @Test
    fun `valid request builds context from pose sequence and delegates to repository`() = runBlocking {
        val provider = FakeAiPoseSequenceProvider()
        val repository = FakeAiAnalysisRepository()
        val useCase = AnalyzeAttemptWithAiUseCase(provider, repository)

        val result = useCase(
            mode = AiAnalysisMode.FAST,
            videoUri = "file:///attempt.mp4",
            holds = listOf(sampleHold()),
            frameWidthPx = 1000,
            frameHeightPx = 500,
            heightCm = 180f,
            weightKg = null,
            wingspanCm = 181f,
            analysisFpsLimit = 12,
            topKCrux = 4,
            frameStep = 3
        )

        assertTrue(result.isSuccess)
        assertEquals(1, provider.callCount)
        assertEquals("file:///attempt.mp4", provider.lastVideoUri)
        assertEquals(12, provider.lastAnalysisFpsLimit)
        assertEquals(1, repository.callCount)

        val context = repository.contexts.single()
        assertEquals(AiAnalysisMode.FAST, context.mode)
        assertEquals(1000, context.frameWidthPx)
        assertEquals(500, context.frameHeightPx)
        assertEquals(180f, context.heightCm)
        assertEquals(181f, context.wingspanCm)
        assertEquals(4, context.topKCrux)
        assertEquals(3, context.frameStep)
        assertFalse(context.poseSequence.frames.isEmpty())
    }

    @Test
    fun `cached pose sequence skips provider and is delegated as request context`() = runBlocking {
        val provider = FakeAiPoseSequenceProvider()
        val repository = FakeAiAnalysisRepository()
        val useCase = AnalyzeAttemptWithAiUseCase(provider, repository)
        val cachedPoseSequence = provider.analyzePoseSequence(
            videoUri = "file:///cached_attempt.mp4",
            analysisFpsLimit = 10
        )
        provider.callCount = 0
        provider.lastVideoUri = null
        provider.lastAnalysisFpsLimit = null

        val result = useCase(
            mode = AiAnalysisMode.FAST,
            videoUri = "file:///cached_attempt.mp4",
            holds = listOf(sampleHold()),
            frameWidthPx = 1000,
            frameHeightPx = 500,
            heightCm = 180f,
            weightKg = null,
            wingspanCm = 181f,
            analysisFpsLimit = 12,
            cachedPoseSequence = cachedPoseSequence
        )

        assertTrue(result.isSuccess)
        assertEquals(0, provider.callCount)
        assertEquals(1, repository.callCount)
        assertEquals(cachedPoseSequence, repository.contexts.single().poseSequence)
    }
}

private class FakeAiPoseSequenceProvider : AiPoseSequenceProvider {
    var callCount = 0
    var lastVideoUri: String? = null
    var lastAnalysisFpsLimit: Int? = null

    override suspend fun analyzePoseSequence(
        videoUri: String,
        analysisFpsLimit: Int
    ): AiPoseSequence {
        callCount += 1
        lastVideoUri = videoUri
        lastAnalysisFpsLimit = analysisFpsLimit
        return AiPoseSequence(
            source = AiPayloadSource(
                uri = videoUri,
                videoUri = videoUri,
                generator = "test",
                exportedAtIso = "2026-03-19T00:00:00Z"
            ),
            videoMetadata = AiVideoMetadata(
                frameWidth = 1920,
                frameHeight = 1080,
                fps = 30f,
                totalFrames = 120,
                processedFrames = 60,
                analysisFpsLimit = analysisFpsLimit
            ),
            frames = listOf(
                AiPoseFrame(
                    frameIndex = 0,
                    timestampMs = 1234L,
                    poseDetected = true,
                    poseLandmarks = listOf(AiLandmark3D(index = 0, x = 0.1f, y = 0.2f, z = 0.3f)),
                    poseWorldLandmarks = listOf(AiLandmark3D(index = 0, x = 1.1f, y = 1.2f, z = 1.3f))
                )
            )
        )
    }
}

private class FakeAiAnalysisRepository : AiAnalysisRepository {
    var callCount = 0
    val contexts = mutableListOf<AiAnalysisRequestContext>()
    private val queuedResults = mutableMapOf<AiAnalysisMode, ArrayDeque<Result<AiAnalysisResult>>>()

    fun enqueue(mode: AiAnalysisMode, result: Result<AiAnalysisResult>) {
        val queue = queuedResults.getOrPut(mode) { ArrayDeque() }
        queue.addLast(result)
    }

    override suspend fun analyze(context: AiAnalysisRequestContext): Result<AiAnalysisResult> {
        callCount += 1
        contexts += context
        val queued = queuedResults[context.mode]
        return if (queued != null && queued.isNotEmpty()) {
            queued.removeFirst()
        } else {
            Result.success(sampleResult(mode = context.mode))
        }
    }
}

private fun sampleResult(mode: AiAnalysisMode): AiAnalysisResult {
    return AiAnalysisResult(
        mode = mode,
        schemaVersion = "2026-03-19",
        videoMetadata = null,
        timingsSeconds = emptyMap(),
        correctionSummary = null,
        cruxResult = AiCruxResult(
            candidateCount = 0,
            topCandidates = emptyList(),
            allCandidates = emptyList()
        ),
        rawResponse = JsonObject(emptyMap())
    )
}

private fun sampleHold(): Hold {
    return Hold(
        holdNo = 1,
        boundingBox = Hold.BoundingBox(left = 0.1f, top = 0.2f, right = 0.4f, bottom = 0.6f),
        confidence = 0.9f,
        polygon = listOf(
            Hold.Point(x = 0.1f, y = 0.25f),
            Hold.Point(x = 0.4f, y = 0.55f)
        )
    )
}
