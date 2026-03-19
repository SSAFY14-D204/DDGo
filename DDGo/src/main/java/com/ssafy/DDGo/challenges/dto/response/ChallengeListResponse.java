package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.gyms.domain.ClimbingBrand;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
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

    @Schema(description = "암장 ID", example = "12")
    private Long gymId;

    @Schema(description = "암장 난이도 ID", example = "101")
    private Long gymGradeId;

    @Schema(description = "암장 이름", example = "더클라임 강남", nullable = true)
    private String gymName;

    @Schema(description = "문제 난이도 색상명", example = "보라")
    private String problemColor;

    @Schema(description = "난이도 라벨", example = "V3", nullable = true)
    private String gradeLabel;

    @Schema(description = "난이도 색상 HEX", example = "#876FFF", nullable = true)
    private String colorHex;

    @Schema(description = "챌린지 상태 (ACTIVE / CLOSED)", example = "ACTIVE")
    private String challengeStatus;

    @Schema(description = "챌린지 결과 (SUCCESS / FAIL / UNKNOWN)", example = "UNKNOWN", nullable = true)
    private String challengeResult;

    @Schema(description = "세션 시작 시각", example = "2026-03-04T10:00:00", nullable = true)
    private LocalDateTime startedAt;

    @Schema(description = "세션 종료 시각", example = "2026-03-04T11:00:00", nullable = true)
    private LocalDateTime endedAt;

    @Schema(description = "생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "암장 로고 bucket", nullable = true)
    private String gymLogoBucket;

    @Schema(description = "암장 로고 object key", nullable = true)
    private String gymLogoObjectKey;

    @Schema(description = "브랜드 로고 bucket", nullable = true)
    private String brandLogoBucket;

    @Schema(description = "브랜드 로고 object key", nullable = true)
    private String brandLogoObjectKey;

    public static ChallengeListResponse from(Challenge challenge) {
        ClimbingGym gym = challenge.getGym();
        ClimbingGymGrade gymGrade = challenge.getGymGrade();
        ClimbingBrand brand = gym.getBrand();

        return ChallengeListResponse.builder()
                .id(challenge.getId())
                .gymId(gym.getId())
                .gymGradeId(gymGrade.getId())
                .gymName(challenge.getGymNameSnapshot())
                .problemColor(challenge.getProblemColorSnapshot())
                .gradeLabel(challenge.getGradeLabelSnapshot())
                .colorHex(gymGrade.getColorHex())
                .challengeStatus(challenge.getChallengeStatus().name())
                .challengeResult(challenge.getChallengeResult() != null
                        ? challenge.getChallengeResult().name()
                        : null)
                .startedAt(challenge.getStartedAt())
                .endedAt(challenge.getEndedAt())
                .createdAt(challenge.getCreatedAt())
                .gymLogoBucket(gym.getLogoBucket())
                .gymLogoObjectKey(gym.getLogoObjectKey())
                .brandLogoBucket(brand != null ? brand.getLogoBucket() : null)
                .brandLogoObjectKey(brand != null ? brand.getLogoObjectKey() : null)
                .build();
    }
}
