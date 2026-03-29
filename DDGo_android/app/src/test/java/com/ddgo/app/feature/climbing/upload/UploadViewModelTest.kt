package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.MutableState
import com.ddgo.app.core.config.AiAnalysisVariant
import com.ddgo.app.core.datastore.UploadRecoveryDataStore
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiCruxResult
import com.ddgo.app.domain.model.AiPayloadSource
import com.ddgo.app.domain.model.AiPoseFrame
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.AiVideoMetadata
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.GymSummary
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.PrePoseVideoAnalysisResult
import com.ddgo.app.domain.model.ProcessedPoseDetectionFrame
import com.ddgo.app.domain.model.ResolvedGym
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
import com.ddgo.app.domain.usecase.CloseChallengeUseCase
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.GetChallengesUseCase
import com.ddgo.app.domain.usecase.DetectStallSegmentFromPoseUseCase
import com.ddgo.app.domain.usecase.DetectWallArrivalTimeUseCase
import com.ddgo.app.domain.usecase.DetectStablePersonObservationUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.StallSegmentAnnotation
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Field
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
        unmockkStatic(android.net.Uri::class)
        UploadPrePoseTimeoutConfig.reset()
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
    fun `pre-pose ready keeps raw poses and exposes filtered overlay cache`() = runTest {
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(
                poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
                poseAt(100L, handLandmark(index = 19, x = 0.24f, y = 0.78f)),
                poseAt(200L, handLandmark(index = 19, x = 0.27f, y = 0.74f)),
                poseAt(4_000L, handLandmark(index = 19, x = 0.55f, y = 0.42f))
            ),
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true),
                processedFrame(4_000L, true)
            )
        )

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val videoUri = "file:///overlay_filtered.mp4"

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

        assertEquals(4, viewModel.currentAttemptPoseSequence.size)
        assertEquals(3, viewModel.currentAttemptFilteredPoseSequence.size)
        assertEquals(3, viewModel.currentAttemptSmoothedPoseSequence.size)
        assertEquals(3, viewModel.currentAttemptOverlayCache?.frames?.size)
        assertEquals(
            viewModel.currentAttemptSmoothedPoseSequence.firstOrNull(),
            viewModel.currentAttemptOverlayCache?.frames?.firstOrNull()?.pose
        )
        assertEquals(4, viewModel.currentAttemptPrePoseEntry?.poseValidityFrames?.size)
        assertEquals(3, viewModel.currentAttemptPrePoseEntry?.smoothedPoses?.size)
        assertFalse(
            viewModel.currentAttemptPrePoseEntry
                ?.poseValidityFrames
                ?.last()
                ?.isValidForEndpoint
                ?: true
        )
    }

    @Test
    fun `pre-pose ready stores wall arrival time and uses it as first timeline point`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
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
            poses = listOf(
                wallArrivalPoseAt(1_000L, 0.50f),
                wallArrivalPoseAt(1_100L, 0.49f),
                wallArrivalPoseAt(1_200L, 0.48f),
                wallArrivalPoseAt(1_300L, 0.30f),
                wallArrivalPoseAt(1_400L, 0.30f),
                wallArrivalPoseAt(1_500L, 0.30f),
                wallArrivalPoseAt(1_600L, 0.30f),
                wallArrivalPoseAt(1_700L, 0.30f),
                wallArrivalPoseAt(1_800L, 0.30f),
                wallArrivalPoseAt(1_900L, 0.30f),
                wallArrivalPoseAt(2_000L, 0.30f)
            ),
            processedFrames = listOf(
                processedFrame(1_000L, true),
                processedFrame(1_100L, true),
                processedFrame(1_200L, true),
                processedFrame(1_300L, true),
                processedFrame(1_400L, true),
                processedFrame(1_500L, true),
                processedFrame(1_600L, true),
                processedFrame(1_700L, true),
                processedFrame(1_800L, true),
                processedFrame(1_900L, true),
                processedFrame(2_000L, true)
            )
        )
        every { analyzeHandPeakAndEndUseCase(any(), any()) } returns annotation

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase
        )
        val videoUri = "file:///wall_arrival.mp4"

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
        assertEquals(1_000L, entry?.personObservationStartTimeMs)
        assertEquals(1_300L, entry?.wallArrivalTimeMs)
        assertEquals(2, entry?.timelinePoints?.size)
        assertEquals(1_300L, entry?.timelinePoints?.firstOrNull()?.timeMs)
        assertEquals(4_000L, entry?.timelinePoints?.getOrNull(1)?.timeMs)
    }

    @Test
    fun `pre-pose ready stores strongest stall segment and adds it to timeline`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val analyzeHandPeakAndEndUseCase = mockk<AnalyzeHandPeakAndEndUseCase>()
        val detectStallSegmentFromPoseUseCase = mockk<DetectStallSegmentFromPoseUseCase>()
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
        val stallSegment = StallSegmentAnnotation(
            startTimeMs = 2_200L,
            endTimeMs = 3_500L,
            durationMs = 1_300L,
            score = 5.4f
        )

        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(
                wallArrivalPoseAt(1_000L, 0.50f),
                wallArrivalPoseAt(1_100L, 0.49f),
                wallArrivalPoseAt(1_200L, 0.48f),
                wallArrivalPoseAt(1_300L, 0.30f),
                wallArrivalPoseAt(1_400L, 0.30f),
                wallArrivalPoseAt(1_500L, 0.30f),
                wallArrivalPoseAt(1_600L, 0.30f),
                wallArrivalPoseAt(1_700L, 0.30f),
                wallArrivalPoseAt(1_800L, 0.30f),
                wallArrivalPoseAt(1_900L, 0.30f),
                wallArrivalPoseAt(2_000L, 0.30f)
            ),
            processedFrames = listOf(
                processedFrame(1_000L, true),
                processedFrame(1_100L, true),
                processedFrame(1_200L, true),
                processedFrame(1_300L, true),
                processedFrame(1_400L, true),
                processedFrame(1_500L, true),
                processedFrame(1_600L, true),
                processedFrame(1_700L, true),
                processedFrame(1_800L, true),
                processedFrame(1_900L, true),
                processedFrame(2_000L, true)
            )
        )
        every { analyzeHandPeakAndEndUseCase(any(), any()) } returns annotation
        every {
            detectStallSegmentFromPoseUseCase(
                poses = any(),
                wallArrivalTimeMs = any(),
                endTimeMs = any(),
                supportWindowMs = any(),
                minSupportCountPerSide = any(),
                gracePeriodMs = any(),
                endGuardMs = any(),
                windowMs = any(),
                maxGapMs = any(),
                maxHipDisplacementNorm = any(),
                minSegmentDurationMs = any()
            )
        } returns stallSegment

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase,
            detectStallSegmentFromPoseUseCase = detectStallSegmentFromPoseUseCase
        )
        val videoUri = "file:///stall_segment.mp4"

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
        assertEquals(stallSegment, entry?.stallSegment)
        assertEquals(listOf(1_300L, 2_200L, 4_000L), entry?.timelinePoints?.map { it.timeMs })
        assertEquals(AnalysisPointKind.STALL, entry?.timelinePoints?.getOrNull(1)?.kind)
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

        coVerify(atLeast = 2) { prePoseVideoAnalysisProvider.analyze(videoUri, any()) }
        assertTrue(viewModel.currentAttemptPoseSequence.isEmpty())
        assertEquals(PrePoseStatus.Failed, viewModel.currentAttemptPrePoseEntry?.status)
    }

    @Test
    fun `submitUpload shows error when pre-pose analysis times out twice`() = runTest {
        UploadPrePoseTimeoutConfig.analysisTimeoutMs = 20L
        UploadPrePoseTimeoutConfig.awaitTimeoutMs = 500L
        UploadPrePoseTimeoutConfig.pollIntervalMs = 10L

        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } coAnswers {
            delay(100L)
            prePoseAnalysisResult(
                poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f))),
                processedFrames = listOf(processedFrame(0L, true))
            )
        }

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val videoUri = "file:///timed_out_prepose.mp4"

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true))
        setPrivateField(viewModel, "detectedHolds", listOf(hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)))
        setPrivateField(
            viewModel,
            "numberedHolds",
            listOf(numberedHold(holdNo = 1, centerX = 0.20f, centerY = 0.82f, role = HoldRole.START))
        )
        setPrivateField(viewModel, "videoUri", videoUri)

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Error
        }

        coVerify(atLeast = 1) { prePoseVideoAnalysisProvider.analyze(videoUri, any()) }
        assertEquals(PrePoseStatus.Failed, viewModel.currentAttemptPrePoseEntry?.status)
    }

    @Test
    fun `submitUpload shows error when there are no upload targets`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk<PoseEstimator>(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Error
        }

        assertEquals(
            "분석할 업로드 영상이 없습니다.",
            (viewModel.uploadSubmissionUiState.value as UploadSubmissionUiState.Error).message
        )
    }

    @Test
    fun `ensureFinalAnalysisReady shows error when there are no upload targets`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk<PoseEstimator>(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        viewModel.ensureFinalAnalysisReady()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.finalAnalysisPreparationUiState.value is FinalAnalysisPreparationUiState.Error
        }

        assertEquals(
            "최종 분석에 필요한 영상이 없습니다.",
            (viewModel.finalAnalysisPreparationUiState.value as FinalAnalysisPreparationUiState.Error).message
        )
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
        assertEquals(null, entry?.wallArrivalTimeMs)
        assertEquals(2, entry?.timelinePoints?.size)
        assertEquals(2_000L, entry?.timelinePoints?.get(0)?.timeMs)
        assertEquals(4_000L, entry?.timelinePoints?.get(1)?.timeMs)
        assertEquals(1, entry?.timelinePoints?.get(0)?.index)
        assertEquals(2, entry?.timelinePoints?.get(1)?.index)
    }

    @Test
    fun `pre-pose와 번호화가 준비되면 분석 prewarm이 자동으로 시작된다`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiCallCount = mutableListOf<String>()
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
        } coAnswers {
            aiCallCount += firstArg<AiAnalysisMode>().name
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )
        val videoUri = "file:///prewarm_auto.mp4"

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
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
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)))
                )
            )
        )

        invokePrivateMethod(
            target = viewModel,
            methodName = "maybeStartSubmissionAnalysisPrewarmForCurrentSelection"
        )

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            aiCallCount.size == 1
        }

        assertEquals(1, aiCallCount.size)
        assertTrue(viewModel.uploadSubmissionUiState.value !is UploadSubmissionUiState.Loading)
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
        assertEquals(null, getPrivateField(viewModel, "holdPrecomputeObservationJob"))
    }

    @Test
    fun `메인 영상 준비 후 hold precompute가 끝나야 pre-pose가 시작된다`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )
        val videoUri = "file:///hold_first.mp4"
        val generation = viewModel.selectionGeneration

        invokePrivateMethod(
            target = viewModel,
            methodName = "onPrimaryVideoPrepared",
            generation,
            videoUri
        )

        @Suppress("UNCHECKED_CAST")
        val prePoseCacheEntries =
            getPrivateField(viewModel, "prePoseCacheEntries") as Map<String, PrePoseCacheEntry>
        assertTrue(prePoseCacheEntries.isEmpty())
        assertEquals(generation, getPrivateField(viewModel, "pendingPrimaryPrePoseGeneration"))

        val holdDetectionDelegate =
            getPrivateField(viewModel, "holdDetectionDelegate") as UploadHoldDetectionDelegate
        setPrivateField(
            holdDetectionDelegate,
            "holdDetectionPrecomputeEntry",
            UploadHoldDetectionDelegate.HoldDetectionPrecomputeEntry(
                selectionGeneration = generation,
                sourceVideoUri = videoUri,
                debugBestFrameImageUri = null,
                status = UploadHoldDetectionDelegate.HoldDetectionPrecomputeStatus.Ready,
                bestFrameBitmap = mockk<Bitmap>(relaxed = true) {
                    every { width } returns 1080
                    every { height } returns 1920
                },
                rawYoloHolds = emptyList(),
                classifiedAllRich = emptyList(),
                allRawHolds = emptyList(),
                detectedHolds = emptyList()
            )
        )

        invokePrivateMethod(
            target = viewModel,
            methodName = "maybeStartPrimaryPrePoseAfterHoldPrecompute"
        )

        @Suppress("UNCHECKED_CAST")
        val updatedPrePoseCacheEntries =
            getPrivateField(viewModel, "prePoseCacheEntries") as Map<String, PrePoseCacheEntry>
        val prePoseEntry = updatedPrePoseCacheEntries[videoUri]
        assertTrue(
            prePoseEntry?.status == PrePoseStatus.Pending ||
                prePoseEntry?.status == PrePoseStatus.Running
        )
        assertEquals(null, getPrivateField(viewModel, "pendingPrimaryPrePoseGeneration"))
    }

    @Test
    fun `start와 end 홀드를 선택하면 분석 prewarm이 자동으로 시작된다`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiInvocationCount = mutableListOf<String>()
        val videoUri = "file:///hold_selection_prewarm.mp4"
        val startHold = hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)
        val endHold = hold(centerX = 0.62f, centerY = 0.34f, holdNo = 2)

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
        } coAnswers {
            aiInvocationCount += secondArg<String>()
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1920
        })
        setPrivateField(viewModel, "detectedHolds", listOf(startHold, endHold))
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(
                        poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
                        poseAt(100L, handLandmark(index = 20, x = 0.62f, y = 0.34f))
                    )
                )
            )
        )

        viewModel.updateSelectedStartHold(startHold)
        viewModel.updateSelectedEndHold(endHold)

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            aiInvocationCount.size == 1
        }

        assertEquals(1, aiInvocationCount.size)
        assertEquals(2, viewModel.numberedHolds.size)
    }

    @Test
    fun `finalizeHoldDetectionColorSelection does not start analysis prewarm`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiInvocationCount = mutableListOf<String>()
        val videoUri = "file:///finalize_no_prewarm.mp4"
        val bitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1920
        }
        val rawHold = hold(centerX = 0.30f, centerY = 0.40f, holdNo = 1)
        val classifiedRich = HoldColorClassifier.ClassifiedHoldRich(
            hold = rawHold.copy(colorLabel = "yellow", colorScore = 0.9f),
            colorLabel = "yellow",
            colorScore = 0.9f,
            colorStatus = "classified",
            primaryColor = "yellow",
            colorDistribution = mapOf("yellow" to 0.9f),
            rawColorScore = 0.9f,
            detectionReliability = 0.9f,
            validPixelRatio = 0.9f,
            warnings = emptySet()
        )

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
        } coAnswers {
            aiInvocationCount += secondArg<String>()
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
        setPrivateField(viewModel, "bestFrameBitmap", bitmap)
        setPrivateField(viewModel, "detectedHolds", listOf(rawHold))
        setPrivateField(
            viewModel,
            "numberedHolds",
            listOf(numberedHold(holdNo = 1, centerX = 0.30f, centerY = 0.40f, role = HoldRole.START))
        )
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.30f, y = 0.40f)))
                )
            )
        )

        viewModel.updateHoldColor("yellow")
        val holdDetectionDelegate =
            getPrivateField(viewModel, "holdDetectionDelegate") as UploadHoldDetectionDelegate
        setPrivateField(
            holdDetectionDelegate,
            "holdDetectionPrecomputeEntry",
            UploadHoldDetectionDelegate.HoldDetectionPrecomputeEntry(
                selectionGeneration = viewModel.selectionGeneration,
                sourceVideoUri = videoUri,
                debugBestFrameImageUri = null,
                status = UploadHoldDetectionDelegate.HoldDetectionPrecomputeStatus.Ready,
                bestFrameBitmap = bitmap,
                bestFrameTimeUs = 1_000_000L,
                rawYoloHolds = listOf(rawHold),
                classifiedAllRich = listOf(classifiedRich),
                allRawHolds = listOf(rawHold),
                detectedHolds = listOf(rawHold)
            )
        )
        holdDetectionDelegate.bestFrameBitmap = bitmap
        holdDetectionDelegate.allRawHolds = listOf(rawHold)

        viewModel.finalizeHoldDetectionColorSelection()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(aiInvocationCount.isEmpty())
    }

    @Test
    fun `pre-pose batch callback does not start analysis prewarm`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiInvocationCount = mutableListOf<String>()
        val videoUri = "file:///prepose_callback_no_prewarm.mp4"

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
        } coAnswers {
            aiInvocationCount += secondArg<String>()
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
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
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)))
                )
            )
        )

        val sessionCallbacks =
            getPrivateField(viewModel, "sessionCallbacks") as UploadSessionCallbacks
        sessionCallbacks.onPrePoseBatchStateChanged()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(aiInvocationCount.isEmpty())
    }

    @Test
    fun `submitUpload starts batch ai before quiet background upload finishes`() = runTest {
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val attemptRepository = mockk<AttemptRepository>()
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val callOrder = mutableListOf<String>()
        val videoUri = "file:///background_upload.mp4"

        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f))),
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true)
            ),
            aiPoseSequence = aiPoseSequence(
                videoUri = videoUri,
                processedFrames = listOf(
                    processedFrame(0L, true),
                    processedFrame(100L, true),
                    processedFrame(200L, true)
                )
            )
        )
        coEvery { attemptRepository.uploadAttemptVideo(any(), any()) } coAnswers {
            callOrder += "upload-start"
            delay(1_000L)
            callOrder += "upload-end"
            Result.success(uploadedAttemptVideo(attemptId = 101L, attemptNo = 1, videoUri = secondArg()))
        }
        coEvery { attemptRepository.endAttempt(any(), any(), any()) } returns Result.success(Unit)
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
        } coAnswers {
            callOrder += "ai-start"
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false,
            attemptRepository = attemptRepository
        )

        setPrivateField(viewModel, "challengeId", 77L)
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

        assertTrue(callOrder.indexOf("ai-start") in 0 until callOrder.indexOf("upload-end"))
    }

    @Test
    fun `submitUpload does not wait for background batch ai to enter attempt result`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiStarted = CompletableDeferred<Unit>()
        val aiGate = CompletableDeferred<Unit>()
        val videoUri = "file:///attempt_result_without_ai_wait.mp4"

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
        } coAnswers {
            aiStarted.complete(Unit)
            aiGate.await()
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
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
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)))
                )
            )
        )

        viewModel.submitUpload()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(aiStarted.isCompleted)
        assertTrue(viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success)
        assertTrue(viewModel.attemptAiAnalysisResults.isEmpty())

        aiGate.complete(Unit)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `submitUpload는 준비된 분석 prewarm 결과를 재사용한다`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiInvocationCount = mutableListOf<String>()
        val videoUri = "file:///prewarm_reuse.mp4"

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
        } coAnswers {
            aiInvocationCount += secondArg<String>()
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
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
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)))
                )
            )
        )

        invokePrivateMethod(
            target = viewModel,
            methodName = "maybeStartSubmissionAnalysisPrewarmForCurrentSelection"
        )
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            aiInvocationCount.size == 1
        }

        viewModel.prepareFinalAnalysisLoading()
        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.finalAnalysisPreparationUiState.value is FinalAnalysisPreparationUiState.Success
        }

        assertEquals(1, aiInvocationCount.size)
        assertEquals(1, viewModel.attemptAiAnalysisResults.size)
    }

    @Test
    fun `same requestKey final ai runs once across eager trigger and submit fallback`() = runTest {
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val aiInvocationCount = mutableListOf<String>()
        val videoUri = "file:///request_key_single_flight.mp4"
        val startHold = hold(centerX = 0.20f, centerY = 0.82f, holdNo = 1)
        val endHold = hold(centerX = 0.62f, centerY = 0.34f, holdNo = 2)

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
        } coAnswers {
            aiInvocationCount += secondArg<String>()
            Result.success(
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

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase,
            stubAnalyzeAttemptWithAiUseCase = false
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
        setPrivateField(viewModel, "videoUri", videoUri)
        setPrivateField(viewModel, "bestFrameBitmap", mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1920
        })
        setPrivateField(viewModel, "detectedHolds", listOf(startHold, endHold))
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = aiPoseSequence(
                        videoUri = videoUri,
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    ),
                    poses = listOf(
                        poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
                        poseAt(100L, handLandmark(index = 20, x = 0.62f, y = 0.34f))
                    )
                )
            )
        )

        viewModel.updateSelectedStartHold(startHold)
        viewModel.updateSelectedEndHold(endHold)

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            aiInvocationCount.size == 1
        }

        viewModel.prepareFinalAnalysisLoading()
        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.finalAnalysisPreparationUiState.value is FinalAnalysisPreparationUiState.Success
        }

        assertEquals(1, aiInvocationCount.size)
        assertEquals(1, viewModel.attemptAiAnalysisResults.size)
    }

    @Test
    fun `buildCurrentSubmissionRequest uses numberedHolds order and polygons for saved challenge holds`() {
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        val detectedStart = Hold(
            holdNo = 99,
            boundingBox = Hold.BoundingBox(0.70f, 0.70f, 0.80f, 0.80f),
            confidence = 0.5f,
            polygon = listOf(Hold.Point(0.70f, 0.70f)),
            colorLabel = "red",
            colorScore = 0.9f
        )
        val detectedEnd = Hold(
            holdNo = 88,
            boundingBox = Hold.BoundingBox(0.10f, 0.10f, 0.20f, 0.20f),
            confidence = 0.5f,
            polygon = listOf(Hold.Point(0.10f, 0.10f)),
            colorLabel = "red",
            colorScore = 0.9f
        )
        val startNumbered = HoldNumbered(
            hold = Hold(
                holdNo = 1,
                boundingBox = Hold.BoundingBox(0.44f, 0.31f, 0.52f, 0.38f),
                confidence = 0.95f,
                polygon = listOf(
                    Hold.Point(0.45f, 0.32f),
                    Hold.Point(0.48f, 0.30f),
                    Hold.Point(0.52f, 0.33f),
                    Hold.Point(0.50f, 0.38f),
                    Hold.Point(0.46f, 0.36f)
                ),
                colorLabel = "purple",
                colorScore = 0.98f
            ),
            progress = 0f,
            axisDistance = 0f,
            role = HoldRole.START
        )
        val middleNumbered = HoldNumbered(
            hold = Hold(
                holdNo = 2,
                boundingBox = Hold.BoundingBox(0.53f, 0.41f, 0.58f, 0.47f),
                confidence = 0.91f,
                polygon = listOf(
                    Hold.Point(0.53f, 0.42f),
                    Hold.Point(0.56f, 0.41f),
                    Hold.Point(0.58f, 0.45f),
                    Hold.Point(0.55f, 0.47f)
                ),
                colorLabel = "purple",
                colorScore = 0.94f
            ),
            progress = 0.5f,
            axisDistance = 0.02f,
            role = HoldRole.NORMAL
        )
        val endNumbered = HoldNumbered(
            hold = Hold(
                holdNo = 3,
                boundingBox = Hold.BoundingBox(0.60f, 0.48f, 0.68f, 0.55f),
                confidence = 0.92f,
                polygon = listOf(
                    Hold.Point(0.60f, 0.50f),
                    Hold.Point(0.65f, 0.48f),
                    Hold.Point(0.68f, 0.53f),
                    Hold.Point(0.63f, 0.55f)
                ),
                colorLabel = "purple",
                colorScore = 0.95f
            ),
            progress = 1f,
            axisDistance = 0f,
            role = HoldRole.END
        )

        setPrivateField(viewModel, "videoUri", "file:///numbered_payload.mp4")
        setPrivateField(viewModel, "detectedHolds", listOf(detectedStart, detectedEnd))
        setPrivateField(viewModel, "numberedHolds", listOf(endNumbered, startNumbered, middleNumbered))

        val request = invokePrivateMethodWithResult(
            viewModel,
            "buildCurrentSubmissionRequestOrNull",
            false
        )!!
        val holdCoordinates = readField<List<com.ddgo.app.domain.model.ChallengeHoldCoordinate>>(
            target = request,
            fieldName = "holdCoordinates"
        )

        assertEquals(listOf(1, 2, 3), holdCoordinates.map { it.holdNo })
        assertEquals(
            listOf(0.45f, 0.48f, 0.52f, 0.50f, 0.46f),
            holdCoordinates.first().polygon.map { it.x }
        )
        assertEquals(
            listOf(0.60f, 0.65f, 0.68f, 0.63f),
            holdCoordinates.last().polygon.map { it.x }
        )
    }

    @Test
    fun `submitUpload keeps attempt result success when quiet background upload fails and retry succeeds`() = runTest {
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val attemptRepository = mockk<AttemptRepository>()
        val videoUri = "file:///background_upload_fail.mp4"

        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f))),
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true)
            ),
            aiPoseSequence = aiPoseSequence(
                videoUri = videoUri,
                processedFrames = listOf(
                    processedFrame(0L, true),
                    processedFrame(100L, true),
                    processedFrame(200L, true)
                )
            )
        )
        coEvery { attemptRepository.uploadAttemptVideo(any(), any()) } returnsMany listOf(
            Result.failure(IllegalStateException("upload boom")),
            Result.success(uploadedAttemptVideo(attemptId = 101L, attemptNo = 1, videoUri = videoUri))
        )
        coEvery { attemptRepository.endAttempt(any(), any(), any()) } returns Result.success(Unit)

        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            attemptRepository = attemptRepository
        )

        setPrivateField(viewModel, "challengeId", 77L)
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
            viewModel.prePoseBatchState.readyCount == 1
        }

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success
        }

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.backgroundUploadState.value == BackgroundUploadState.Failed
        }

        assertTrue(viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success)
        assertTrue(viewModel.backgroundUploadNotice.value != null)

        viewModel.retryBackgroundAttemptUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.backgroundUploadState.value == BackgroundUploadState.Ready
        }

        assertEquals(1, viewModel.uploadedAttemptVideos.size)
        assertEquals(null, viewModel.backgroundUploadNotice.value)
    }

    @Test
    fun `challenge hold waits for existing precompute after challenge selection reset`() = runTest {
        val bitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1920
        }
        val parsedUri = mockk<Uri> {
            every { scheme } returns "file"
            every { path } returns "/debug_frame.png"
        }
        val rawHold = hold(centerX = 0.30f, centerY = 0.40f, holdNo = 1)
        val classifiedHold = rawHold.copy(colorLabel = "red", colorScore = 0.9f)
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        var detectInvocationCount = 0
        val holdDetector = mockk<HoldDetector>()
        val holdColorClassifier = mockk<HoldColorClassifier>(relaxed = true)
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            holdDetector = holdDetector,
            holdColorClassifier = holdColorClassifier
        )

        mockkStatic(BitmapFactory::class)
        mockkStatic(Uri::class)
        every { Uri.parse("file:///debug_frame.png") } returns parsedUri
        every { BitmapFactory.decodeFile("/debug_frame.png") } returns bitmap
        coEvery { holdDetector.detectFromFrame(bitmap) } coAnswers {
            when (detectInvocationCount++) {
                0 -> {
                    firstGate.await()
                    listOf(rawHold)
                }

                1 -> {
                    secondGate.await()
                    listOf(rawHold)
                }

                else -> listOf(rawHold)
            }
        }
        every {
            holdColorClassifier.classifyAllRich(
                bitmap = bitmap,
                holds = listOf(rawHold),
                relaxedRejection = true
            )
        } returns HoldColorClassifier.ClassifiedHoldPrecomputeResult(
            classifiedHolds = listOf(
                HoldColorClassifier.ClassifiedHoldRich(
                    hold = classifiedHold,
                    colorLabel = "red",
                    colorScore = 0.9f,
                    colorStatus = "classified",
                    primaryColor = "red",
                    colorDistribution = mapOf("red" to 0.9f),
                    rawColorScore = 0.9f,
                    detectionReliability = 0.9f,
                    validPixelRatio = 0.9f,
                    warnings = emptySet()
                )
            ),
            allHolds = listOf(classifiedHold)
        )
        every {
            holdColorClassifier.filterClassifiedHolds(
                classifiedHolds = any(),
                targetColorName = "red",
                scoreThreshold = 0.25f
            )
        } returns listOf(classifiedHold)

        viewModel.useDebugBestFrameImage("file:///debug_frame.png")
        viewModel.updateHoldColor("red")
        viewModel.markHoldPrecomputeEligibleForCurrentSelection()

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val holdDetectionDelegate =
            getPrivateField(viewModel, "holdDetectionDelegate") as UploadHoldDetectionDelegate
        assertTrue(
            holdDetectionDelegate.isPrecomputeRunning(
                selectionGeneration = viewModel.selectionGeneration,
                sourceVideoUri = null
            )
        )

        invokePrivateMethod(viewModel, "clearChallengeSelectionStatePreservingHoldPrecompute")
        viewModel.updateHoldColor("red")
        viewModel.ensureHoldDetectionReadyForCurrentColor()

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is UploadUiState.Loading)
        assertEquals(1, detectInvocationCount)

        firstGate.complete(Unit)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value is UploadUiState.Error)

        secondGate.complete(Unit)
        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value is UploadUiState.Success
        }

        assertTrue(viewModel.uiState.value is UploadUiState.Success)
    }

    @Test
    fun `challenge selection reset preserves ready hold source cache`() = runTest {
        val bitmap = mockk<Bitmap>(relaxed = true) {
            every { width } returns 1080
            every { height } returns 1920
        }
        val rawHold = hold(centerX = 0.30f, centerY = 0.40f, holdNo = 1)
        val numberedHold = HoldNumbered(
            hold = rawHold,
            progress = 0f,
            axisDistance = 0f,
            role = HoldRole.START
        )
        val classifiedRich = HoldColorClassifier.ClassifiedHoldRich(
            hold = rawHold.copy(colorLabel = "red", colorScore = 0.9f),
            colorLabel = "red",
            colorScore = 0.9f,
            colorStatus = "classified",
            primaryColor = "red",
            colorDistribution = mapOf("red" to 0.9f),
            rawColorScore = 0.9f,
            detectionReliability = 0.9f,
            validPixelRatio = 0.9f,
            warnings = emptySet()
        )
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )
        val videoUri = "file:///cached.mp4"

        setPrivateField(viewModel, "videoUri", videoUri)
        viewModel.updateHoldColor("red")

        val holdDetectionDelegate =
            getPrivateField(viewModel, "holdDetectionDelegate") as UploadHoldDetectionDelegate
        setPrivateField(
            holdDetectionDelegate,
            "holdDetectionPrecomputeEntry",
            UploadHoldDetectionDelegate.HoldDetectionPrecomputeEntry(
                selectionGeneration = viewModel.selectionGeneration,
                sourceVideoUri = videoUri,
                debugBestFrameImageUri = null,
                status = UploadHoldDetectionDelegate.HoldDetectionPrecomputeStatus.Ready,
                bestFrameBitmap = bitmap,
                bestFrameTimeUs = 1_000_000L,
                rawYoloHolds = listOf(rawHold),
                classifiedAllRich = listOf(classifiedRich),
                allRawHolds = listOf(rawHold),
                lastAppliedColorKey = "red",
                detectedHolds = listOf(rawHold)
            )
        )
        holdDetectionDelegate.bestFrameBitmap = bitmap
        holdDetectionDelegate.allRawHolds = listOf(rawHold)
        holdDetectionDelegate.detectedHolds = listOf(rawHold)
        holdDetectionDelegate.selectedStartHold = rawHold
        holdDetectionDelegate.selectedEndHold = rawHold
        holdDetectionDelegate.numberedHolds = listOf(numberedHold)

        invokePrivateMethod(viewModel, "clearChallengeSelectionStatePreservingHoldPrecompute")

        assertTrue(
            holdDetectionDelegate.isPrecomputeReady(
                selectionGeneration = viewModel.selectionGeneration,
                sourceVideoUri = videoUri
            )
        )
        assertEquals(bitmap, viewModel.bestFrameBitmap)
        assertEquals(listOf(rawHold), viewModel.allRawHolds)
        assertTrue(viewModel.detectedHolds.isEmpty())
        assertEquals(null, viewModel.selectedStartHold)
        assertEquals(null, viewModel.selectedEndHold)
        assertTrue(viewModel.numberedHolds.isEmpty())
        assertEquals(null, viewModel.selectedHoldColorKey)
    }

    @Test
    fun `realtime recorded attempt without session id uses cached batch ai path`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val cachedAiPoseSequence = aiPoseSequence(
            videoUri = "file:///realtime_without_session.mp4",
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
        val videoUri = "file:///realtime_without_session.mp4"

        viewModel.beginRealtimeChallengeUploadFlow()
        viewModel.setLocalAnalysisWithoutChallengeEnabled(true)
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

        viewModel.submitUpload()

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success
        }

        assertTrue(viewModel.isRealtimeEntryMode)
        assertEquals(RealtimeAttemptActionState.ShowingOptions, viewModel.realtimeAttemptActionState)
        assertEquals(cachedAiPoseSequence, cachedSequenceSlot.captured)
    }

    @Test
    fun `prepareRealtimeRetake keeps realtime mode and marks retake requested`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        viewModel.beginRealtimeChallengeUploadFlow()
        viewModel.prepareRealtimeRetake()

        assertTrue(viewModel.isRealtimeEntryMode)
        assertEquals(
            AnalysisLoadingPhase.AttemptResultPreparation,
            viewModel.analysisLoadingPhase
        )
        assertEquals(
            RealtimeAttemptActionState.RetakeRequested,
            viewModel.realtimeAttemptActionState
        )
        assertEquals(
            RealtimeSetupStep.Ready,
            viewModel.realtimeOverlayUiState.setupStep
        )
    }

    @Test
    fun `prepareFinalAnalysisLoading keeps realtime options and marks final analysis requested`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )

        viewModel.beginRealtimeChallengeUploadFlow()
        viewModel.prepareFinalAnalysisLoading()

        assertTrue(viewModel.isRealtimeEntryMode)
        assertEquals(
            AnalysisLoadingPhase.FinalAnalysisPreparation,
            viewModel.analysisLoadingPhase
        )
        assertEquals(
            RealtimeAttemptActionState.FinalAnalysisRequested,
            viewModel.realtimeAttemptActionState
        )
    }

    @Test
    fun `realtime challenge create flow keeps selection local until explicit completion and preserves ready sheet behavior`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>(relaxed = true)
        val gymRepository = mockk<GymRepository>(relaxed = true)
        val challengeRepository = mockk<ChallengeRepository>(relaxed = true)
        val place = NearbyPlace(
            externalPlaceId = "place-1",
            placeName = "DDGo Climbing",
            addressName = "Seoul",
            roadAddressName = "Seoul Road 1",
            latitude = 37.0,
            longitude = 127.0,
            distanceMeters = 120
        )
        val grade = GymGrade(
            gymGradeId = 101,
            colorName = "skyblue",
            sortOrder = 3,
            colorHex = "#4396FB",
            gradeLabel = ""
        )
        val resolvedGym = ResolvedGym(
            matched = true,
            gymId = 88,
            gradeSource = "ddgo",
            matchStatus = "matched",
            needsReview = false,
            gym = GymSummary(
                id = 88,
                displayName = "DDGo Climbing",
                region = "Seoul",
                logoBucket = null,
                logoObjectKey = null,
                brandLogoBucket = null,
                brandLogoObjectKey = null
            ),
            grades = listOf(grade)
        )
        val challengeSession = ChallengeSession(
            challengeId = 501L,
            gymId = 88L,
            gymGradeId = 101L,
            gymName = "DDGo Climbing",
            problemColor = "skyblue",
            gradeLabel = "V3",
            challengeStatus = "CREATED",
            startedAt = "2026-03-24T10:00:00",
            createdAt = "2026-03-24T10:00:01"
        )

        coEvery { gymRepository.resolveGym(any()) } returns Result.success(resolvedGym)
        coEvery { challengeRepository.createChallenge(any(), any(), any()) } returns Result.success(challengeSession)

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            gymRepository = gymRepository,
            challengeRepository = challengeRepository
        )

        viewModel.beginRealtimeChallengeUploadFlow()
        viewModel.resolveSelectedPlace(place)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RealtimeSetupStep.ChallengeCreate, viewModel.realtimeOverlayUiState.setupStep)

        viewModel.onRealtimeGymGradeSelected(grade)

        assertEquals(RealtimeSetupStep.ChallengeCreate, viewModel.realtimeOverlayUiState.setupStep)
        assertEquals(grade.gymGradeId, viewModel.realtimeOverlayUiState.selectedGymGrade?.gymGradeId)
        assertEquals("V3", viewModel.realtimeOverlayUiState.difficultyLabel)
        assertEquals("skyblue", viewModel.selectedHoldColorKey)
        assertFalse(viewModel.realtimeOverlayUiState.isChallengeReady)
        coVerify(exactly = 0) { challengeRepository.createChallenge(any(), any(), any()) }

        viewModel.updateRealtimeHoldColorSheetVisible(true)
        assertFalse(viewModel.realtimeOverlayUiState.isHoldColorSheetVisible)

        viewModel.onRealtimeHoldColorSelected("navy")
        assertEquals(RealtimeSetupStep.ChallengeCreate, viewModel.realtimeOverlayUiState.setupStep)
        assertEquals("navy", viewModel.selectedHoldColorKey)
        assertFalse(viewModel.realtimeOverlayUiState.isChallengeReady)
        coVerify(exactly = 0) { challengeRepository.createChallenge(any(), any(), any()) }

        viewModel.completeRealtimeChallengeSetup()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(RealtimeSetupStep.Ready, viewModel.realtimeOverlayUiState.setupStep)
        assertEquals("navy", viewModel.selectedHoldColorKey)
        assertEquals("V3", viewModel.realtimeOverlayUiState.difficultyLabel)
        assertTrue(viewModel.realtimeOverlayUiState.isChallengeReady)
        coVerify(exactly = 1) { challengeRepository.createChallenge(any(), any(), any()) }

        viewModel.updateRealtimeHoldColorSheetVisible(true)
        assertTrue(viewModel.realtimeOverlayUiState.isHoldColorSheetVisible)

        viewModel.onRealtimeHoldColorSelected("red")

        assertEquals("red", viewModel.selectedHoldColorKey)
        assertFalse(viewModel.realtimeOverlayUiState.isHoldColorSheetVisible)
        coVerify(exactly = 1) { challengeRepository.createChallenge(any(), any(), any()) }
    }

    @Test
    fun `exportCurrentAttemptBatchAiJson sampled export keeps the normalized full 10fps sequence`() = runTest {
        val videoUri = "file:///normalized_sampled_export.mp4"
        val targetUri = Uri.parse("file:///tmp/normalized_sampled_export.json")
        val outputStream = ByteArrayOutputStream()
        val context = mockContext()
        every { context.contentResolver.openOutputStream(targetUri) } returns outputStream

        val frameCount = 120
        val normalizedSequence = normalizedAiPoseSequence(
            videoUri = videoUri,
            frameCount = frameCount
        )

        val viewModel = createViewModel(
            context = context,
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true),
            aiAnalysisVariant = AiAnalysisVariant.V2_GZIP_10FPS
        )

        setPrivateField(viewModel, "videoUri", videoUri)
        setPrivateField(viewModel, "currentAttemptIndex", 0)
        setPrivateField(
            viewModel,
            "prePoseCacheEntries",
            mapOf(
                videoUri to PrePoseCacheEntry(
                    playbackUri = videoUri,
                    selectionGeneration = viewModel.selectionGeneration,
                    status = PrePoseStatus.Ready,
                    aiPoseSequence = normalizedSequence,
                    filteredAiPoseSequence = normalizedSequence,
                    poses = emptyList(),
                    filteredPoses = emptyList(),
                    smoothedPoses = emptyList(),
                    processedFrames = List(frameCount) { index ->
                        processedFrame(timestampMs = index * 100L, poseDetected = true)
                    }
                )
            )
        )
        setPrivateField(
            viewModel,
            "attemptAlignedHoldSets",
            listOf(
                AttemptAlignedHoldSet(
                    playbackUri = videoUri,
                    frameWidthPx = 1080,
                    frameHeightPx = 1920,
                    mode = AttemptHoldAlignmentMode.Matched,
                    confidence = 1f,
                    matchedHoldCount = 1,
                    warpOnlyHoldCount = 0,
                    alignedHolds = listOf(
                        numberedHold(
                            holdNo = 1,
                            centerX = 0.45f,
                            centerY = 0.40f,
                            role = HoldRole.START
                        )
                    ),
                    rawCropBounds = null,
                    debugSummary = "test"
                )
            )
        )

        val result = viewModel.exportCurrentAttemptBatchAiJson(
            targetUri = targetUri,
            variant = BatchAiJsonExportVariant.SAMPLED
        )

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(frameCount, summary.poseFrameCount)
        assertEquals(frameCount, summary.sampledFrameCount)
        assertEquals(1, summary.sampledFrameStep)
        assertEquals(frameCount, summary.rawFrameCount)
        assertEquals(1, summary.rawFrameStep)

        val exportedJson = Json.parseToJsonElement(outputStream.toString(Charsets.UTF_8.name())).jsonObject
        val poseSequenceJson = exportedJson["pose3d_sequence_json"]!!.jsonObject
        val videoMetadataJson = poseSequenceJson["video_metadata"]!!.jsonObject

        assertEquals(1, exportedJson["frame_step"]!!.jsonPrimitive.int)
        assertEquals(10f, videoMetadataJson["fps"]!!.jsonPrimitive.float, 0.0f)
        assertEquals(10, videoMetadataJson["analysis_fps_limit"]!!.jsonPrimitive.int)
        assertEquals(frameCount, poseSequenceJson["frames"]!!.jsonArray.size)
    }

    private fun createViewModel(
        context: Context = mockContext(),
        poseEstimator: PoseEstimator,
        prePoseVideoAnalysisProvider: PrePoseVideoAnalysisProvider,
        analyzeHandPeakAndEndUseCase: AnalyzeHandPeakAndEndUseCase = AnalyzeHandPeakAndEndUseCase(),
        detectStallSegmentFromPoseUseCase: DetectStallSegmentFromPoseUseCase = DetectStallSegmentFromPoseUseCase(),
        detectWallArrivalTimeUseCase: DetectWallArrivalTimeUseCase = DetectWallArrivalTimeUseCase(),
        analyzeAttemptWithAiUseCase: AnalyzeAttemptWithAiUseCase = mockk(),
        stubAnalyzeAttemptWithAiUseCase: Boolean = true,
        attemptRepository: AttemptRepository = mockk(relaxed = true),
        challengeRepository: ChallengeRepository = mockk(relaxed = true),
        gymRepository: GymRepository = mockk(relaxed = true),
        personDetector: PersonDetector = mockk(relaxed = true),
        holdDetector: HoldDetector = mockk(relaxed = true),
        aiAnalysisVariant: AiAnalysisVariant = AiAnalysisVariant.V1,
        holdColorClassifier: HoldColorClassifier = HoldColorClassifier()
    ): UploadViewModel {
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>()
        val uploadRecoveryDataStore = mockk<UploadRecoveryDataStore>(relaxed = true)
        val getChallengesUseCase = GetChallengesUseCase(challengeRepository)
        coEvery {
            challengeRepository.saveChallengeHolds(any(), any())
        } answers {
            val challengeId = firstArg<Long>()
            val holds = secondArg<List<com.ddgo.app.domain.model.ChallengeHoldCoordinate>>()
            Result.success(
                com.ddgo.app.domain.model.SavedChallengeHolds(
                    challengeId = challengeId,
                    holdCount = holds.size,
                    holds = holds
                )
            )
        }
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
        return UploadViewModel(
            context = context,
            personDetector = personDetector,
            holdDetector = holdDetector,
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            aiAnalysisVariant = aiAnalysisVariant,
            holdColorClassifier = holdColorClassifier,
            searchNearbyClimbingGymsUseCase = SearchNearbyClimbingGymsUseCase(gymRepository),
            resolveGymUseCase = ResolveGymUseCase(gymRepository),
            createChallengeUseCase = CreateChallengeUseCase(challengeRepository),
            closeChallengeUseCase = CloseChallengeUseCase(challengeRepository),
            saveChallengeHoldsUseCase = SaveChallengeHoldsUseCase(challengeRepository),
            uploadAttemptVideoUseCase = UploadAttemptVideoUseCase(attemptRepository),
            endAttemptUseCase = EndAttemptUseCase(attemptRepository),
            analyzeHandPeakAndEndUseCase = analyzeHandPeakAndEndUseCase,
            detectStallSegmentFromPoseUseCase = detectStallSegmentFromPoseUseCase,
            detectWallArrivalTimeUseCase = detectWallArrivalTimeUseCase,
            detectStablePersonObservationUseCase = DetectStablePersonObservationUseCase(),
            uploadRecoveryDataStore = uploadRecoveryDataStore,
            getMyInfoUseCase = getMyInfoUseCase,
            getChallengesUseCase = getChallengesUseCase,
            analyzeAttemptWithAiUseCase = analyzeAttemptWithAiUseCase
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

    private fun invokePrivateMethodWithResult(target: Any, methodName: String, vararg args: Any?): Any? {
        val method = target.javaClass.declaredMethods.firstOrNull { method ->
            method.name == methodName && method.parameterCount == args.size
        }
            ?: throw NoSuchMethodException(methodName)
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    private fun <T> readField(target: Any, fieldName: String): T {
        val field = target.javaClass.declaredFields.firstOrNull { it.name == fieldName }
            ?: throw NoSuchFieldException(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T
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
                analysisFpsLimit = 10
            ),
            frames = processedFrames.mapIndexed { index, frame ->
                AiPoseFrame(
                    frameIndex = index,
                    timestampMs = frame.timestampMs,
                    poseDetected = frame.poseDetected,
                    poseLandmarks = if (frame.poseDetected) {
                        sampleAiLandmarks(offset = 0f)
                    } else {
                        emptyList()
                    },
                    poseWorldLandmarks = if (frame.poseDetected) {
                        sampleAiLandmarks(offset = 1f)
                    } else {
                        emptyList()
                    }
                )
            }
        )
    }

    private fun normalizedAiPoseSequence(
        videoUri: String,
        frameCount: Int
    ): AiPoseSequence {
        val frames = List(frameCount) { index ->
            AiPoseFrame(
                frameIndex = index,
                timestampMs = index * 100L,
                poseDetected = true,
                poseLandmarks = sampleAiLandmarks(offset = 0f),
                poseWorldLandmarks = sampleAiLandmarks(offset = 1f)
            )
        }

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
                fps = 10f,
                totalFrames = frameCount,
                processedFrames = frameCount,
                analysisFpsLimit = 10
            ),
            frames = frames
        )
    }

    private fun processedFrame(timestampMs: Long, poseDetected: Boolean): ProcessedPoseDetectionFrame =
        ProcessedPoseDetectionFrame(
            timestampMs = timestampMs,
            poseDetected = poseDetected
        )

    private fun sampleAiLandmarks(offset: Float): List<AiLandmark3D> = List(33) { index ->
        AiLandmark3D(
            index = index,
            x = offset + (index * 0.01f),
            y = offset + (index * 0.02f),
            z = offset + (index * 0.03f),
            visibility = 0.9f,
            presence = 0.8f
        )
    }

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

    private fun wallArrivalPoseAt(frameTimeMs: Long, torsoScale: Float): Pose {
        val shoulderY = 0.5f - (torsoScale / 2f)
        val hipY = 0.5f + (torsoScale / 2f)
        return Pose(
            frameTimeMs = frameTimeMs,
            landmarks = listOf(
                PoseLandmark(index = 11, x = 0.65f, y = shoulderY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 12, x = 0.35f, y = shoulderY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 23, x = 0.64f, y = hipY, z = 0f, visibility = 0.99f, presence = 0.99f),
                PoseLandmark(index = 24, x = 0.36f, y = hipY, z = 0f, visibility = 0.99f, presence = 0.99f)
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
