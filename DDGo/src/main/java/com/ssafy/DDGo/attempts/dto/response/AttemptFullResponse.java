package com.ssafy.DDGo.attempts.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.attempts.domain.AttemptResult;
import com.ssafy.DDGo.attempts.domain.AttemptStatus;

import java.time.LocalDateTime;

public record AttemptFullResponse(
        Long attemptId,
        Integer attemptNo,
        AttemptStatus attemptStatus,
        AttemptResult attemptResult,
        LocalDateTime createdAt,
        Integer durationMs,
        Integer maxHoldNo,
        String videoUrl,
        MetricsData metricsData,
        FeedbacksData feedbacksData) {

    public record MetricsData(
            Double centerStabilityRatio,
            Integer cruxHoldNo,
            Integer cruxDurationMs,
            Integer dangerEventCount) {
        public static MetricsData from(AttemptMetrics metrics) {
            if (metrics == null)
                return null;
            return new MetricsData(
                    metrics.getCenterStabilityRatio(),
                    metrics.getCruxHoldNo(),
                    metrics.getCruxDurationMs(),
                    metrics.getDangerEventCount());
        }
    }

    public record FeedbacksData(
            String failureReason,
            String riskAlert,
            String nextMission) {
        public static FeedbacksData from(AttemptFeedback feedback) {
            if (feedback == null)
                return null;
            return new FeedbacksData(
                    feedback.getFailureReason(),
                    feedback.getRiskAlert(),
                    feedback.getNextMission());
        }
    }

    public static AttemptFullResponse from(Attempt attempt, String videoUrl, AttemptMetrics metrics,
            AttemptFeedback feedback) {
        return new AttemptFullResponse(
                attempt.getId(),
                attempt.getAttemptNo(),
                attempt.getAttemptStatus(),
                attempt.getAttemptResult(),
                attempt.getCreatedAt(),
                attempt.getDurationMs(),
                attempt.getMaxHoldNo(),
                videoUrl,
                MetricsData.from(metrics),
                FeedbacksData.from(feedback));
    }
}
