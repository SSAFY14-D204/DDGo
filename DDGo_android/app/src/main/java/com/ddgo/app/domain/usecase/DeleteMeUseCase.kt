package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 회원 탈퇴 UseCase.
 *
 * 서버에서 사용자 정보를 삭제하고 로컬의 토큰을 정리합니다.
 */
class DeleteMeUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.deleteMe()
    }
}
