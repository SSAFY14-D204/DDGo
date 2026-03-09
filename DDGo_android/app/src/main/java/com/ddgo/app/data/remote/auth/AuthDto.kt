package com.ddgo.app.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 로그인 요청 DTO.
 * 서버가 요구하는 필드명과 정확히 일치해야 합니다.
 * @SerialName: 서버 필드명이 카멜케이스가 아닐 때 사용합니다.
 */
@Serializable
data class LoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)

/**
 * 로그인 응답 DTO.
 * 서버에서 받아온 raw 데이터입니다.
 * → 절대 domain layer로 직접 넘기지 마세요. Mapper를 통해 변환해야 합니다.
 */
@Serializable
data class LoginResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
)

/** 토큰 갱신 요청 DTO */
@Serializable
data class RefreshTokenRequestDto(
    @SerialName("refresh_token") val refreshToken: String
)
