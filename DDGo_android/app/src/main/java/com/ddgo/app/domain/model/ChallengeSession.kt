package com.ddgo.app.domain.model

/**
 * 생성된 챌린지 세션 domain 모델입니다.
 *
 * 역할:
 * - 선택한 암장과 난이도로 생성된 백엔드 챌린지 정보를 보관합니다.
 */
data class ChallengeSession(
    val challengeId: Long,
    val gymId: Long,
    val gymGradeId: Long,
    val gymName: String,
    val problemColor: String,
    val gradeLabel: String?,
    val challengeStatus: String,
    val challengeResult: String? = null,
    val doneAttemptCount: Int = 0,
    val startedAt: String,
    val createdAt: String
)

/** 챌린지 홀드 저장에 사용하는 좌표 모델입니다. (바운딩 박스 + 세그멘테이션 폴리곤) */
data class ChallengeHoldCoordinate(
    val holdNo: Int,
    val boundingBox: HoldBoundingBox,
    val polygon: List<HoldPoint>
)

/** 바운딩 박스 좌표 (정규화 0~1) */
data class HoldBoundingBox(
    val x1: Float,
    val x2: Float,
    val y1: Float,
    val y2: Float
)

/** 폴리곤 좌표 점 (정규화 0~1) */
data class HoldPoint(
    val x: Float,
    val y: Float
)

/** 챌린지 홀드 저장 후 반환되는 결과 모델입니다. */
data class SavedChallengeHolds(
    val challengeId: Long,
    val holdCount: Int,
    val holds: List<ChallengeHoldCoordinate>
)
