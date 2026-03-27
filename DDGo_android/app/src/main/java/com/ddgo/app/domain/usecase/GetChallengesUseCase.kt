package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.ChallengeOverview
import com.ddgo.app.domain.repository.ChallengeRepository
import javax.inject.Inject

class GetChallengesUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository
) {
    suspend operator fun invoke(): Result<List<ChallengeOverview>> {
        return challengeRepository.getChallenges()
    }
}
