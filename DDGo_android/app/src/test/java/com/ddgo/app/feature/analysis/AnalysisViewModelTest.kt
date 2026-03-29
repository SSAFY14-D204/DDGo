package com.ddgo.app.feature.analysis

import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeStatus
import com.ddgo.app.domain.usecase.GetAnalysisSnapshotsUseCase
import com.ddgo.app.feature.analysis.model.AnalysisScreenState
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `reset to root returns dashboard from all challenges`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openAllChallenges()
        assertEquals(AnalysisScreenState.AllChallenges, viewModel.uiState.value.currentScreen)

        viewModel.resetToRoot()

        assertEquals(AnalysisScreenState.Dashboard, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `reset to root returns dashboard from challenge detail`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openChallengeDetail(1L)
        assertEquals(AnalysisScreenState.ChallengeDetail, viewModel.uiState.value.currentScreen)

        viewModel.resetToRoot()

        assertEquals(AnalysisScreenState.Dashboard, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `reset to root returns dashboard from attempt detail`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openChallengeDetail(1L)
        viewModel.openAttemptDetail(2)
        assertEquals(AnalysisScreenState.AttemptDetail, viewModel.uiState.value.currentScreen)

        viewModel.resetToRoot()

        assertEquals(AnalysisScreenState.Dashboard, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `reset to root returns dashboard after external challenge detail open`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openChallengeDetailIfAvailable(1L)
        assertEquals(AnalysisScreenState.ChallengeDetail, viewModel.uiState.value.currentScreen)

        viewModel.resetToRoot()

        assertEquals(AnalysisScreenState.Dashboard, viewModel.uiState.value.currentScreen)
    }

    @Test
    fun `reset to root returns dashboard from externally opened challenge detail`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openChallengeDetailIfAvailable(1L)
        assertEquals(AnalysisScreenState.ChallengeDetail, viewModel.uiState.value.currentScreen)

        viewModel.resetToRoot()

        assertEquals(AnalysisScreenState.Dashboard, viewModel.uiState.value.currentScreen)
    }

    private fun createViewModel(
        getAnalysisSnapshotsUseCase: GetAnalysisSnapshotsUseCase = mockk()
    ): AnalysisViewModel {
        coEvery { getAnalysisSnapshotsUseCase() } returns Result.success(
            listOf(sampleChallenge())
        )

        return AnalysisViewModel(
            getAnalysisSnapshotsUseCase = getAnalysisSnapshotsUseCase
        )
    }

    private fun sampleChallenge(): AnalysisChallengeSnapshot {
        return AnalysisChallengeSnapshot(
            id = 1L,
            gymName = "DDGo Gym",
            problemColor = "Blue",
            gradeLabel = "V4",
            challengeStatus = AnalysisChallengeStatus.CLOSED,
            challengeResult = AnalysisChallengeResult.FAIL,
            startedAt = LocalDateTime.of(2026, 3, 26, 10, 0),
            endedAt = LocalDateTime.of(2026, 3, 26, 10, 15),
            finalComment = "Keep driving with your legs.",
            attempts = listOf(
                AnalysisAttemptSnapshot(
                    attemptId = 10L,
                    attemptNo = 1,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    videoUrl = null,
                    durationMs = 8_000L,
                    maxHoldNo = 5,
                    centerStabilityRatio = 0.62f,
                    cruxHoldNo = 4,
                    cruxDurationMs = 1_400L,
                    dangerEventCount = 1,
                    failureReason = "Lost balance",
                    riskAlert = null,
                    nextMission = "Push from the right foot"
                ),
                AnalysisAttemptSnapshot(
                    attemptId = 11L,
                    attemptNo = 2,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    videoUrl = null,
                    durationMs = 9_200L,
                    maxHoldNo = 6,
                    centerStabilityRatio = 0.68f,
                    cruxHoldNo = 5,
                    cruxDurationMs = 1_250L,
                    dangerEventCount = 0,
                    failureReason = "Hip drift",
                    riskAlert = null,
                    nextMission = "Stay tighter on the wall"
                )
            )
        )
    }
}
