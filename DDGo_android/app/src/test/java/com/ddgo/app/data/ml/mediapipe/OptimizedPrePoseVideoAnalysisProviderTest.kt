package com.ddgo.app.data.ml.mediapipe

import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult
import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OptimizedPrePoseVideoAnalysisProviderTest {

    @Test
    fun `optimized analysis succeeds without sequential fallback`() = runTest {
        val optimizedAnalyzer = mockk<UploadOptimizedPrePoseVideoAnalyzer>()
        val sequentialAnalyzer = mockk<SequentialPoseVideoAnalyzer>()
        val expected = prePoseAnalysisResult(processedFrameCount = 3)
        val provider = OptimizedPrePoseVideoAnalysisProvider(
            optimizedAnalyzer = optimizedAnalyzer,
            sequentialAnalyzer = sequentialAnalyzer
        )

        coEvery {
            optimizedAnalyzer.analyze("file:///upload.mp4", 10)
        } returns expected

        val actual = provider.analyze("file:///upload.mp4", 10)

        assertSame(expected, actual)
        coVerify(exactly = 1) { optimizedAnalyzer.analyze("file:///upload.mp4", 10) }
        coVerify(exactly = 0) { sequentialAnalyzer.analyze(any(), any()) }
    }

    @Test
    fun `optimized analysis failure falls back to sequential analyzer`() = runTest {
        val optimizedAnalyzer = mockk<UploadOptimizedPrePoseVideoAnalyzer>()
        val sequentialAnalyzer = mockk<SequentialPoseVideoAnalyzer>()
        val expected = prePoseAnalysisResult(processedFrameCount = 5)
        val provider = OptimizedPrePoseVideoAnalysisProvider(
            optimizedAnalyzer = optimizedAnalyzer,
            sequentialAnalyzer = sequentialAnalyzer
        )

        coEvery {
            optimizedAnalyzer.analyze("file:///upload.mp4", 10)
        } throws IllegalStateException("egl setup failed")
        coEvery {
            sequentialAnalyzer.analyze("file:///upload.mp4", 10)
        } returns expected

        val actual = provider.analyze("file:///upload.mp4", 10)

        assertSame(expected, actual)
        coVerify(exactly = 1) { optimizedAnalyzer.analyze("file:///upload.mp4", 10) }
        coVerify(exactly = 1) { sequentialAnalyzer.analyze("file:///upload.mp4", 10) }
    }

    @Test
    fun `optimized analysis cancellation does not fall back to sequential analyzer`() = runTest {
        val optimizedAnalyzer = mockk<UploadOptimizedPrePoseVideoAnalyzer>()
        val sequentialAnalyzer = mockk<SequentialPoseVideoAnalyzer>()
        val provider = OptimizedPrePoseVideoAnalysisProvider(
            optimizedAnalyzer = optimizedAnalyzer,
            sequentialAnalyzer = sequentialAnalyzer
        )

        coEvery {
            optimizedAnalyzer.analyze("file:///upload.mp4", 10)
        } throws CancellationException("timed out")

        try {
            provider.analyze("file:///upload.mp4", 10)
            fail("Expected CancellationException to be rethrown.")
        } catch (_: CancellationException) {
            // expected
        }

        coVerify(exactly = 1) { optimizedAnalyzer.analyze("file:///upload.mp4", 10) }
        coVerify(exactly = 0) { sequentialAnalyzer.analyze(any(), any()) }
    }

    private fun prePoseAnalysisResult(
        processedFrameCount: Int
    ): PrePoseVideoAnalysisResult {
        return PrePoseVideoAnalysisResult(
            aiPoseSequence = mockk<AiPoseSequence>(relaxed = true),
            poses = emptyList(),
            processedFrames = List(processedFrameCount) { index ->
                ProcessedPoseDetectionFrame(
                    timestampMs = index * 100L,
                    poseDetected = true
                )
            }
        )
    }
}
