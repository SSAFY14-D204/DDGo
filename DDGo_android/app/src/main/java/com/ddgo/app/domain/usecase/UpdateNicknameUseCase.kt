package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 닉네임 등록/변경 요청을 감싸는 유스케이스입니다.
 *
 * 역할:
 * - 프로필 화면이 repository 구현을 직접 알지 않도록 분리합니다.
 * - 초기 회원의 첫 닉네임 등록과 기존 닉네임 변경을 같은 진입점으로 처리합니다.
 */
class UpdateNicknameUseCase @Inject constructor(
    private val repository: AuthRepository
) {

    /** 새 닉네임을 저장합니다. */
    suspend operator fun invoke(nickname: String): Result<Unit> {
        return repository.updateNickname(nickname)
    }
}
