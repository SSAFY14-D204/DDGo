package com.ddgo.app.domain.mock

import com.ddgo.app.domain.model.AnalysisAttemptSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeResult
import com.ddgo.app.domain.model.AnalysisChallengeSnapshot
import com.ddgo.app.domain.model.AnalysisChallengeStatus
import java.time.LocalDateTime

/**
 * 분석 기능 목업 단계에서 사용하는 고정 샘플 데이터입니다.
 *
 * 역할:
 * - 아직 API 연동 전인 분석 화면이 실제 서비스 흐름처럼 보이도록 샘플 데이터를 제공합니다.
 * - Repository, Preview, UI 테스트가 같은 원본 데이터를 바라보도록 해 중복 정의를 줄입니다.
 */
object AnalysisMockFixtures {

    val challengeSnapshots: List<AnalysisChallengeSnapshot> = listOf(
        AnalysisChallengeSnapshot(
            id = 104L,
            gymName = "더클라임 강남",
            problemColor = "빨강",
            gradeLabel = "V3",
            challengeStatus = AnalysisChallengeStatus.CLOSED,
            challengeResult = AnalysisChallengeResult.SUCCESS,
            startedAt = LocalDateTime.of(2026, 3, 16, 19, 24),
            endedAt = LocalDateTime.of(2026, 3, 16, 20, 2),
            finalComment = "후반부로 갈수록 중심 흔들림이 줄었고, 크럭스 구간에서 힘 배분이 안정적으로 유지됐어요.",
            attempts = listOf(
                AnalysisAttemptSnapshot(
                    attemptId = 4201L,
                    attemptNo = 1,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    durationMs = 28_000L,
                    maxHoldNo = 8,
                    centerStabilityRatio = 0.67f,
                    cruxHoldNo = 6,
                    cruxDurationMs = 3_400L,
                    dangerEventCount = 3,
                    failureReason = "중단 구간에서 오른손 이동이 늦어지면서 리듬이 끊겼어요.",
                    riskAlert = "상체가 먼저 열리면서 발이 밀리는 장면이 반복됐어요.",
                    nextMission = "6홀드 전환 구간에서 시선과 발 순서를 먼저 고정해 보세요."
                ),
                AnalysisAttemptSnapshot(
                    attemptId = 4202L,
                    attemptNo = 2,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    durationMs = 36_000L,
                    maxHoldNo = 10,
                    centerStabilityRatio = 0.74f,
                    cruxHoldNo = 9,
                    cruxDurationMs = 2_800L,
                    dangerEventCount = 2,
                    failureReason = "후반부 발 재배치에서 템포가 느려지며 손 힘이 먼저 빠졌어요.",
                    riskAlert = "왼발 체중이 짧게 풀리며 중심이 한 번 크게 흔들렸어요.",
                    nextMission = "9홀드 진입 전 짧은 정지 자세를 만들고 다시 밀어 올려 보세요."
                ),
                AnalysisAttemptSnapshot(
                    attemptId = 4203L,
                    attemptNo = 3,
                    attemptResult = AnalysisChallengeResult.SUCCESS,
                    durationMs = 43_000L,
                    maxHoldNo = 12,
                    centerStabilityRatio = 0.83f,
                    cruxHoldNo = 10,
                    cruxDurationMs = 1_950L,
                    dangerEventCount = 1,
                    failureReason = "완등한 시도예요.",
                    riskAlert = "위험 신호는 거의 없었고, 상체 흔들림도 작았어요.",
                    nextMission = "같은 리듬을 유지한 채 다음 난이도에서도 초반 템포를 가져가 보세요."
                )
            )
        ),
        AnalysisChallengeSnapshot(
            id = 105L,
            gymName = "서울숲 클라이밍",
            problemColor = "파랑",
            gradeLabel = "V5",
            challengeStatus = AnalysisChallengeStatus.CLOSED,
            challengeResult = AnalysisChallengeResult.FAIL,
            startedAt = LocalDateTime.of(2026, 3, 12, 18, 48),
            endedAt = LocalDateTime.of(2026, 3, 12, 19, 35),
            finalComment = "최대 도달 홀드는 좋아졌지만, 크럭스 직전 중심 유지가 아직 불안정해요.",
            attempts = listOf(
                AnalysisAttemptSnapshot(
                    attemptId = 4211L,
                    attemptNo = 1,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    durationMs = 24_000L,
                    maxHoldNo = 7,
                    centerStabilityRatio = 0.59f,
                    cruxHoldNo = 5,
                    cruxDurationMs = 4_900L,
                    dangerEventCount = 4,
                    failureReason = "시작 구간에서 당기는 힘이 먼저 들어가며 하체가 따라오지 못했어요.",
                    riskAlert = "발이 자주 떨어져 위험 이벤트가 많이 발생했어요.",
                    nextMission = "5홀드 전환 전에 골반 방향을 먼저 정리해 보세요."
                ),
                AnalysisAttemptSnapshot(
                    attemptId = 4212L,
                    attemptNo = 2,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    durationMs = 31_000L,
                    maxHoldNo = 9,
                    centerStabilityRatio = 0.64f,
                    cruxHoldNo = 8,
                    cruxDurationMs = 4_100L,
                    dangerEventCount = 3,
                    failureReason = "크럭스 진입은 좋아졌지만 마지막 발 밀기에서 힘이 풀렸어요.",
                    riskAlert = "상체가 벽에서 멀어지는 순간이 반복돼요.",
                    nextMission = "8홀드 진입에서 팔로 버티지 말고 발로 밀어 올리는 감각을 키워 보세요."
                ),
                AnalysisAttemptSnapshot(
                    attemptId = 4213L,
                    attemptNo = 3,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    durationMs = 35_000L,
                    maxHoldNo = 11,
                    centerStabilityRatio = 0.71f,
                    cruxHoldNo = 10,
                    cruxDurationMs = 3_200L,
                    dangerEventCount = 2,
                    failureReason = "최종 구간까지는 도달했지만 손 교체 타이밍이 한 박자 늦었어요.",
                    riskAlert = "중심은 나아졌지만 마무리 구간에서 하체 지지가 더 필요해요.",
                    nextMission = "10홀드 이후 손 교체 전에 짧게 멈추는 루틴을 만들어 보세요."
                )
            )
        ),
        AnalysisChallengeSnapshot(
            id = 106L,
            gymName = "노보클라이밍",
            problemColor = "노랑",
            gradeLabel = "V2",
            challengeStatus = AnalysisChallengeStatus.CLOSED,
            challengeResult = AnalysisChallengeResult.SUCCESS,
            startedAt = LocalDateTime.of(2026, 3, 9, 14, 10),
            endedAt = LocalDateTime.of(2026, 3, 9, 14, 32),
            finalComment = "짧은 문제였지만 중심 이동이 깔끔했고, 위험 이벤트 없이 마무리한 시도였어요.",
            attempts = listOf(
                AnalysisAttemptSnapshot(
                    attemptId = 4221L,
                    attemptNo = 1,
                    attemptResult = AnalysisChallengeResult.FAIL,
                    durationMs = 19_000L,
                    maxHoldNo = 6,
                    centerStabilityRatio = 0.72f,
                    cruxHoldNo = 4,
                    cruxDurationMs = 2_400L,
                    dangerEventCount = 2,
                    failureReason = "발 위치를 다시 잡는 동안 리듬이 끊겼어요.",
                    riskAlert = "좌우 이동이 커지며 중심이 짧게 흔들렸어요.",
                    nextMission = "짧은 문제일수록 초반 리듬을 더 빠르게 가져가 보세요."
                ),
                AnalysisAttemptSnapshot(
                    attemptId = 4222L,
                    attemptNo = 2,
                    attemptResult = AnalysisChallengeResult.SUCCESS,
                    durationMs = 23_000L,
                    maxHoldNo = 9,
                    centerStabilityRatio = 0.88f,
                    cruxHoldNo = 7,
                    cruxDurationMs = 1_600L,
                    dangerEventCount = 0,
                    failureReason = "완등한 시도예요.",
                    riskAlert = "위험 이벤트 없이 안정적으로 마무리했어요.",
                    nextMission = "다음 난이도에서도 같은 리듬으로 발-손 전환을 이어가 보세요."
                )
            )
        )
    )
}
