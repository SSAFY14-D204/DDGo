package com.ddgo.app.feature.climbing.upload

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadLoadingTimingTest {

    @Test
    fun `remaining loading delay keeps minimum 1 point 5 seconds`() {
        val remaining = remainingLoadingDisplayMillis(
            startedAtMillis = 1_000L,
            nowMillis = 1_400L
        )

        assertEquals(1_100L, remaining)
    }

    @Test
    fun `remaining loading delay becomes zero after minimum duration`() {
        val remaining = remainingLoadingDisplayMillis(
            startedAtMillis = 1_000L,
            nowMillis = 2_700L
        )

        assertEquals(0L, remaining)
    }

    @Test
    fun `analysis loading message hides video upload wording`() {
        val message = toAnalysisLoadingMessage("영상 업로드 중입니다. (1/2)")

        assertEquals("AI 분석 결과를 정리하고 있어요", message)
    }

    @Test
    fun `analysis loading message keeps ai related wording`() {
        val message = toAnalysisLoadingMessage("AI physics 분석 중입니다. (1/2)")

        assertEquals("AI physics 분석 중입니다. (1/2)", message)
    }
}
