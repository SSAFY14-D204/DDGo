package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.Hold
import kotlin.math.sqrt

private const val AXIS_LENGTH_EPSILON = 1e-6f

/** 홀드의 bounding box 중심점입니다. */
data class HoldCenter(
    val x: Float,
    val y: Float
)

/** 시작 홀드에서 끝 홀드로 향하는 진행축 벡터입니다. */
data class HoldAxisVector(
    val dx: Float,
    val dy: Float
) {
    val lengthSquared: Float = (dx * dx) + (dy * dy)
}

/** 번호 부여 이후 홀드가 어떤 역할인지 구분합니다. */
enum class HoldRole {
    START,
    NORMAL,
    END
}

/**
 * 번호가 매겨진 홀드와 진행축 메타데이터를 함께 보관합니다.
 *
 * progress:
 * - 시작 홀드 기준 투영 위치입니다.
 * - 0f에 가까우면 시작점, 1f에 가까우면 끝점입니다.
 * - 0보다 작거나 1보다 큰 값도 나올 수 있습니다.
 *
 * axisDistance:
 * - 홀드 중심이 진행축에서 얼마나 벗어났는지 나타냅니다.
 * - 값이 작을수록 start → end 축에 더 가깝습니다.
 */
data class HoldNumbered(
    val hold: Hold,
    val progress: Float,
    val axisDistance: Float,
    val role: HoldRole
) {
    val holdNo: Int
        get() = hold.holdNo

    val isStart: Boolean
        get() = role == HoldRole.START

    val isEnd: Boolean
        get() = role == HoldRole.END

    val normalizedProgress: Float
        get() = progress.coerceIn(0f, 1f)
}

private data class HoldProjection(
    val originalIndex: Int,
    val hold: Hold,
    val center: HoldCenter,
    val progress: Float,
    val axisDistance: Float,
    val role: HoldRole
)

/**
 * Hold의 중심점을 계산합니다.
 *
 * centerX = (left + right) / 2
 * centerY = (top + bottom) / 2
 */
fun calculateHoldCenter(hold: Hold): HoldCenter = HoldCenter(
    x = (hold.boundingBox.left + hold.boundingBox.right) / 2f,
    y = (hold.boundingBox.top + hold.boundingBox.bottom) / 2f
)

/** 시작점과 끝점으로 진행축 벡터를 생성합니다. */
fun createHoldAxisVector(
    startCenter: HoldCenter,
    endCenter: HoldCenter
): HoldAxisVector {
    val axisVector = HoldAxisVector(
        dx = endCenter.x - startCenter.x,
        dy = endCenter.y - startCenter.y
    )
    validateAxisVector(axisVector)
    return axisVector
}

/** 시작 홀드와 끝 홀드로 진행축 벡터를 생성합니다. */
fun createHoldAxisVector(
    startHold: Hold,
    endHold: Hold
): HoldAxisVector = createHoldAxisVector(
    startCenter = calculateHoldCenter(startHold),
    endCenter = calculateHoldCenter(endHold)
)

/**
 * 홀드 중심점을 진행축 위로 투영한 progress 값을 계산합니다.
 *
 * progress가 커질수록 끝 홀드 쪽에 더 가깝습니다.
 */
fun calculateProgress(
    center: HoldCenter,
    startCenter: HoldCenter,
    axisVector: HoldAxisVector
): Float {
    validateAxisVector(axisVector)

    val vx = center.x - startCenter.x
    val vy = center.y - startCenter.y

    return ((vx * axisVector.dx) + (vy * axisVector.dy)) / axisVector.lengthSquared
}

/**
 * 홀드 중심점이 진행축에서 얼마나 벗어났는지 계산합니다.
 */
fun calculateAxisDistance(
    center: HoldCenter,
    startCenter: HoldCenter,
    axisVector: HoldAxisVector,
    progress: Float = calculateProgress(center, startCenter, axisVector)
): Float {
    validateAxisVector(axisVector)

    val projX = startCenter.x + (progress * axisVector.dx)
    val projY = startCenter.y + (progress * axisVector.dy)

    val diffX = center.x - projX
    val diffY = center.y - projY

    return sqrt((diffX * diffX) + (diffY * diffY))
}

