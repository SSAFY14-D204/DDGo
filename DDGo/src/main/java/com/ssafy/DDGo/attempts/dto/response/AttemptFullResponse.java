package com.ssafy.DDGo.attempts.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptHeartRateSample;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.attempts.domain.AttemptResult;
import com.ssafy.DDGo.attempts.domain.AttemptStabilityPoint;
import com.ssafy.DDGo.attempts.domain.AttemptStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AttemptFullResponse(
        Long attemptId,
        Integer attemptNo,
        AttemptStatus attemptStatus,
        AttemptResult attemptResult,
        LocalDateTime createdAt,
        Integer durationMs,
        Integer maxHoldNo,
        String videoUrl,
        Integer videoDurationMs,
        MetricsData metricsData,
        FeedbacksData feedbacksData,
        List<StabilityTimelinePoint> stabilityTimeline,
        List<HeartRateSample> heartRateSeries) {

    public record MetricsData(
            Double centerStabilityRatio,
            Integer stabilityRecoveryScore,
            Double stableContactRatio,
            Integer lowerBodyDriveScore,
            Integer overallMovementScore,
            Integer cruxHoldNo,
            Integer cruxDurationMs,
            Integer dangerEventCount,
            String loadFocusLabel) {
        public static MetricsData from(AttemptMetrics metrics) {
            if (metrics == null) {
                return null;
            }
            return new MetricsData(
                    metrics.getCenterStabilityRatio(),
                    metrics.getStabilityRecoveryScore(),
                    metrics.getStableContactRatio(),
                    metrics.getLowerBodyDriveScore(),
                    metrics.getOverallMovementScore(),
                    metrics.getCruxHoldNo(),
                    metrics.getCruxDurationMs(),
                    metrics.getDangerEventCount(),
                    metrics.getLoadFocusLabel());
        }
    }

    public record FeedbacksData(
            String failureReason,
            String riskAlert,
            String nextMission) {
        public static FeedbacksData from(AttemptFeedback feedback) {
            if (feedback == null) {
                return null;
            }
            return new FeedbacksData(
                    feedback.getFailureReason(),
                    feedback.getRiskAlert(),
                    feedback.getNextMission());
        }
    }

    public record StabilityTimelinePoint(
            Long timestampMs,
            Double stabilityScore) {
        public static StabilityTimelinePoint from(AttemptStabilityPoint point) {
            return new StabilityTimelinePoint(point.getTimestampMs(), point.getStabilityScore());
        }
    }

    public record HeartRateSample(
            Long timestampMs,
            Integer bpm) {
        public static HeartRateSample from(AttemptHeartRateSample sample) {
            return new HeartRateSample(sample.getTimestampMs(), sample.getBpm());
        }
    }

    public static AttemptFullResponse from(
            Attempt attempt,
            String videoUrl,
            AttemptMetrics metrics,
            AttemptFeedback feedback,
            List<AttemptStabilityPoint> stabilityTimeline,
            List<AttemptHeartRateSample> heartRateSeries) {
        return new AttemptFullResponse(
                attempt.getId(),
                attempt.getAttemptNo(),
                attempt.getAttemptStatus(),
                attempt.getAttemptResult(),
                attempt.getCreatedAt(),
                attempt.getDurationMs(),
                attempt.getMaxHoldNo(),
                videoUrl,
                attempt.getDurationMs(),
                MetricsData.from(metrics),
                FeedbacksData.from(feedback),
                stabilityTimeline == null ? List.of() : stabilityTimeline.stream().map(StabilityTimelinePoint::from).toList(),
                heartRateSeries == null ? List.of() : heartRateSeries.stream().map(HeartRateSample::from).toList());
    }
}
