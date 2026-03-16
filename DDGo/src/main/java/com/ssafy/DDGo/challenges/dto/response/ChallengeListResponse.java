package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.Challenge;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "챌린지 목록 항목")
public class ChallengeListResponse {

    @Schema(description = "챌린지 ID", example = "1")
    private Long id;

    @Schema(description = "클라이밍장 이름", example = "더클라임 강남", nullable = true)
    private String gymName;

    @Schema(description = "문제 색상", example = "빨강")
    private String problemColor;

    @Schema(description = "난이도 레이블", example = "V3", nullable = true)
    private String gradeLabel;

    @Schema(description = "챌린지 상태 (ACTIVE / CLOSED)", example = "ACTIVE")
    private String challengeStatus;

    @Schema(description = "챌린지 결과 (SUCCESS / FAIL / UNKNOWN)", example = "UNKNOWN", nullable = true)
    private String challengeResult;

    @Schema(description = "세션 시작 시각", example = "2026-03-04T10:00:00")
    private LocalDateTime startedAt;

    @Schema(description = "세션 종료 시각", example = "2026-03-04T11:00:00", nullable = true)
    private LocalDateTime endedAt;

    @Schema(description = "생성 시각")
    private LocalDateTime createdAt;

    public static ChallengeListResponse from(Challenge challenge) {
        return ChallengeListResponse.builder()
                .id(challenge.getId())
                .gymName(challenge.getGymNameSnapshot())
                .problemColor(challenge.getProblemColorSnapshot())
                .gradeLabel(challenge.getGradeLabelSnapshot())
                .challengeStatus(challenge.getChallengeStatus().name())
                .challengeResult(challenge.getChallengeResult() != null
                        ? challenge.getChallengeResult().name()
                        : null)
                .startedAt(challenge.getStartedAt())
                .endedAt(challenge.getEndedAt())
                .createdAt(challenge.getCreatedAt())
                .build();
    }
}
