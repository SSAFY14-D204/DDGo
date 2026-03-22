package com.ddgo.app.data.repository

import com.ddgo.app.data.remote.attempt.AttemptApi
import com.ddgo.app.data.remote.attempt.AttemptDetailResponseDto
import com.ddgo.app.data.remote.attempt.AttemptFullResponseDto
import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeListResponseDto
import com.ddgo.app.data.remote.common.RemoteDateTimeParser
import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeStatus
import com.ddgo.app.domain.repository.AnalysisRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AnalysisRepositoryImpl @Inject constructor(
    private val challengeApi: ChallengeApi,
    private val attemptApi: AttemptApi
) : AnalysisRepository {

    override suspend fun getAnalysisSnapshots(): Result<List<AnalysisChallengeSnapshot>> = runCatching {
        val response = challengeApi.getChallenges()
        val challenges = response.data.takeIf { response.success }
            ?: throw IllegalStateException(response.message.ifBlank { "Failed to load challenges." })

        coroutineScope {
            challenges.map { challenge ->
                async { buildChallengeSnapshot(challenge) }
            }.awaitAll()
        }
            .filterNotNull()
            .sortedByDescending { it.startedAt }
    }

    private suspend fun buildChallengeSnapshot(
        challenge: ChallengeListResponseDto
    ): AnalysisChallengeSnapshot? {
        val attemptsResponse = runCatching { attemptApi.getAttempts(challenge.id) }.getOrNull()
            ?: return null
        val attempts = attemptsResponse.data?.attempts
            ?.filter { it.attemptStatus.equals("DONE", ignoreCase = true) }
            .orEmpty()
        if (!attemptsResponse.success || attempts.isEmpty()) {
            return null
        }

        val attemptSnapshots = coroutineScope {
            attempts.map { attempt ->
                async { buildAttemptSnapshot(challenge.id, attempt) }
            }.awaitAll()
        }
            .filterNotNull()
            .sortedBy { it.attemptNo }

        if (attemptSnapshots.isEmpty()) {
            return null
        }

        val startedAt = RemoteDateTimeParser.parse(challenge.startedAt ?: challenge.createdAt)
            ?: return null

        return AnalysisChallengeSnapshot(
            id = challenge.id,
            gymName = challenge.gymName,
            problemColor = challenge.problemColor,
            gradeLabel = challenge.gradeLabel,
            challengeStatus = challenge.challengeStatus.toAnalysisStatus(),
            challengeResult = challenge.challengeResult.toAnalysisResult()
                .takeUnless { it == AnalysisChallengeResult.UNKNOWN }
                ?: deriveChallengeResult(attemptSnapshots),
            startedAt = startedAt,
            endedAt = RemoteDateTimeParser.parse(challenge.endedAt),
            finalComment = attemptSnapshots
                .asReversed()
                .mapNotNull { attempt ->
                    attempt.nextMission?.takeIf { it.isNotBlank() }
                        ?: attempt.failureReason?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
                .orEmpty(),
            attempts = attemptSnapshots
        )
    }

    private suspend fun buildAttemptSnapshot(
        challengeId: Long,
        attempt: AttemptDetailResponseDto
    ): AnalysisAttemptSnapshot? {
        val detailResponse = runCatching {
            attemptApi.getAttemptDetail(challengeId = challengeId, attemptId = attempt.attemptId)
        }.getOrNull()

        val detail = detailResponse?.data.takeIf { detailResponse?.success == true }
        return detail?.toAnalysisAttemptSnapshot()
            ?: attempt.toFallbackAnalysisAttemptSnapshot()
    }

    private fun AttemptFullResponseDto.toAnalysisAttemptSnapshot(): AnalysisAttemptSnapshot {
        return AnalysisAttemptSnapshot(
            attemptId = attemptId,
            attemptNo = attemptNo,
            attemptResult = attemptResult.toAnalysisResult(),
            durationMs = durationMs?.toLong() ?: 0L,
            maxHoldNo = maxHoldNo ?: 0,
            centerStabilityRatio = (centerStabilityRatio ?: 0.0).toFloat(),
            cruxHoldNo = cruxHoldNo,
            cruxDurationMs = cruxDurationMs?.toLong(),
            dangerEventCount = dangerEventCount ?: 0,
            failureReason = failureReason,
            riskAlert = riskAlert,
            nextMission = nextMission
        )
    }

    private fun AttemptDetailResponseDto.toFallbackAnalysisAttemptSnapshot(): AnalysisAttemptSnapshot {
        return AnalysisAttemptSnapshot(
            attemptId = attemptId,
            attemptNo = attemptNo,
            attemptResult = attemptResult.toAnalysisResult(),
            durationMs = durationMs?.toLong() ?: 0L,
            maxHoldNo = maxHoldNo ?: 0,
            centerStabilityRatio = 0f,
            cruxHoldNo = null,
            cruxDurationMs = null,
            dangerEventCount = 0,
            failureReason = null,
            riskAlert = null,
            nextMission = null
        )
    }

    private fun deriveChallengeResult(
        attempts: List<AnalysisAttemptSnapshot>
    ): AnalysisChallengeResult {
        return when {
            attempts.any { it.attemptResult == AnalysisChallengeResult.SUCCESS } ->
                AnalysisChallengeResult.SUCCESS
            attempts.any { it.attemptResult == AnalysisChallengeResult.FAIL } ->
                AnalysisChallengeResult.FAIL
            else -> AnalysisChallengeResult.UNKNOWN
        }
    }

    private fun String?.toAnalysisStatus(): AnalysisChallengeStatus {
        return if (this.equals("CLOSED", ignoreCase = true)) {
            AnalysisChallengeStatus.CLOSED
        } else {
            AnalysisChallengeStatus.ACTIVE
        }
    }

    private fun String?.toAnalysisResult(): AnalysisChallengeResult {
        return when (this?.uppercase()) {
            "SUCCESS" -> AnalysisChallengeResult.SUCCESS
            "FAIL" -> AnalysisChallengeResult.FAIL
            else -> AnalysisChallengeResult.UNKNOWN
        }
    }
}
