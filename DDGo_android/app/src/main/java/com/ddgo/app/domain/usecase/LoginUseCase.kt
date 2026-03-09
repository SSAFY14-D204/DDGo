package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
이 질문 하나로 판단하면 됩니다.
"사용자가 하는 행동 하나인가?"

그럼 UseCase 하나입니다.

이 파일에서는 로그인 이라는 행동만을 정의합니다.
 */
class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): Result<AuthToken> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(Exception("아이디와 비밀번호를 입력해주세요."))
        }
        return repository.login(username, password)
    }
}