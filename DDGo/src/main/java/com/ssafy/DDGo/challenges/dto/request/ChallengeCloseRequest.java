package com.ssafy.DDGo.challenges.dto.request;

import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "챌린지 종료 요청")
public class ChallengeCloseRequest {

    @Schema(description = "챌린지 결과 (SUCCESS / FAIL / UNKNOWN). 미입력 시 UNKNOWN으로 처리됩니다.", example = "SUCCESS", nullable = true, allowableValues = {
            "SUCCESS", "FAIL", "UNKNOWN" })
    private ChallengeResult challengeResult;

    @Valid
    @Schema(description = "챌린지 종합 분석 요약 데이터. 선택적으로 제공할 수 있습니다.", nullable = true)
    private ChallengeCloseSummaryRequest summary;

    @Getter
    @NoArgsConstructor
    @Schema(description = "챌린지 종료 시 전달되는 종합 분석 요약 정보")
    public static class ChallengeCloseSummaryRequest {
        @DecimalMin(value = "0.0", message = "평균 중심 안정 비율은 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "평균 중심 안정 비율은 1.0 이하여야 합니다.")
        @Schema(description = "평균 중심 안정 비율 (0.0 ~ 1.0)", example = "0.72", nullable = true)
        private Double averageCenterStabilityRatio;

        @PositiveOrZero(message = "크럭스 홀드 번호는 0 또는 양수여야 합니다.")
        @Schema(description = "가장 많이 등장한 크럭스 홀드 번호", example = "7", nullable = true)
        private Integer mostCruxHoldNo;

        @PositiveOrZero(message = "크럭스 구간 시간은 0 또는 양수여야 합니다.")
        @Schema(description = "가장 긴 크럭스 구간 시간(ms)", example = "2860", nullable = true)
        private Integer maxCruxDurationMs;

        @Size(max = 500, message = "종합 코멘트는 500자를 초과할 수 없습니다.")
        @Schema(description = "AI 종합 코멘트", example = "총 4번 시도 중 1번 완등에 성공했고, 평균 7/10 홀드까지 도달했습니다.", nullable = true)
        private String finalComment;
    }
}
