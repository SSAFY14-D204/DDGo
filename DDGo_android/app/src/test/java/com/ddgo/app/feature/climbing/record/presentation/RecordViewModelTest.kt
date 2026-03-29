package com.ddgo.app.feature.climbing.record.presentation

import com.ddgo.app.data.wear.RecordingStateSyncManager
import com.ddgo.app.data.wear.WatchRuntimeMonitor
import com.ddgo.app.data.wear.WatchRuntimeSyncSnapshot
import com.ddgo.app.domain.repository.LivePoseAnalysisSummary
import com.ddgo.app.domain.repository.LivePoseAnalyzerRepository
import com.ddgo.app.domain.repository.LivePoseFrameInput
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {

    @get:Rule
    val mainDispatcherRule = RecordMainDispatcherRule()

    @Test
    fun `recording stop keeps only batch upload draft metadata`() = runTest {
        val recordingStateSyncManager = mockk<RecordingStateSyncManager>(relaxed = true)
        val watchRuntimeMonitor = mockk<WatchRuntimeMonitor>()
        val snapshotFlow = MutableStateFlow(WatchRuntimeSyncSnapshot())
        every { watchRuntimeMonitor.snapshot } returns snapshotFlow
        every { watchRuntimeMonitor.start() } returns Unit
        every { watchRuntimeMonitor.stop() } returns Unit

        val livePoseAnalyzerRepository = mockk<LivePoseAnalyzerRepository>()
        coEvery { livePoseAnalyzerRepository.start(any(), any(), any()) } returns Result.success(Unit)

        val frameInput = LivePoseFrameInput(
            frameIndex = 0,
            timestampMs = 100L,
            width = 1080,
            height = 1920,
            rotationDegrees = 0,
            argb8888Bytes = ByteArray(4)
        )
        coEvery { livePoseAnalyzerRepository.submitFrame(frameInput) } returns Result.success(Unit)
        coEvery { livePoseAnalyzerRepository.stop() } returns Result.success(
            LivePoseAnalysisSummary(
                submittedFrameCount = 1,
                detectedFrameCount = 1,
                lastFrameTimestampMs = 100L
            )
        )

        val viewModel = RecordViewModel(
            recordingStateSyncManager = recordingStateSyncManager,
            watchRuntimeMonitor = watchRuntimeMonitor,
            livePoseAnalyzerRepository = livePoseAnalyzerRepository
        )

        viewModel.onPermissionChanged(true)
        viewModel.onCameraBound()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRecordingStarted()
        viewModel.submitLivePoseFrame(frameInput)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRecordingStopped(
            RecordedAttemptDraft(videoUri = "file:///recorded_attempt.mp4")
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val recordedDraft = viewModel.uiState.value.recordedDraft
        assertEquals("file:///recorded_attempt.mp4", recordedDraft?.videoUri)
        assertEquals(1080, recordedDraft?.frameWidthPx)
        assertEquals(1920, recordedDraft?.frameHeightPx)
        assertFalse(viewModel.uiState.value.isRealtimeUploadActive)
        assertEquals(0, viewModel.uiState.value.uploadedPoseFrameCount)
        assertTrue(viewModel.uiState.value.statusMessage.contains("batch AI"))

        coVerify(exactly = 1) { livePoseAnalyzerRepository.submitFrame(frameInput) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RecordMainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
