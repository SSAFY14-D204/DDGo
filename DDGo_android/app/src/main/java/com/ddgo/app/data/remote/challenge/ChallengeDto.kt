package com.ddgo.app.data.remote.challenge

import kotlinx.serialization.Serializable

/**
 * 챌린지 생성 요청 DTO입니다.
 *
 * 규칙:
 * - DTO는 data 계층에만 둡니다.
 * - feature/domain 계층에서는 매핑된 domain 모델만 사용합니다.
 */
@Serializable
data class ChallengeCreateRequestDto(
    val gymId: Long,
    val gymGradeId: Long,
    val startedAt: String
)

/** 챌린지 생성 응답 DTO입니다. */
@Serializable
data class ChallengeCreateResponseDto(
    val id: Long,
    val gymName: String,
    val problemColor: String,
    val gradeLabel: String? = null,
    val challengeStatus: String,
    val startedAt: String,
    val createdAt: String
)

/** 폴리곤 좌표 점 DTO입니다. (정규화 0~1) */
@Serializable
data class PointItemDto(
    val x: Float,
    val y: Float
)

/** 바운딩 박스 DTO입니다. (정규화 0~1) */
@Serializable
data class BoundingBoxDto(
    val x1: Float,
    val x2: Float,
    val y1: Float,
    val y2: Float
)

/** 홀드 좌표 1건에 대한 요청/응답 DTO입니다. (바운딩 박스 + 세그멘테이션 폴리곤) */
@Serializable
data class HoldItemDto(
    val holdNo: Int,
    val boundingBox: BoundingBoxDto,
    val polygon: List<PointItemDto>
)

/** 홀드 저장 요청 DTO입니다. */
@Serializable
data class HoldSaveRequestDto(
    val holds: List<HoldItemDto>
)

/** 홀드 저장 응답 DTO입니다. */
@Serializable
data class HoldSaveResponseDto(
    val challengeId: Long,
    val holdCount: Int,
    val holds: List<HoldItemDto>
)
