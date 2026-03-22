package com.ssafy.DDGo.attempts.dto.request;

import com.ssafy.DDGo.attempts.domain.AttemptResult;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AttemptEndRequest(
                @Valid BaseData baseData,
                @Valid MetricsData metricsData,
                @Valid FeedbacksData feedbacksData) {
        public record BaseData(
                        AttemptResult attemptResult,
                        @PositiveOrZero(message = "소요 시간은 0 또는 양수여야 합니다.") Integer durationMs,
                        @PositiveOrZero(message = "최대 홀드 번호는 0 또는 양수여야 합니다.") Integer maxHoldNo) {
        }

        public record MetricsData(
                        @DecimalMin(value = "0.0", message = "안정 비율은 0.0 이상이어야 합니다.") @DecimalMax(value = "1.0", message = "안정 비율은 1.0 이하여야 합니다.") Double centerStabilityRatio,
                        @PositiveOrZero(message = "크럭스 홀드 번호는 0 또는 양수여야 합니다.") Integer cruxHoldNo,
                        @PositiveOrZero(message = "크럭스 구간 시간은 0 또는 양수여야 합니다.") Integer cruxDurationMs,
                        @PositiveOrZero(message = "위험 이벤트 수는 0 또는 양수여야 합니다.") Integer dangerEventCount) {
        }

        public record FeedbacksData(
                        @Size(max = 200, message = "실패 사유는 200자를 초과할 수 없습니다.") String failureReason,
                        @Size(max = 200, message = "위험 알림은 200자를 초과할 수 없습니다.") String riskAlert,
                        @Size(max = 200, message = "다음 미션은 200자를 초과할 수 없습니다.") String nextMission) {
        }
}
