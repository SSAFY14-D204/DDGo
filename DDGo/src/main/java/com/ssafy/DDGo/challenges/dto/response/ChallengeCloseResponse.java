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

    @Schema(description = "세션 종료 시각")
    private LocalDateTime endedAt;

    public static ChallengeCloseResponse from(Challenge challenge) {
        return ChallengeCloseResponse.builder()
                .challengeId(challenge.getId())
                .challengeStatus(challenge.getChallengeStatus().name())
                .challengeResult(challenge.getChallengeResult().name())
                .endedAt(challenge.getEndedAt())
                .build();
    }
}
