package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val LEFT_HAND_LANDMARK_INDICES = listOf(15, 17, 19, 21)
private val RIGHT_HAND_LANDMARK_INDICES = listOf(16, 18, 20, 22)

/**
 * 홀드 접촉 판정에 사용하는 임계값 모음입니다.
 *
 * 모든 값은 정규화 좌표(0~1) 기준입니다.
 */
data class HoldContactConfig(
    val bboxPaddingRatio: Float = 0.18f,
    val minPadding: Float = 0.015f,
    val maxPadding: Float = 0.06f,
    val centerDistanceRatio: Float = 0.70f,
    val minCenterDistance: Float = 0.035f
)

/** 어느 손이 홀드에 닿았는지 구분합니다. */
enum class ContactHand {
    LEFT,
    RIGHT
}

/**
 * 단일 프레임에서 감지된 홀드 접촉 결과입니다.
 *
 * handSides:
 * - 왼손/오른손 중 어떤 손이 접촉했는지 나타냅니다.
 *
 * landmarkIndices:
 * - 접촉 판정에 사용된 손 랜드마크 인덱스입니다.
 */
data class HoldContact(
    val hold: HoldNumbered,
    val handSides: Set<ContactHand>,
    val landmarkIndices: Set<Int>,
    val minDistanceToHold: Float
) {
    val holdNo: Int
        get() = hold.holdNo
}

/**
 * 한 개의 시도 영상에서 계산된 도달 홀드 요약입니다.
 */
data class AttemptHoldReachResult(
    val highestReachedHold: HoldNumbered?,
    val highestReachedHoldNo: Int,
    val highestReachedFrameTimeMs: Long?,
    val totalHoldCount: Int,
    val contactedHoldNos: Set<Int>,
    val reachedRatio: Float
) {
    val highestReachedProgress: Float?
        get() = highestReachedHold?.normalizedProgress
}

/**
 * 여러 시도 영상을 묶어서 평균 도달 홀드를 계산한 결과입니다.
 */
data class OverallHoldReachSummary(
    val attempts: List<AttemptHoldReachResult>,
    val averageHighestReachedHoldNo: Float,
    val roundedAverageHighestReachedHoldNo: Int,
    val totalHoldCount: Int,
    val averageReachedRatio: Float
)

private data class NormalizedPoint(
    val x: Float,
    val y: Float
)

private data class IndexedPoint(
    val index: Int,
    val point: NormalizedPoint
)

private data class HandPose(
    val hand: ContactHand,
    val points: List<IndexedPoint>
) {
    val center: NormalizedPoint
        get() = NormalizedPoint(
            x = points.map { it.point.x }.average().toFloat(),
            y = points.map { it.point.y }.average().toFloat()
        )
}

/**
 * 현재 프레임의 손 랜드마크가 어떤 홀드에 접촉했는지 계산합니다.
 *
 * 판정 규칙:
 * 1. 손 랜드마크(손목/엄지/검지/새끼손가락) 중 하나라도 홀드 영역에 들어오면 접촉
 * 2. 위 조건이 애매할 때는 손 중심점이 홀드 중심에 충분히 가까우면 접촉으로 보정
 * 3. 발 랜드마크는 제외합니다. "잡았는지" 판정을 손 기준으로만 계산합니다.
 */
fun detectHoldContacts(
    landmarks: List<PoseLandmark>,
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig()
): List<HoldContact> {
    if (landmarks.isEmpty() || holds.isEmpty()) return emptyList()

    val hands = extractHands(landmarks)
    if (hands.isEmpty()) return emptyList()

    return holds.mapNotNull { hold ->
        val handContacts = hands.mapNotNull { hand ->
            detectHandContact(
                hand = hand,
                hold = hold,
                config = config
            )
        }

        if (handContacts.isEmpty()) {
            null
        } else {
            HoldContact(
                hold = hold,
                handSides = handContacts.map { it.hand }.toSet(),
                landmarkIndices = handContacts
                    .flatMap { contact -> contact.points.map { it.index } }
                    .toSet(),
                minDistanceToHold = handContacts.minOf { it.minDistanceToHold }
            )
        }
    }.sortedBy { it.holdNo }
}

/** 단일 프레임에서 가장 높은 번호의 접촉 홀드를 반환합니다. */
fun findHighestReachedHold(
    landmarks: List<PoseLandmark>,
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig()
): HoldNumbered? = detectHoldContacts(
    landmarks = landmarks,
    holds = holds,
    config = config
).maxByOrNull { it.holdNo }?.hold

