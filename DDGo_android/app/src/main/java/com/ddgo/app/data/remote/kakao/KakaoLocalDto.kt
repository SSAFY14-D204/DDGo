package com.ddgo.app.data.remote.kakao

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kakao 키워드 검색 응답 DTO.
 *
 * 규칙:
 * - 서버/외부 API 응답을 그대로 UI에 넘기지 않습니다.
 * - Mapper를 통해 domain/model의 NearbyPlace로 변환해서 사용합니다.
 */
@Serializable
data class KakaoKeywordSearchResponseDto(
    @SerialName("meta") val meta: KakaoSearchMetaDto,
    @SerialName("documents") val documents: List<KakaoPlaceDocumentDto>
)

/**
 * Kakao 검색 메타 정보 DTO.
 *
 * 이번 단계에서는 주로 documents만 사용하지만,
 * 추후 페이징이나 결과 수 표시가 필요하면 meta도 활용할 수 있습니다.
 */
@Serializable
data class KakaoSearchMetaDto(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("pageable_count") val pageableCount: Int = 0,
    @SerialName("is_end") val isEnd: Boolean = true
)

/**
 * Kakao 장소 1건에 대한 DTO.
 *
 * 주의:
 * - x, y는 문자열로 내려오므로 Mapper에서 Double로 안전하게 변환합니다.
 * - distance도 문자열이므로 Int?로 변환합니다.
 */
@Serializable
data class KakaoPlaceDocumentDto(
    @SerialName("id") val id: String,
    @SerialName("place_name") val placeName: String,
    @SerialName("address_name") val addressName: String? = null,
    @SerialName("road_address_name") val roadAddressName: String? = null,
    @SerialName("x") val longitude: String,
    @SerialName("y") val latitude: String,
    @SerialName("distance") val distance: String? = null,
    @SerialName("place_url") val placeUrl: String? = null
)
