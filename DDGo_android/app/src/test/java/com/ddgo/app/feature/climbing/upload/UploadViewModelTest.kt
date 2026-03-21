package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.MutableState
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult
import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.repository.AttemptRepository
import com.ddgo.app.domain.repository.ChallengeRepository
import com.ddgo.app.domain.repository.GymRepository
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.repository.PrePoseVideoAnalysisProvider
import com.ddgo.app.domain.repository.PoseEstimator
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.AnalyzeAttemptWithAiUseCase
import com.ddgo.app.domain.usecase.AnalyzeHandPeakAndEndUseCase
import com.ddgo.app.domain.usecase.AttachAiRealtimeContextUseCase
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.DetectStablePersonObservationUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.FinalizeAiRealtimeSessionUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import com.ddgo.app.domain.usecase.summarizeHoldReachResults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.lang.reflect.Field
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
        unmockkStatic(android.net.Uri::class)
        tempDirs.forEach(File::deleteRecursively)
        tempDirs.clear()
    }

    @Test
    fun `submitUpload은 첫 pre-pose 결과를 재사용하고 estimator를 다시 호출하지 않는다`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(
                poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
                poseAt(500L, handLandmark(index = 20, x = 0.62f, y = 0.34f))
            ),
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true),
                processedFrame(300L, true),
                processedFrame(400L, true)
            )
        )

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val videoUri = "file:///single_attempt.mp4"

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true))
        setPrivateField(
            viewModel,
            "detectedHolds",
            listOf(
                hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1),
                hold(centerX = 0.62f, centerY = 0.34f, holdNo = 2)
            )
        )
        setPrivateField(
            viewModel,
            "numberedHolds",
            listOf(
                numberedHold(holdNo = 1, centerX = 0.20f, centerY = 0.82f, role = HoldRole.START),
                numberedHold(holdNo = 2, centerX = 0.62f, centerY = 0.34f, role = HoldRole.END)
            )
        )

        setPrivateField(viewModel, "videoUri", videoUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.prePoseBatchState.readyCount == 1
        }

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success
        }

        coVerify(exactly = 1) { prePoseVideoAnalysisProvider.analyze(videoUri, any()) }
        assertEquals(listOf(videoUri), viewModel.playbackAttemptUris)
        assertEquals(1, viewModel.attemptHoldReachResults.size)
        assertEquals(2, viewModel.currentAttemptPoseSequence.size)
    }

    @Test
    fun `submitUpload passes cached ai pose sequence into batch ai use case`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val cachedAiPoseSequence = aiPoseSequence(
            videoUri = "file:///ai_cached.mp4",
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true)
            )
        )
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f))),
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true)
            ),
            aiPoseSequence = cachedAiPoseSequence
        )

        val cachedSequenceSlot = slot<AiPoseSequence>()
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        coEvery {
            analyzeAttemptWithAiUseCase.invoke(
                mode = any(),
                videoUri = any(),
                holds = any(),
                frameWidthPx = any(),
                frameHeightPx = any(),
                heightCm = any(),
                weightKg = any(),
                wingspanCm = any(),
                analysisFpsLimit = any(),
                cachedPoseSequence = capture(cachedSequenceSlot),
                topKCrux = any(),
                frameStep = any()
            )
        } returns Result.success(
            AiAnalysisResult(
                mode = AiAnalysisMode.FAST,
                schemaVersion = "test",
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
        )

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )
        val videoUri = "file:///ai_cached.mp4"

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1920
        })
        setPrivateField(viewModel, "detectedHolds", listOf(hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)))
        setPrivateField(
            viewModel,
            "numberedHolds",
            listOf(numberedHold(holdNo = 1, centerX = 0.20f, centerY = 0.82f, role = HoldRole.START))
        )

        setPrivateField(viewModel, "videoUri", videoUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.prePoseBatchState.readyCount == 1
        }

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success
        }

        assertEquals(cachedAiPoseSequence, cachedSequenceSlot.captured)
    }

    @Test
    fun `pre-pose 실패 후 submitUpload에서도 estimator를 자동 재호출하지 않는다`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } throws IllegalStateException("boom") andThen
            prePoseAnalysisResult(
                poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f))),
                processedFrames = listOf(
                    processedFrame(0L, true),
                    processedFrame(100L, true),
                    processedFrame(200L, true)
                )
            )

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val videoUri = "file:///failed_prepose.mp4"

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true))
        setPrivateField(viewModel, "detectedHolds", listOf(hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)))
        setPrivateField(
            viewModel,
            "numberedHolds",
            listOf(numberedHold(holdNo = 1, centerX = 0.20f, centerY = 0.82f, role = HoldRole.START))
        )

        setPrivateField(viewModel, "videoUri", videoUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.prePoseBatchState.failedCount == 1
        }

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success &&
                viewModel.currentAttemptPrePoseEntry?.status == PrePoseStatus.Ready
        }

        coVerify(exactly = 2) { prePoseVideoAnalysisProvider.analyze(videoUri, any()) }
        assertEquals(PrePoseStatus.Ready, viewModel.currentAttemptPrePoseEntry?.status)
        assertEquals(1, viewModel.currentAttemptPoseSequence.size)
        assertEquals(1, viewModel.attemptHoldReachResults.size)
    }

    @Test
    fun `submitUpload shows error when retried pre-pose still has no reusable media pipe cache`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } throws IllegalStateException("boom")

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val videoUri = "file:///failed_prepose_twice.mp4"

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true))
        setPrivateField(viewModel, "detectedHolds", listOf(hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)))
        setPrivateField(
            viewModel,
            "numberedHolds",
            listOf(numberedHold(holdNo = 1, centerX = 0.20f, centerY = 0.82f, role = HoldRole.START))
        )

        setPrivateField(viewModel, "videoUri", videoUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.prePoseBatchState.failedCount == 1
        }

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Error
        }

        coVerify(exactly = 2) { prePoseVideoAnalysisProvider.analyze(videoUri, any()) }
        assertTrue(viewModel.currentAttemptPoseSequence.isEmpty())
        assertEquals(PrePoseStatus.Failed, viewModel.currentAttemptPrePoseEntry?.status)
    }

    @Test
    fun `홀드 재선택은 pre-pose를 유지하고 hold reach만 초기화한다`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk<PoseEstimator>(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )
        val playbackUri = "file:///hold_reselect.mp4"
        val readyPoses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)))
        val startHold = hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)
        val endHold = hold(centerX = 0.62f, centerY = 0.34f, holdNo = 2)

        setPrivateField(viewModel, "videoUri", playbackUri)
        setPrivateField(viewModel, "resultPlaybackUris", listOf(playbackUri))
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                playbackUri to PrePoseCacheEntry(
                    playbackUri = playbackUri,
                    selectionGeneration = 1L,
                    status = PrePoseStatus.Ready,
                    poses = readyPoses
                )
            )
        )
        setPrivateField(viewModel, "detectedHolds", listOf(startHold, endHold))
        setPrivateField(
            viewModel,
            "attemptHoldReachResults",
            listOf(
                AttemptHoldReachResult(
                    highestReachedHold = null,
                    highestReachedHoldNo = 2,
                    highestReachedFrameTimeMs = 500L,
                    totalHoldCount = 2,
                    contactedHoldNos = setOf(1, 2),
                    reachedRatio = 1f
                )
            )
        )

        viewModel.updateSelectedStartHold(startHold)
        viewModel.updateSelectedEndHold(endHold)

        assertEquals(readyPoses, viewModel.currentAttemptPoseSequence)
        assertTrue(viewModel.attemptHoldReachResults.isEmpty())
        assertEquals(2, viewModel.numberedHolds.size)
    }

    @Test
    fun `AttemptResult에서 추가 시도 업로드를 취소하면 기존 결과 세션을 복구한다`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        val publishedUris = listOf("file:///published_1.mp4", "file:///published_2.mp4")
        val publishedResults = listOf(
            AttemptHoldReachResult(
                highestReachedHold = null,
                highestReachedHoldNo = 2,
                highestReachedFrameTimeMs = 500L,
                totalHoldCount = 4,
                contactedHoldNos = setOf(1, 2),
                reachedRatio = 0.5f
            ),
            AttemptHoldReachResult(
                highestReachedHold = null,
                highestReachedHoldNo = 3,
                highestReachedFrameTimeMs = 900L,
                totalHoldCount = 4,
                contactedHoldNos = setOf(1, 2, 3),
                reachedRatio = 0.75f
            )
        )
        val publishedSummary = summarizeHoldReachResults(
            results = publishedResults,
            totalHoldCount = 4
        )
        val publishedPoses = mapOf(
            publishedUris[0] to PrePoseCacheEntry(
                playbackUri = publishedUris[0],
                selectionGeneration = 1L,
                status = PrePoseStatus.Ready,
                poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)))
            ),
            publishedUris[1] to PrePoseCacheEntry(
                playbackUri = publishedUris[1],
                selectionGeneration = 1L,
                status = PrePoseStatus.Ready,
                poses = listOf(poseAt(500L, handLandmark(index = 20, x = 0.55f, y = 0.45f)))
            )
        )

        setPrivateField(viewModel, "challengeId", 77L)
        setPrivateField(viewModel, "resultPlaybackUris", publishedUris)
        setPrivateField(
            viewModel,
            "uploadedAttemptVideos",
            listOf(
                uploadedAttemptVideo(attemptId = 10L, attemptNo = 1, videoUri = publishedUris[0]),
                uploadedAttemptVideo(attemptId = 11L, attemptNo = 2, videoUri = publishedUris[1])
            )
        )
        setPrivateField(viewModel, "attemptHoldReachResults", publishedResults)
        setPrivateField(viewModel, "overallHoldReachSummary", publishedSummary)
        setPrivateField(viewModel, "currentAttemptIndex", 1)
        setPrivateField(viewModel, "prePoseCacheEntries", publishedPoses)

        assertTrue(viewModel.enterAttemptOnlyUploadMode())

        val draftUri = "file:///draft_attempt.mp4"
        setPrivateField(viewModel, "attemptOnlyVideoUris", listOf(draftUri))
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            publishedPoses + (
                draftUri to PrePoseCacheEntry(
                    playbackUri = draftUri,
                    selectionGeneration = 2L,
                    status = PrePoseStatus.Pending,
                    taskId = 99L
                )
            )
        )

        setPrivateField(viewModel, "resultPlaybackUris", emptyList<String>())
        setPrivateField(viewModel, "uploadedAttemptVideos", emptyList<UploadedAttemptVideo>())
        setPrivateField(viewModel, "attemptHoldReachResults", emptyList<AttemptHoldReachResult>())
        setPrivateField(viewModel, "overallHoldReachSummary", null as OverallHoldReachSummary?)
        setPrivateField(viewModel, "currentAttemptIndex", 0)

        viewModel.cancelAttemptOnlyUploadMode()

        @Suppress("UNCHECKED_CAST")
        val restoredCache = getPrivateField(viewModel, "prePoseCacheEntries") as Map<String, PrePoseCacheEntry>

        assertFalse(viewModel.isAttemptOnlyUploadMode)
        assertEquals(publishedUris, viewModel.playbackAttemptUris)
        assertEquals(1, viewModel.currentAttemptIndex)
        assertEquals(publishedResults[1], viewModel.currentAttemptHoldReachResult)
        assertEquals(publishedPoses.getValue(publishedUris[1]).poses, viewModel.currentAttemptPoseSequence)
        assertFalse(restoredCache.containsKey(draftUri))
    }

    @Test
    fun `pre-pose success caches raw hand endpoint timeline when hand peak end is detected`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val poses = listOf(poseAt(0L, handLandmark(index = 15, x = 0.20f, y = 0.60f)))
        val annotation = HandPeakAnnotation(
            globalTopTimeMs = 6_000L,
            globalTopHeight = 0.71,
            selectedTopTimeMs = 5_500L,
            selectedTopHeight = 0.69,
            supportCount = 12,
            endTimeMs = 4_000L,
            endHeight = 0.67,
            validTopFound = true
        )
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = poses,
            processedFrames = listOf(
                processedFrame(1_000L, false),
                processedFrame(2_000L, true),
                processedFrame(2_100L, true),
                processedFrame(2_200L, true),
                processedFrame(2_300L, true),
                processedFrame(2_400L, true)
            )
        )
        every { analyzeHandPeakAndEndUseCase(any(), any()) } returns annotation

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )
        val videoUri = "file:///detected_end.mp4"

        setPrivateField(viewModel, "videoUri", videoUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.prePoseBatchState.readyCount == 1
        }

        val entry = viewModel.currentAttemptPrePoseEntry
        assertEquals(PrePoseStatus.Ready, entry?.status)
        assertTrue(entry?.aiPoseSequence != null)
        assertTrue(entry?.poses?.isNotEmpty() == true)
        assertEquals(6, entry?.processedFrames?.size)
        assertEquals(2_000L, entry?.personObservationStartTimeMs)
        assertEquals(2, entry?.timelinePoints?.size)
        assertEquals(2_000L, entry?.timelinePoints?.get(0)?.timeMs)
        assertEquals(4_000L, entry?.timelinePoints?.get(1)?.timeMs)
        assertEquals(1, entry?.timelinePoints?.get(0)?.index)
        assertEquals(2, entry?.timelinePoints?.get(1)?.index)
    }

    @Test
    fun `hold precompute does not start while current pre-pose is still pending`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        setPrivateField(viewModel, "videoUri", "file:///current.mp4")
        viewModel.useDebugBestFrameImage("file:///debug_frame.png")
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                "file:///current.mp4" to PrePoseCacheEntry(
                    playbackUri = "file:///current.mp4",
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Pending,
                    taskId = 1L
                )
            )
        )

        viewModel.markHoldPrecomputeEligibleForCurrentSelection()
        invokePrivateMethod(
            target = viewModel,
            methodName = "maybeStartHoldPrecomputeForCurrentSelection"
        )

        assertTrue(viewModel.allRawHolds.isEmpty())
        assertEquals(null, getPrivateField(viewModel, "holdPrecomputeJob"))
    }

    @Test
    fun `attempt only upload mode에서는 hold precompute를 자동 시작하지 않는다`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        setPrivateField(viewModel, "uploadFlowMode", UploadFlowMode.AttemptOnly)
        setPrivateField(viewModel, "videoUri", "file:///attempt_only.mp4")
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                "file:///attempt_only.mp4" to PrePoseCacheEntry(
                    playbackUri = "file:///attempt_only.mp4",
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready
                )
            )
        )

        viewModel.markHoldPrecomputeEligibleForCurrentSelection()
        invokePrivateMethod(
            target = viewModel,
            methodName = "maybeStartHoldPrecomputeForCurrentSelection"
        )

        assertEquals(null, getPrivateField(viewModel, "holdPrecomputeRequestedGeneration"))
        assertEquals(null, getPrivateField(viewModel, "holdPrecomputeJob"))
    }

    private fun createViewModel(
        context: Context = mockContext(),
        poseEstimator: PoseEstimator,
        prePoseVideoAnalysisProvider: PrePoseVideoAnalysisProvider,
        analyzeHandPeakAndEndUseCase: AnalyzeHandPeakAndEndUseCase = AnalyzeHandPeakAndEndUseCase(),
        analyzeAttemptWithAiUseCase: AnalyzeAttemptWithAiUseCase = mockk(),
        stubAnalyzeAttemptWithAiUseCase: Boolean = true,
        personDetector: PersonDetector = mockk(relaxed = true),
        holdDetector: HoldDetector = mockk(relaxed = true),
        holdColorClassifier: HoldColorClassifier = HoldColorClassifier()
    ): UploadViewModel {
        val challengeRepository = mockk<ChallengeRepository>(relaxed = true)
        val attemptRepository = mockk<AttemptRepository>(relaxed = true)
        val gymRepository = mockk<GymRepository>(relaxed = true)
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>()
        val attachAiRealtimeContextUseCase = mockk<AttachAiRealtimeContextUseCase>()
        val finalizeAiRealtimeSessionUseCase = mockk<FinalizeAiRealtimeSessionUseCase>()

        coEvery { getMyInfoUseCase.invoke() } returns Result.success(
            User(
                id = 1L,
                username = "tester",
                nickname = "tester",
                heightCm = 175f,
                wingspanCm = 176f,
                weightKg = 70f
            )
        )
        if (stubAnalyzeAttemptWithAiUseCase) {
            coEvery {
                analyzeAttemptWithAiUseCase.invoke(
                    mode = any(),
                    videoUri = any(),
                    holds = any(),
                    frameWidthPx = any(),
                    frameHeightPx = any(),
                    heightCm = any(),
                    weightKg = any(),
                    wingspanCm = any(),
                    analysisFpsLimit = any(),
                    cachedPoseSequence = any(),
                    topKCrux = any(),
                    frameStep = any()
                )
            } returns Result.success(
                AiAnalysisResult(
                    mode = AiAnalysisMode.FAST,
                    schemaVersion = "test",
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
            )
        }
        coEvery {
            attachAiRealtimeContextUseCase.invoke(
                session = any(),
                request = any()
            )
        } returns Result.failure(IllegalStateException("unused in baseline upload tests"))
        coEvery {
            finalizeAiRealtimeSessionUseCase.invoke(any())
        } returns Result.failure(IllegalStateException("unused in baseline upload tests"))

        return UploadViewModel(
            context = context,
            personDetector = personDetector,
            holdDetector = holdDetector,
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            holdColorClassifier = holdColorClassifier,
            searchNearbyClimbingGymsUseCase = SearchNearbyClimbingGymsUseCase(gymRepository),
            resolveGymUseCase = ResolveGymUseCase(gymRepository),
            createChallengeUseCase = CreateChallengeUseCase(challengeRepository),
            saveChallengeHoldsUseCase = SaveChallengeHoldsUseCase(challengeRepository),
            uploadAttemptVideoUseCase = UploadAttemptVideoUseCase(attemptRepository),
            endAttemptUseCase = EndAttemptUseCase(attemptRepository),
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase,
            detectStablePersonObservationUseCase = DetectStablePersonObservationUseCase(),
            getMyInfoUseCase = getMyInfoUseCase,
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            attachAiRealtimeContextUseCase = attachAiRealtimeContextUseCase,
            finalizeAiRealtimeSessionUseCase = finalizeAiRealtimeSessionUseCase
        )
    }

    private fun mockContext(): Context {
        val cacheDir = createTempDirectory("upload_vm_cache_").toFile()
        tempDirs += cacheDir
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        every { context.contentResolver } returns mockk(relaxed = true)
        return context
    }

    private fun waitUntil(
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean
    ) {
        val startedAt = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - startedAt > timeoutMs) {
                throw AssertionError("Condition was not met within ${timeoutMs}ms")
            }
            Thread.sleep(25L)
        }
    }

    private fun setPrivateField(target: Any, fieldName: String, value: Any?) {
        val (owner, field) = resolveFieldOwner(target, fieldName)
        if (field.name.endsWith("\$delegate")) {
            @Suppress("UNCHECKED_CAST")
            val state = field.get(owner) as MutableState<Any?>
            state.value = value
        } else {
            field.set(owner, value)
        }
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val (owner, field) = resolveFieldOwner(target, fieldName)
        if (field.name.endsWith("\$delegate")) {
            @Suppress("UNCHECKED_CAST")
            val state = field.get(owner) as MutableState<Any?>
            return state.value
        }
        return field.get(owner)
    }

    private fun resolveFieldOwner(
        target: Any,
        fieldName: String,
        visited: MutableSet<Any> = mutableSetOf()
    ): Pair<Any, Field> {
        if (!visited.add(target)) {
            throw NoSuchFieldException(fieldName)
        }

        resolveField(target, fieldName)?.let { return target to it }

        target.javaClass.declaredFields
            .filter { field -> field.name.endsWith("Delegate") }
            .forEach { delegateField ->
                delegateField.isAccessible = true
                val delegate = delegateField.get(target) ?: return@forEach
                try {
                    return resolveFieldOwner(delegate, fieldName, visited)
                } catch (_: NoSuchFieldException) {
                    // Try the next delegate field.
                }
            }

        throw NoSuchFieldException(fieldName)
    }

    private fun resolveField(target: Any, fieldName: String): Field? = target.javaClass.declaredFields
        .firstOrNull { field ->
            field.name == fieldName || field.name == "${fieldName}\$delegate" || field.name.startsWith(fieldName)
        }
        ?.apply { isAccessible = true }

    private fun invokePrivateMethod(target: Any, methodName: String, vararg args: Any?) {
        val method = target.javaClass.declaredMethods.firstOrNull { method ->
            method.name == methodName && method.parameterCount == args.size
        }
            ?: throw NoSuchMethodException(methodName)
        method.isAccessible = true
        method.invoke(target, *args)
    }

    private fun poseAt(frameTimeMs: Long, vararg landmarks: PoseLandmark): Pose = Pose(
        frameTimeMs = frameTimeMs,
        landmarks = landmarks.toList()
    )

    private fun prePoseAnalysisResult(
        poses: List<Pose>,
        processedFrames: List<ProcessedPoseDetectionFrame>,
        aiPoseSequence: AiPoseSequence = aiPoseSequence(
            videoUri = "file:///prepose_cache.mp4",
            processedFrames = processedFrames
        )
    ): PrePoseVideoAnalysisResult = PrePoseVideoAnalysisResult(
        aiPoseSequence = aiPoseSequence,
        poses = poses,
        processedFrames = processedFrames
    )

    private fun aiPoseSequence(
        videoUri: String,
        processedFrames: List<ProcessedPoseDetectionFrame>
    ): AiPoseSequence {
        return AiPoseSequence(
            source = AiPayloadSource(
                uri = videoUri,
                videoUri = videoUri,
                generator = "test",
                exportedAtIso = "2026-03-21T00:00:00Z"
            ),
            videoMetadata = AiVideoMetadata(
                frameWidth = 1080,
                frameHeight = 1920,
                fps = 30f,
                totalFrames = processedFrames.size,
                processedFrames = processedFrames.size,
                analysisFpsLimit = 30
            ),
            frames = processedFrames.mapIndexed { index, frame ->
                AiPoseFrame(
                    frameIndex = index,
                    timestampMs = frame.timestampMs,
                    poseDetected = frame.poseDetected,
                    poseLandmarks = if (frame.poseDetected) {
                        listOf(AiLandmark3D(index = 0, x = 0.1f, y = 0.2f, z = 0.3f))
                    } else {
                        emptyList()
                    },
                    poseWorldLandmarks = if (frame.poseDetected) {
                        listOf(AiLandmark3D(index = 0, x = 1.1f, y = 1.2f, z = 1.3f))
                    } else {
                        emptyList()
                    }
                )
            }
        )
    }

    private fun processedFrame(timestampMs: Long, poseDetected: Boolean): ProcessedPoseDetectionFrame =
        ProcessedPoseDetectionFrame(
            timestampMs = timestampMs,
            poseDetected = poseDetected
        )

    private fun handLandmark(index: Int, x: Float, y: Float): PoseLandmark = PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = 0f
    )

    private fun torsoPoseAt(frameTimeMs: Long, torsoY: Float, wristY: Float): Pose {
        val shoulderY = torsoY - 0.10f
        val hipY = torsoY + 0.10f
        return Pose(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                PoseLandmark(index = 11, x = 0.65f, y = shoulderY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 12, x = 0.35f, y = shoulderY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 23, x = 0.64f, y = hipY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 24, x = 0.36f, y = hipY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 15, x = 0.62f, y = wristY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 16, x = 0.38f, y = wristY, z = 0f, visibility = 0.99f, presence = 0.99f)
            )
        )
    }

    private fun hold(centerX: Float, centerY: Float, holdNo: Int): Hold = Hold(
        holdNo = holdNo,
        boundingBox = Hold.BoundingBox(
            left = centerX - 0.025f,
            top = centerY - 0.025f,
            right = centerX + 0.025f,
            bottom = centerY + 0.025f
        ),
        confidence = 0.95f,
        polygon = emptyList(),
        colorLabel = "red",
        colorScore = 0.9f
    )

    private fun numberedHold(
        holdNo: Int,
        centerX: Float,
        centerY: Float,
        role: HoldRole
    ): HoldNumbered = HoldNumbered(
        hold = hold(centerX = centerX, centerY = centerY, holdNo = holdNo),
        progress = (holdNo - 1).toFloat(),
        axisDistance = 0f,
        role = role
    )

    private fun uploadedAttemptVideo(
        attemptId: Long,
        attemptNo: Int,
        videoUri: String
    ): UploadedAttemptVideo = UploadedAttemptVideo(
        challengeId = 77L,
        attemptId = attemptId,
        attemptNo = attemptNo,
        videoUri = videoUri,
        objectKey = "attempt/$attemptId"
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
