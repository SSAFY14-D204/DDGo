package com.ddgo.app.data.remote.ai

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface AiRealtimeSessionApi {
    @POST("api/v1/mujoco-complete/session/start")
    suspend fun startSession(
        @Body request: AiRealtimeSessionStartRequestDto
    ): AiRealtimeSessionAckResponseDto

    @POST("api/v1/mujoco-complete/session/{session_id}/pose-chunks")
    suspend fun appendPoseChunks(
        @Path("session_id") sessionId: String,
        @Body request: AiRealtimePoseChunkRequestDto
    ): AiRealtimeSessionAckResponseDto

    @POST("api/v1/mujoco-complete/session/{session_id}/context")
    suspend fun attachContext(
        @Path("session_id") sessionId: String,
        @Body request: AiRealtimeSessionContextRequestDto
    ): AiRealtimeSessionAckResponseDto

    @POST("api/v1/mujoco-complete/session/{session_id}/finalize")
    suspend fun finalizeSession(
        @Path("session_id") sessionId: String
    ): AiAnalysisResponseDto

    @DELETE("api/v1/mujoco-complete/session/{session_id}")
    suspend fun abortSession(
        @Path("session_id") sessionId: String
    ): AiRealtimeSessionAckResponseDto
}
