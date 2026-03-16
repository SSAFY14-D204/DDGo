package com.ddgo.app.domain.model

/**
 * 앱 내부 표준 사용자 모델.
 *
 * 서버 DTO(LoginResponseDto)와는 완전히 독립됩니다.
 * AuthMapper를 통해서만 생성됩니다.
 */
data class User(
    val id: Long,
    val username: String,
    val nickname: String,
    val sex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val wingspanCm: Float? = null
)
