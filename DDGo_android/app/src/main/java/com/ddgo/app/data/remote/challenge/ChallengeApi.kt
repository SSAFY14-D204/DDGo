package com.ddgo.app.data.remote.challenge

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 챌린지 세션 관련 백엔드 API입니다.
 *
 * 역할:
 * - 사용자가 암장과 난이도를 선택한 뒤 챌린지를 생성합니다.
 * - 생성된 챌린지에 홀드 좌표를 저장합니다.
 *
 * 규칙:
 * - 챌린지 데이터는 로그인 사용자 소유이므로 인증 Retrofit 인스턴스를 사용합니다.
 */
interface ChallengeApi {

    /** 새로운 챌린지를 생성합니다. */
    @POST("v1/challenges")
    suspend fun createChallenge(
        @Body request: ChallengeCreateRequestDto
    ): ApiResponse<ChallengeCreateResponseDto>

    /** 특정 챌린지에 홀드 좌표를 저장합니다. */
    @PATCH("v1/challenges/{challengeId}/holds")
    suspend fun saveChallengeHolds(
        @Path("challengeId") challengeId: Long,
        @Body request: HoldSaveRequestDto
    ): ApiResponse<HoldSaveResponseDto>
}
