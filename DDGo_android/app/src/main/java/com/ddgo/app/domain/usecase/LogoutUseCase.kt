package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 로그아웃 비즈니스 로직.
 *
 * 서버 로그아웃 API를 호출하고 로컬에 저장된 토큰을 삭제합니다.
 */
class LogoutUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.logout()
    }
}
