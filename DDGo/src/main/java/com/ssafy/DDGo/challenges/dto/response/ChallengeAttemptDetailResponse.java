package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "챌린지 시도 상세 분석 응답")
public class ChallengeAttemptDetailResponse {

    @Schema(description = "시도 ID", example = "10")
    private Long attemptId;

    @Schema(description = "시도 번호", example = "1")
    private Integer attemptNo;

    @Schema(description = "시도 결과 (SUCCESS / FAIL / UNKNOWN)", example = "FAIL")
    private String attemptResult;

    @Schema(description = "시도 지속 시간(ms)", example = "15000", nullable = true)
    private Integer durationMs;

    @Schema(description = "도달한 최대 홀드 번호", example = "8", nullable = true)
    private Integer maxHoldNo;

    @Schema(description = "중심 안정 비율(0.0~1.0)", example = "0.73", nullable = true)
    private Double centerStabilityRatio;

    @Schema(description = "안정성 회복 점수(0~100)", example = "78", nullable = true)
    private Integer stabilityRecoveryScore;

    @Schema(description = "안정 접촉 비율(0.0~1.0)", example = "0.66", nullable = true)
    private Double stableContactRatio;

    @Schema(description = "하체 주도성 점수(0~100)", example = "81", nullable = true)
    private Integer lowerBodyDriveScore;

    @Schema(description = "종합 움직임 점수(0~100)", example = "88", nullable = true)
    private Integer overallMovementScore;

    @Schema(description = "크럭스 홀드 번호", example = "5", nullable = true)
    private Integer cruxHoldNo;

    @Schema(description = "크럭스 구간 시간(ms)", example = "3200", nullable = true)
    private Integer cruxDurationMs;

    @Schema(description = "위험 이벤트 수", example = "2", nullable = true)
    private Integer dangerEventCount;

    @Schema(description = "부담 집중 부위", example = "왼팔", nullable = true)
    private String loadFocusLabel;

    @Schema(description = "실패 원인", nullable = true)
    private String failureReason;

    @Schema(description = "위험 알림", nullable = true)
    private String riskAlert;

    @Schema(description = "다음 미션", nullable = true)
    private String nextMission;

    @Schema(description = "재생 가능한 동영상 URL", nullable = true)
    private String videoUrl;

    public static ChallengeAttemptDetailResponse from(
            Attempt attempt,
            AttemptMetrics metrics,
            AttemptFeedback feedback,
            String videoUrl
    ) {
        return ChallengeAttemptDetailResponse.builder()
                .attemptId(attempt.getId())
                .attemptNo(attempt.getAttemptNo())
                .attemptResult(attempt.getAttemptResult() != null
                        ? attempt.getAttemptResult().name()
                        : null)
                .durationMs(attempt.getDurationMs())
                .maxHoldNo(attempt.getMaxHoldNo())
                .centerStabilityRatio(metrics != null ? metrics.getCenterStabilityRatio() : null)
                .stabilityRecoveryScore(metrics != null ? metrics.getStabilityRecoveryScore() : null)
                .stableContactRatio(metrics != null ? metrics.getStableContactRatio() : null)
                .lowerBodyDriveScore(metrics != null ? metrics.getLowerBodyDriveScore() : null)
                .overallMovementScore(metrics != null ? metrics.getOverallMovementScore() : null)
                .cruxHoldNo(metrics != null ? metrics.getCruxHoldNo() : null)
                .cruxDurationMs(metrics != null ? metrics.getCruxDurationMs() : null)
                .dangerEventCount(metrics != null ? metrics.getDangerEventCount() : null)
                .loadFocusLabel(metrics != null ? metrics.getLoadFocusLabel() : null)
                .failureReason(feedback != null ? feedback.getFailureReason() : null)
                .riskAlert(feedback != null ? feedback.getRiskAlert() : null)
                .nextMission(feedback != null ? feedback.getNextMission() : null)
                .videoUrl(videoUrl)
                .build();
    }
}
