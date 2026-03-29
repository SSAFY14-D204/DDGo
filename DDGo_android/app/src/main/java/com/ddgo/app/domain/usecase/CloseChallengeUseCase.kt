package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.ClosedChallenge
import com.ddgo.app.domain.repository.ChallengeRepository
import javax.inject.Inject

class CloseChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository
) {

    suspend operator fun invoke(
        challengeId: Long,
        challengeResult: String? = null,
        averageCenterStabilityRatio: Double? = null,
        mostCruxHoldNo: Int? = null,
        maxCruxDurationMs: Int? = null,
        finalComment: String? = null
    ): Result<ClosedChallenge> {
        if (challengeId <= 0L) {
            return Result.failure(Exception("Challenge ID is invalid."))
        }

        return challengeRepository.closeChallenge(
            challengeId = challengeId,
            challengeResult = challengeResult,
            averageCenterStabilityRatio = averageCenterStabilityRatio,
            mostCruxHoldNo = mostCruxHoldNo,
            maxCruxDurationMs = maxCruxDurationMs,
            finalComment = finalComment
        )
    }
}
