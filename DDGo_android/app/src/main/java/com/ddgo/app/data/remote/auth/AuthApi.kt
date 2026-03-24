package com.ddgo.app.data.remote.auth

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 회원/인증 관련 Retrofit API 정의입니다.
 *
 * 역할:
 * - 로그인, 로그아웃, 내 정보 조회, 프로필 수정 같은 사용자 관련 HTTP 요청을 선언합니다.
 * - repository 계층은 이 인터페이스를 통해 네트워크 요청만 수행하고,
 *   결과 해석과 로컬 상태 정리는 별도 레이어에서 담당합니다.
 */
interface AuthApi {

    /** 회원가입 요청입니다. */
    @POST("v1/users/register")
    suspend fun register(
        @Body request: RegisterRequestDto
    ): ApiResponse<EmptyDto>

    @GET("v1/users/check-username")
    suspend fun checkUsernameAvailability(
        @Query("username") username: String
    ): ApiResponse<AvailabilityResponseDto>

    @GET("v1/users/check-nickname")
    suspend fun checkNicknameAvailability(
        @Query("nickname") nickname: String
    ): ApiResponse<AvailabilityResponseDto>

    /** Refresh Token으로 토큰을 재발급합니다. */
    @POST("v1/users/refresh")
    suspend fun refresh(
        @Body request: RefreshTokenRequestDto
    ): ApiResponse<RefreshTokenResponseDto>

    /** 아이디/비밀번호로 로그인합니다. */
    @POST("v1/users/login")
    suspend fun login(
        @Body request: LoginRequestDto
    ): ApiResponse<LoginResponseDto>

    @POST("v1/users/social/login")
    suspend fun socialLogin(
        @Body request: SocialLoginRequestDto
    ): ApiResponse<LoginResponseDto>

    /** 현재 로그인 상태를 종료합니다. */
    @POST("v1/users/password/reset/request")
    suspend fun requestPasswordReset(
        @Body request: PasswordResetMailRequestDto
    ): ApiResponse<EmptyDto>

    @POST("v1/users/password/reset/confirm")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetConfirmRequestDto
    ): ApiResponse<EmptyDto>

    @POST("v1/users/logout")
    suspend fun logout(): ApiResponse<EmptyDto>

    /** 로그인한 사용자의 기본 정보를 조회합니다. */
    @GET("v1/users/me")
    suspend fun getMyInfo(): ApiResponse<UserResponseDto>

    @GET("v1/users/me")
    suspend fun getMyInfoWithAuthorization(
        @Header("Authorization") authorization: String
    ): ApiResponse<UserResponseDto>

    /** 로그인한 사용자의 신체 정보를 수정합니다. */
    @PATCH("v1/users/profile")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequestDto
    ): ApiResponse<EmptyDto>

    /** 로그인한 사용자의 닉네임을 등록하거나 변경합니다. */
    @PATCH("v1/users/nickname")
    suspend fun updateNickname(
        @Body request: UpdateNicknameRequestDto
    ): ApiResponse<EmptyDto>

    /** 로그인한 사용자의 기존 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다. */
    @PATCH("v1/users/password")
    suspend fun updatePassword(
        @Body request: UpdatePasswordRequestDto
    ): ApiResponse<EmptyDto>

    /** 로그인한 사용자의 계정을 탈퇴 처리합니다. */
    @DELETE("v1/users/me")
    suspend fun deleteMe(): ApiResponse<EmptyDto>
}
