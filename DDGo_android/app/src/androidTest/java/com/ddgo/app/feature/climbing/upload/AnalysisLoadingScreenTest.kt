package com.ddgo.app.feature.climbing.upload

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisLoadingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun idle_state_submits_once_even_after_screen_reentry() {
        val viewModel = mockk<UploadViewModel>(relaxed = true)
        val submitCallCount = AtomicInteger(0)
        val uploadSubmissionUiState =
            MutableStateFlow<UploadSubmissionUiState>(UploadSubmissionUiState.Idle)
        val showScreen = mutableStateOf(true)

        every { viewModel.uploadSubmissionUiState } returns uploadSubmissionUiState
        every { viewModel.submitUpload() } answers {
            submitCallCount.incrementAndGet()
            uploadSubmissionUiState.value = UploadSubmissionUiState.Loading("분석 중입니다.")
            Unit
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            if (showScreen.value) {
                AnalysisLoadingScreen(viewModel = viewModel)
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitUntil(timeoutMillis = 3_000) { submitCallCount.get() == 1 }

        composeRule.activity.runOnUiThread { showScreen.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.activity.runOnUiThread { showScreen.value = true }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { assertEquals(1, submitCallCount.get()) }
    }
}
