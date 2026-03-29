package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AttemptCompletionPayload
import com.ddgo.app.domain.repository.AttemptRepository
import javax.inject.Inject

/**
 * 업로드가 끝난 시도를 종료 처리하는 유스케이스입니다.
 *
 * 역할:
 * - attempt 종료 시점에 필요한 최소 입력값을 검증합니다.
 * - repository를 통해 서버 종료 API를 호출합니다.
 */
class EndAttemptUseCase @Inject constructor(
    private val attemptRepository: AttemptRepository
) {

    suspend operator fun invoke(
        challengeId: Long,
        attemptId: Long,
        payload: AttemptCompletionPayload
    ): Result<Unit> {
        if (challengeId <= 0L) {
            return Result.failure(Exception("Challenge ID is invalid."))
        }
        if (attemptId <= 0L) {
            return Result.failure(Exception("Attempt ID is invalid."))
        }

        return attemptRepository.endAttempt(
            challengeId = challengeId,
            attemptId = attemptId,
            payload = payload
        )
    }
}
