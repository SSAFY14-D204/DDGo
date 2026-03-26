package com.ddgo.app.data.remote.gym

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * DDGo 백엔드의 gym 관련 API 인터페이스.
 *
 * 역할:
 * - 프론트에서 선택한 카카오 장소 정보를 서버로 보내고,
 *   DDGo DB의 climbing_gyms와 매칭/보정된 결과를 받아옵니다.
 *
 * 주의:
 * - baseUrl에 /api/가 이미 포함되어 있으므로 컨트롤러 매핑인 "v1/gyms/resolve"만 적습니다.
 */
interface GymApi {

    /**
     * 선택한 장소 정보를 백엔드에 전달하여 gym resolve를 수행합니다.
     */
    @POST("v1/gyms/resolve")
    suspend fun resolveGym(
        @Body request: ResolveGymRequestDto
    ): ApiResponse<ResolveGymResponseDto>

    /**
     * 특정 암장의 난이도(grade) 목록을 조회합니다.
     *
     * [GYM_GRADES_API] 로그 태그로 Logcat에서 검색 가능합니다.
     */
    @GET("v1/gyms/{gymId}/grades")
    suspend fun getGymGrades(
        @Path("gymId") gymId: Int
    ): ApiResponse<List<GymGradeDto>>
}
