package com.ddgo.app.domain.usecase

import android.util.Log
import com.ddgo.app.data.remote.pose.PoseSequenceDto
import com.ddgo.app.domain.model.Hold
import com.ddgo.app.domain.model.Pose
import com.ddgo.app.domain.model.PoseLandmark
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val HOLD_CONTACT_LOG_TAG = "HoldContactAnalysis"
private const val HOLD_CONTACT_LOG_PREFIX = "[DDGO_HOLD_CONTACT]"
private const val LEFT_ELBOW_LANDMARK_INDEX = 13
private const val RIGHT_ELBOW_LANDMARK_INDEX = 14
private const val LEFT_WRIST_LANDMARK_INDEX = 15
private const val RIGHT_WRIST_LANDMARK_INDEX = 16
private const val LEFT_INDEX_LANDMARK_INDEX = 19
private const val RIGHT_INDEX_LANDMARK_INDEX = 20
private const val LEFT_PINKY_LANDMARK_INDEX = 17
private const val RIGHT_PINKY_LANDMARK_INDEX = 18
private const val LEFT_THUMB_LANDMARK_INDEX = 21
private const val RIGHT_THUMB_LANDMARK_INDEX = 22
private const val LEFT_HIP_LANDMARK_INDEX = 23
private const val RIGHT_HIP_LANDMARK_INDEX = 24
private const val LEFT_ANKLE_LANDMARK_INDEX = 27
private const val RIGHT_ANKLE_LANDMARK_INDEX = 28
private val LEFT_HAND_LANDMARK_INDICES = listOf(15, 17, 19, 21)
private val RIGHT_HAND_LANDMARK_INDICES = listOf(16, 18, 20, 22)
private val CONTACT_HAND_LANDMARK_INDICES =
    (LEFT_HAND_LANDMARK_INDICES + RIGHT_HAND_LANDMARK_INDICES).distinct().sorted()

/**
 * 홀드 접촉 판정에 사용하는 임계값 모음입니다.
 *
 * 모든 값은 정규화 좌표(0~1) 기준입니다.
 */
