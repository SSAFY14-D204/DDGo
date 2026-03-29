package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

enum class AttemptHoldAlignmentStatus {
    ExactReference,
    Matched,
    PartialWarpFallback,
    ReferenceFallback,
    Failed
}

data class AttemptHoldAlignmentResult(
    val status: AttemptHoldAlignmentStatus,
    val alignedHolds: List<HoldNumbered>,
    val matchedCount: Int,
    val warpedCount: Int,
    val coverage: Float,
    val meanCenterDistance: Float?,
    val debugSummary: String
)

fun alignAttemptHolds(
    referenceHolds: List<HoldNumbered>,
    candidateHolds: List<Hold>
): AttemptHoldAlignmentResult {
    if (referenceHolds.isEmpty()) {
        return AttemptHoldAlignmentResult(
            status = AttemptHoldAlignmentStatus.Failed,
            alignedHolds = emptyList(),
            matchedCount = 0,
            warpedCount = 0,
            coverage = 0f,
            meanCenterDistance = null,
            debugSummary = "reference holds are empty"
        )
    }

    if (candidateHolds.isEmpty()) {
        return AttemptHoldAlignmentResult(
            status = AttemptHoldAlignmentStatus.Failed,
            alignedHolds = emptyList(),
            matchedCount = 0,
            warpedCount = referenceHolds.size,
            coverage = 0f,
            meanCenterDistance = null,
            debugSummary = "candidate holds are empty"
        )
    }

    if (referenceHolds.size == 1) {
        val reference = referenceHolds.first()
        val matched = candidateHolds.minByOrNull { candidate ->
            holdDistance(reference.hold, candidate)
        } ?: return AttemptHoldAlignmentResult(
            status = AttemptHoldAlignmentStatus.Failed,
            alignedHolds = emptyList(),
            matchedCount = 0,
            warpedCount = 1,
            coverage = 0f,
            meanCenterDistance = null,
            debugSummary = "single-hold alignment failed"
        )

        return AttemptHoldAlignmentResult(
            status = AttemptHoldAlignmentStatus.Matched,
            alignedHolds = listOf(reference.withAlignedHold(matched)),
            matchedCount = 1,
            warpedCount = 0,
            coverage = 1f,
            meanCenterDistance = holdDistance(reference.hold, matched),
            debugSummary = "single-hold nearest match"
        )
    }

    val referenceStart = referenceHolds.firstOrNull { it.isStart } ?: referenceHolds.minByOrNull(HoldNumbered::holdNo)
    val referenceEnd = referenceHolds.firstOrNull { it.isEnd } ?: referenceHolds.maxByOrNull(HoldNumbered::holdNo)
    if (referenceStart == null || referenceEnd == null || referenceStart.holdNo == referenceEnd.holdNo) {
        return AttemptHoldAlignmentResult(
            status = AttemptHoldAlignmentStatus.Failed,
            alignedHolds = emptyList(),
            matchedCount = 0,
            warpedCount = referenceHolds.size,
            coverage = 0f,
            meanCenterDistance = null,
            debugSummary = "reference start/end could not be resolved"
        )
    }

    var bestEvaluation: TransformEvaluation? = null
    candidateHolds.forEachIndexed { startIndex, startCandidate ->
        candidateHolds.forEachIndexed { endIndex, endCandidate ->
            if (startIndex == endIndex) return@forEachIndexed
            val transform = buildSimilarityTransform(
                fromStart = calculateHoldCenter(referenceStart.hold),
                fromEnd = calculateHoldCenter(referenceEnd.hold),
                toStart = calculateHoldCenter(startCandidate),
                toEnd = calculateHoldCenter(endCandidate)
            ) ?: return@forEachIndexed

            val evaluation = evaluateTransform(
                transform = transform,
                referenceHolds = referenceHolds,
                candidateHolds = candidateHolds
            )

            bestEvaluation = chooseBetterEvaluation(
                current = bestEvaluation,
                candidate = evaluation
            )
        }
    }

    val chosen = bestEvaluation ?: return AttemptHoldAlignmentResult(
        status = AttemptHoldAlignmentStatus.Failed,
        alignedHolds = emptyList(),
        matchedCount = 0,
        warpedCount = referenceHolds.size,
        coverage = 0f,
        meanCenterDistance = null,
        debugSummary = "no viable transform found"
    )

    val minRequiredMatches = when {
        referenceHolds.size >= 5 -> 3
        referenceHolds.size >= 3 -> 2
        else -> referenceHolds.size
    }
    if (chosen.matches.size < minRequiredMatches) {
        return AttemptHoldAlignmentResult(
            status = AttemptHoldAlignmentStatus.Failed,
            alignedHolds = emptyList(),
            matchedCount = chosen.matches.size,
            warpedCount = referenceHolds.size - chosen.matches.size,
            coverage = chosen.coverage,
            meanCenterDistance = chosen.meanDistance,
            debugSummary = "insufficient matched holds: matched=${chosen.matches.size}, required=$minRequiredMatches"
        )
    }

    val aligned = referenceHolds.mapIndexed { refIndex, reference ->
        val matchedCandidateIndex = chosen.matches[refIndex]
        if (matchedCandidateIndex != null) {
            reference.withAlignedHold(candidateHolds[matchedCandidateIndex])
        } else {
            reference.withAlignedHold(transformHold(reference.hold, chosen.transform))
        }
    }

    val warpedCount = referenceHolds.size - chosen.matches.size
    val status = if (warpedCount == 0) {
        AttemptHoldAlignmentStatus.Matched
    } else {
        AttemptHoldAlignmentStatus.PartialWarpFallback
    }
    val debugSummary = buildString {
        append("matched=${chosen.matches.size}/${referenceHolds.size}")
        append(", coverage=${"%.3f".format(chosen.coverage)}")
        append(", orderViolations=${chosen.orderViolationCount}")
        append(", meanCost=${"%.3f".format(chosen.meanCost)}")
    }

    return AttemptHoldAlignmentResult(
        status = status,
        alignedHolds = aligned,
        matchedCount = chosen.matches.size,
        warpedCount = warpedCount,
        coverage = chosen.coverage,
        meanCenterDistance = chosen.meanDistance,
        debugSummary = debugSummary
    )
}

