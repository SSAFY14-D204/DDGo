package com.ddgo.app.data.repository

import com.ddgo.app.data.mapper.GymMapper.toDomain
import com.ddgo.app.data.mapper.GymMapper.toDomainOrNull
import com.ddgo.app.data.remote.gym.GymApi
import com.ddgo.app.data.remote.gym.ResolveGymRequestDto
import com.ddgo.app.data.remote.kakao.KakaoLocalApi
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym
import com.ddgo.app.domain.repository.GymRepository
import javax.inject.Inject

/**
 * GymRepository 구현체.
 *
 * 역할:
 * - Kakao Local API 호출
 * - DDGo backend gym resolve 호출
 * - DTO -> Domain 변환
 *
 * 규칙:
 * - feature나 usecase는 Retrofit DTO를 몰라야 합니다.
 * - 네트워크 예외는 Result.failure로 감싸서 상위 계층에 전달합니다.
 */
class GymRepositoryImpl @Inject constructor(
    private val kakaoLocalApi: KakaoLocalApi,
    private val gymApi: GymApi
) : GymRepository {

    /**
     * 현재 위치 기준으로 주변 장소를 검색합니다.
     */
    override suspend fun searchNearbyPlaces(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
        size: Int
    ): Result<List<NearbyPlace>> {
        return try {
            val response = kakaoLocalApi.searchPlacesByKeyword(
                query = query,
                longitude = longitude.toString(),
                latitude = latitude.toString(),
                radius = radiusMeters,
                size = size
            )

            Result.success(response.documents.mapNotNull { it.toDomainOrNull() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자가 선택한 장소를 DDGo gym으로 resolve 합니다.
     */
    override suspend fun resolveGym(place: NearbyPlace): Result<ResolvedGym> {
        return try {
            val response = gymApi.resolveGym(
                ResolveGymRequestDto(
                    mapProvider = "KAKAO",
                    externalPlaceId = place.externalPlaceId,
                    placeName = place.placeName,
                    addressName = place.addressName,
                    roadAddressName = place.roadAddressName,
                    latitude = place.latitude,
                    longitude = place.longitude
                )
            )

            if (response.success && response.data != null) {
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to resolve gym." }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
