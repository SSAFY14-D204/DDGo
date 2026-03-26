package com.ddgo.app.feature.climbing.upload

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ddgo.app.core.ui.theme.DDGoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdditionalAttemptPromptDialogTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersPromptCopyAndButtons() {
        composeTestRule.setContent {
            DDGoTheme(darkTheme = false) {
                AdditionalAttemptPromptDialog(
                    onNavigateToAdditional = {},
                    onNavigateToNext = {}
                )
            }
        }

        composeTestRule.onNodeWithText("이 문제의 추가 시도 영상이 있나요?").assertIsDisplayed()
        composeTestRule.onNodeWithText("시도 별로 비교해서 분석을 보여줄게요").assertIsDisplayed()
        composeTestRule.onNodeWithText("없어요").assertIsDisplayed()
        composeTestRule.onNodeWithText("더 있어요!").assertIsDisplayed()
    }

    @Test
    fun clickingSkipCallsNextOnly() {
        var nextCalls = 0
        var additionalCalls = 0

        composeTestRule.setContent {
            DDGoTheme(darkTheme = false) {
                AdditionalAttemptPromptDialog(
                    onNavigateToAdditional = { additionalCalls += 1 },
                    onNavigateToNext = { nextCalls += 1 }
                )
            }
        }

        composeTestRule.onNodeWithText("없어요").performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, nextCalls)
            assertEquals(0, additionalCalls)
        }
    }

    @Test
    fun clickingAdditionalCallsAdditionalOnly() {
        var nextCalls = 0
        var additionalCalls = 0

        composeTestRule.setContent {
            DDGoTheme(darkTheme = false) {
                AdditionalAttemptPromptDialog(
                    onNavigateToAdditional = { additionalCalls += 1 },
                    onNavigateToNext = { nextCalls += 1 }
                )
            }
        }

        composeTestRule.onNodeWithText("더 있어요!").performClick()

        composeTestRule.runOnIdle {
            assertEquals(0, nextCalls)
            assertEquals(1, additionalCalls)
        }
    }
}
