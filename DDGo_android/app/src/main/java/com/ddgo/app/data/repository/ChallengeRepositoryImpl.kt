package com.ddgo.app.data.repository

import com.ddgo.app.data.mapper.ChallengeMapper.toDomain
import com.ddgo.app.data.mapper.ChallengeMapper.toDto
import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeCreateRequestDto
import com.ddgo.app.data.remote.challenge.HoldSaveRequestDto
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.repository.ChallengeRepository
import javax.inject.Inject

/**
 * ChallengeRepository 구현체입니다.
 *
 * 역할:
 * - 챌린지 생성 API와 홀드 저장 API를 호출합니다.
 * - DTO를 domain 모델로 변환합니다.
 */
class ChallengeRepositoryImpl @Inject constructor(
    private val challengeApi: ChallengeApi
) : ChallengeRepository {

    override suspend fun createChallenge(
        gymId: Long,
        gymGradeId: Long,
        startedAt: String
    ): Result<ChallengeSession> {
        return try {
            val response = challengeApi.createChallenge(
                ChallengeCreateRequestDto(
                    gymId = gymId,
                    gymGradeId = gymGradeId,
                    startedAt = startedAt
                )
            )

            if (response.success && response.data != null) {
                Result.success(response.data.toDomain(gymId = gymId, gymGradeId = gymGradeId))
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to create challenge." }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveChallengeHolds(
        challengeId: Long,
        holds: List<ChallengeHoldCoordinate>
    ): Result<SavedChallengeHolds> {
        return try {
            val response = challengeApi.saveChallengeHolds(
                challengeId = challengeId,
                request = HoldSaveRequestDto(
                    holds = holds.map { it.toDto() }
                )
            )

            if (response.success && response.data != null) {
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to save holds." }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