private data class SimilarityTransform(
    val scale: Float,
    val cosTheta: Float,
    val sinTheta: Float,
    val tx: Float,
    val ty: Float
) {
    fun mapPoint(point: Hold.Point): Hold.Point = Hold.Point(
        x = (scale * (cosTheta * point.x - sinTheta * point.y) + tx).coerceIn(0f, 1f),
        y = (scale * (sinTheta * point.x + cosTheta * point.y) + ty).coerceIn(0f, 1f)
    )

    fun mapCenter(center: HoldCenter): HoldCenter = HoldCenter(
        x = (scale * (cosTheta * center.x - sinTheta * center.y) + tx),
        y = (scale * (sinTheta * center.x + cosTheta * center.y) + ty)
    )
}

private data class MatchCost(
    val refIndex: Int,
    val candidateIndex: Int,
    val totalCost: Float,
    val centerDistance: Float
)

private data class TransformEvaluation(
    val transform: SimilarityTransform,
    val matches: Map<Int, Int>,
    val coverage: Float,
    val meanCost: Float,
    val meanDistance: Float?,
    val orderViolationCount: Int
)

private fun buildSimilarityTransform(
    fromStart: HoldCenter,
    fromEnd: HoldCenter,
    toStart: HoldCenter,
    toEnd: HoldCenter
): SimilarityTransform? {
    val fromDx = fromEnd.x - fromStart.x
    val fromDy = fromEnd.y - fromStart.y
    val toDx = toEnd.x - toStart.x
    val toDy = toEnd.y - toStart.y

    val fromLength = hypot(fromDx, fromDy)
    val toLength = hypot(toDx, toDy)
    if (fromLength <= 1e-6f || toLength <= 1e-6f) {
        return null
    }

    val scale = toLength / fromLength
    val cosTheta = ((fromDx * toDx) + (fromDy * toDy)) / (fromLength * toLength)
    val sinTheta = ((fromDx * toDy) - (fromDy * toDx)) / (fromLength * toLength)

    val mappedStartX = scale * (cosTheta * fromStart.x - sinTheta * fromStart.y)
    val mappedStartY = scale * (sinTheta * fromStart.x + cosTheta * fromStart.y)

    return SimilarityTransform(
        scale = scale,
        cosTheta = cosTheta,
        sinTheta = sinTheta,
        tx = toStart.x - mappedStartX,
        ty = toStart.y - mappedStartY
    )
}

