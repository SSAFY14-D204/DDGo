package com.ddgo.app.feature.analysis.model

import com.ddgo.app.feature.analysis.AnalysisStrings

/**
 * 메인 분석 탭 전체를 그리기 위한 화면 상태입니다.
 *
 * 역할:
 * - 대시보드, 챌린지 상세, 시도 상세를 한 상태 모델 안에서 분기할 수 있도록 묶습니다.
 * - Composable이 원본 데이터 구조를 직접 해석하지 않고 화면 전용 모델만 소비하도록 만듭니다.
 */
data class AnalysisUiState(
    val title: String,
    val growthSummary: AnalysisGrowthSummaryUiModel,
    val challenges: List<AnalysisChallengeListItemUiModel>,
    val challengeDetail: AnalysisChallengeDetailUiModel?,
    val attemptDetail: AnalysisAttemptDetailUiModel?
) {
    companion object {
        /** 아직 표시할 분석 데이터가 없을 때 사용하는 기본 상태입니다. */
        fun empty(): AnalysisUiState {
            return AnalysisUiState(
                title = AnalysisStrings.ScreenTitle,
                growthSummary = AnalysisGrowthSummaryUiModel.empty(),
                challenges = emptyList(),
                challengeDetail = null,
                attemptDetail = null
            )
        }
    }
}

/** 전체 분석 화면 상단의 성장 요약 모델입니다. */
data class AnalysisGrowthSummaryUiModel(
    val title: String,
    val headline: String,
    val trendBadges: List<AnalysisBadgeUiModel>,
    val metrics: List<AnalysisOverviewStatUiModel>,
    val trendPoints: List<AnalysisTrendPointUiModel>,
    val stabilityScore: Float,
    val completionScore: Float,
    val averageDangerEvents: Float,
    val dangerEventProgress: Float
) {
    companion object {
        /** 데이터가 없을 때도 레이아웃이 무너지지 않도록 비어 있는 요약 모델을 제공합니다. */
        fun empty(): AnalysisGrowthSummaryUiModel {
            return AnalysisGrowthSummaryUiModel(
                title = AnalysisStrings.GrowthSection,
                headline = AnalysisStrings.EmptyGrowthHeadline,
                trendBadges = emptyList(),
                metrics = emptyList(),
                trendPoints = emptyList(),
                stabilityScore = 0f,
                completionScore = 0f,
                averageDangerEvents = 0f,
                dangerEventProgress = 0f
            )
        }
    }
}

/** 성장 그래프에 표시할 한 지점의 값입니다. */
data class AnalysisTrendPointUiModel(
    val label: String,
    val value: Float,
    val highlight: Boolean
)

/** 대시보드 챌린지 목록 카드 모델입니다. */
data class AnalysisChallengeListItemUiModel(
    val challengeId: Long,
    val title: String,
    val subtitle: String,
    val meta: String,
    val resultBadge: AnalysisBadgeUiModel,
    val isRecent: Boolean
)

/** 챌린지 상세 화면 전체를 구성하는 모델입니다. */
data class AnalysisChallengeDetailUiModel(
    val title: String,
    val subtitle: String,
    val badges: List<AnalysisBadgeUiModel>,
    val attemptFlow: List<AnalysisAttemptFlowItemUiModel>,
    val growthPoints: List<AnalysisAttemptGrowthPointUiModel>,
    val summary: AnalysisChallengeSummaryUiModel,
    val attempts: List<AnalysisAttemptListItemUiModel>
)

/** 챌린지 종합 분석 카드 모델입니다. */
data class AnalysisChallengeSummaryUiModel(
    val title: String,
    val headline: String,
    val stats: List<AnalysisOverviewStatUiModel>
)

/** 챌린지 안의 시도 목록 카드 모델입니다. */
data class AnalysisAttemptListItemUiModel(
    val attemptNo: Int,
    val title: String,
    val subtitle: String,
    val holdLabel: String,
    val stabilityLabel: String,
    val resultBadge: AnalysisBadgeUiModel
)

/** 시도 상세 화면 전체를 구성하는 모델입니다. */
data class AnalysisAttemptDetailUiModel(
    val title: String,
    val subtitle: String,
    val resultBadge: AnalysisBadgeUiModel,
    val headline: String,
    val stabilityScore: Float,
    val reachScore: Float,
    val dangerEventScore: Float,
    val cruxFocusScore: Float,
    val stabilityValueLabel: String,
    val reachValueLabel: String,
    val dangerEventValueLabel: String,
    val cruxFocusValueLabel: String,
    val metricCards: List<AnalysisOverviewStatUiModel>,
    val timelineItems: List<AnalysisTimelineItemUiModel>,
    val coachCards: List<AnalysisCoachCardUiModel>
)

/** 챌린지 내 시도 흐름을 간단히 보여주는 모델입니다. */
data class AnalysisAttemptFlowItemUiModel(
    val attemptNo: Int,
    val tone: AnalysisBadgeTone,
    val isLatest: Boolean
)

/** 챌린지 상세의 시도별 성장 그래프에 쓰는 모델입니다. */
data class AnalysisAttemptGrowthPointUiModel(
    val label: String,
    val stabilityScore: Float,
    val maxHoldNo: Int,
    val riskEventCount: Int,
    val tone: AnalysisBadgeTone
)

/** 시도 상세 타임라인 카드 하나를 표현하는 모델입니다. */
data class AnalysisTimelineItemUiModel(
    val title: String,
    val description: String,
    val tone: AnalysisBadgeTone
)

/** 시도 상세의 코칭 카드 모델입니다. */
data class AnalysisCoachCardUiModel(
    val title: String,
    val body: String,
    val tone: AnalysisBadgeTone
)

/** 배지 라벨과 색상 톤을 함께 다루기 위한 모델입니다. */
data class AnalysisBadgeUiModel(
    val label: String,
    val tone: AnalysisBadgeTone
)

/** 분석 화면에서 쓰는 공통 배지 톤입니다. */
enum class AnalysisBadgeTone {
    Accent,
    Success,
    Danger,
    Warning,
    Neutral
}

/** 작은 통계 카드에 쓰는 라벨/값 모델입니다. */
data class AnalysisOverviewStatUiModel(
    val label: String,
    val value: String
)
