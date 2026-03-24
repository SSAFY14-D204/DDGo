package com.ddgo.app.feature.climbing.upload

import android.graphics.Bitmap
import com.ddgo.app.domain.model.AiAnalysisMode
import com.ddgo.app.domain.model.AiPoseSequence
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.HoldBoundingBox
import com.ddgo.app.domain.model.HoldPoint
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.model.UploadedAttemptVideo
import com.ddgo.app.domain.usecase.AnalyzeAttemptWithAiUseCase
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadSubmissionDelegateTest {

    @Test
    fun `attempt result preparation proceeds even when loading message already exists from hold alignment`() = runTest {
        val delegate = UploadSubmissionDelegate(
            saveChallengeHoldsUseCase = mockk<SaveChallengeHoldsUseCase>(relaxed = true),
            uploadAttemptVideoUseCase = mockk<UploadAttemptVideoUseCase>(relaxed = true),
            endAttemptUseCase = mockk<EndAttemptUseCase>(relaxed = true),
            getMyInfoUseCase = mockk<GetMyInfoUseCase>(relaxed = true),
            analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>(relaxed = true)
        )
        val callbacks = FakeSubmissionCallbacks(
            readyPlaybackUris = listOf("file:///attempt.mp4")
        )
        val request = UploadSubmissionRequest(
            selectionGeneration = 1L,
            challengeId = null,
            useLocalAnalysisOnly = true,
            isAttemptOnlyUploadMode = true,
            attemptUris = listOf("file:///attempt.mp4"),
            attemptAlignedHoldSets = emptyMap(),
            detectedHolds = listOf(
                hold(holdNo = 1, left = 0.15f, top = 0.70f, right = 0.22f, bottom = 0.78f),
                hold(holdNo = 2, left = 0.58f, top = 0.32f, right = 0.65f, bottom = 0.40f)
            ),
            numberedHolds = listOf(
                numberedHold(holdNo = 1, role = HoldRole.START, left = 0.15f, top = 0.70f, right = 0.22f, bottom = 0.78f),
                numberedHold(holdNo = 2, role = HoldRole.END, left = 0.58f, top = 0.32f, right = 0.65f, bottom = 0.40f)
            ),
            bestFrameBitmap = mockk<Bitmap>(relaxed = true),
            aiMode = AiAnalysisMode.FAST,
            holdCoordinates = listOf(
                ChallengeHoldCoordinate(
                    holdNo = 1,
                    boundingBox = HoldBoundingBox(0.15f, 0.22f, 0.70f, 0.78f),
                    polygon = listOf(HoldPoint(0.15f, 0.70f), HoldPoint(0.22f, 0.78f))
                )
            )
        )

        delegate.setUploadSubmissionLoading("홀드 정렬 결과를 기다리는 중입니다.")

        delegate.submitUploadForAttemptResult(
            scope = this,
            request = request,
            callbacks = callbacks
        )

        assertTrue(delegate.uploadSubmissionUiState.value is UploadSubmissionUiState.Success)
        assertEquals(listOf("file:///attempt.mp4"), callbacks.resultPlaybackUris)
        assertEquals(0, callbacks.currentAttemptIndexValue)
        assertTrue(callbacks.publishedSessionValue != null)
    }

    private class FakeSubmissionCallbacks(
        private val readyPlaybackUris: List<String>
    ) : UploadSubmissionCallbacks {
        var currentAttemptIndexValue: Int = 0
        var resultPlaybackUris: List<String> = emptyList()
        var publishedSessionValue: PublishedAttemptResultSession? = null

        override suspend fun awaitSubmitReadyPrePose(
            playbackUris: List<String>,
            emitLoading: Boolean
        ): TerminalPrePoseSnapshot {
            return TerminalPrePoseSnapshot(
                generation = 1L,
                entriesByPlaybackUri = playbackUris.associateWith { playbackUri ->
                    TerminalPrePoseEntry(
                        playbackUri = playbackUri,
                        selectionGeneration = 1L,
                        status = if (playbackUri in readyPlaybackUris) PrePoseStatus.Ready else PrePoseStatus.Failed,
                        aiPoseSequence = if (playbackUri in readyPlaybackUris) {
                            mockk<AiPoseSequence>(relaxed = true)
                        } else {
                            null
                        },
                        filteredAiPoseSequence = null,
                        poses = emptyList<Pose>(),
                        filteredPoses = emptyList(),
                        smoothedPoses = emptyList(),
                        processedFrames = emptyList(),
                        poseValidityFrames = emptyList(),
                        overlayCache = null,
                        personObservationStartTimeMs = null,
                        wallArrivalTimeMs = null,
                        stallSegment = null,
                        climbEndDetection = null,
                        handPeakAnnotation = null,
                        timelinePoints = emptyList(),
                        errorMessage = null
                    )
                }
            )
        }

        override fun currentAttemptIndex(): Int = currentAttemptIndexValue

        override fun setCurrentAttemptIndex(index: Int) {
            currentAttemptIndexValue = index
        }

        override fun clearCurrentPoseLandmarks() = Unit

        override fun syncDisplayedAnalysisPoints() = Unit

        override fun resetDisplayedAnalysisPoints() = Unit

        override fun sessionResultPlaybackUris(): List<String> = resultPlaybackUris

        override fun setSessionResultPlaybackUris(uris: List<String>) {
            resultPlaybackUris = uris
        }

        override fun publishedSession(): PublishedAttemptResultSession? = publishedSessionValue

        override fun setPublishedSession(session: PublishedAttemptResultSession?) {
            publishedSessionValue = session
        }

        override fun setSavedChallengeHolds(saved: SavedChallengeHolds?) = Unit
    }

    private fun hold(
        holdNo: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Hold {
        return Hold(
            holdNo = holdNo,
            boundingBox = Hold.BoundingBox(left, top, right, bottom),
            confidence = 0.95f,
            polygon = listOf(
                Hold.Point(left, top),
                Hold.Point(right, top),
                Hold.Point(right, bottom),
                Hold.Point(left, bottom)
            ),
            colorLabel = "pink",
            colorScore = 0.95f
        )
    }

    private fun numberedHold(
        holdNo: Int,
        role: HoldRole,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): HoldNumbered {
        return HoldNumbered(
            hold = hold(
                holdNo = holdNo,
                left = left,
                top = top,
                right = right,
                bottom = bottom
            ),
            progress = if (role == HoldRole.START) 0f else 1f,
            axisDistance = 0f,
            role = role
        )
    }
}