data class HoldContactConfig(
    val bboxPaddingRatio: Float = 0.10f,
    val minPadding: Float = 0.015f,
    val maxPadding: Float = 0.035f,
    val centerDistanceRatio: Float = 0.40f,
    val minCenterDistance: Float = 0.035f,
    val holdRadiusRatio: Float = 0.35f,
    val enterRadiusScale: Float = 0.85f,
    val palmFingertipDirectionWeight: Float = 0.55f,
    val palmForearmDirectionWeight: Float = 0.45f,
    val palmForearmOffsetRatio: Float = 0.28f,
    val palmFingertipOffsetRatio: Float = 0.65f,
    val palmMinOffset: Float = 0.010f,
    val palmMaxOffset: Float = 0.055f,
    val fallHipDropPerFrameThreshold: Float = 0.10f,
    val fallHipDropFromBestThreshold: Float = 0.18f,
    val fallHipFloorThreshold: Float = 0.72f,
    val fallAnkleFloorThreshold: Float = 0.90f
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

data class HoldContactZone(
    val hold: HoldNumbered,
    val expandedBoundingBox: Hold.BoundingBox
)

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
    val points: List<IndexedPoint>,
    val wrist: IndexedPoint? = null,
    val elbow: IndexedPoint? = null,
    val indexTip: IndexedPoint? = null,
    val pinkyTip: IndexedPoint? = null,
    val thumbTip: IndexedPoint? = null
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
 * 2. 또는 손목/팔꿈치/손가락 방향으로 추정한 손바닥 접점이 홀드 영역에 들어오면 접촉
 * 3. 거리만 가까운 경우는 접촉으로 보지 않습니다.
 * 4. 발 랜드마크는 제외합니다. "잡았는지" 판정을 손 기준으로만 계산합니다.
 */
fun detectHoldContacts(
    landmarks: List<PoseLandmark>,
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig(),
    enableLogging: Boolean = true
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
        .also { contacts ->
            if (enableLogging) {
                logDetectedContacts(contacts)
            }
        }
}

fun buildHoldContactZones(
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig()
): List<HoldContactZone> = holds.map { hold ->
    val bbox = hold.hold.boundingBox
    val holdWidth = (bbox.right - bbox.left).coerceAtLeast(0f)
    val holdHeight = (bbox.bottom - bbox.top).coerceAtLeast(0f)
    val padding = computePadding(
        holdWidth = holdWidth,
        holdHeight = holdHeight,
        config = config
    )

    HoldContactZone(
        hold = hold,
        expandedBoundingBox = Hold.BoundingBox(
            left = (bbox.left - padding).coerceAtLeast(0f),
            top = (bbox.top - padding).coerceAtLeast(0f),
            right = (bbox.right + padding).coerceAtMost(1f),
            bottom = (bbox.bottom + padding).coerceAtMost(1f)
        )
    )
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
    poseSequenceDto: PoseSequenceDto,
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig()
): AttemptHoldReachResult {
    logPoseDtoReceipt(poseSequenceDto)
    return analyzeAttemptHoldReach(
        poses = poses,
        holds = holds,
        config = config
    )
}

fun analyzeAttemptHoldReach(
    poses: List<Pose>,
    holds: List<HoldNumbered>,
    config: HoldContactConfig = HoldContactConfig()
): AttemptHoldReachResult {
    logAttemptHoldReachStart(
        poseCount = poses.size,
        holdCount = holds.size
    )
    logAttemptHoldReachInputs(
        poses = poses,
        holds = holds
    )

    val totalHoldCount = holds.size
    if (poses.isEmpty() || holds.isEmpty()) {
        return AttemptHoldReachResult(
            highestReachedHold = null,
            highestReachedHoldNo = 0,
            highestReachedFrameTimeMs = null,
            totalHoldCount = totalHoldCount,
            contactedHoldNos = emptySet(),
            reachedRatio = 0f
        ).also { result ->
            logAttemptHoldReachSummary(result)
        }
    }

    var highestReachedHold: HoldNumbered? = null
    var highestReachedFrameTimeMs: Long? = null
    val contactedHoldNos = linkedSetOf<Int>()
    var attemptStarted = false
    var minHipYSinceAttemptStart: Float? = null
    var previousPose: Pose? = null

    for (pose in poses.sortedBy { it.frameTimeMs }) {
        if (
            attemptStarted &&
            previousPose != null &&
            shouldStopAnalysisAfterFall(
                previousPose = previousPose!!,
                currentPose = pose,
                minHipYSinceAttemptStart = minHipYSinceAttemptStart,
                config = config
            )
        ) {
            logAttemptCutoff(previousPose = previousPose!!, currentPose = pose)
            break
        }

        val contacts = detectHoldContacts(
            landmarks = pose.landmarks,
            holds = holds,
            config = config
        )
        previousPose = pose
        averageLandmarkY(
            pose = pose,
            landmarkIndices = setOf(LEFT_HIP_LANDMARK_INDEX, RIGHT_HIP_LANDMARK_INDEX)
        )?.let { hipY ->
            if (attemptStarted || contacts.isNotEmpty()) {
                minHipYSinceAttemptStart = minOf(minHipYSinceAttemptStart ?: hipY, hipY)
            }
        }

        if (contacts.isEmpty()) continue

        attemptStarted = true

        contacts.mapTo(contactedHoldNos) { it.holdNo }

        val frameHighest = contacts.maxByOrNull { it.holdNo }?.hold ?: continue
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
    ).also { result ->
        logAttemptHoldReachSummary(result)
    }
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
        if (leftHand.isNotEmpty()) {
            add(
                HandPose(
                    hand = ContactHand.LEFT,
                    points = leftHand,
                    wrist = indexedLandmarks[LEFT_WRIST_LANDMARK_INDEX]?.toIndexedPoint(),
                    elbow = indexedLandmarks[LEFT_ELBOW_LANDMARK_INDEX]?.toIndexedPoint(),
                    indexTip = indexedLandmarks[LEFT_INDEX_LANDMARK_INDEX]?.toIndexedPoint(),
                    pinkyTip = indexedLandmarks[LEFT_PINKY_LANDMARK_INDEX]?.toIndexedPoint(),
                    thumbTip = indexedLandmarks[LEFT_THUMB_LANDMARK_INDEX]?.toIndexedPoint()
                )
            )
        }
        if (rightHand.isNotEmpty()) {
            add(
                HandPose(
                    hand = ContactHand.RIGHT,
                    points = rightHand,
                    wrist = indexedLandmarks[RIGHT_WRIST_LANDMARK_INDEX]?.toIndexedPoint(),
                    elbow = indexedLandmarks[RIGHT_ELBOW_LANDMARK_INDEX]?.toIndexedPoint(),
                    indexTip = indexedLandmarks[RIGHT_INDEX_LANDMARK_INDEX]?.toIndexedPoint(),
                    pinkyTip = indexedLandmarks[RIGHT_PINKY_LANDMARK_INDEX]?.toIndexedPoint(),
                    thumbTip = indexedLandmarks[RIGHT_THUMB_LANDMARK_INDEX]?.toIndexedPoint()
                )
            )
        }
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
    val palmContactPoint = inferPalmContactPoint(hand, config)

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

    if (palmContactPoint != null && isPointInsideHoldArea(palmContactPoint, hold.hold, padding, padding)) {
        return HandContact(
            hand = hand.hand,
            points = hand.points,
            minDistanceToHold = distancePointToBoundingBox(
                point = palmContactPoint,
                hold = hold.hold,
                paddingX = padding,
                paddingY = padding
            )
        )
    }

    return null
}

private data class HandContact(
    val hand: ContactHand,
    val points: List<IndexedPoint>,
    val minDistanceToHold: Float
)

private fun logDetectedContacts(contacts: List<HoldContact>) {
    if (contacts.isEmpty()) return

    contacts.forEach { contact ->
        val contactedParts = contact.handSides
            .sortedBy { it.ordinal }
            .joinToString(", ") { hand -> hand.displayName }

        runCatching {
            Log.i(
                HOLD_CONTACT_LOG_TAG,
                "$HOLD_CONTACT_LOG_PREFIX 현재 접촉한 홀드 번호: ${contact.holdNo}, 접촉한 부위: $contactedParts"
            )
        }
    }
}

private fun logAttemptHoldReachStart(
    poseCount: Int,
    holdCount: Int
) {
    runCatching {
        Log.i(
            HOLD_CONTACT_LOG_TAG,
            "$HOLD_CONTACT_LOG_PREFIX 분석 시작: poseCount=$poseCount, holdCount=$holdCount"
        )
    }
}

private fun logAttemptHoldReachInputs(
    poses: List<Pose>,
    holds: List<HoldNumbered>
) {
    runCatching {
        val holdSummary = if (holds.isEmpty()) {
            "[]"
        } else {
            holds.joinToString(" | ") { hold ->
                "#${hold.holdNo}(bbox=${hold.hold.boundingBox.toLogString()}, " +
                    "color=${hold.hold.colorLabel}, conf=${hold.hold.confidence.toLogValue()})"
            }
        }

        Log.i(
            HOLD_CONTACT_LOG_TAG,
            "$HOLD_CONTACT_LOG_PREFIX 입력 holds(${holds.size}): $holdSummary"
        )

        if (poses.isEmpty()) {
            Log.i(
                HOLD_CONTACT_LOG_TAG,
                "$HOLD_CONTACT_LOG_PREFIX 입력 poses(0): []"
            )
            return@runCatching
        }

        poses.take(3).forEachIndexed { index, pose ->
            val handLandmarks = pose.landmarks
                .filter { landmark -> landmark.index in CONTACT_HAND_LANDMARK_INDICES }
                .sortedBy { landmark -> landmark.index }
                .joinToString(", ") { landmark -> landmark.toLogString() }
                .ifBlank { "none" }

            Log.i(
                HOLD_CONTACT_LOG_TAG,
                "$HOLD_CONTACT_LOG_PREFIX poseSample[$index]: " +
                    "frameTimeMs=${pose.frameTimeMs}, " +
                    "landmarkCount=${pose.landmarks.size}, " +
                    "handLandmarks=$handLandmarks"
            )
        }

        if (poses.size > 3) {
            Log.i(
                HOLD_CONTACT_LOG_TAG,
                "$HOLD_CONTACT_LOG_PREFIX poseSample: 나머지 ${poses.size - 3}개 프레임은 생략"
            )
        }
    }
}

private fun logPoseDtoReceipt(poseSequenceDto: PoseSequenceDto) {
    runCatching {
        val firstPose = poseSequenceDto.poses.firstOrNull()
        Log.i(
            HOLD_CONTACT_LOG_TAG,
            "$HOLD_CONTACT_LOG_PREFIX PoseDto 전달 확인: " +
                "frameCount=${poseSequenceDto.poses.size}, " +
                "firstLandmarksPxKeys=${firstPose?.landmarksPx?.keys?.joinToString() ?: "[]"}, " +
                "firstWorldKeys=${firstPose?.worldLandmarksSample?.keys?.joinToString() ?: "[]"}"
        )
    }
}

private fun logAttemptHoldReachSummary(result: AttemptHoldReachResult) {
    runCatching {
        Log.i(
            HOLD_CONTACT_LOG_TAG,
            "$HOLD_CONTACT_LOG_PREFIX 분석 요약: highestHoldNo=${result.highestReachedHoldNo}, " +
                "contacted=${result.contactedHoldNos}"
        )
    }
}

private fun logAttemptCutoff(
    previousPose: Pose,
    currentPose: Pose
) {
    runCatching {
        val previousHipY = averageLandmarkY(
            pose = previousPose,
            landmarkIndices = setOf(LEFT_HIP_LANDMARK_INDEX, RIGHT_HIP_LANDMARK_INDEX)
        )
        val currentHipY = averageLandmarkY(
            pose = currentPose,
            landmarkIndices = setOf(LEFT_HIP_LANDMARK_INDEX, RIGHT_HIP_LANDMARK_INDEX)
        )
        val currentAnkleY = averageLandmarkY(
            pose = currentPose,
            landmarkIndices = setOf(LEFT_ANKLE_LANDMARK_INDEX, RIGHT_ANKLE_LANDMARK_INDEX)
        )

        Log.i(
            HOLD_CONTACT_LOG_TAG,
            "$HOLD_CONTACT_LOG_PREFIX 낙하 이후 프레임 제외: " +
                "prevTimeMs=${previousPose.frameTimeMs}, currentTimeMs=${currentPose.frameTimeMs}, " +
                "prevHipY=${previousHipY?.toLogValue() ?: "none"}, " +
                "currentHipY=${currentHipY?.toLogValue() ?: "none"}, " +
                "currentAnkleY=${currentAnkleY?.toLogValue() ?: "none"}"
        )
    }
}

private val ContactHand.displayName: String
    get() = when (this) {
        ContactHand.LEFT -> "왼손"
        ContactHand.RIGHT -> "오른손"
    }

private fun Hold.BoundingBox.toLogString(): String = buildString {
    append('[')
    append(left.toLogValue())
    append(", ")
    append(top.toLogValue())
    append(", ")
    append(right.toLogValue())
    append(", ")
    append(bottom.toLogValue())
    append(']')
}

private fun PoseLandmark.toLogString(): String =
    "${index}=(${x.toLogValue()}, ${y.toLogValue()}, ${z.toLogValue()})"

private fun Float.toLogValue(): String = String.format(Locale.US, "%.3f", this)

private fun computePadding(
    holdWidth: Float,
    holdHeight: Float,
    config: HoldContactConfig
): Float {
    val baseSize = max(holdWidth, holdHeight)
    return (baseSize * config.bboxPaddingRatio)
        .coerceIn(config.minPadding, config.maxPadding)
}

private fun computeHoldRadius(
    holdWidth: Float,
    holdHeight: Float,
    config: HoldContactConfig
): Float {
    return minOf(holdWidth, holdHeight) * config.holdRadiusRatio
}

private fun inferPalmContactPoint(
    hand: HandPose,
    config: HoldContactConfig
): NormalizedPoint? {
    val wrist = hand.wrist?.point ?: return null
    val elbow = hand.elbow?.point ?: return null
    val indexTip = hand.indexTip?.point ?: return null
    val pinkyTip = hand.pinkyTip?.point ?: return null
    val thumbTip = hand.thumbTip?.point ?: return null

    val fingertipCentroid = NormalizedPoint(
        x = (indexTip.x + pinkyTip.x + thumbTip.x) / 3f,
        y = (indexTip.y + pinkyTip.y + thumbTip.y) / 3f
    )
    val fingertipDirection = normalizeVector(
        x = fingertipCentroid.x - wrist.x,
        y = fingertipCentroid.y - wrist.y
    )
    val forearmDirection = normalizeVector(
        x = wrist.x - elbow.x,
        y = wrist.y - elbow.y
    )

    var blendedDirection = normalizeVector(
        x = fingertipDirection.x * config.palmFingertipDirectionWeight +
            forearmDirection.x * config.palmForearmDirectionWeight,
        y = fingertipDirection.y * config.palmFingertipDirectionWeight +
            forearmDirection.y * config.palmForearmDirectionWeight
    )
    if (blendedDirection.isZero) {
        blendedDirection = when {
            !forearmDirection.isZero -> forearmDirection
            !fingertipDirection.isZero -> fingertipDirection
            else -> return wrist
        }
    }

    val forearmLength = distanceBetween(wrist, elbow)
    val fingertipSpan = distanceBetween(fingertipCentroid, wrist)
    val offset = max(
        config.palmForearmOffsetRatio * forearmLength,
        config.palmFingertipOffsetRatio * fingertipSpan
    ).coerceIn(config.palmMinOffset, config.palmMaxOffset)

    return NormalizedPoint(
        x = (wrist.x + blendedDirection.x * offset).coerceIn(0f, 1f),
        y = (wrist.y + blendedDirection.y * offset).coerceIn(0f, 1f)
    )
}

private data class NormalizedVector(
    val x: Float,
    val y: Float
) {
    val isZero: Boolean
        get() = x == 0f && y == 0f
}

private fun normalizeVector(x: Float, y: Float): NormalizedVector {
    val norm = sqrt((x * x) + (y * y))
    if (norm < 1e-6f) {
        return NormalizedVector(0f, 0f)
    }
    return NormalizedVector(
        x = x / norm,
        y = y / norm
    )
}

private fun distanceBetween(a: NormalizedPoint, b: NormalizedPoint): Float {
    val diffX = a.x - b.x
    val diffY = a.y - b.y
    return sqrt((diffX * diffX) + (diffY * diffY))
}

private fun averageLandmarkY(
    pose: Pose,
    landmarkIndices: Set<Int>
): Float? {
    val points = pose.landmarks.filter { landmark -> landmark.index in landmarkIndices }
    if (points.isEmpty()) return null
    return points.map { landmark -> landmark.y }.average().toFloat()
}

private fun shouldStopAnalysisAfterFall(
    previousPose: Pose,
    currentPose: Pose,
    minHipYSinceAttemptStart: Float?,
    config: HoldContactConfig
): Boolean {
    val previousHipY = averageLandmarkY(
        pose = previousPose,
        landmarkIndices = setOf(LEFT_HIP_LANDMARK_INDEX, RIGHT_HIP_LANDMARK_INDEX)
    ) ?: return false
    val currentHipY = averageLandmarkY(
        pose = currentPose,
        landmarkIndices = setOf(LEFT_HIP_LANDMARK_INDEX, RIGHT_HIP_LANDMARK_INDEX)
    ) ?: return false
    val currentAnkleY = averageLandmarkY(
        pose = currentPose,
        landmarkIndices = setOf(LEFT_ANKLE_LANDMARK_INDEX, RIGHT_ANKLE_LANDMARK_INDEX)
    ) ?: return false

    val hipDropPerFrame = currentHipY - previousHipY
    val hipDropFromBest = currentHipY - (minHipYSinceAttemptStart ?: currentHipY)
    val reachedFloor = currentHipY >= config.fallHipFloorThreshold &&
        currentAnkleY >= config.fallAnkleFloorThreshold

    return hipDropPerFrame >= config.fallHipDropPerFrameThreshold ||
        (hipDropFromBest >= config.fallHipDropFromBestThreshold && reachedFloor)
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