/**
 * 시작 홀드와 끝 홀드를 기준으로 모든 홀드에 번호를 다시 부여합니다.
 *
 * 번호 부여 규칙:
 * 1. 시작 홀드는 항상 1번
 * 2. 끝 홀드는 항상 마지막 번호
 * 3. 중간 홀드는 start → end 진행축 기준 progress 오름차순으로 정렬
 * 4. progress가 같으면 axisDistance가 작은 홀드를 먼저 둬서 축에 가까운 홀드를 우선
 *
 * 사용 예시:
 * val numbered = assignHoldNumbers(holds, startHold, endHold)
 * val holdsForDb = numbered.map { it.hold }
 * val highestReached = numbered.maxByOrNull { it.holdNo }
 */
fun assignHoldNumbers(
    holds: List<Hold>,
    startHold: Hold,
    endHold: Hold
): List<HoldNumbered> {
    require(holds.size >= 2) { "홀드 번호를 매기려면 최소 2개의 홀드가 필요합니다." }

    // 입력으로 받은 시작/끝 홀드 자체가 거의 같은 위치면 즉시 예외 처리합니다.
    createHoldAxisVector(startHold, endHold)

    val startIndex = holds.findHoldIndex(startHold)
    require(startIndex >= 0) { "시작 홀드가 목록에 없습니다." }

    val endIndex = holds.findHoldIndex(endHold)
    require(endIndex >= 0) { "끝 홀드가 목록에 없습니다." }
    require(startIndex != endIndex) { "시작 홀드와 끝 홀드는 서로 달라야 합니다." }

    val start = holds[startIndex]
    val end = holds[endIndex]
    val startCenter = calculateHoldCenter(start)
    val endCenter = calculateHoldCenter(end)
    val axisVector = createHoldAxisVector(startCenter, endCenter)

    val projections = holds.mapIndexed { index, hold ->
        val center = calculateHoldCenter(hold)
        val progress = calculateProgress(center, startCenter, axisVector)
        val axisDistance = calculateAxisDistance(center, startCenter, axisVector, progress)

        HoldProjection(
            originalIndex = index,
            hold = hold,
            center = center,
            progress = progress,
            axisDistance = axisDistance,
            role = when (index) {
                startIndex -> HoldRole.START
                endIndex -> HoldRole.END
                else -> HoldRole.NORMAL
            }
        )
    }

    val startProjection = projections[startIndex]
    val endProjection = projections[endIndex]

    val middleProjections = projections
        .filter { it.role == HoldRole.NORMAL }
        .sortedWith(
            compareBy<HoldProjection> { it.progress }
                .thenBy { it.axisDistance }
                .thenBy { it.center.y }
                .thenBy { it.center.x }
                .thenBy { it.originalIndex }
        )

    return buildList(holds.size) {
        add(startProjection.toNumbered(holdNo = 1))

        middleProjections.forEachIndexed { index, projection ->
            add(projection.toNumbered(holdNo = index + 2))
        }

        add(endProjection.toNumbered(holdNo = middleProjections.size + 2))
    }
}

/** 번호가 다시 부여된 Hold만 필요할 때 사용합니다. */
fun List<HoldNumbered>.toHolds(): List<Hold> = map { it.hold }

private fun HoldProjection.toNumbered(holdNo: Int): HoldNumbered = HoldNumbered(
    hold = hold.copy(holdNo = holdNo),
    progress = progress,
    axisDistance = axisDistance,
    role = role
)

private fun validateAxisVector(axisVector: HoldAxisVector) {
    require(axisVector.lengthSquared > AXIS_LENGTH_EPSILON) {
        "시작 홀드와 끝 홀드가 거의 같은 위치입니다."
    }
}

private fun List<Hold>.findHoldIndex(target: Hold): Int {
    val exactMatch = indexOfFirst { it == target }
    if (exactMatch >= 0) return exactMatch

    val sameBoundingBox = withIndex()
        .filter { (_, hold) -> hold.boundingBox == target.boundingBox }

    if (sameBoundingBox.isEmpty()) return -1
    if (sameBoundingBox.size == 1) return sameBoundingBox.first().index

    return sameBoundingBox.firstOrNull { indexed ->
        indexed.value.polygon == target.polygon
    }?.index ?: sameBoundingBox.first().index
}
