package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.auth.LoginResponseDto
import com.ddgo.app.data.remote.auth.RefreshTokenResponseDto
import com.ddgo.app.data.remote.auth.UserResponseDto
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.User

/**
 * Auth 관련 DTO → Domain Model 변환 매퍼.
 *
 * 규칙:
 * - DTO(서버 데이터)를 그대로 UI나 domain으로 넘기지 마세요.
 * - 반드시 이 Mapper를 통해 순수 Kotlin 데이터 클래스(domain/model)로 변환하세요.
 */
object AuthMapper {
    /** 로그인 응답 DTO → AuthToken 도메인 모델로 변환 */
    fun LoginResponseDto.toDomain(): AuthToken = AuthToken(
        accessToken = this.accessToken,
        refreshToken = this.refreshToken,
        isNewUser = this.isNewUser,
        needsOnboarding = this.needsOnboarding
    )

    /** 토큰 재발급 응답 DTO → AuthToken 도메인 모델로 변환 */
    fun RefreshTokenResponseDto.toDomain(): AuthToken = AuthToken(
        accessToken = this.accessToken,
        refreshToken = this.refreshToken
    )

    /** 내 정보 응답 DTO → User 도메인 모델로 변환 */
    fun UserResponseDto.toDomain(): User = User(
        id = this.id,
        username = this.username,
        email = this.email,
        nickname = this.nickname,
        sex = this.sex,
        heightCm = this.heightCm,
        weightKg = this.weightKg,
        wingspanCm = this.wingspanCm
    )
}
