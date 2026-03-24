package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

class RequestPasswordResetUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String): Result<Unit> {
        return repository.requestPasswordReset(email)
    }
}
