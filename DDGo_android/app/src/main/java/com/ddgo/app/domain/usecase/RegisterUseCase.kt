package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
이 질문 하나로 판단하면 됩니다.
"사용자가 하는 행동 하나인가?"

그럼 UseCase 하나입니다.

이 파일에서는 로그인 이라는 행동만을 정의합니다.
 */
class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String, nickname: String): Result<Unit> {
        // 1. 사전 유효성 검사 (입력값이 비어있는지)
        if (username.isBlank() || password.isBlank() || nickname.isBlank()) {
            return Result.failure(Exception("모든 정보를 입력해주세요."))
        }

        // 2. (선택 사항) 추가 검사: 비밀번호 길이 등
        if (password.length < 7) {
            return Result.failure(Exception("비밀번호는 7자 이상이어야 합니다."))
        }

        // 3. 실제 서버 요청 (Repository 호출)
        // 여기서 .map { Unit }을 써서 DTO를 Unit으로 세탁합니다.
        return repository.register(username, password, nickname)
            .map { Unit }
    }
}