/**
 * 비디오 전체 Pose 시퀀스를 훑으면서 최고 도달 홀드를 계산합니다.
 *
 * 프레임마다 접촉 홀드를 찾고,
 * - 가장 큰 holdNo를 최고 도달 홀드로 유지
 * - 한 번이라도 접촉한 홀드 번호를 누적합니다.
 */
fun analyzeAttemptHoldReach(
    poses: List<Pose>,
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig()
): AttemptHoldReachResult {
    val totalHoldCount = holds.size
    if (poses.isEmpty() || holds.isEmpty()) {
        return AttemptHoldReachResult(
            highestReachedHold = null,
            highestReachedHoldNo = 0,
            highestReachedFrameTimeMs = null,
            totalHoldCount = totalHoldCount,
            contactedHoldNos = emptySet(),
            reachedRatio = 0f
        )
    }

    var highestReachedHold: HoldNumbered? = null
    var highestReachedFrameTimeMs: Long? = null
    val contactedHoldNos = linkedSetOf<Int>()

    poses.sortedBy { it.frameTimeMs }.forEach { pose ->
        val contacts = detectHoldContacts(
            landmarks = pose.landmarks,
            holds = holds,
            config = config
        )
        if (contacts.isEmpty()) return@forEach

        contacts.mapTo(contactedHoldNos) { it.holdNo }

        val frameHighest = contacts.maxByOrNull { it.holdNo }?.hold ?: return@forEach
        if (highestReachedHold == null || frameHighest.holdNo > highestReachedHold!!.holdNo) {
            highestReachedHold = frameHighest
            highestReachedFrameTimeMs = pose.frameTimeMs
        }
    }

    val highestReachedHoldNo = highestReachedHold?.holdNo ?: 0
    val reachedRatio = if (totalHoldCount > 0) {
        highestReachedHoldNo.toFloat() / totalHoldCount.toFloat()
    } else {
        0f
    }

    return AttemptHoldReachResult(
        highestReachedHold = highestReachedHold,
        highestReachedHoldNo = highestReachedHoldNo,
        highestReachedFrameTimeMs = highestReachedFrameTimeMs,
        totalHoldCount = totalHoldCount,
        contactedHoldNos = contactedHoldNos.toSet(),
        reachedRatio = reachedRatio
    )
}

/** 시도별 최고 도달 홀드를 평균 내서 최종 요약을 만듭니다. */
fun summarizeHoldReachResults(
    results: List<AttemptHoldReachResult>,
    totalHoldCount: Int = results.maxOfOrNull { it.totalHoldCount } ?: 0
): OverallHoldReachSummary {
    val averageHighestReachedHoldNo = if (results.isEmpty()) {
        0f
    } else {
        results.map { it.highestReachedHoldNo }.average().toFloat()
    }

    val averageReachedRatio = if (results.isEmpty()) {
        0f
    } else {
        results.map { it.reachedRatio }.average().toFloat()
    }

    return OverallHoldReachSummary(
        attempts = results,
        averageHighestReachedHoldNo = averageHighestReachedHoldNo,
        roundedAverageHighestReachedHoldNo = averageHighestReachedHoldNo.roundToInt(),
        totalHoldCount = totalHoldCount,
        averageReachedRatio = averageReachedRatio
    )
}

private fun extractHands(landmarks: List<PoseLandmark>): List<HandPose> {
    val indexedLandmarks = landmarks.associateBy { it.index }

    val leftHand = LEFT_HAND_LANDMARK_INDICES.mapNotNull { index ->
        indexedLandmarks[index]?.toIndexedPoint()
    }
    val rightHand = RIGHT_HAND_LANDMARK_INDICES.mapNotNull { index ->
        indexedLandmarks[index]?.toIndexedPoint()
    }

    return buildList {
        if (leftHand.isNotEmpty()) add(HandPose(hand = ContactHand.LEFT, points = leftHand))
        if (rightHand.isNotEmpty()) add(HandPose(hand = ContactHand.RIGHT, points = rightHand))
    }
}

private fun PoseLandmark.toIndexedPoint(): IndexedPoint = IndexedPoint(
    index = index,
    point = NormalizedPoint(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f)
    )
)

