package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.model.SocialLoginProvider
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

class SocialLoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(
        provider: SocialLoginProvider,
        accessToken: String? = null,
        idToken: String? = null
    ): Result<AuthToken> {
        return repository.socialLogin(
            provider = provider,
            accessToken = accessToken,
            idToken = idToken
        )
    }
}
