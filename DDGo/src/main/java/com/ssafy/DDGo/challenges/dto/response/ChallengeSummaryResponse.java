package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "챌린지 종합 분석 응답")
public class ChallengeSummaryResponse {

    @Schema(description = "시도 수", example = "4")
    private Integer attemptCount;

    @Schema(description = "평균 중심 안정 비율(0.0~1.0)", example = "0.75", nullable = true)
    private Double averageCenterStabilityRatio;

    @Schema(description = "평균 안정성 회복 점수", example = "68.5", nullable = true)
    private Double averageStabilityRecoveryScore;

    @Schema(description = "평균 안정 접촉 비율(0.0~1.0)", example = "0.61", nullable = true)
    private Double averageStableContactRatio;

    @Schema(description = "평균 하체 주도성 점수", example = "73.2", nullable = true)
    private Double averageLowerBodyDriveScore;

    @Schema(description = "평균 종합 움직임 점수", example = "76.4", nullable = true)
    private Double averageOverallMovementScore;

    @Schema(description = "가장 많이 나타난 크럭스 홀드 번호", example = "3", nullable = true)
    private Integer mostCruxHoldNo;

    @Schema(description = "최대 크럭스 구간 시간(ms)", example = "5000", nullable = true)
    private Integer maxCruxDurationMs;

    @Schema(description = "가장 자주 나타난 부담 집중 부위", example = "왼팔", nullable = true)
    private String repeatedLoadFocusLabel;

    @Schema(description = "AI 최종 코멘트", nullable = true)
    private String finalComment;

    public static ChallengeSummaryResponse from(ChallengeSummary summary) {
        return ChallengeSummaryResponse.builder()
                .averageCenterStabilityRatio(summary.getAverageCenterStabilityRatio() != null
                        ? summary.getAverageCenterStabilityRatio().doubleValue()
                        : null)
                .mostCruxHoldNo(summary.getMostCruxHoldNo())
                .maxCruxDurationMs(summary.getMaxCruxDurationMs())
                .finalComment(summary.getFinalComment())
                .build();
    }
}
