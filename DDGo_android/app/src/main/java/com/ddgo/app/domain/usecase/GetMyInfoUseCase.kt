package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * 내 정보를 가져오는 UseCase.
 */
class GetMyInfoUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(): Result<User> {
        return repository.getMyInfo()
    }
}
