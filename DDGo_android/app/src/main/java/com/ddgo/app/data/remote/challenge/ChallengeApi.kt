package com.ddgo.app.data.remote.challenge

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ChallengeApi {

    @GET("v1/challenges")
    suspend fun getChallenges(): ApiResponse<List<ChallengeListResponseDto>>

    @POST("v1/challenges")
    suspend fun createChallenge(
        @Body request: ChallengeCreateRequestDto
    ): ApiResponse<ChallengeCreateResponseDto>

    @PATCH("v1/challenges/{challengeId}/holds")
    suspend fun saveChallengeHolds(
        @Path("challengeId") challengeId: Long,
        @Body request: HoldSaveRequestDto
    ): ApiResponse<HoldSaveResponseDto>

    @PATCH("v1/challenges/{challengeId}/close")
    suspend fun closeChallenge(
        @Path("challengeId") challengeId: Long,
        @Body request: ChallengeCloseRequestDto
    ): ApiResponse<ChallengeCloseResponseDto>
}
