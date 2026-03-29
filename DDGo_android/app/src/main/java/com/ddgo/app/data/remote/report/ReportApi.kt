package com.ddgo.app.data.remote.report

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.GET
import retrofit2.http.Path

/** 클라이밍 분석 리포트 API */
interface ReportApi {

    @GET("climbs/{climbId}/report")
    suspend fun getReport(@Path("climbId") climbId: String): ApiResponse<ReportResponseDto>

    @GET("climbs")
    suspend fun getMyClimbs(): ApiResponse<List<ReportResponseDto>>
}
