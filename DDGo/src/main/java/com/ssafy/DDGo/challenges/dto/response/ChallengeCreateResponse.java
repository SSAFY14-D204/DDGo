package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.Challenge;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChallengeCreateResponse {

    private Long id;
    private String gymName;
    private String problemColor;
    private String gradeLabel;
    private String challengeStatus;
    private LocalDateTime startedAt;
    private LocalDateTime createdAt;

    public static ChallengeCreateResponse from(Challenge challenge) {
        return ChallengeCreateResponse.builder()
                .id(challenge.getId())
                .gymName(challenge.getGymName())
                .problemColor(challenge.getProblemColor())
                .gradeLabel(challenge.getGradeLabel())
                .challengeStatus(challenge.getChallengeStatus().name())
                .startedAt(challenge.getStartedAt())
                .createdAt(challenge.getCreatedAt())
                .build();
    }
}
