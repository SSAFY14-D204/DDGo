package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldContactAnalysisTest {

    @Test
    fun `손 랜드마크가 홀드 안에 들어오면 접촉으로 판정한다`() {
        val hold = numberedHold(holdNo = 1, centerX = 0.30f, centerY = 0.70f)

        val contacts = detectHoldContacts(
            landmarks = listOf(handLandmark(index = 19, x = 0.30f, y = 0.70f)),
            holds = listOf(hold)
        )

        assertEquals(1, contacts.size)
        assertEquals(1, contacts.first().holdNo)
        assertTrue(19 in contacts.first().landmarkIndices)
    }

    @Test
    fun `프레임 시퀀스를 따라 최고 도달 홀드가 갱신된다`() {
        val start = numberedHold(holdNo = 1, centerX = 0.20f, centerY = 0.82f)
        val middle = numberedHold(holdNo = 2, centerX = 0.42f, centerY = 0.56f)
        val end = numberedHold(holdNo = 3, centerX = 0.64f, centerY = 0.28f)

        val result = analyzeAttemptHoldReach(
            poses = listOf(
                poseAt(0L, handLandmark(index = 19, x = 0.20f, y = 0.82f)),
                poseAt(500L, handLandmark(index = 20, x = 0.42f, y = 0.56f)),
                poseAt(1_000L, handLandmark(index = 19, x = 0.64f, y = 0.28f))
            ),
            holds = listOf(start, middle, end)
        )

        assertEquals(3, result.highestReachedHoldNo)
        assertEquals(1_000L, result.highestReachedFrameTimeMs)
        assertEquals(setOf(1, 2, 3), result.contactedHoldNos)
        assertEquals(1.0f, result.reachedRatio, 0.0001f)
    }

    @Test
    fun `발 랜드마크만 닿은 경우에는 접촉으로 보지 않는다`() {
        val highHold = numberedHold(holdNo = 4, centerX = 0.60f, centerY = 0.24f)

        val result = analyzeAttemptHoldReach(
            poses = listOf(
                Pose(
                    frameTimeMs = 0L,
                    landmarks = listOf(
                        PoseLandmark(index = 31, x = 0.60f, y = 0.24f, z = 0f),
                        PoseLandmark(index = 32, x = 0.60f, y = 0.24f, z = 0f)
                    )
                )
            ),
            holds = listOf(highHold)
        )

        assertEquals(0, result.highestReachedHoldNo)
        assertTrue(result.contactedHoldNos.isEmpty())
    }

    @Test
    fun `시도별 최고 도달 홀드 번호를 평균내어 최종 요약을 만든다`() {
        val holdCount = 5
        val attemptA = AttemptHoldReachResult(
            highestReachedHold = null,
            highestReachedHoldNo = 2,
            highestReachedFrameTimeMs = 500L,
            totalHoldCount = holdCount,
            contactedHoldNos = setOf(1, 2),
            reachedRatio = 2f / holdCount
        )
        val attemptB = AttemptHoldReachResult(
            highestReachedHold = null,
            highestReachedHoldNo = 4,
            highestReachedFrameTimeMs = 1_000L,
            totalHoldCount = holdCount,
            contactedHoldNos = setOf(1, 2, 3, 4),
            reachedRatio = 4f / holdCount
        )

        val summary = summarizeHoldReachResults(
            results = listOf(attemptA, attemptB),
            totalHoldCount = holdCount
        )

        assertEquals(3.0f, summary.averageHighestReachedHoldNo, 0.0001f)
        assertEquals(3, summary.roundedAverageHighestReachedHoldNo)
        assertEquals(holdCount, summary.totalHoldCount)
        assertEquals(0.6f, summary.averageReachedRatio, 0.0001f)
    }

    private fun poseAt(timeMs: Long, vararg landmarks: PoseLandmark): Pose = Pose(
        frameTimeMs = timeMs,
        landmarks = landmarks.toList()
    )

    private fun handLandmark(index: Int, x: Float, y: Float): PoseLandmark = PoseLandmark(
        index = index,
        x = x,
        y = y,
        z = 0f
    )

    private fun numberedHold(holdNo: Int, centerX: Float, centerY: Float): HoldNumbered {
        val hold = Hold(
            holdNo = holdNo,
            boundingBox = Hold.BoundingBox(
                left = centerX - 0.025f,
                top = centerY - 0.025f,
                right = centerX + 0.025f,
                bottom = centerY + 0.025f
            ),
            confidence = 0.9f,
            polygon = emptyList(),
            colorLabel = "red",
            colorScore = 0.95f
        )

        return HoldNumbered(
            hold = hold,
            progress = (holdNo - 1).toFloat(),
            axisDistance = 0f,
            role = when (holdNo) {
                1 -> HoldRole.START
                else -> HoldRole.NORMAL
            }
        )
    }
}
