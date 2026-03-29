package com.ddgo.app.domain.model

/**
 * 주변 장소 domain 모델.
 *
 * 규칙:
 * - 외부 API DTO 대신 feature/domain 계층에서 이 모델을 사용합니다.
 * - UI는 카카오 DTO를 몰라도 되도록 추상화된 형태를 유지합니다.
 */
data class NearbyPlace(
    val externalPlaceId: String,
    val placeName: String,
    val addressName: String?,
    val roadAddressName: String?,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Int?
)
