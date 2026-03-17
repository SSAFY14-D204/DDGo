package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.repository.ChallengeRepository
import javax.inject.Inject

/** 감지된 홀드 좌표를 챌린지에 저장하는 유스케이스입니다. */
class SaveChallengeHoldsUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository
) {

    suspend operator fun invoke(
        challengeId: Long,
        holds: List<ChallengeHoldCoordinate>
    ): Result<SavedChallengeHolds> {
        if (challengeId <= 0L) {
            return Result.failure(Exception("Challenge ID is invalid."))
        }
        if (holds.isEmpty()) {
            return Result.failure(Exception("At least one hold must be saved."))
        }

        return challengeRepository.saveChallengeHolds(
            challengeId = challengeId,
            holds = holds
        )
    }
}
