package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "챌린지 시도별 상세 분석 데이터")
public class ChallengeAttemptDetailResponse {

    @Schema(description = "시도 번호", example = "1")
    private Integer attemptNo;

    @Schema(description = "시도 결과 (SUCCESS / FAIL / UNKNOWN)", example = "FAIL")
    private String attemptResult;

    @Schema(description = "시도 총 시간(ms)", example = "15000", nullable = true)
    private Integer durationMs;

    @Schema(description = "도달한 최고 홀드 번호", example = "8", nullable = true)
    private Integer maxHoldNo;

    @Schema(description = "중심 안정 비율 (그래프용)", example = "0.73", nullable = true)
    private Double centerStabilityRatio;

    @Schema(description = "크럭스 홀드 번호", example = "5", nullable = true)
    private Integer cruxHoldNo;

    @Schema(description = "크럭스 구간 시간(ms)", example = "3200", nullable = true)
    private Integer cruxDurationMs;

    @Schema(description = "위험 이벤트 횟수", example = "2", nullable = true)
    private Integer dangerEventCount;

    public static ChallengeAttemptDetailResponse from(AttemptMetrics metrics) {
        return ChallengeAttemptDetailResponse.builder()
                .attemptNo(metrics.getAttempt().getAttemptNo())
                .attemptResult(metrics.getAttempt().getAttemptResult() != null
                        ? metrics.getAttempt().getAttemptResult().name()
                        : null)
                .durationMs(metrics.getAttempt().getDurationMs())
                .maxHoldNo(metrics.getAttempt().getMaxHoldNo())
                .centerStabilityRatio(metrics.getCenterStabilityRatio())
                .cruxHoldNo(metrics.getCruxHoldNo())
                .cruxDurationMs(metrics.getCruxDurationMs())
                .dangerEventCount(metrics.getDangerEventCount())
                .build();
    }
}
