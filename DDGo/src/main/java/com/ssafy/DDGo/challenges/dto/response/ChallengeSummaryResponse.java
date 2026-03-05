package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "챌린지 종합 리포트")
public class ChallengeSummaryResponse {

    @Schema(description = "평균 중심 안정 비율", example = "0.75", nullable = true)
    private BigDecimal averageCenterStabilityRatio;

    @Schema(description = "가장 많이 등장한 크럭스 홀드 번호", example = "3", nullable = true)
    private Integer mostCruxHoldNo;

    @Schema(description = "가장 긴 크럭스 구간 시간(ms)", example = "5000", nullable = true)
    private Integer maxCruxDurationMs;

    @Schema(description = "AI 종합 코멘트 (추후 생성 예정)", nullable = true)
    private String finalComment;

    public static ChallengeSummaryResponse from(ChallengeSummary summary) {
        return ChallengeSummaryResponse.builder()
                .averageCenterStabilityRatio(summary.getAverageCenterStabilityRatio())
                .mostCruxHoldNo(summary.getMostCruxHoldNo())
                .maxCruxDurationMs(summary.getMaxCruxDurationMs())
                .finalComment(summary.getFinalComment())
                .build();
    }
}
