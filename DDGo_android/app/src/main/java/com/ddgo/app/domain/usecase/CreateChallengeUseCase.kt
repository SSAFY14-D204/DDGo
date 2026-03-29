package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.repository.ChallengeRepository
import javax.inject.Inject

/**
 * 선택한 암장과 난이도로 챌린지를 생성하는 유스케이스입니다.
 *
 * 역할:
 * - repository 호출 전에 간단한 입력 검증을 수행합니다.
 */
class CreateChallengeUseCase @Inject constructor(
    private val challengeRepository: ChallengeRepository
) {

    suspend operator fun invoke(
        gymId: Long,
        gymGradeId: Long,
        startedAt: String
    ): Result<ChallengeSession> {
        if (gymId <= 0L) {
            return Result.failure(Exception("Gym selection is required."))
        }
        if (gymGradeId <= 0L) {
            return Result.failure(Exception("Gym grade selection is required."))
        }
        if (startedAt.isBlank()) {
            return Result.failure(Exception("Challenge start time is required."))
        }

        return challengeRepository.createChallenge(
            gymId = gymId,
            gymGradeId = gymGradeId,
            startedAt = startedAt
        )
    }
}
