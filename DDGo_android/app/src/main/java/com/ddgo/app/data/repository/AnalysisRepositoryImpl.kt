package com.ddgo.app.data.repository

import android.util.Log
import com.ddgo.app.core.datastore.AnalysisAttemptInsightCacheDataStore
import com.ddgo.app.data.remote.attempt.AttemptApi
import com.ddgo.app.data.remote.attempt.AttemptDetailResponseDto
import com.ddgo.app.data.remote.attempt.AttemptFullResponseDto
import com.ddgo.app.data.remote.attempt.AttemptHeartRateSampleResponseDto
import com.ddgo.app.data.remote.attempt.AttemptStabilityPointResponseDto
import com.ddgo.app.data.remote.challenge.ChallengeApi
import com.ddgo.app.data.remote.challenge.ChallengeListResponseDto
import com.ddgo.app.data.remote.common.GymNameFormatter
import com.ddgo.app.data.remote.common.RemoteDateTimeParser
import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeStatus
import com.ddgo.app.domain.model.AnalysisHeartRateSample
import com.ddgo.app.domain.repository.AnalysisRepository
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val TAG = "AnalysisRepository"

class AnalysisRepositoryImpl @Inject constructor(
    private val challengeApi: ChallengeApi,
    private val attemptApi: AttemptApi,
    private val analysisAttemptInsightCacheDataStore: AnalysisAttemptInsightCacheDataStore
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
        if (challenge.doneAttemptCount <= 0) {
            return null
        }
        val attemptsResponse = runCatching { attemptApi.getAttempts(challenge.id) }.getOrNull()
            ?: return null
        val attempts = attemptsResponse.data?.attempts
            ?.filter { it.attemptStatus.equals("DONE", ignoreCase = true) }
            .orEmpty()
        if (!attemptsResponse.success || attempts.isEmpty()) {
            return null
        }
        val attemptInsights = analysisAttemptInsightCacheDataStore.getAttemptInsights(
            attempts.map(AttemptDetailResponseDto::attemptId)
        )

        val attemptSnapshots = coroutineScope {
            attempts.map { attempt ->
                async {
                    buildAttemptSnapshot(
                        challengeId = challenge.id,
                        attempt = attempt,
                        cachedInsight = attemptInsights[attempt.attemptId]
                    )
                }
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
            gymName = GymNameFormatter.sanitize(challenge.gymName),
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
        attempt: AttemptDetailResponseDto,
        cachedInsight: com.ddgo.app.domain.model.AnalysisAttemptInsight?
    ): AnalysisAttemptSnapshot? {
        val detailResponse = runCatching {
            attemptApi.getAttemptDetail(challengeId = challengeId, attemptId = attempt.attemptId)
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "buildAttemptSnapshot: attempt detail request failed, challengeId=$challengeId, " +
                    "attemptId=${attempt.attemptId}",
                throwable
            )
        }.getOrNull()

        val detail = detailResponse?.data.takeIf { detailResponse?.success == true }
        if (detail == null) {
            Log.w(
                TAG,
                "buildAttemptSnapshot: using fallback attempt data, challengeId=$challengeId, " +
                    "attemptId=${attempt.attemptId}"
            )
        }
        return detail?.toAnalysisAttemptSnapshot(cachedInsight)
            ?: attempt.toFallbackAnalysisAttemptSnapshot(cachedInsight)
    }

    private fun AttemptFullResponseDto.toAnalysisAttemptSnapshot(
        cachedInsight: com.ddgo.app.domain.model.AnalysisAttemptInsight?
    ): AnalysisAttemptSnapshot {
        val resolvedMetrics = metricsData
        val resolvedFeedbacks = feedbacksData
        val resolvedInsight = resolveServerInsight() ?: cachedInsight

        return AnalysisAttemptSnapshot(
            attemptId = attemptId,
            attemptNo = attemptNo,
            attemptResult = attemptResult.toAnalysisResult(),
            videoUrl = videoUrl,
            durationMs = durationMs?.toLong() ?: 0L,
            maxHoldNo = maxHoldNo ?: 0,
            centerStabilityRatio = (
                resolvedMetrics?.centerStabilityRatio
                    ?: centerStabilityRatio
                    ?: 0.0
                ).toFloat(),
            stabilityRecoveryScore = resolvedMetrics?.stabilityRecoveryScore ?: stabilityRecoveryScore,
            stableContactRatio = (
                resolvedMetrics?.stableContactRatio
                    ?: stableContactRatio
                )?.toFloat(),
            lowerBodyDriveScore = resolvedMetrics?.lowerBodyDriveScore ?: lowerBodyDriveScore,
            overallMovementScore = resolvedMetrics?.overallMovementScore ?: overallMovementScore,
            cruxHoldNo = resolvedMetrics?.cruxHoldNo ?: cruxHoldNo,
            cruxDurationMs = (resolvedMetrics?.cruxDurationMs ?: cruxDurationMs)?.toLong(),
            dangerEventCount = resolvedMetrics?.dangerEventCount ?: dangerEventCount ?: 0,
            loadFocusLabel = resolvedMetrics?.loadFocusLabel ?: loadFocusLabel,
            failureReason = resolvedFeedbacks?.failureReason ?: failureReason,
            riskAlert = resolvedFeedbacks?.riskAlert ?: riskAlert,
            nextMission = resolvedFeedbacks?.nextMission ?: nextMission,
            insight = resolvedInsight
        )
    }

    private fun AttemptDetailResponseDto.toFallbackAnalysisAttemptSnapshot(
        cachedInsight: com.ddgo.app.domain.model.AnalysisAttemptInsight?
    ): AnalysisAttemptSnapshot {
        return AnalysisAttemptSnapshot(
            attemptId = attemptId,
            attemptNo = attemptNo,
            attemptResult = attemptResult.toAnalysisResult(),
            videoUrl = null,
            durationMs = durationMs?.toLong() ?: 0L,
            maxHoldNo = maxHoldNo ?: 0,
            centerStabilityRatio = 0f,
            stabilityRecoveryScore = null,
            stableContactRatio = null,
            lowerBodyDriveScore = null,
            overallMovementScore = null,
            cruxHoldNo = null,
            cruxDurationMs = null,
            dangerEventCount = 0,
            loadFocusLabel = null,
            failureReason = null,
            riskAlert = null,
            nextMission = null,
            insight = cachedInsight
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

    private fun AttemptFullResponseDto.resolveServerInsight():
        com.ddgo.app.domain.model.AnalysisAttemptInsight? {
        val nestedInsight = insightData
        val timelinePoints = (nestedInsight?.stabilityTimeline ?: stabilityTimeline)
            .sortedBy(AttemptStabilityPointResponseDto::timestampMs)
        val heartRatePoints = (nestedInsight?.heartRateSeries ?: heartRateSeries)
            .sortedBy(AttemptHeartRateSampleResponseDto::timestampMs)

        if (timelinePoints.isEmpty() && heartRatePoints.isEmpty()) {
            return null
        }

        return com.ddgo.app.domain.model.AnalysisAttemptInsight(
            stabilityTimeline = timelinePoints.map { it.stabilityScore.toFloat() },
            heartRateSeries = heartRatePoints.map {
                AnalysisHeartRateSample(
                    timestampMs = it.timestampMs.toLong(),
                    bpm = it.bpm
                )
            },
            videoDurationMs = (nestedInsight?.videoDurationMs ?: videoDurationMs)?.toLong(),
            stabilityFocusFraction = (
                nestedInsight?.stabilityFocusFraction ?: stabilityFocusFraction
                )?.toFloat()
        )
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