private fun evaluateTransform(
    transform: SimilarityTransform,
    referenceHolds: List<HoldNumbered>,
    candidateHolds: List<Hold>
): TransformEvaluation {
    val costs = mutableListOf<MatchCost>()

    referenceHolds.forEachIndexed { refIndex, reference ->
        val predictedCenter = transform.mapCenter(calculateHoldCenter(reference.hold))
        val predictedBox = transformBoundingBox(reference.hold.boundingBox, transform)
        val predictedArea = holdArea(predictedBox)
        val predictedDiagonal = bboxDiagonal(predictedBox)
        val searchRadius = max(0.06f, predictedDiagonal * 1.15f)

        candidateHolds.forEachIndexed { candidateIndex, candidate ->
            val centerDistance = centerDistance(
                predictedCenter = predictedCenter,
                candidate = candidate
            )
            if (centerDistance > searchRadius) {
                return@forEachIndexed
            }

            val candidateArea = holdArea(candidate.boundingBox)
            val areaPenalty = normalizedAreaPenalty(predictedArea, candidateArea)
            val distancePenalty = (centerDistance / searchRadius).coerceIn(0f, 2f)
            val totalCost = (distancePenalty * 0.75f) + (areaPenalty * 0.25f)

            costs += MatchCost(
                refIndex = refIndex,
                candidateIndex = candidateIndex,
                totalCost = totalCost,
                centerDistance = centerDistance
            )
        }
    }

    val assignedRefs = mutableSetOf<Int>()
    val assignedCandidates = mutableSetOf<Int>()
    val acceptedCosts = costs
        .sortedBy(MatchCost::totalCost)
        .filter { match ->
            if (match.refIndex in assignedRefs || match.candidateIndex in assignedCandidates) {
                false
            } else {
                assignedRefs += match.refIndex
                assignedCandidates += match.candidateIndex
                true
            }
        }

    val matches = acceptedCosts.associate { it.refIndex to it.candidateIndex }
    val meanCost = acceptedCosts.map(MatchCost::totalCost).averageOrZero()
    val meanDistance = acceptedCosts.map(MatchCost::centerDistance).averageOrNullCompat()
    val coverage = matches.size.toFloat() / referenceHolds.size.toFloat()

    return TransformEvaluation(
        transform = transform,
        matches = matches,
        coverage = coverage,
        meanCost = meanCost,
        meanDistance = meanDistance,
        orderViolationCount = computeOrderViolations(
            matches = matches,
            referenceHolds = referenceHolds,
            candidateHolds = candidateHolds
        )
    )
}

private fun chooseBetterEvaluation(
    current: TransformEvaluation?,
    candidate: TransformEvaluation
): TransformEvaluation {
    if (current == null) {
        return candidate
    }

    return when {
        candidate.coverage > current.coverage + 1e-4f -> candidate
        current.coverage > candidate.coverage + 1e-4f -> current
        candidate.orderViolationCount < current.orderViolationCount -> candidate
        current.orderViolationCount < candidate.orderViolationCount -> current
        candidate.meanCost < current.meanCost -> candidate
        else -> current
    }
}

