package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiAnalysisResult
import com.ddgo.app.domain.model.AiCruxResult
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
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.io.path.createTempDirectory
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalCoroutinesApi::class)
class UploadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
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
    fun `submitUpload ignores duplicate calls while loading`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        val saveChallengeHoldsUseCase = mockk<SaveChallengeHoldsUseCase>(relaxed = true)
        val uploadAttemptVideoUseCase = mockk<UploadAttemptVideoUseCase>(relaxed = true)
        val endAttemptUseCase = mockk<EndAttemptUseCase>(relaxed = true)
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } returns prePoseAnalysisResult(
            poses = listOf(
                poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
                poseAt(500L, handLandmark(index = 20, x = 0.62f, y = 0.34f))
            ),
            processedFrames = listOf(
                processedFrame(0L, true),
                processedFrame(100L, true),
                processedFrame(200L, true)
            )
        )

        val viewModel = createViewModel(
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            saveChallengeHoldsUseCase = saveChallengeHoldsUseCase,
            uploadAttemptVideoUseCase = uploadAttemptVideoUseCase,
            endAttemptUseCase = endAttemptUseCase
        )

        viewModel.setLocalAnalysisWithoutChallengeEnabled(false)
        setPrivateField(viewModel, "challengeId", 42L)
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
        setPrivateField(viewModel, "videoUri", "file:///duplicate_submit.mp4")
        setPrivateField(
            viewModel,
            "_uploadSubmissionUiState",
            UploadSubmissionUiState.Loading("이미 제출 중입니다.")
        )

        viewModel.submitUpload()
        viewModel.submitUpload()

        assertEquals(
            UploadSubmissionUiState.Loading("이미 제출 중입니다."),
            viewModel.uploadSubmissionUiState.value
        )
        coVerify(exactly = 0) { saveChallengeHoldsUseCase.invoke(any(), any()) }
        coVerify(exactly = 0) { uploadAttemptVideoUseCase.invoke(any(), any()) }
        coVerify(exactly = 0) { endAttemptUseCase.invoke(any(), any(), any()) }
    }

    @Test
    fun `pre-pose 실패 후 submitUpload에서도 estimator를 자동 재호출하지 않는다`() = runTest {
        val poseEstimator = mockk<PoseEstimator>(relaxed = true)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } throws IllegalStateException("boom")

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
            viewModel.uploadSubmissionUiState.value is UploadSubmissionUiState.Success
        }

        coVerify(exactly = 1) { prePoseVideoAnalysisProvider.analyze(videoUri, any()) }
        assertTrue(viewModel.currentAttemptPoseSequence.isEmpty())
        assertEquals(PrePoseStatus.Failed, viewModel.currentAttemptPrePoseEntry?.status)
        assertEquals(0, viewModel.attemptHoldReachResults.single().highestReachedHoldNo)
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
        @Suppress("UNCHECKED_CAST")
        val restoredUploadedVideos =
            getPrivateField(viewModel, "uploadedAttemptVideos") as List<UploadedAttemptVideo>
        val restoredSummary =
            getPrivateField(viewModel, "overallHoldReachSummary") as OverallHoldReachSummary?

        assertFalse(viewModel.isAttemptOnlyUploadMode)
        assertEquals(publishedUris, viewModel.playbackAttemptUris)
        assertEquals(1, viewModel.currentAttemptIndex)
        assertEquals(publishedResults[1], viewModel.currentAttemptHoldReachResult)
        assertEquals(publishedPoses.getValue(publishedUris[1]).poses, viewModel.currentAttemptPoseSequence)
        assertEquals(publishedUris, restoredUploadedVideos.map { it.videoUri })
        assertEquals(publishedSummary, restoredSummary)
        assertFalse(restoredCache.containsKey(draftUri))
    }

    @Test
    fun `cleanupUnusedManagedTempFiles preserves referenced temp files and deletes orphan`() = runTest {
        val context = mockContext()
        val viewModel = createViewModel(
            context = context,
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = mockk(relaxed = true)
        )
        val cacheDir = context.cacheDir
        val selectedTemp = File(cacheDir, "selected.mp4").apply { writeText("selected") }
        val resultTemp = File(cacheDir, "result.mp4").apply { writeText("result") }
        val publishedTemp = File(cacheDir, "published.mp4").apply { writeText("published") }
        val activePrePoseTemp = File(cacheDir, "active_prepose.mp4").apply { writeText("active") }
        val orphanTemp = File(cacheDir, "orphan.mp4").apply { writeText("orphan") }

        val selectedPlaybackUri = "file:///selected.mp4"
        val resultPlaybackUri = "file:///result.mp4"
        val publishedPlaybackUri = "file:///published.mp4"
        val activePrePosePlaybackUri = "file:///active_prepose.mp4"
        val orphanPlaybackUri = "file:///orphan.mp4"

        setPrivateField(
            viewModel,
            "primaryManagedVideo",
            managedAttemptVideo(
                sourceUri = "content://selected",
                playbackUri = selectedPlaybackUri,
                tempFile = selectedTemp
            )
        )
        setPrivateField(viewModel, "resultPlaybackUris", listOf(publishedPlaybackUri))

        invokePrivateMethod(
            target = viewModel,
            methodName = "captureCurrentAttemptResultSession"
        )

        setPrivateField(viewModel, "resultPlaybackUris", listOf(resultPlaybackUri))

        @Suppress("UNCHECKED_CAST")
        val managedVideosByPlaybackUri =
            getPrivateField(viewModel, "managedVideosByPlaybackUri") as MutableMap<String, ManagedAttemptVideo>
        managedVideosByPlaybackUri += mapOf(
            resultPlaybackUri to managedAttemptVideo(
                sourceUri = "content://result",
                playbackUri = resultPlaybackUri,
                tempFile = resultTemp
            ),
            publishedPlaybackUri to managedAttemptVideo(
                sourceUri = "content://published",
                playbackUri = publishedPlaybackUri,
                tempFile = publishedTemp
            ),
            activePrePosePlaybackUri to managedAttemptVideo(
                sourceUri = "content://active",
                playbackUri = activePrePosePlaybackUri,
                tempFile = activePrePoseTemp
            ),
            orphanPlaybackUri to managedAttemptVideo(
                sourceUri = "content://orphan",
                playbackUri = orphanPlaybackUri,
                tempFile = orphanTemp
            )
        )

        @Suppress("UNCHECKED_CAST")
        val managedTempFilePaths =
            getPrivateField(viewModel, "managedTempFilePaths") as MutableSet<String>
        managedTempFilePaths += listOf(
            selectedTemp.absolutePath,
            resultTemp.absolutePath,
            publishedTemp.absolutePath,
            activePrePoseTemp.absolutePath,
            orphanTemp.absolutePath
        )

        @Suppress("UNCHECKED_CAST")
        val activePrePosePlaybackUris =
            getPrivateField(viewModel, "activePrePosePlaybackUris") as MutableSet<String>
        activePrePosePlaybackUris += activePrePosePlaybackUri

        invokePrivateMethod(
            target = viewModel,
            methodName = "cleanupUnusedManagedTempFiles",
            false
        )

        assertTrue(selectedTemp.exists())
        assertTrue(resultTemp.exists())
        assertTrue(publishedTemp.exists())
        assertTrue(activePrePoseTemp.exists())
        assertFalse(orphanTemp.exists())
        assertFalse(managedTempFilePaths.contains(orphanTemp.absolutePath))
        assertFalse(managedVideosByPlaybackUri.containsKey(orphanPlaybackUri))
        assertTrue(managedVideosByPlaybackUri.containsKey(resultPlaybackUri))
        assertTrue(managedVideosByPlaybackUri.containsKey(publishedPlaybackUri))
        assertTrue(managedVideosByPlaybackUri.containsKey(activePrePosePlaybackUri))
    }

    @Test
    fun `selection change keeps current pre-pose aligned with latest generation`() = runTest {
        val firstAnalyzeGate = CompletableDeferred<Unit>()
        val analyzeCallCount = AtomicInteger(0)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } coAnswers {
            when (analyzeCallCount.incrementAndGet()) {
                1 -> {
                    firstAnalyzeGate.await()
                    prePoseAnalysisResult(
                        poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.12f, y = 0.88f))),
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    )
                }

                else -> prePoseAnalysisResult(
                    poses = listOf(poseAt(0L, handLandmark(index = 20, x = 0.86f, y = 0.22f))),
                    processedFrames = listOf(
                        processedFrame(0L, true),
                        processedFrame(100L, true),
                        processedFrame(200L, true)
                    )
                )
            }
        }

        val viewModel = createViewModel(
            context = mockContext(),
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val firstPlaybackUri = "file:///selection_a.mp4"
        val secondPlaybackUri = "file:///selection_b.mp4"

        invokePrivateMethod(
            target = viewModel,
            methodName = "beginSelectionUpdate",
            false
        )
        setPrivateField(viewModel, "videoUri", firstPlaybackUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            analyzeCallCount.get() == 1 && viewModel.prePoseBatchState.runningCount == 1
        }

        invokePrivateMethod(
            target = viewModel,
            methodName = "beginSelectionUpdate",
            false
        )
        setPrivateField(viewModel, "videoUri", secondPlaybackUri)
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        firstAnalyzeGate.complete(Unit)

        waitUntil(timeoutMs = 10_000L) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            analyzeCallCount.get() == 2
        }

        waitUntil(timeoutMs = 10_000L) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            viewModel.currentAttemptPrePoseEntry?.status == PrePoseStatus.Ready &&
                viewModel.currentAttemptPrePoseEntry?.selectionGeneration == viewModel.selectionGeneration
        }

        assertEquals(secondPlaybackUri, viewModel.playbackAttemptUris.single())
        assertEquals(viewModel.selectionGeneration, viewModel.currentAttemptPrePoseEntry?.selectionGeneration)
        assertEquals(1, viewModel.prePoseBatchState.readyCount)
        assertEquals(0.86f, viewModel.currentAttemptPoseSequence.first().landmarks.first().x, 0.0001f)
    }

    @Test
    fun `completed stale pre-pose worker cleans orphan temp file after reselection`() = runTest {
        val firstAnalyzeGate = CompletableDeferred<Unit>()
        val analyzeCallCount = AtomicInteger(0)
        val prePoseVideoAnalysisProvider = mockk<PrePoseVideoAnalysisProvider>()
        coEvery { prePoseVideoAnalysisProvider.analyze(any(), any()) } coAnswers {
            when (analyzeCallCount.incrementAndGet()) {
                1 -> {
                    firstAnalyzeGate.await()
                    prePoseAnalysisResult(
                        poses = listOf(poseAt(0L, handLandmark(index = 19, x = 0.12f, y = 0.88f))),
                        processedFrames = listOf(
                            processedFrame(0L, true),
                            processedFrame(100L, true),
                            processedFrame(200L, true)
                        )
                    )
                }

                else -> prePoseAnalysisResult(
                    poses = listOf(poseAt(0L, handLandmark(index = 20, x = 0.86f, y = 0.22f))),
                    processedFrames = listOf(
                        processedFrame(0L, true),
                        processedFrame(100L, true),
                        processedFrame(200L, true)
                    )
                )
            }
        }

        val viewModel = createViewModel(
            context = mockContext(),
            poseEstimator = mockk(relaxed = true),
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider
        )
        val cacheDir = viewModel.run {
            (getPrivateField(this, "context") as Context).cacheDir
        }
        val firstPlaybackUri = "file:///temp_cleanup_a.mp4"
        val secondPlaybackUri = "file:///temp_cleanup_b.mp4"
        val firstTempFile = File(cacheDir, "temp_cleanup_a.mp4").apply { writeText("first") }
        val secondTempFile = File(cacheDir, "temp_cleanup_b.mp4").apply { writeText("second") }

        invokePrivateMethod(
            target = viewModel,
            methodName = "beginSelectionUpdate",
            false
        )
        setPrivateField(
            viewModel,
            "primaryManagedVideo",
            managedAttemptVideo(
                sourceUri = "content://temp_cleanup_a",
                playbackUri = firstPlaybackUri,
                tempFile = firstTempFile
            )
        )
        setPrivateField(viewModel, "videoUri", firstPlaybackUri)

        @Suppress("UNCHECKED_CAST")
        val managedVideosByPlaybackUri =
            getPrivateField(viewModel, "managedVideosByPlaybackUri") as MutableMap<String, ManagedAttemptVideo>
        @Suppress("UNCHECKED_CAST")
        val managedTempFilePaths =
            getPrivateField(viewModel, "managedTempFilePaths") as MutableSet<String>
        managedVideosByPlaybackUri[firstPlaybackUri] = managedAttemptVideo(
            sourceUri = "content://temp_cleanup_a",
            playbackUri = firstPlaybackUri,
            tempFile = firstTempFile
        )
        managedTempFilePaths += firstTempFile.absolutePath

        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )

        waitUntil {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            analyzeCallCount.get() == 1
        }

        invokePrivateMethod(
            target = viewModel,
            methodName = "beginSelectionUpdate",
            false
        )
        setPrivateField(
            viewModel,
            "primaryManagedVideo",
            managedAttemptVideo(
                sourceUri = "content://temp_cleanup_b",
                playbackUri = secondPlaybackUri,
                tempFile = secondTempFile
            )
        )
        setPrivateField(viewModel, "videoUri", secondPlaybackUri)
        managedVideosByPlaybackUri[secondPlaybackUri] = managedAttemptVideo(
            sourceUri = "content://temp_cleanup_b",
            playbackUri = secondPlaybackUri,
            tempFile = secondTempFile
        )
        managedTempFilePaths += secondTempFile.absolutePath
        invokePrivateMethod(
            target = viewModel,
            methodName = "refreshCurrentSelectionPrePoseTargets",
            viewModel.selectionGeneration
        )
        invokePrivateMethod(
            target = viewModel,
            methodName = "cleanupUnusedManagedTempFiles",
            false
        )
        assertTrue(firstTempFile.exists())

        firstAnalyzeGate.complete(Unit)

        waitUntil(timeoutMs = 10_000L) {
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
            analyzeCallCount.get() == 2 && !firstTempFile.exists()
        }

        assertFalse(managedTempFilePaths.contains(firstTempFile.absolutePath))
        assertFalse(managedVideosByPlaybackUri.containsKey(firstPlaybackUri))
        assertTrue(managedVideosByPlaybackUri.containsKey(secondPlaybackUri))
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
        assertTrue(entry?.poses?.isNotEmpty() == true)
        assertEquals(2_000L, entry?.personObservationStartTimeMs)
        assertEquals(2, entry?.timelinePoints?.size)
        assertEquals(2_000L, entry?.timelinePoints?.get(0)?.timeMs)
        assertEquals(4_000L, entry?.timelinePoints?.get(1)?.timeMs)
        assertEquals(1, entry?.timelinePoints?.get(0)?.index)
        assertEquals(2, entry?.timelinePoints?.get(1)?.index)
    }

    private fun createViewModel(
        context: Context = mockContext(),
        poseEstimator: PoseEstimator,
        prePoseVideoAnalysisProvider: PrePoseVideoAnalysisProvider,
        saveChallengeHoldsUseCase: SaveChallengeHoldsUseCase? = null,
        uploadAttemptVideoUseCase: UploadAttemptVideoUseCase? = null,
        endAttemptUseCase: EndAttemptUseCase? = null,
        analyzeHandPeakAndEndUseCase: AnalyzeHandPeakAndEndUseCase = AnalyzeHandPeakAndEndUseCase(),
        personDetector: PersonDetector = mockk(relaxed = true),
        holdDetector: HoldDetector = mockk(relaxed = true)
    ): UploadViewModel {
        val challengeRepository = mockk<ChallengeRepository>(relaxed = true)
        val attemptRepository = mockk<AttemptRepository>(relaxed = true)
        val gymRepository = mockk<GymRepository>(relaxed = true)
        val getMyInfoUseCase = mockk<GetMyInfoUseCase>()
        val analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>()
        val attachAiRealtimeContextUseCase = mockk<AttachAiRealtimeContextUseCase>(relaxed = true)
        val finalizeAiRealtimeSessionUseCase =
            mockk<FinalizeAiRealtimeSessionUseCase>(relaxed = true)
        val resolvedSaveChallengeHoldsUseCase =
            saveChallengeHoldsUseCase ?: SaveChallengeHoldsUseCase(challengeRepository)
        val resolvedUploadAttemptVideoUseCase =
            uploadAttemptVideoUseCase ?: UploadAttemptVideoUseCase(attemptRepository)
        val resolvedEndAttemptUseCase = endAttemptUseCase ?: EndAttemptUseCase(attemptRepository)

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

        return UploadViewModel(
            context = context,
            personDetector = personDetector,
            holdDetector = holdDetector,
            poseEstimator = poseEstimator,
            prePoseVideoAnalysisProvider = prePoseVideoAnalysisProvider,
            holdColorClassifier = HoldColorClassifier(),
            searchNearbyClimbingGymsUseCase = SearchNearbyClimbingGymsUseCase(gymRepository),
            resolveGymUseCase = ResolveGymUseCase(gymRepository),
            createChallengeUseCase = CreateChallengeUseCase(challengeRepository),
            saveChallengeHoldsUseCase = resolvedSaveChallengeHoldsUseCase,
            uploadAttemptVideoUseCase = resolvedUploadAttemptVideoUseCase,
            endAttemptUseCase = resolvedEndAttemptUseCase,
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
        val field = resolveFieldOrNull(target, fieldName)
        if (field != null) {
            val fieldValue = field.get(target)
            when (fieldValue) {
                is MutableStateFlow<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val stateFlow = fieldValue as MutableStateFlow<Any?>
                    stateFlow.value = value
                }

                is MutableState<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val state = fieldValue as MutableState<Any?>
                    state.value = value
                }

                else -> field.set(target, value)
            }
            return
        }

        val setter = resolveSetter(target, fieldName) ?: throw NoSuchFieldException(fieldName)
        setter.invoke(target, value)
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = resolveFieldOrNull(target, fieldName)
        if (field != null) {
            val fieldValue = field.get(target)
            when (fieldValue) {
                is MutableStateFlow<*> -> return fieldValue.value
                is MutableState<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val state = fieldValue as MutableState<Any?>
                    return state.value
                }
            }
            return field.get(target)
        }
        val getter = resolveGetter(target, fieldName) ?: throw NoSuchFieldException(fieldName)
        return getter.invoke(target)
    }

    private fun resolveFieldOrNull(target: Any, fieldName: String) = target.javaClass.declaredFields
        .firstOrNull { field ->
            field.name == fieldName || field.name == "${fieldName}\$delegate" || field.name.startsWith(fieldName)
        }
        ?.apply { isAccessible = true }

    private fun resolveGetter(target: Any, fieldName: String) = target.javaClass.declaredMethods
        .firstOrNull { method ->
            method.parameterCount == 0 && method.name == "get${fieldName.replaceFirstChar(Char::titlecase)}"
        }
        ?.apply { isAccessible = true }

    private fun resolveSetter(target: Any, fieldName: String) = target.javaClass.declaredMethods
        .firstOrNull { method ->
            method.parameterCount == 1 && method.name == "set${fieldName.replaceFirstChar(Char::titlecase)}"
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
        processedFrames: List<ProcessedPoseDetectionFrame>
    ): PrePoseVideoAnalysisResult = PrePoseVideoAnalysisResult(
        poses = poses,
        processedFrames = processedFrames
    )

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

    private fun managedAttemptVideo(
        sourceUri: String,
        playbackUri: String,
        tempFile: File,
        realtimeSessionId: String? = null
    ): ManagedAttemptVideo = ManagedAttemptVideo(
        sourceUri = sourceUri,
        playbackUri = playbackUri,
        tempFilePath = tempFile.absolutePath,
        realtimeSessionId = realtimeSessionId
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
