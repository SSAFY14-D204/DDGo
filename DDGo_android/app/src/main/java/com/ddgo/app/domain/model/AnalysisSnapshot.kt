package com.ddgo.app.domain.model

import java.time.LocalDateTime

/**
 * 분석 화면에서 공통으로 사용하는 챌린지 원본 데이터입니다.
 *
 * 역할:
 * - 챌린지 단위 분석 화면을 그리기 위한 최소 원본 정보를 담습니다.
 * - 이후 실제 API를 연동할 때도 UI가 아닌 domain 모델 기준으로 데이터를 전달하기 위한 기준점입니다.
 */
data class AnalysisChallengeSnapshot(
    val id: Long,
    val gymName: String,
    val problemColor: String,
    val gradeLabel: String?,
    val challengeStatus: AnalysisChallengeStatus,
    val challengeResult: AnalysisChallengeResult,
    val startedAt: LocalDateTime,
    val endedAt: LocalDateTime?,
    val finalComment: String,
    val attempts: List<AnalysisAttemptSnapshot>
)

/**
 * 한 챌린지 안의 개별 시도 원본 데이터입니다.
 *
 * 역할:
 * - 시도 목록, 시도 상세, 성장 그래프에서 공통으로 쓰는 기준 데이터를 담습니다.
 * - 분석 UI에서 필요한 수치와 코칭 문구를 한 번에 참조할 수 있도록 유지합니다.
 */
data class AnalysisAttemptSnapshot(
    val attemptId: Long,
    val attemptNo: Int,
    val attemptResult: AnalysisChallengeResult,
    val videoUrl: String?,
    val durationMs: Long,
    val maxHoldNo: Int,
    val centerStabilityRatio: Float,
    val stabilityRecoveryScore: Int? = null,
    val stableContactRatio: Float? = null,
    val lowerBodyDriveScore: Int? = null,
    val overallMovementScore: Int? = null,
    val cruxHoldNo: Int?,
    val cruxDurationMs: Long?,
    val dangerEventCount: Int,
    val loadFocusLabel: String? = null,
    val failureReason: String?,
    val riskAlert: String?,
    val nextMission: String?,
    val insight: AnalysisAttemptInsight? = null
)

data class AnalysisAttemptInsight(
    val stabilityTimeline: List<Float> = emptyList(),
    val heartRateSeries: List<AnalysisHeartRateSample> = emptyList(),
    val videoDurationMs: Long? = null,
    val stabilityFocusFraction: Float? = null
)

data class AnalysisHeartRateSample(
    val timestampMs: Long,
    val bpm: Int
)

/** 챌린지 진행 상태입니다. */
enum class AnalysisChallengeStatus {
    ACTIVE,
    CLOSED
}

/** 챌린지/시도 공통 결과 상태입니다. */
enum class AnalysisChallengeResult {
    SUCCESS,
    FAIL,
    UNKNOWN
}
