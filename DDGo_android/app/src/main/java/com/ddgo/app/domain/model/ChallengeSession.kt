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
    val startedAt: String,
    val createdAt: String
)

/** 챌린지 홀드 저장에 사용하는 좌표 모델입니다. */
data class ChallengeHoldCoordinate(
    val holdNo: Int,
    val x1: Int,
    val x2: Int,
    val y1: Int,
    val y2: Int
)

/** 챌린지 홀드 저장 후 반환되는 결과 모델입니다. */
data class SavedChallengeHolds(
    val challengeId: Long,
    val holdCount: Int,
    val holds: List<ChallengeHoldCoordinate>
)
