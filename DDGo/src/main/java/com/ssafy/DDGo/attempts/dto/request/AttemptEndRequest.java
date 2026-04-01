package com.ssafy.DDGo.attempts.dto.request;

import com.ssafy.DDGo.attempts.domain.AttemptResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AttemptEndRequest(
        @Valid BaseData baseData,
        @Valid MetricsData metricsData,
        @Valid FeedbacksData feedbacksData,
        @Valid List<StabilityTimelinePoint> stabilityTimeline,
        @Valid List<HeartRateSample> heartRateSeries) {

    public record BaseData(
            AttemptResult attemptResult,
            @PositiveOrZero(message = "시도 지속 시간은 0 또는 양수여야 합니다.")
            Integer durationMs,
            @PositiveOrZero(message = "최대 홀드 번호는 0 또는 양수여야 합니다.")
            Integer maxHoldNo) {
    }

    public record MetricsData(
            @DecimalMin(value = "0.0", message = "중심 안정 비율은 0.0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "중심 안정 비율은 1.0 이하여야 합니다.")
            Double centerStabilityRatio,

            @PositiveOrZero(message = "안정성 회복 점수는 0 또는 양수여야 합니다.")
            Integer stabilityRecoveryScore,

            @DecimalMin(value = "0.0", message = "안정 접촉 비율은 0.0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "안정 접촉 비율은 1.0 이하여야 합니다.")
            Double stableContactRatio,

            @PositiveOrZero(message = "하체 주도 점수는 0 또는 양수여야 합니다.")
            Integer lowerBodyDriveScore,

            @PositiveOrZero(message = "전체 움직임 점수는 0 또는 양수여야 합니다.")
            Integer overallMovementScore,

            @PositiveOrZero(message = "크럭스 홀드 번호는 0 또는 양수여야 합니다.")
            Integer cruxHoldNo,

            @PositiveOrZero(message = "크럭스 구간 시간은 0 또는 양수여야 합니다.")
            Integer cruxDurationMs,

            @PositiveOrZero(message = "위험 이벤트 수는 0 또는 양수여야 합니다.")
            Integer dangerEventCount,

            @Size(max = 100, message = "부하 집중 라벨은 100자를 초과할 수 없습니다.")
            String loadFocusLabel) {
    }

    public record FeedbacksData(
            @Size(max = 200, message = "실패 원인은 200자를 초과할 수 없습니다.")
            String failureReason,
            @Size(max = 200, message = "위험 경고는 200자를 초과할 수 없습니다.")
            String riskAlert,
            @Size(max = 200, message = "다음 미션은 200자를 초과할 수 없습니다.")
            String nextMission) {
    }

    public record StabilityTimelinePoint(
            @PositiveOrZero(message = "안정성 타임라인 시간은 0 또는 양수여야 합니다.")
            Long timestampMs,
            @DecimalMin(value = "0.0", message = "안정성 점수는 0.0 이상이어야 합니다.")
            @DecimalMax(value = "1.0", message = "안정성 점수는 1.0 이하여야 합니다.")
            Double stabilityScore) {
    }

    public record HeartRateSample(
            @PositiveOrZero(message = "심박 샘플 시간은 0 또는 양수여야 합니다.")
            Long timestampMs,
            @PositiveOrZero(message = "심박수는 0 또는 양수여야 합니다.")
            Integer bpm) {
    }
}
