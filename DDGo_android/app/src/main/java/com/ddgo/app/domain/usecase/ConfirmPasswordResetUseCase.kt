package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

class ConfirmPasswordResetUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(token: String, newPassword: String): Result<Unit> {
        return repository.confirmPasswordReset(
            token = token,
            newPassword = newPassword
        )
    }
}
