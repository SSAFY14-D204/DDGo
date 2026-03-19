package com.ddgo.app.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 회원가입 요청 DTO입니다. */
@Serializable
data class RegisterRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("nickname") val nickname: String
)

/** 로그인 요청 DTO입니다. */
@Serializable
data class LoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)

/** 로그인 응답 DTO입니다. */
@Serializable
data class LoginResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String
)

/** 토큰 재발급 요청 DTO입니다. */
@Serializable
data class RefreshTokenRequestDto(
    @SerialName("refreshToken") val refreshToken: String
)

/** 토큰 재발급 응답 DTO입니다. */
@Serializable
data class RefreshTokenResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String
)

/** 내 정보 조회 응답 DTO입니다. */
@Serializable
data class UserResponseDto(
    @SerialName("id") val id: Long,
    @SerialName("username") val username: String,
    @SerialName("nickname") val nickname: String,
    @SerialName("sex") val sex: String? = null,
    @SerialName("heightCm") val heightCm: Float? = null,
    @SerialName("weightKg") val weightKg: Float? = null,
    @SerialName("wingspanCm") val wingspanCm: Float? = null
)

/**
 * 신체 정보 수정 요청 DTO입니다.
 *
 * PATCH /v1/users/profile 요청 본문으로 사용되며,
 * 온보딩에서 값을 넣지 않은 사용자도 이 API로 최초 등록이 가능합니다.
 */
@Serializable
data class UpdateProfileRequestDto(
    @SerialName("sex") val sex: String,
    @SerialName("heightCm") val heightCm: Float,
    @SerialName("weightKg") val weightKg: Float,
    @SerialName("wingspanCm") val wingspanCm: Float
)

/**
 * 닉네임 등록/변경 요청 DTO입니다.
 *
 * PATCH /v1/users/nickname 요청 본문으로 사용되며,
 * 초기 회원의 첫 닉네임 등록과 기존 닉네임 변경을 모두 담당합니다.
 */
@Serializable
data class UpdateNicknameRequestDto(
    @SerialName("nickname") val nickname: String
)

/**
 * 비밀번호 변경 요청 DTO입니다.
 *
 * PATCH /v1/users/password 요청 본문으로 사용되며,
 * 기존 비밀번호 확인용 값과 새 비밀번호 값을 함께 전달합니다.
 */
@Serializable
data class UpdatePasswordRequestDto(
    @SerialName("oldPassword") val oldPassword: String,
    @SerialName("newPassword") val newPassword: String
)

/** 별도 데이터가 없는 응답에서 사용하는 빈 DTO입니다. */
@Serializable
class EmptyDto
