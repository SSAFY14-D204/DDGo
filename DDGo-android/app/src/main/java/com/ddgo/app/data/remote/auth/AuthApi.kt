package com.ddgo.app.data.remote.auth

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 인증 관련 API 엔드포인트.
 *
 * 새 인증 API를 추가할 때는 여기에 함수를 추가하세요.
 */
interface AuthApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequestDto): ApiResponse<LoginResponseDto>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequestDto): ApiResponse<LoginResponseDto>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>
}