private fun detectHandContact(
    hand: HandPose,
    hold: HoldNumbered,
    config: HoldContactConfig
): HandContact? {
    val bbox = hold.hold.boundingBox
    val holdWidth = (bbox.right - bbox.left).coerceAtLeast(0f)
    val holdHeight = (bbox.bottom - bbox.top).coerceAtLeast(0f)
    val padding = computePadding(
        holdWidth = holdWidth,
        holdHeight = holdHeight,
        config = config
    )

    val touchingPoints = hand.points.filter { indexedPoint ->
        isPointInsideHoldArea(indexedPoint.point, hold.hold, padding, padding)
    }

    if (touchingPoints.isNotEmpty()) {
        return HandContact(
            hand = hand.hand,
            points = touchingPoints,
            minDistanceToHold = touchingPoints.minOf { indexedPoint ->
                distancePointToBoundingBox(
                    point = indexedPoint.point,
                    hold = hold.hold,
                    paddingX = padding,
                    paddingY = padding
                )
            }
        )
    }

    val centerDistanceThreshold = max(
        max(holdWidth, holdHeight) * config.centerDistanceRatio,
        config.minCenterDistance
    )
    val centerDistance = distanceToHoldCenter(hand.center, hold.hold)

    return if (centerDistance <= centerDistanceThreshold) {
        HandContact(
            hand = hand.hand,
            points = hand.points,
            minDistanceToHold = centerDistance
        )
    } else {
        null
    }
}

private data class HandContact(
    val hand: ContactHand,
    val points: List<IndexedPoint>,
    val minDistanceToHold: Float
)

private fun computePadding(
    holdWidth: Float,
    holdHeight: Float,
    config: HoldContactConfig
): Float {
    val baseSize = max(holdWidth, holdHeight)
    return (baseSize * config.bboxPaddingRatio)
        .coerceIn(config.minPadding, config.maxPadding)
}

private fun isPointInsideHoldArea(
    point: NormalizedPoint,
    hold: Hold,
    paddingX: Float,
    paddingY: Float
): Boolean {
    return isPointInsidePolygon(point, hold.polygon) ||
        isPointInsideExpandedBoundingBox(point, hold.boundingBox, paddingX, paddingY)
}

private fun isPointInsideExpandedBoundingBox(
    point: NormalizedPoint,
    boundingBox: Hold.BoundingBox,
    paddingX: Float,
    paddingY: Float
): Boolean {
    val left = (boundingBox.left - paddingX).coerceAtLeast(0f)
    val top = (boundingBox.top - paddingY).coerceAtLeast(0f)
    val right = (boundingBox.right + paddingX).coerceAtMost(1f)
    val bottom = (boundingBox.bottom + paddingY).coerceAtMost(1f)

    return point.x in left..right && point.y in top..bottom
}

private fun isPointInsidePolygon(
    point: NormalizedPoint,
    polygon: List<Hold.Point>
): Boolean {
    if (polygon.size < 3) return false

    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        val intersects = ((current.y > point.y) != (previous.y > point.y)) &&
            (point.x < (previous.x - current.x) * (point.y - current.y) /
            ((previous.y - current.y).takeIf { it != 0f } ?: 1e-6f) + current.x)

        if (intersects) {
            inside = !inside
        }
        previous = current
    }
    return inside
}

private fun distancePointToBoundingBox(
    point: NormalizedPoint,
    hold: Hold,
    paddingX: Float,
    paddingY: Float
): Float {
    val left = (hold.boundingBox.left - paddingX).coerceAtLeast(0f)
    val top = (hold.boundingBox.top - paddingY).coerceAtLeast(0f)
    val right = (hold.boundingBox.right + paddingX).coerceAtMost(1f)
    val bottom = (hold.boundingBox.bottom + paddingY).coerceAtMost(1f)

    val nearestX = point.x.coerceIn(left, right)
    val nearestY = point.y.coerceIn(top, bottom)

    val diffX = point.x - nearestX
    val diffY = point.y - nearestY
    return sqrt((diffX * diffX) + (diffY * diffY))
}

private fun distanceToHoldCenter(
    point: NormalizedPoint,
    hold: Hold
): Float {
    val centerX = (hold.boundingBox.left + hold.boundingBox.right) / 2f
    val centerY = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
    val diffX = point.x - centerX
    val diffY = point.y - centerY
    return sqrt((diffX * diffX) + (diffY * diffY))
}
