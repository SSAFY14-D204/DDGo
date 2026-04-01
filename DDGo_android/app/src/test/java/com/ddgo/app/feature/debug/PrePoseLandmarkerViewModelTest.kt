package com.ddgo.app.feature.debug

import android.net.Uri
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.poseanalysis.PoseFrame
import com.ddgo.app.domain.usecase.AnalyzeHandPeakAndEndUseCase
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrePoseLandmarkerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `analyzeVideo stores raw hand endpoint analysis point`() = runTest {
        val prePoseVideoAnalyzer = mockk<PrePoseVideoAnalyzer>()
        val optimizedPrePoseVideoAnalyzer = mockk<OptimizedPrePoseVideoAnalyzer>()
        val officialSampledPrePoseVideoAnalyzer = mockk<OfficialSampledPrePoseVideoAnalyzer>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val poseFrames = listOf(
            debugPoseFrameAt(0L),
            debugPoseFrameAt(1_000L)
        )
        val annotation = HandPeakAnnotation(
            globalTopTimeMs = 8_000L,
            globalTopHeight = 0.71,
            selectedTopTimeMs = 7_500L,
            selectedTopHeight = 0.69,
            supportCount = 12,
            endTimeMs = 5_000L,
            endHeight = 0.67,
            validTopFound = true
        )

        coEvery {
            prePoseVideoAnalyzer.invoke(any(), any(), any(), any())
        } returns Result.success(poseFrames)
        every { analyzeHandPeakAndEndUseCase(any<List<PoseFrame>>(), any()) } returns annotation

        val viewModel = PrePoseLandmarkerViewModel(
            prePoseVideoAnalyzer = prePoseVideoAnalyzer,
            optimizedPrePoseVideoAnalyzer = optimizedPrePoseVideoAnalyzer,
            officialSampledPrePoseVideoAnalyzer = officialSampledPrePoseVideoAnalyzer,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )

        viewModel.analyzeVideo(
            uri = fileUri(),
            displayName = "single_video.mp4",
            analysisMode = PrePoseAnalysisMode.NORMAL
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(annotation, uiState.handPeakAnnotation)
        assertEquals(1, uiState.analysisPoints.size)
        assertEquals(annotation.endTimeMs, uiState.analysisPoints.single().timeMs)
        verify(exactly = 1) { analyzeHandPeakAndEndUseCase(any<List<PoseFrame>>(), any()) }
    }

    @Test
    fun `analyzeVideo keeps endpoint output empty when analyzer returns null`() = runTest {
        val prePoseVideoAnalyzer = mockk<PrePoseVideoAnalyzer>()
        val optimizedPrePoseVideoAnalyzer = mockk<OptimizedPrePoseVideoAnalyzer>()
        val officialSampledPrePoseVideoAnalyzer = mockk<OfficialSampledPrePoseVideoAnalyzer>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val poseFrames = listOf(debugPoseFrameAt(0L))

        coEvery {
            prePoseVideoAnalyzer.invoke(any(), any(), any(), any())
        } returns Result.success(poseFrames)
        every { analyzeHandPeakAndEndUseCase(any<List<PoseFrame>>(), any()) } returns null

        val viewModel = PrePoseLandmarkerViewModel(
            prePoseVideoAnalyzer = prePoseVideoAnalyzer,
            optimizedPrePoseVideoAnalyzer = optimizedPrePoseVideoAnalyzer,
            officialSampledPrePoseVideoAnalyzer = officialSampledPrePoseVideoAnalyzer,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )

        viewModel.analyzeVideo(
            uri = fileUri(),
            displayName = "single_video.mp4",
            analysisMode = PrePoseAnalysisMode.NORMAL
        )
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertNull(uiState.handPeakAnnotation)
        assertTrue(uiState.analysisPoints.isEmpty())
    }

    @Test
    fun `analyzeVideo resets previous endpoint state when a new analysis starts`() = runTest {
        val prePoseVideoAnalyzer = mockk<PrePoseVideoAnalyzer>()
        val optimizedPrePoseVideoAnalyzer = mockk<OptimizedPrePoseVideoAnalyzer>()
        val officialSampledPrePoseVideoAnalyzer = mockk<OfficialSampledPrePoseVideoAnalyzer>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val poseFrames = listOf(debugPoseFrameAt(0L))
        val annotation = HandPeakAnnotation(
            globalTopTimeMs = 6_000L,
            globalTopHeight = 0.65,
            selectedTopTimeMs = 6_000L,
            selectedTopHeight = 0.65,
            supportCount = 10,
            endTimeMs = 3_000L,
            endHeight = 0.61,
            validTopFound = true
        )

        coEvery {
            prePoseVideoAnalyzer.invoke(any(), any(), any(), any())
        } returns Result.success(poseFrames)
        every { analyzeHandPeakAndEndUseCase(any<List<PoseFrame>>(), any()) } returns annotation

        val viewModel = PrePoseLandmarkerViewModel(
            prePoseVideoAnalyzer = prePoseVideoAnalyzer,
            optimizedPrePoseVideoAnalyzer = optimizedPrePoseVideoAnalyzer,
            officialSampledPrePoseVideoAnalyzer = officialSampledPrePoseVideoAnalyzer,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )

        viewModel.analyzeVideo(
            uri = fileUri(),
            displayName = "first_video.mp4",
            analysisMode = PrePoseAnalysisMode.NORMAL
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.analysisPoints.isNotEmpty())

        viewModel.analyzeVideo(
            uri = fileUri(),
            displayName = "second_video.mp4",
            analysisMode = PrePoseAnalysisMode.NORMAL
        )

        val uiState = viewModel.uiState.value
        assertTrue(uiState.isAnalyzing)
        assertNull(uiState.handPeakAnnotation)
        assertTrue(uiState.analysisPoints.isEmpty())
        assertTrue(uiState.poseFrames.isEmpty())
    }

    @Test
    fun `analyzeVideo forwards gpu toggle to optimized analyzer`() = runTest {
        val prePoseVideoAnalyzer = mockk<PrePoseVideoAnalyzer>()
        val optimizedPrePoseVideoAnalyzer = mockk<OptimizedPrePoseVideoAnalyzer>()
        val officialSampledPrePoseVideoAnalyzer = mockk<OfficialSampledPrePoseVideoAnalyzer>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val poseFrames = listOf(debugPoseFrameAt(0L))

        coEvery {
            optimizedPrePoseVideoAnalyzer.invoke(any(), any(), any(), any())
        } returns Result.success(poseFrames)
        every { analyzeHandPeakAndEndUseCase(any<List<PoseFrame>>(), any()) } returns null

        val viewModel = PrePoseLandmarkerViewModel(
            prePoseVideoAnalyzer = prePoseVideoAnalyzer,
            optimizedPrePoseVideoAnalyzer = optimizedPrePoseVideoAnalyzer,
            officialSampledPrePoseVideoAnalyzer = officialSampledPrePoseVideoAnalyzer,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )

        viewModel.analyzeVideo(
            uri = fileUri(),
            displayName = "gpu_video.mp4",
            analysisMode = PrePoseAnalysisMode.OPTIMIZED,
            useGpuAcceleration = true
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            optimizedPrePoseVideoAnalyzer.invoke(any(), 30, true, any())
        }
    }

    @Test
    fun `analyzeVideo dispatches official sampled mode to retriever analyzer`() = runTest {
        val prePoseVideoAnalyzer = mockk<PrePoseVideoAnalyzer>()
        val optimizedPrePoseVideoAnalyzer = mockk<OptimizedPrePoseVideoAnalyzer>()
        val officialSampledPrePoseVideoAnalyzer = mockk<OfficialSampledPrePoseVideoAnalyzer>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val poseFrames = listOf(debugPoseFrameAt(34L))

        coEvery {
            officialSampledPrePoseVideoAnalyzer.invoke(any(), any(), any(), any())
        } returns Result.success(poseFrames)
        every { analyzeHandPeakAndEndUseCase(any<List<PoseFrame>>(), any()) } returns null

        val viewModel = PrePoseLandmarkerViewModel(
            prePoseVideoAnalyzer = prePoseVideoAnalyzer,
            optimizedPrePoseVideoAnalyzer = optimizedPrePoseVideoAnalyzer,
            officialSampledPrePoseVideoAnalyzer = officialSampledPrePoseVideoAnalyzer,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )

        viewModel.analyzeVideo(
            uri = fileUri(),
            displayName = "official_sample.mp4",
            analysisMode = PrePoseAnalysisMode.OFFICIAL_SAMPLED,
            analysisFpsLimit = 30
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            officialSampledPrePoseVideoAnalyzer.invoke(any(), 30, true, any())
        }
    }

    private fun debugPoseFrameAt(frameTimeMs: Long): DebugPoseFrameResult = DebugPoseFrameResult(
        pose = Pose(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                poseLandmark(index = 11, x = 0.65f, y = 0.30f),
                poseLandmark(index = 12, x = 0.35f, y = 0.30f),
                poseLandmark(index = 23, x = 0.64f, y = 0.50f),
                poseLandmark(index = 24, x = 0.36f, y = 0.50f)
            )
        ),
        worldLandmarks = emptyList(),
        capturedBitmap = null
    )

    private fun poseLandmark(index: Int, x: Float, y: Float): PoseLandmark = PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = 0f,
        visibility = 0.99f,
        presence = 0.99f
    )

    private fun fileUri(): Uri = mockk<Uri>(relaxed = true)
}
