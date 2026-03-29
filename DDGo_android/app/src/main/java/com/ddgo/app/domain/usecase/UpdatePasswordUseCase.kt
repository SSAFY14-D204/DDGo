package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 비밀번호 변경 요청을 실행하는 유스케이스입니다.
 *
 * 역할:
 * - 프로필 화면이 repository 구현을 직접 알지 않도록 중간 진입점을 제공합니다.
 * - 현재 비밀번호와 새 비밀번호를 묶어 domain 레이어에서 전달합니다.
 */
class UpdatePasswordUseCase @Inject constructor(
    private val repository: AuthRepository
) {

    /** 현재 비밀번호 확인 후 새 비밀번호로 변경합니다. */
    suspend operator fun invoke(
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return repository.updatePassword(
            oldPassword = oldPassword,
            newPassword = newPassword
        )
    }
}
