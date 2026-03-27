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
import com.ddgo.app.domain.usecase.AttemptHoldReachResult
import com.ddgo.app.domain.usecase.EndAttemptUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.HoldNumbered
import com.ddgo.app.domain.usecase.HoldRole
import com.ddgo.app.domain.usecase.OverallHoldReachSummary
import com.ddgo.app.domain.usecase.PolygonHoldContact
import com.ddgo.app.domain.usecase.PolygonHoldContactDebugResult
import com.ddgo.app.domain.usecase.PolygonHoldContactFrame
import com.ddgo.app.domain.usecase.PolygonLimbFrameState
import com.ddgo.app.domain.usecase.PolygonTrackedLimb
import com.ddgo.app.domain.usecase.findFirstStartFootContactTimeMs
import com.ddgo.app.domain.usecase.SaveChallengeHoldsUseCase
import com.ddgo.app.domain.usecase.UploadAttemptVideoUseCase
import com.ddgo.app.domain.usecase.findSuccessfulTopContactTimeMs
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UploadSubmissionDelegateTest {

    @Test
    fun `findSuccessfulTopContactTimeMs returns first simultaneous end hold contact after analysis start`() {
        val endHold = numberedHold(
            holdNo = 2,
            role = HoldRole.END,
            left = 0.45f,
            top = 0.45f,
            right = 0.55f,
            bottom = 0.55f
        )
        val debugResult = PolygonHoldContactDebugResult(
            frames = listOf(
                polygonFrame(
                    timeMs = 120L,
                    hold = endHold,
                    leftHandHoldNo = 2,
                    rightHandHoldNo = 2
                ),
                polygonFrame(
                    timeMs = 260L,
                    hold = endHold,
                    leftHandHoldNo = 2,
                    rightHandHoldNo = 2
                )
            ),
            highestReachedHoldNo = 2,
            highestReachedFrameTimeMs = 260L,
            contactedHoldNos = setOf(2)
        )

        assertEquals(
            260L,
            debugResult.findSuccessfulTopContactTimeMs(
                endHoldNo = 2,
                analysisStartTimeMs = 150L
            )
        )
    }

    @Test
    fun `findFirstStartFootContactTimeMs returns earliest foot contact on start hold after analysis start`() {
        val startHold = numberedHold(
            holdNo = 1,
            role = HoldRole.START,
            left = 0.12f,
            top = 0.62f,
            right = 0.24f,
            bottom = 0.78f
        )
        val debugResult = PolygonHoldContactDebugResult(
            frames = listOf(
                polygonFrame(
                    timeMs = 120L,
                    hold = startHold,
                    leftFootHoldNo = 1
                ),
                polygonFrame(
                    timeMs = 260L,
                    hold = startHold,
                    rightFootHoldNo = 1
                )
            ),
            highestReachedHoldNo = 1,
            highestReachedFrameTimeMs = 260L,
            contactedHoldNos = setOf(1)
        )

        assertEquals(
            260L,
            debugResult.findFirstStartFootContactTimeMs(
                startHoldNo = 1,
                analysisStartTimeMs = 150L
            )
        )
    }

    @Test
    fun `attempt success resolution requires both hands on end hold`() {
        val delegate = UploadSubmissionDelegate(
            saveChallengeHoldsUseCase = mockk<SaveChallengeHoldsUseCase>(relaxed = true),
            uploadAttemptVideoUseCase = mockk<UploadAttemptVideoUseCase>(relaxed = true),
            endAttemptUseCase = mockk<EndAttemptUseCase>(relaxed = true),
            getMyInfoUseCase = mockk<GetMyInfoUseCase>(relaxed = true),
            analyzeAttemptWithAiUseCase = mockk<AnalyzeAttemptWithAiUseCase>(relaxed = true)
        )

        delegate.attemptHoldReachResults = listOf(
            AttemptHoldReachResult(
                highestReachedHold = null,
                highestReachedHoldNo = 2,
                highestReachedFrameTimeMs = 500L,
                totalHoldCount = 2,
                contactedHoldNos = setOf(1, 2),
                reachedRatio = 1f,
                completedWithBothHandsOnEndHold = false
            ),
            AttemptHoldReachResult(
                highestReachedHold = null,
                highestReachedHoldNo = 2,
                highestReachedFrameTimeMs = 700L,
                totalHoldCount = 2,
                contactedHoldNos = setOf(1, 2),
                reachedRatio = 1f,
                completedWithBothHandsOnEndHold = true
            )
        )

        assertEquals(false, delegate.resolveAttemptSuccess(index = 0, fallback = true, totalHoldCount = 2))
        assertTrue(delegate.resolveAttemptSuccess(index = 1, fallback = false, totalHoldCount = 2))
    }

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
        var appliedAttemptEndRefinements: List<AttemptEndRefinement> = emptyList()

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
                        resolvedAttemptStartTimeMs = null,
                        resolvedAttemptEndTimeMs = null,
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

        override fun applyAttemptEndRefinements(refinements: List<AttemptEndRefinement>) {
            appliedAttemptEndRefinements = refinements
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

    private fun polygonFrame(
        timeMs: Long,
        hold: HoldNumbered,
        leftHandHoldNo: Int? = null,
        rightHandHoldNo: Int? = null,
        leftFootHoldNo: Int? = null,
        rightFootHoldNo: Int? = null
    ): PolygonHoldContactFrame {
        val limbStates = listOf(
            PolygonLimbFrameState(
                limb = PolygonTrackedLimb.LEFT_HAND,
                state = "GRIP",
                activeHoldNo = leftHandHoldNo,
                candidateHoldNo = leftHandHoldNo,
                distancePx = 0f,
                speedPxPerSec = 0f,
                transition = null,
                insidePolygon = true,
                contactPointNormalized = null
            ),
            PolygonLimbFrameState(
                limb = PolygonTrackedLimb.RIGHT_HAND,
                state = "GRIP",
                activeHoldNo = rightHandHoldNo,
                candidateHoldNo = rightHandHoldNo,
                distancePx = 0f,
                speedPxPerSec = 0f,
                transition = null,
                insidePolygon = true,
                contactPointNormalized = null
            ),
            PolygonLimbFrameState(
                limb = PolygonTrackedLimb.LEFT_FOOT,
                state = "STEP",
                activeHoldNo = leftFootHoldNo,
                candidateHoldNo = leftFootHoldNo,
                distancePx = 0f,
                speedPxPerSec = 0f,
                transition = null,
                insidePolygon = true,
                contactPointNormalized = null
            ),
            PolygonLimbFrameState(
                limb = PolygonTrackedLimb.RIGHT_FOOT,
                state = "STEP",
                activeHoldNo = rightFootHoldNo,
                candidateHoldNo = rightFootHoldNo,
                distancePx = 0f,
                speedPxPerSec = 0f,
                transition = null,
                insidePolygon = true,
                contactPointNormalized = null
            )
        )

        val activeContacts = buildList {
            if (leftHandHoldNo == hold.holdNo) {
                add(
                    PolygonHoldContact(
                        hold = hold,
                        limb = PolygonTrackedLimb.LEFT_HAND,
                        state = "GRIP",
                        insidePolygon = true,
                        distancePx = 0f,
                        speedPxPerSec = 0f,
                        contactPointNormalized = null
                    )
                )
            }
            if (rightHandHoldNo == hold.holdNo) {
                add(
                    PolygonHoldContact(
                        hold = hold,
                        limb = PolygonTrackedLimb.RIGHT_HAND,
                        state = "GRIP",
                        insidePolygon = true,
                        distancePx = 0f,
                        speedPxPerSec = 0f,
                        contactPointNormalized = null
                    )
                )
            }
            if (leftFootHoldNo == hold.holdNo) {
                add(
                    PolygonHoldContact(
                        hold = hold,
                        limb = PolygonTrackedLimb.LEFT_FOOT,
                        state = "STEP",
                        insidePolygon = true,
                        distancePx = 0f,
                        speedPxPerSec = 0f,
                        contactPointNormalized = null
                    )
                )
            }
            if (rightFootHoldNo == hold.holdNo) {
                add(
                    PolygonHoldContact(
                        hold = hold,
                        limb = PolygonTrackedLimb.RIGHT_FOOT,
                        state = "STEP",
                        insidePolygon = true,
                        distancePx = 0f,
                        speedPxPerSec = 0f,
                        contactPointNormalized = null
                    )
                )
            }
        }

        return PolygonHoldContactFrame(
            frameTimeMs = timeMs,
            limbStates = limbStates,
            activeContacts = activeContacts
        )
    }
}
