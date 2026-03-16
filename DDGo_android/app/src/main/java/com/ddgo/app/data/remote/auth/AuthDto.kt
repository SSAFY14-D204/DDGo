package com.ddgo.app.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class RegisterRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
    @SerialName("nickname") val nickname: String,
)

@Serializable
data class LoginRequestDto(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String
)

@Serializable
data class LoginResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
)

@Serializable
data class RefreshTokenRequestDto(
    @SerialName("refreshToken") val refreshToken: String
)

@Serializable
data class RefreshTokenResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
)

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

@Serializable
class EmptyDto()