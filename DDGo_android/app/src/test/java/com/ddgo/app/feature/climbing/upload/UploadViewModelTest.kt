package com.ddgo.app.feature.climbing.upload

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import com.ddgo.app.data.ml.color.HoldColorClassifier
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.repository.AttemptRepository
import com.ddgo.app.domain.repository.ChallengeRepository
import com.ddgo.app.domain.repository.GymRepository
import com.ddgo.app.domain.repository.HoldDetector
import com.ddgo.app.domain.repository.PersonDetector
import com.ddgo.app.domain.repository.PoseEstimator
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.CreateChallengeUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.ResolveGymUseCase
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.SearchNearbyClimbingGymsUseCase
import com.ddgo.app.domain.usecase.AnalyzeAttemptWithAiUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import com.ddgo.app.domain.usecase.summarizeHoldReachResults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val poseEstimator = mockk<PoseEstimator>()
        coEvery { poseEstimator.estimateFromVideo(any()) } returns listOf(
            poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
            poseAt(500L, handLandmark(index = 20, x = 0.62f, y = 0.34f))
        )

        val viewModel = createViewModel(poseEstimator = poseEstimator)
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

        coVerify(exactly = 1) { poseEstimator.estimateFromVideo(videoUri) }
        assertEquals(listOf(videoUri), viewModel.playbackAttemptUris)
        assertEquals(1, viewModel.attemptHoldReachResults.size)
        assertEquals(2, viewModel.currentAttemptHoldReachResult?.highestReachedHoldNo)
    }

    @Test
    fun `pre-pose 실패 후 submitUpload에서도 estimator를 자동 재호출하지 않는다`() = runTest {
        val poseEstimator = mockk<PoseEstimator>()
        coEvery { poseEstimator.estimateFromVideo(any()) } throws IllegalStateException("boom")

        val viewModel = createViewModel(poseEstimator = poseEstimator)
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

        coVerify(exactly = 1) { poseEstimator.estimateFromVideo(videoUri) }
        assertTrue(viewModel.currentAttemptPoseSequence.isEmpty())
        assertEquals(PrePoseStatus.Failed, viewModel.currentAttemptPrePoseEntry?.status)
        assertEquals(0, viewModel.attemptHoldReachResults.single().highestReachedHoldNo)
    }

    @Test
    fun `홀드 재선택은 pre-pose를 유지하고 hold reach만 초기화한다`() = runTest {
        val viewModel = createViewModel(
            poseEstimator = mockk<PoseEstimator>(relaxed = true)
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
        val poseEstimator = mockk<PoseEstimator>()
        coEvery { poseEstimator.estimateFromVideo(any()) } returns emptyList()
        val viewModel = createViewModel(poseEstimator = poseEstimator)

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

    private fun createViewModel(
        context: Context = mockContext(),
        poseEstimator: PoseEstimator,
        personDetector: PersonDetector = mockk(relaxed = true),
        holdDetector: HoldDetector = mockk(relaxed = true)
    ): UploadViewModel {
        val challengeRepository = mockk<ChallengeRepository>(relaxed = true)
        val attemptRepository = mockk<AttemptRepository>(relaxed = true)
        val gymRepository = mockk<GymRepository>(relaxed = true)

        return UploadViewModel(
            context = context,
            personDetector = personDetector,
            holdDetector = holdDetector,
            poseEstimator = poseEstimator,
            holdColorClassifier = HoldColorClassifier(),
            searchNearbyClimbingGymsUseCase = SearchNearbyClimbingGymsUseCase(gymRepository),
            resolveGymUseCase = ResolveGymUseCase(gymRepository),
            createChallengeUseCase = CreateChallengeUseCase(challengeRepository),
            saveChallengeHoldsUseCase = SaveChallengeHoldsUseCase(challengeRepository),
            uploadAttemptVideoUseCase = UploadAttemptVideoUseCase(attemptRepository),
            endAttemptUseCase = EndAttemptUseCase(attemptRepository),
            getMyInfoUseCase = mockk<GetMyInfoUseCase>(relaxed = true),
            analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>(relaxed = true)
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
        val field = resolveField(target, fieldName)
        if (field.name.endsWith("\$delegate")) {
            @Suppress("UNCHECKED_CAST")
            val state = field.get(target) as MutableState<Any?>
            state.value = value
        } else {
            field.set(target, value)
        }
    }

    private fun getPrivateField(target: Any, fieldName: String): Any? {
        val field = resolveField(target, fieldName)
        if (field.name.endsWith("\$delegate")) {
            @Suppress("UNCHECKED_CAST")
            val state = field.get(target) as MutableState<Any?>
            return state.value
        }
        return field.get(target)
    }

    private fun resolveField(target: Any, fieldName: String) = target.javaClass.declaredFields
        .firstOrNull { field ->
            field.name == fieldName || field.name == "${fieldName}\$delegate" || field.name.startsWith(fieldName)
        }
        ?.apply { isAccessible = true }
        ?: throw NoSuchFieldException(fieldName)

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

    private fun handLandmark(index: Int, x: Float, y: Float): PoseLandmark = PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = 0f
    )

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
