package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.repository.GymRepository
import javax.inject.Inject

/**
 * 주변 암장 검색 유스케이스.
 *
 * 역할:
 * - 입력값 검증
 * - repository 호출
 *
 * 규칙:
 * - ViewModel은 가능한 한 비즈니스 검증 로직을 직접 가지지 않습니다.
 * - 검증은 UseCase에 모읍니다.
 */
class SearchNearbyClimbingGymsUseCase @Inject constructor(
    private val gymRepository: GymRepository
) {

    /**
     * @param latitude 사용자 위도
     * @param longitude 사용자 경도
     * @param query 기본 검색어는 "클라이밍"
     * @param radiusMeters 검색 반경
     * @param size 검색 개수 제한
     */
    suspend operator fun invoke(
        latitude: Double,
        longitude: Double,
        query: String = "\uD074\uB77C\uC774\uBC0D",
        radiusMeters: Int = 3000,
        size: Int = 15
    ): Result<List<NearbyPlace>> {
        if (query.isBlank()) {
            return Result.failure(Exception("Query must not be blank."))
        }

        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return Result.failure(Exception("Invalid location coordinates."))
        }

        return gymRepository.searchNearbyPlaces(
            query = query,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            size = size
        )
    }
}
