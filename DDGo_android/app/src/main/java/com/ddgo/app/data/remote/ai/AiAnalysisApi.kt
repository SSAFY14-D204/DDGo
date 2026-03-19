package com.ddgo.app.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.POST

interface AiAnalysisApi {

    @POST("api/v1/mujoco-complete/analyze/fast")
    suspend fun analyzeFast(
        @Body request: AiAnalysisRequestDto
    ): AiAnalysisResponseDto

    @POST("api/v1/mujoco-complete/analyze/physics")
    suspend fun analyzePhysics(
        @Body request: AiAnalysisRequestDto
    ): AiAnalysisResponseDto
}
