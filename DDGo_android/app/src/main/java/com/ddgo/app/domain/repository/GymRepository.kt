package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.domain.model.NearbyPlace
import com.ddgo.app.domain.model.ResolvedGym

/**
 * gym 관련 repository 인터페이스.
 *
 * 규칙:
 * - feature/usecase는 구현체를 몰라야 합니다.
 * - data 계층의 GymRepositoryImpl이 실제 Kakao API, DDGo API 호출을 담당합니다.
 */
interface GymRepository {

    /**
     * 현재 위치 기준으로 주변 장소를 검색합니다.
     */
    suspend fun searchNearbyPlaces(
        query: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 3000,
        size: Int = 15,
        allowGlobalFallback: Boolean = true
    ): Result<List<NearbyPlace>>

    /**
     * 선택한 장소를 DDGo gym으로 resolve 합니다.
     */
    suspend fun resolveGym(place: NearbyPlace): Result<ResolvedGym>

    /**
     * 특정 암장의 난이도(grade) 목록을 조회합니다.
     *
     * Logcat에서 [GYM_GRADES_API] 태그로 검색하면 관련 로그를 볼 수 있습니다.
     */
    suspend fun getGymGrades(gymId: Int): Result<List<GymGrade>>
}
