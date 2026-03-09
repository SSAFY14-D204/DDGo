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
    @POST("v1/users/register")
    suspend fun register(@Body request: RegisterRequestDto): ApiResponse<EmptyDto>

    @POST("v1/users/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequestDto): ApiResponse<RefreshTokenResponseDto>

    @POST("v1/users/login")
    suspend fun login(@Body request: LoginRequestDto): ApiResponse<LoginResponseDto>

    @POST("v1/users/logout")
    suspend fun logout(): ApiResponse<EmptyDto>
}