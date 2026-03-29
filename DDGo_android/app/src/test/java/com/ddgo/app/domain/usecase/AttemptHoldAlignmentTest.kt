package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AttemptHoldAlignmentTest {

    @Test
    fun `rotated additional attempt matches the reference route`() {
        val reference = referenceRoute()
        val transformedRoute = reference.map { numbered ->
            transformHold(numbered.hold, scale = 0.88f, rotationDeg = 14f, tx = 0.08f, ty = 0.05f)
        }
        val candidateHolds = listOf(
            holdAt(centerX = 0.12f, centerY = 0.20f),
            transformedRoute[2],
            transformedRoute[0],
            transformedRoute[3],
            transformedRoute[1],
            holdAt(centerX = 0.90f, centerY = 0.88f)
        )

        val result = alignAttemptHolds(
            referenceHolds = reference,
            candidateHolds = candidateHolds
        )

        assertEquals(AttemptHoldAlignmentStatus.Matched, result.status)
        assertEquals(reference.size, result.matchedCount)
        assertEquals(0, result.warpedCount)
        assertEquals(listOf(1, 2, 3, 4), result.alignedHolds.map(HoldNumbered::holdNo))
        assertCentersNear(
            expected = transformedRoute,
            actual = result.alignedHolds.map(HoldNumbered::hold)
        )
    }

    @Test
    fun `missing one detected hold falls back to warped prediction`() {
        val reference = referenceRoute()
        val transformedRoute = reference.map { numbered ->
            transformHold(numbered.hold, scale = 0.92f, rotationDeg = -10f, tx = 0.07f, ty = -0.03f)
        }
        val candidateHolds = listOf(
            transformedRoute[0],
            transformedRoute[1],
            transformedRoute[3],
            holdAt(centerX = 0.86f, centerY = 0.12f)
        )

        val result = alignAttemptHolds(
            referenceHolds = reference,
            candidateHolds = candidateHolds
        )

        assertEquals(AttemptHoldAlignmentStatus.PartialWarpFallback, result.status)
        assertEquals(3, result.matchedCount)
        assertEquals(1, result.warpedCount)
        assertCentersNear(
            expected = transformedRoute,
            actual = result.alignedHolds.map(HoldNumbered::hold)
        )
    }

    @Test
    fun `single hold alignment chooses nearest candidate`() {
        val reference = listOf(
            numberedHold(
                holdNo = 1,
                centerX = 0.28f,
                centerY = 0.74f,
                role = HoldRole.START
            )
        )
        val nearCandidate = holdAt(centerX = 0.30f, centerY = 0.72f)
        val farCandidate = holdAt(centerX = 0.80f, centerY = 0.22f)

        val result = alignAttemptHolds(
            referenceHolds = reference,
            candidateHolds = listOf(farCandidate, nearCandidate)
        )

        assertEquals(AttemptHoldAlignmentStatus.Matched, result.status)
        assertEquals(1, result.matchedCount)
        assertEquals(0, result.warpedCount)
        assertCenterNear(expected = nearCandidate, actual = result.alignedHolds.single().hold)
    }

    @Test
    fun `alignment fails when there are not enough candidates to infer a transform`() {
        val reference = referenceRoute()
        val result = alignAttemptHolds(
            referenceHolds = reference,
            candidateHolds = listOf(holdAt(centerX = 0.20f, centerY = 0.82f))
        )

        assertEquals(AttemptHoldAlignmentStatus.Failed, result.status)
        assertEquals(0, result.matchedCount)
        assertEquals(reference.size, result.warpedCount)
        assertTrue(result.alignedHolds.isEmpty())
    }

    @Test
    fun `start and end anchors are resolved by role even when reference order is shuffled`() {
        val orderedReference = referenceRoute()
        val shuffledReference = listOf(
            orderedReference[2],
            orderedReference[0],
            orderedReference[3],
            orderedReference[1]
        )
        val transformedRoute = orderedReference.map { numbered ->
            transformHold(numbered.hold, scale = 0.90f, rotationDeg = 8f, tx = 0.05f, ty = 0.04f)
        }

        val result = alignAttemptHolds(
            referenceHolds = shuffledReference,
            candidateHolds = transformedRoute
        )

        assertEquals(AttemptHoldAlignmentStatus.Matched, result.status)
        val alignedByHoldNo = result.alignedHolds.associateBy(HoldNumbered::holdNo)
        transformedRoute.forEachIndexed { index, expectedHold ->
            val holdNo = index + 1
            assertCenterNear(
                expected = expectedHold,
                actual = alignedByHoldNo.getValue(holdNo).hold
            )
        }
    }

    private fun referenceRoute(): List<HoldNumbered> = listOf(
        numberedHold(holdNo = 1, centerX = 0.16f, centerY = 0.84f, role = HoldRole.START),
        numberedHold(holdNo = 2, centerX = 0.30f, centerY = 0.69f, role = HoldRole.NORMAL),
        numberedHold(holdNo = 3, centerX = 0.47f, centerY = 0.55f, role = HoldRole.NORMAL),
        numberedHold(holdNo = 4, centerX = 0.63f, centerY = 0.33f, role = HoldRole.END)
    )

    private fun numberedHold(
        holdNo: Int,
        centerX: Float,
        centerY: Float,
        role: HoldRole
    ): HoldNumbered = HoldNumbered(
        hold = holdAt(
            centerX = centerX,
            centerY = centerY,
            holdNo = holdNo
        ),
        progress = (holdNo - 1).toFloat(),
        axisDistance = 0f,
        role = role
    )

    private fun holdAt(
        centerX: Float,
        centerY: Float,
        holdNo: Int = 0,
        width: Float = 0.05f,
        height: Float = 0.04f
    ): Hold {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        return Hold(
            holdNo = holdNo,
            boundingBox = Hold.BoundingBox(
                left = (centerX - halfWidth).coerceIn(0f, 1f),
                top = (centerY - halfHeight).coerceIn(0f, 1f),
                right = (centerX + halfWidth).coerceIn(0f, 1f),
                bottom = (centerY + halfHeight).coerceIn(0f, 1f)
            ),
            confidence = 0.95f,
            polygon = emptyList(),
            colorLabel = "red",
            colorScore = 0.9f
        )
    }

    private fun transformHold(
        hold: Hold,
        scale: Float,
        rotationDeg: Float,
        tx: Float,
        ty: Float
    ): Hold {
        val radians = Math.toRadians(rotationDeg.toDouble())
        val cosTheta = cos(radians).toFloat()
        val sinTheta = sin(radians).toFloat()
        val center = calculateHoldCenter(hold)
        val transformedCenterX = (scale * (cosTheta * center.x - sinTheta * center.y) + tx).coerceIn(0f, 1f)
        val transformedCenterY = (scale * (sinTheta * center.x + cosTheta * center.y) + ty).coerceIn(0f, 1f)
        val scaledWidth = (hold.boundingBox.right - hold.boundingBox.left) * scale
        val scaledHeight = (hold.boundingBox.bottom - hold.boundingBox.top) * scale

        return holdAt(
            centerX = transformedCenterX,
            centerY = transformedCenterY,
            holdNo = hold.holdNo,
            width = scaledWidth,
            height = scaledHeight
        )
    }

    private fun assertCentersNear(
        expected: List<Hold>,
        actual: List<Hold>
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedHold, actualHold) ->
            assertCenterNear(expected = expectedHold, actual = actualHold)
        }
    }

    private fun assertCenterNear(
        expected: Hold,
        actual: Hold,
        tolerance: Float = 0.03f
    ) {
        val expectedCenter = calculateHoldCenter(expected)
        val actualCenter = calculateHoldCenter(actual)
        assertEquals(expectedCenter.x, actualCenter.x, tolerance)
        assertEquals(expectedCenter.y, actualCenter.y, tolerance)
    }
}
