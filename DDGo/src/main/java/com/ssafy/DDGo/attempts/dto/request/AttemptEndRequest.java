package com.ssafy.DDGo.attempts.dto.request;

import com.ssafy.DDGo.attempts.domain.AttemptResult;

public record AttemptEndRequest(
                BaseData baseData,
                MetricsData metricsData,
                FeedbacksData feedbacksData) {
        public record BaseData(
                        AttemptResult attemptResult,
                        Integer durationMs,
                        Integer maxHoldNo) {
        }

        public record MetricsData(
                        Double centerStabilityRatio,
                        Integer cruxHoldNo,
                        Integer cruxDurationMs,
                        Integer dangerEventCount) {
        }

        public record FeedbacksData(
                        String failureReason,
                        String riskAlert,
                        String nextMission) {
        }
}
