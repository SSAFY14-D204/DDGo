package com.ddgo.app.data.remote.gym

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * gym resolve 요청 DTO.
 *
 * 규칙:
 * - 사용자가 카카오 검색 결과에서 선택한 장소 정보를 서버에 전달할 때 사용합니다.
 * - mapProvider는 현재 단계에서 "KAKAO" 고정으로 사용합니다.
 */
@Serializable
data class ResolveGymRequestDto(
    @SerialName("mapProvider") val mapProvider: String,
    @SerialName("externalPlaceId") val externalPlaceId: String,
    @SerialName("placeName") val placeName: String,
    @SerialName("addressName") val addressName: String? = null,
    @SerialName("roadAddressName") val roadAddressName: String? = null,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double
)

/**
 * gym resolve 응답 DTO.
 *
 * 규칙:
 * - 이 DTO를 그대로 UI에 넘기지 않습니다.
 * - GymMapper를 통해 ResolvedGym domain 모델로 변환합니다.
 */
@Serializable
data class ResolveGymResponseDto(
    @SerialName("matched") val matched: Boolean,
    @SerialName("gymId") val gymId: Int,
    @SerialName("gradeSource") val gradeSource: String,
    @SerialName("matchStatus") val matchStatus: String,
    @SerialName("needsReview") val needsReview: Boolean,
    @SerialName("gym") val gym: GymSummaryDto,
    @SerialName("grades") val grades: List<GymGradeDto>
)

/**
 * gym 기본 정보 DTO.
 */
@Serializable
data class GymSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("displayName") val displayName: String,
    @SerialName("region") val region: String? = null,
    @SerialName("logoBucket") val logoBucket: String? = null,
    @SerialName("logoObjectKey") val logoObjectKey: String? = null,
    @SerialName("brandLogoBucket") val brandLogoBucket: String? = null,
    @SerialName("brandLogoObjectKey") val brandLogoObjectKey: String? = null
)

/**
 * gym grade 정보 DTO.
 */
@Serializable
data class GymGradeDto(
    @SerialName("gymGradeId") val gymGradeId: Int,
    @SerialName("colorName") val colorName: String,
    @SerialName("sortOrder") val sortOrder: Int,
    @SerialName("colorHex") val colorHex: String? = null,
    @SerialName("gradeLabel") val gradeLabel: String? = null
)
