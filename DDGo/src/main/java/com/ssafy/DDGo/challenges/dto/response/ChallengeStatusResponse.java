package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.Challenge;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "챌린지 상태 응답")
public class ChallengeStatusResponse {

    @Schema(description = "챌린지 ID", example = "1")
    private Long challengeId;

    @Schema(description = "챌린지 상태 (ACTIVE / CLOSED)", example = "ACTIVE")
    private String challengeStatus;

    public static ChallengeStatusResponse from(Challenge challenge) {
        return ChallengeStatusResponse.builder()
                .challengeId(challenge.getId())
                .challengeStatus(challenge.getChallengeStatus().name())
                .build();
    }
}
