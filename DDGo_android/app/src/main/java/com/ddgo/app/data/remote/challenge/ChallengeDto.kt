package com.ddgo.app.data.remote.challenge

import kotlinx.serialization.SerialName
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
data class ChallengeListItemDto(
    @SerialName("id") val id: Long,
    @SerialName("gymId") val gymId: Long? = null,
    @SerialName("gymGradeId") val gymGradeId: Long? = null,
    @SerialName("gymName") val gymName: String? = null,
    @SerialName("problemColor") val problemColor: String? = null,
    @SerialName("gradeLabel") val gradeLabel: String? = null,
    @SerialName("colorHex") val colorHex: String? = null,
    @SerialName("challengeStatus") val challengeStatus: String,
    @SerialName("challengeResult") val challengeResult: String? = null,
    @SerialName("startedAt") val startedAt: String? = null,
    @SerialName("endedAt") val endedAt: String? = null,
    @SerialName("createdAt") val createdAt: String,
    @SerialName("gymLogoBucket") val gymLogoBucket: String? = null,
    @SerialName("gymLogoObjectKey") val gymLogoObjectKey: String? = null,
    @SerialName("brandLogoBucket") val brandLogoBucket: String? = null,
    @SerialName("brandLogoObjectKey") val brandLogoObjectKey: String? = null
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
