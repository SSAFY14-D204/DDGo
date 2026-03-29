package com.ddgo.app.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface AiAnalysisApi {

    @POST
    suspend fun analyzeFast(
        @Url url: String,
        @Body request: AiAnalysisRequestDto
    ): AiAnalysisResponseDto

    @POST
    suspend fun analyzePhysics(
        @Url url: String,
        @Body request: AiAnalysisRequestDto
    ): AiAnalysisResponseDto
}
