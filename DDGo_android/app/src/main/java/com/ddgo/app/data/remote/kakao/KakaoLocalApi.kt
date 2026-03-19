package com.ddgo.app.data.remote.kakao

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Kakao Local REST API 인터페이스.
 *
 * 역할:
 * - 사용자의 현재 위치를 기준으로 카카오 장소 검색 API를 호출합니다.
 * - DDGo에서는 "클라이밍" 키워드로 주변 암장 목록을 가져오는 데 사용합니다.
 *
 * 주의:
 * - Kakao Local API는 x=경도, y=위도 순서를 사용합니다.
 * - 반환 DTO는 반드시 Mapper를 통해 domain 모델로 변환해서 사용하세요.
 */
interface KakaoLocalApi {

    /**
     * 키워드 기반 장소 검색.
     *
     * @param query 검색어. DDGo에서는 기본적으로 "클라이밍"을 사용합니다.
     * @param longitude 경도. Kakao API 파라미터명은 x 입니다.
     * @param latitude 위도. Kakao API 파라미터명은 y 입니다.
     * @param radius 검색 반경(m)
     * @param sort 정렬 기준. 거리순이면 distance
     * @param size 최대 결과 개수
     */
    @GET("v2/local/search/keyword.json")
    suspend fun searchPlacesByKeyword(
        @Query("query") query: String,
        @Query("x") longitude: String? = null,
        @Query("y") latitude: String? = null,
        @Query("radius") radius: Int? = null,
        @Query("sort") sort: String? = "distance",
        @Query("size") size: Int = 15
    ): KakaoKeywordSearchResponseDto
}