private fun computeOrderViolations(
    matches: Map<Int, Int>,
    referenceHolds: List<HoldNumbered>,
    candidateHolds: List<Hold>
): Int {
    if (matches.size < 2) {
        return 0
    }

    val orderedMatches = matches.entries
        .sortedBy { (refIndex, _) -> referenceHolds[refIndex].holdNo }
        .mapNotNull { (_, candidateIndex) ->
            candidateHolds.getOrNull(candidateIndex)
        }

    return orderedMatches
        .zipWithNext()
        .count { (left, right) ->
            val leftCenter = calculateHoldCenter(left)
            val rightCenter = calculateHoldCenter(right)
            rightCenter.y > leftCenter.y + 0.01f && rightCenter.x < leftCenter.x - 0.25f
        }
}

private fun HoldNumbered.withAlignedHold(alignedHold: Hold): HoldNumbered = copy(
    hold = alignedHold.copy(holdNo = holdNo)
)

private fun transformHold(
    reference: Hold,
    transform: SimilarityTransform
): Hold {
    val transformedBoundingBox = transformBoundingBox(reference.boundingBox, transform)
    val transformedPolygon = reference.polygon.map(transform::mapPoint)
    return reference.copy(
        boundingBox = transformedBoundingBox,
        polygon = transformedPolygon
    )
}

private fun transformBoundingBox(
    bbox: Hold.BoundingBox,
    transform: SimilarityTransform
): Hold.BoundingBox {
    val corners = listOf(
        Hold.Point(bbox.left, bbox.top),
        Hold.Point(bbox.right, bbox.top),
        Hold.Point(bbox.right, bbox.bottom),
        Hold.Point(bbox.left, bbox.bottom)
    ).map(transform::mapPoint)

    return Hold.BoundingBox(
        left = corners.minOf(Hold.Point::x).coerceIn(0f, 1f),
        top = corners.minOf(Hold.Point::y).coerceIn(0f, 1f),
        right = corners.maxOf(Hold.Point::x).coerceIn(0f, 1f),
        bottom = corners.maxOf(Hold.Point::y).coerceIn(0f, 1f)
    )
}

private fun centerDistance(
    predictedCenter: HoldCenter,
    candidate: Hold
): Float {
    val candidateCenter = calculateHoldCenter(candidate)
    return hypot(candidateCenter.x - predictedCenter.x, candidateCenter.y - predictedCenter.y)
}

private fun holdDistance(
    left: Hold,
    right: Hold
): Float {
    val leftCenter = calculateHoldCenter(left)
    val rightCenter = calculateHoldCenter(right)
    return hypot(rightCenter.x - leftCenter.x, rightCenter.y - leftCenter.y)
}

private fun normalizedAreaPenalty(
    expectedArea: Float,
    actualArea: Float
): Float {
    val safeExpected = max(expectedArea, 1e-6f)
    val safeActual = max(actualArea, 1e-6f)
    val ratio = max(safeExpected, safeActual) / min(safeExpected, safeActual)
    return (ln(ratio.toDouble()) / ln(2.0)).toFloat().coerceIn(0f, 2f)
}

private fun holdArea(bbox: Hold.BoundingBox): Float {
    return max(0f, bbox.right - bbox.left) * max(0f, bbox.bottom - bbox.top)
}

private fun bboxDiagonal(bbox: Hold.BoundingBox): Float {
    return hypot(bbox.right - bbox.left, bbox.bottom - bbox.top)
}

private fun List<Float>.averageOrZero(): Float {
    return if (isEmpty()) 0f else average().toFloat()
}

private fun List<Float>.averageOrNullCompat(): Float? {
    return if (isEmpty()) null else average().toFloat()
}
