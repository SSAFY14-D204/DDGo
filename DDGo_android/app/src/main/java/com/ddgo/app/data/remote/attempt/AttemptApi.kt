package com.ddgo.app.data.remote.attempt

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 시도 영상 관련 백엔드 API입니다.
 *
 * 역할:
 * - 특정 챌린지 아래에서 시도를 시작합니다.
 * - 시도 영상 업로드를 위한 presigned URL을 발급받습니다.
 */
interface AttemptApi {

    /** 특정 챌린지에 대한 새 시도를 시작합니다. */
    @POST("v1/challenges/{challengeId}/attempts")
    suspend fun startAttempt(
        @Path("challengeId") challengeId: Long
    ): ApiResponse<AttemptStartResponseDto>

    /** 영상 직접 업로드용 presigned URL을 요청합니다. */
    @POST("v1/attempts/{attemptId}/video-url")
    suspend fun generateVideoUploadUrl(
        @Path("attemptId") attemptId: Long,
        @Body request: GenerateVideoUrlRequestDto
    ): ApiResponse<GenerateVideoUrlResponseDto>
}
