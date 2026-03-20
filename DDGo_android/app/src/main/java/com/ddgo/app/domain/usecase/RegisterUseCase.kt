package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String, nickname: String): Result<Unit> {
        return repository.register(username, password, nickname)
            .map { Unit }
    }
}
