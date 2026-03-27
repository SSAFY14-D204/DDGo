package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.ChallengeOverview
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.ClosedChallenge
import com.ddgo.app.domain.model.SavedChallengeHolds

/**
 * ChallengeRepository 계약입니다.
 *
 * 규칙:
 * - feature/domain 계층은 이 인터페이스에만 의존합니다.
 * - 실제 백엔드 구현은 data 계층에서 제공합니다.
 */
interface ChallengeRepository {

    suspend fun getChallenges(): Result<List<ChallengeOverview>>

    /** 선택한 암장과 난이도로 챌린지 세션을 생성합니다. */
    suspend fun createChallenge(
        gymId: Long,
        gymGradeId: Long,
        startedAt: String
    ): Result<ChallengeSession>

    /** 챌린지 홀드 좌표를 저장합니다. */
    suspend fun saveChallengeHolds(
        challengeId: Long,
        holds: List<ChallengeHoldCoordinate>
    ): Result<SavedChallengeHolds>

    suspend fun closeChallenge(
        challengeId: Long,
        challengeResult: String? = null,
        averageCenterStabilityRatio: Double? = null,
        mostCruxHoldNo: Int? = null,
        maxCruxDurationMs: Int? = null,
        finalComment: String? = null
    ): Result<ClosedChallenge>
}
