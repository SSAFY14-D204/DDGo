package com.ddgo.app.data.remote.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeCreateRequestDto(
    val gymId: Long,
    val gymGradeId: Long,
    val startedAt: String
)

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

@Serializable
data class ChallengeListResponseDto(
    val id: Long,
    val gymId: Long? = null,
    val gymName: String,
    val problemColor: String,
    val gradeLabel: String? = null,
    val challengeStatus: String,
    val challengeResult: String? = null,
    val startedAt: String? = null,
    val endedAt: String? = null,
    val createdAt: String
)

@Serializable
data class PointItemDto(
    val x: Float,
    val y: Float
)

@Serializable
data class BoundingBoxDto(
    val x1: Float,
    val x2: Float,
    val y1: Float,
    val y2: Float
)

@Serializable
data class HoldItemDto(
    val holdNo: Int,
    val boundingBox: BoundingBoxDto,
    val polygon: List<PointItemDto>
)

@Serializable
data class HoldSaveRequestDto(
    val holds: List<HoldItemDto>
)

@Serializable
data class HoldSaveResponseDto(
    val challengeId: Long,
    val holdCount: Int,
    val holds: List<HoldItemDto>
)

@Serializable
data class ChallengeCloseRequestDto(
    val challengeResult: String? = null,
    val summary: ChallengeCloseSummaryDto? = null
)

@Serializable
data class ChallengeCloseSummaryDto(
    val averageCenterStabilityRatio: Double? = null,
    val mostCruxHoldNo: Int? = null,
    val maxCruxDurationMs: Int? = null,
    val finalComment: String? = null
)

@Serializable
data class ChallengeCloseResponseDto(
    val challengeId: Long,
    val challengeStatus: String,
    val challengeResult: String,
    val endedAt: String? = null,
    val summary: ChallengeCloseSummaryDto? = null
)
