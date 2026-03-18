package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HoldNumberingTest {

    @Test
    fun `start에서 end 축 기준 progress 순으로 홀드 번호를 부여한다`() {
        val start = holdAt(centerX = 0.10f, centerY = 0.90f)
        val holdA = holdAt(centerX = 0.25f, centerY = 0.75f)
        val holdB = holdAt(centerX = 0.40f, centerY = 0.70f)
        val holdC = holdAt(centerX = 0.52f, centerY = 0.48f)
        val end = holdAt(centerX = 0.70f, centerY = 0.30f)

        val numbered = assignHoldNumbers(
            holds = listOf(holdC, end, holdA, start, holdB),
            startHold = start,
            endHold = end
        )

        assertEquals(listOf(1, 2, 3, 4, 5), numbered.map { it.holdNo })
        assertEquals(start.boundingBox, numbered.first().hold.boundingBox)
        assertEquals(end.boundingBox, numbered.last().hold.boundingBox)
        assertTrue(numbered.zipWithNext().all { (left, right) -> left.progress <= right.progress || right.isEnd })
    }

    @Test
    fun `axisDistance는 진행축에 가까울수록 작게 계산된다`() {
        val start = holdAt(centerX = 0.10f, centerY = 0.90f)
        val end = holdAt(centerX = 0.70f, centerY = 0.30f)
        val onAxis = holdAt(centerX = 0.40f, centerY = 0.60f)
        val offAxis = holdAt(centerX = 0.40f, centerY = 0.72f)

        val numbered = assignHoldNumbers(
            holds = listOf(start, onAxis, offAxis, end),
            startHold = start,
            endHold = end
        )

        val onAxisNumbered = numbered.first { it.hold.boundingBox == onAxis.boundingBox }
        val offAxisNumbered = numbered.first { it.hold.boundingBox == offAxis.boundingBox }

        assertEquals(0f, onAxisNumbered.axisDistance, 0.0001f)
        assertTrue(offAxisNumbered.axisDistance > onAxisNumbered.axisDistance)
    }

    @Test
    fun `end보다 더 멀리 투영되는 홀드가 있어도 end 홀드는 마지막 번호로 고정된다`() {
        val start = holdAt(centerX = 0.10f, centerY = 0.90f)
        val beyondEnd = holdAt(centerX = 0.82f, centerY = 0.18f)
        val end = holdAt(centerX = 0.70f, centerY = 0.30f)

        val numbered = assignHoldNumbers(
            holds = listOf(beyondEnd, end, start),
            startHold = start,
            endHold = end
        )

        assertEquals(start.boundingBox, numbered[0].hold.boundingBox)
        assertEquals(beyondEnd.boundingBox, numbered[1].hold.boundingBox)
        assertEquals(end.boundingBox, numbered[2].hold.boundingBox)
        assertEquals(3, numbered.last().holdNo)
        assertTrue(numbered[1].progress > numbered.last().progress)
    }

    @Test
    fun `홀드가 두 개 미만이면 예외를 던진다`() {
        expectIllegalArgument("최소 2개의 홀드") {
            assignHoldNumbers(
                holds = listOf(holdAt(centerX = 0.20f, centerY = 0.80f)),
                startHold = holdAt(centerX = 0.20f, centerY = 0.80f),
                endHold = holdAt(centerX = 0.60f, centerY = 0.40f)
            )
        }
    }

    @Test
    fun `시작 홀드와 끝 홀드가 거의 같은 위치면 예외를 던진다`() {
        val start = holdAt(centerX = 0.20f, centerY = 0.80f)
        val end = holdAt(centerX = 0.20f, centerY = 0.80f)
        val middle = holdAt(centerX = 0.40f, centerY = 0.60f)

        expectIllegalArgument("거의 같은 위치") {
            assignHoldNumbers(
                holds = listOf(start, middle, end),
                startHold = start,
                endHold = end
            )
        }
    }

    @Test
    fun `start hold 또는 end hold가 목록에 없으면 예외를 던진다`() {
        val start = holdAt(centerX = 0.10f, centerY = 0.90f)
        val middle = holdAt(centerX = 0.40f, centerY = 0.60f)
        val end = holdAt(centerX = 0.70f, centerY = 0.30f)
        val missing = holdAt(centerX = 0.85f, centerY = 0.15f)

        expectIllegalArgument("목록에 없습니다") {
            assignHoldNumbers(
                holds = listOf(start, middle, end),
                startHold = missing,
                endHold = end
            )
        }
    }

    private fun holdAt(centerX: Float, centerY: Float): Hold {
        val halfSize = 0.02f
        return Hold(
            boundingBox = Hold.BoundingBox(
                left = centerX - halfSize,
                top = centerY - halfSize,
                right = centerX + halfSize,
                bottom = centerY + halfSize
            ),
            confidence = 0.9f,
            polygon = emptyList(),
            colorLabel = "red",
            colorScore = 0.8f
        )
    }

    private fun expectIllegalArgument(expectedMessagePart: String, block: () -> Unit) {
        try {
            block()
            fail("IllegalArgumentException 이 발생해야 합니다.")
        } catch (exception: IllegalArgumentException) {
            assertTrue(
                "예외 메시지에 '$expectedMessagePart'가 포함되어야 합니다: ${exception.message}",
                exception.message?.contains(expectedMessagePart) == true
            )
        }
    }
}
