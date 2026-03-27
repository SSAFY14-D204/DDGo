package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.Challenge;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "챌린지 종료 응답")
public class ChallengeCloseResponse {

    @Schema(description = "챌린지 ID", example = "1")
    private Long challengeId;

    @Schema(description = "챌린지 상태", example = "CLOSED")
    private String challengeStatus;

    @Schema(description = "챌린지 결과 (SUCCESS / FAIL / UNKNOWN)", example = "SUCCESS")
    private String challengeResult;

    @Schema(description = "종료 시각")
    private LocalDateTime endedAt;

    @Schema(description = "챌린지 종합 분석 응답")
    private ChallengeSummaryResponse summary;

    public static ChallengeCloseResponse from(Challenge challenge, ChallengeSummaryResponse summary) {
        return ChallengeCloseResponse.builder()
                .challengeId(challenge.getId())
                .challengeStatus(challenge.getChallengeStatus() != null ? challenge.getChallengeStatus().name() : null)
                .challengeResult(challenge.getChallengeResult() != null ? challenge.getChallengeResult().name() : null)
                .endedAt(challenge.getEndedAt())
                .summary(summary)
                .build();
    }
}
