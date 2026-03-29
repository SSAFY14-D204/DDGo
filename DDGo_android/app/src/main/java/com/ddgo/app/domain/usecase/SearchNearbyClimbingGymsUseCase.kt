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
        query: String = "",
        radiusMeters: Int = 3000,
        size: Int = 15,
        nearbyOnly: Boolean = false
    ): Result<List<NearbyPlace>> {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            return Result.failure(Exception("Invalid location coordinates."))
        }

        val candidateQueries = buildClimbingGymQueries(query)
        var lastFailure: Throwable? = null
        val aggregatedPlaces = linkedMapOf<String, NearbyPlace>()

        candidateQueries.forEach { candidate ->
            val result = gymRepository.searchNearbyPlaces(
                query = candidate.query,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                size = size,
                allowGlobalFallback = !nearbyOnly && candidate.allowGlobalFallback
            )

            if (result.isSuccess) {
                val places = result.getOrDefault(emptyList())
                if (nearbyOnly) {
                    places.forEach { place -> aggregatedPlaces.putIfAbsent(place.externalPlaceId, place) }
                } else if (places.isNotEmpty()) {
                    return Result.success(places)
                }
            } else {
                lastFailure = result.exceptionOrNull()
            }
        }

        if (nearbyOnly && aggregatedPlaces.isNotEmpty()) {
            return Result.success(aggregatedPlaces.values.toList())
        }

        return lastFailure?.let { Result.failure(it) } ?: Result.success(emptyList())
    }

    private fun buildClimbingGymQueries(query: String): List<SearchQueryCandidate> {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            return listOf(
                SearchQueryCandidate("클라이밍장"),
                SearchQueryCandidate("암장"),
                SearchQueryCandidate("볼더링")
            )
        }

        val normalized = trimmedQuery.lowercase()
        val climbingKeywords = listOf(
            "클라이밍",
            "클라이밍장",
            "암장",
            "볼더링",
            "climbing",
            "bouldering",
            "gym"
        )

        if (climbingKeywords.any { normalized.contains(it) }) {
            return listOf(SearchQueryCandidate(trimmedQuery))
        }

        return buildList {
            add(SearchQueryCandidate("$trimmedQuery 클라이밍"))
            add(SearchQueryCandidate("$trimmedQuery 클라이밍장"))
            add(SearchQueryCandidate("$trimmedQuery 암장"))
            add(SearchQueryCandidate("$trimmedQuery 볼더링"))
            add(SearchQueryCandidate("$trimmedQuery climbing"))
            add(SearchQueryCandidate("$trimmedQuery bouldering"))
            add(SearchQueryCandidate(trimmedQuery, allowGlobalFallback = false))
        }.distinct()
    }

    private data class SearchQueryCandidate(
        val query: String,
        val allowGlobalFallback: Boolean = true
    )
}
