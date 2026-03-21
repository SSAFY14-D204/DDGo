package com.ddgo.app.data.remote.attempt

import com.ddgo.app.data.remote.auth.EmptyDto
import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface AttemptApi {

    @POST("v1/challenges/{challengeId}/attempts")
    suspend fun startAttempt(
        @Path("challengeId") challengeId: Long
    ): ApiResponse<AttemptStartResponseDto>

    @POST("v1/attempts/{attemptId}/video-url")
    suspend fun generateVideoUploadUrl(
        @Path("attemptId") attemptId: Long,
        @Body request: GenerateVideoUrlRequestDto
    ): ApiResponse<GenerateVideoUrlResponseDto>

    @GET("v1/challenges/{challengeId}/attempts")
    suspend fun getAttempts(
        @Path("challengeId") challengeId: Long
    ): ApiResponse<AttemptListResponseDto>

    @GET("v1/challenges/{challengeId}/attempts/{attemptId}")
    suspend fun getAttemptDetail(
        @Path("challengeId") challengeId: Long,
        @Path("attemptId") attemptId: Long
    ): ApiResponse<AttemptFullResponseDto>

    @PATCH("v1/challenges/{challengeId}/attempts/{attemptId}")
    suspend fun endAttempt(
        @Path("challengeId") challengeId: Long,
        @Path("attemptId") attemptId: Long,
        @Body request: AttemptEndRequestDto
    ): ApiResponse<EmptyDto>
}
