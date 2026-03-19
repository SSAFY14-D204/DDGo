package com.ddgo.app.feature.analysis

/**
 * 분석 화면에서 공통으로 쓰는 사용자 노출 문구 모음입니다.
 *
 * 역할:
 * - 화면 타이틀과 라벨을 한 곳에서 관리해 문구 변경 시 영향 범위를 줄입니다.
 * - 문자열 깨짐이 발생했던 analysis feature의 원본 문구를 안정적으로 다시 정의합니다.
 */
internal object AnalysisStrings {
    const val ScreenTitle = "분석"

    const val GrowthSection = "나의 성장"
    const val ChallengeListSection = "챌린지 목록"
    const val ChallengeSummarySection = "챌린지 종합 분석"
    const val AttemptsSection = "시도 목록"
    const val AttemptDetailSection = "시도 상세 분석"
    const val TimelineSection = "타임라인"
    const val CoachSection = "코칭 포인트"

    const val TotalChallengesLabel = "챌린지"
    const val TotalAttemptsLabel = "시도"
    const val AverageStabilityLabel = "평균 안정률"

    const val ChallengeAttemptCountLabel = "시도 수"
    const val ChallengeStabilityLabel = "평균 안정률"
    const val ChallengeCruxLabel = "크럭스 홀드"
    const val ChallengeDangerLabel = "위험 이벤트"

    const val AttemptDurationLabel = "소요 시간"
    const val AttemptMaxHoldLabel = "최대 홀드"
    const val AttemptStabilityLabel = "안정률"
    const val AttemptCruxTimeLabel = "크럭스 체류"

    const val CoachFailureTitle = "실패 원인"
    const val CoachRiskTitle = "리스크 신호"
    const val CoachMissionTitle = "다음 미션"

    const val StatusClosed = "종료"
    const val StatusActive = "진행 중"
    const val ResultSuccess = "완등"
    const val ResultFail = "미완등"
    const val ResultUnknown = "미정"

    const val BackToDashboard = "전체 분석으로"
    const val BackToChallenge = "챌린지로"

    const val EmptyGrowthHeadline = "아직 분석할 기록이 없어요."
    const val EmptyCoachMessage = "데이터가 쌓이면 시도 패턴과 코칭 포인트를 더 정확하게 보여드릴게요."
}
