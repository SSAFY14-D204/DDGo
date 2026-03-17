package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.challenge.ChallengeCreateResponseDto
import com.ddgo.app.data.remote.challenge.HoldItemDto
import com.ddgo.app.data.remote.challenge.HoldSaveResponseDto
import com.ddgo.app.domain.model.ChallengeHoldCoordinate
import com.ddgo.app.domain.model.ChallengeSession
import com.ddgo.app.domain.model.SavedChallengeHolds

/**
 * Challenge DTO를 Domain 모델로 변환하는 매퍼입니다.
 *
 * 규칙:
 * - DTO를 feature/domain 계층으로 직접 넘기지 않습니다.
 * - 백엔드 응답은 여기서 순수 domain 모델로 변환합니다.
 */
object ChallengeMapper {

    /** 챌린지 생성 응답을 ChallengeSession으로 변환합니다. */
    fun ChallengeCreateResponseDto.toDomain(
        gymId: Long,
        gymGradeId: Long
    ): ChallengeSession = ChallengeSession(
        challengeId = id,
        gymId = gymId,
        gymGradeId = gymGradeId,
        gymName = gymName,
        problemColor = problemColor,
        gradeLabel = gradeLabel,
        challengeStatus = challengeStatus,
        startedAt = startedAt,
        createdAt = createdAt
    )

    /** 홀드 저장 응답을 domain 결과 모델로 변환합니다. */
    fun HoldSaveResponseDto.toDomain(): SavedChallengeHolds = SavedChallengeHolds(
        challengeId = challengeId,
        holdCount = holdCount,
        holds = holds.map { it.toDomain() }
    )

    /** domain 홀드 좌표를 요청 DTO로 변환합니다. */
    fun ChallengeHoldCoordinate.toDto(): HoldItemDto = HoldItemDto(
        holdNo = holdNo,
        x1 = x1,
        x2 = x2,
        y1 = y1,
        y2 = y2
    )

    private fun HoldItemDto.toDomain(): ChallengeHoldCoordinate = ChallengeHoldCoordinate(
        holdNo = holdNo,
        x1 = x1,
        x2 = x2,
        y1 = y1,
        y2 = y2
    )
}
