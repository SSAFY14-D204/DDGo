package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.gym.GymGradeDto
import com.ddgo.app.data.remote.gym.GymSummaryDto
import com.ddgo.app.data.remote.gym.ResolveGymResponseDto
import com.ddgo.app.data.remote.kakao.KakaoPlaceDocumentDto
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.GymSummary
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym

/**
 * Gym 관련 DTO -> Domain 변환 매퍼.
 *
 * 규칙:
 * - 외부 API / 서버 DTO를 그대로 feature나 domain에 전달하지 않습니다.
 * - 반드시 Mapper를 통해 순수 Kotlin domain 모델로 변환합니다.
 */
object GymMapper {

    /**
     * Kakao 장소 DTO를 NearbyPlace domain 모델로 변환합니다.
     *
     * 주의:
     * - latitude, longitude는 문자열이므로 안전하게 Double로 변환합니다.
     * - 변환 실패 시 null을 반환하여 상위에서 걸러낼 수 있게 합니다.
     */
    fun KakaoPlaceDocumentDto.toDomainOrNull(): NearbyPlace? {
        val parsedLatitude = latitude.toDoubleOrNull() ?: return null
        val parsedLongitude = longitude.toDoubleOrNull() ?: return null

        return NearbyPlace(
            externalPlaceId = id,
            placeName = placeName,
            addressName = addressName,
            roadAddressName = roadAddressName,
            latitude = parsedLatitude,
            longitude = parsedLongitude,
            distanceMeters = distance?.toIntOrNull()
        )
    }

    /**
     * gym resolve 응답 DTO를 ResolvedGym domain 모델로 변환합니다.
     */
    fun ResolveGymResponseDto.toDomain(): ResolvedGym = ResolvedGym(
        matched = matched,
        gymId = gymId,
        gradeSource = gradeSource,
        matchStatus = matchStatus,
        needsReview = needsReview,
        gym = gym.toDomain(),
        grades = grades.map { it.toDomain() }
    )

    /**
     * gym summary DTO -> domain 변환.
     */
    private fun GymSummaryDto.toDomain(): GymSummary = GymSummary(
        id = id,
        displayName = displayName,
        region = region,
        logoBucket = logoBucket,
        logoObjectKey = logoObjectKey,
        brandLogoBucket = brandLogoBucket,
        brandLogoObjectKey = brandLogoObjectKey
    )

    /**
     * gym grade DTO -> domain 변환.
     */
    private fun GymGradeDto.toDomain(): GymGrade = GymGrade(
        gymGradeId = gymGradeId,
        colorName = colorName,
        sortOrder = sortOrder,
        colorHex = colorHex,
        gradeLabel = gradeLabel
    )
}
