package com.ddgo.app.data.repository

import android.util.Log
import com.ddgo.app.core.network.toUserFacingNetworkMessageOrNull
import com.ddgo.app.data.mapper.ChallengeMapper.toDomain
import com.ddgo.app.data.mapper.ChallengeMapper.toOverview
import com.ddgo.app.data.mapper.ChallengeMapper.toRequestDto
import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeCloseRequestDto
import com.ddgo.app.data.remote.challenge.ChallengeCloseSummaryDto
import com.ddgo.app.data.remote.challenge.ChallengeCreateRequestDto
import com.ddgo.app.data.remote.challenge.HoldSaveRequestDto
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.ChallengeOverview
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.ClosedChallenge
import com.ddgo.app.domain.model.SavedChallengeHolds
import com.ddgo.app.domain.repository.ChallengeRepository
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val HOLD_API_TAG = "holdapi"

private val HoldApiJson = Json {
    prettyPrint = true
}

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

    override suspend fun getChallenges(): Result<List<ChallengeOverview>> {
        return try {
            val response = challengeApi.getChallenges()
            if (response.success && response.data != null) {
                Result.success(response.data.map { it.toOverview() })
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to get challenges." }))
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.toUserFacingNetworkMessageOrNull() ?: e.message ?: "챌린지 목록을 불러오지 못했어요.",
                    e
                )
            )
        }
    }

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
            Result.failure(
                IllegalStateException(
                    e.toUserFacingNetworkMessageOrNull() ?: e.message ?: "챌린지를 생성하지 못했어요.",
                    e
                )
            )
        }
    }

    override suspend fun saveChallengeHolds(
        challengeId: Long,
        holds: List<ChallengeHoldCoordinate>
    ): Result<SavedChallengeHolds> {
        return try {
            val request = HoldSaveRequestDto(
                holds = holds.map { it.toRequestDto() }
            )
            Log.d(
                HOLD_API_TAG,
                "PATCH /v1/challenges/$challengeId/holds request=\n" +
                    HoldApiJson.encodeToString(HoldSaveRequestDto.serializer(), request)
            )
            val response = challengeApi.saveChallengeHolds(
                challengeId = challengeId,
                request = request
            )

            if (response.success && response.data != null) {
                Log.d(
                    HOLD_API_TAG,
                    "PATCH /v1/challenges/$challengeId/holds success holdCount=${response.data.holdCount}"
                )
                Result.success(response.data.toDomain())
            } else {
                Log.e(
                    HOLD_API_TAG,
                    "PATCH /v1/challenges/$challengeId/holds failed message=${response.message}"
                )
                Result.failure(Exception(response.message.ifBlank { "Failed to save holds." }))
            }
        } catch (e: Exception) {
            Log.e(HOLD_API_TAG, "PATCH /v1/challenges/$challengeId/holds exception", e)
            Result.failure(
                IllegalStateException(
                    e.toUserFacingNetworkMessageOrNull() ?: e.message ?: "홀드 정보를 저장하지 못했어요.",
                    e
                )
            )
        }
    }

    override suspend fun closeChallenge(
        challengeId: Long,
        challengeResult: String?,
        averageCenterStabilityRatio: Double?,
        mostCruxHoldNo: Int?,
        maxCruxDurationMs: Int?,
        finalComment: String?
    ): Result<ClosedChallenge> {
        return try {
            val response = challengeApi.closeChallenge(
                challengeId = challengeId,
                request = ChallengeCloseRequestDto(
                    challengeResult = challengeResult,
                    summary = ChallengeCloseSummaryDto(
                        averageCenterStabilityRatio = averageCenterStabilityRatio,
                        mostCruxHoldNo = mostCruxHoldNo,
                        maxCruxDurationMs = maxCruxDurationMs,
                        finalComment = finalComment
                    )
                )
            )

            if (response.success && response.data != null) {
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message.ifBlank { "Failed to close challenge." }))
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    e.toUserFacingNetworkMessageOrNull() ?: e.message ?: "챌린지를 종료하지 못했어요.",
                    e
                )
            )
        }
    }
}
