package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.model.User

/**
 * 인증/회원 기능의 도메인 저장소 계약입니다.
 *
 * 역할:
 * - 회원가입, 로그인, 토큰 재발급, 로그아웃을 추상화합니다.
 * - 내 정보 조회와 프로필 편집 같은 사용자 계정 관리 기능도 함께 제공합니다.
 * - 실제 네트워크 구현은 data 계층의 RepositoryImpl이 담당합니다.
 */
interface AuthRepository {

    /** 아이디, 비밀번호, 닉네임으로 회원가입합니다. */
    suspend fun register(
        username: String,
        password: String,
        nickname: String
    ): Result<Unit>

    /** 아이디와 비밀번호로 로그인하고 토큰을 발급받습니다. */
    suspend fun login(
        username: String,
        password: String
    ): Result<AuthToken>

    suspend fun socialLogin(
        provider: SocialLoginProvider,
        accessToken: String? = null,
        idToken: String? = null
    ): Result<AuthToken>

    /** Refresh Token으로 새로운 토큰을 재발급받습니다. */
    suspend fun requestPasswordReset(email: String): Result<Unit>

    suspend fun confirmPasswordReset(
        token: String,
        newPassword: String
    ): Result<Unit>

    suspend fun refreshToken(refreshToken: String): Result<AuthToken>

    /** 현재 로그인 상태를 종료합니다. */
    suspend fun logout(): Result<LogoutResult>

    /** 로그인한 사용자의 기본 정보를 조회합니다. */
    suspend fun getMyInfo(): Result<User>

    /**
     * 로그인한 사용자의 신체 정보를 수정합니다.
     *
     * 온보딩에서 값을 입력하지 않은 사용자도 같은 API로 최초 등록할 수 있습니다.
     */
    suspend fun updateProfile(
        sex: String,
        heightCm: Float,
        weightKg: Float,
        wingspanCm: Float
    ): Result<Unit>

    /**
     * 로그인한 사용자의 닉네임을 등록하거나 변경합니다.
     *
     * 초기 회원의 첫 닉네임 등록과 기존 닉네임 변경을 모두 담당합니다.
     */
    suspend fun updateNickname(nickname: String): Result<Unit>

    /**
     * 로그인한 사용자의 비밀번호를 변경합니다.
     *
     * 기존 비밀번호 확인과 새 비밀번호 저장을 함께 처리합니다.
     */
    suspend fun updatePassword(
        oldPassword: String,
        newPassword: String
    ): Result<Unit>

    /** 로그인한 사용자의 계정을 탈퇴 처리합니다. */
    suspend fun deleteMe(): Result<Unit>
}
