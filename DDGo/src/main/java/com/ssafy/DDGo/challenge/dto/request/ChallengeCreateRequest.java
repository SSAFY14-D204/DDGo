package com.ssafy.DDGo.challenge.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Schema(description = "챌린지 생성 요청")
public class ChallengeCreateRequest {

    @Schema(description = "클라이밍장 이름 (선택)", example = "더클라임 강남", nullable = true)
    @Size(max = 50, message = "암장 이름은 50자 이내여야 합니다.")
    private String gymName;

    @Schema(description = "문제 색상 (필수)", example = "빨강")
    @NotBlank(message = "문제 색상은 필수입니다.")
    @Size(max = 30, message = "문제 색상은 30자 이내여야 합니다.")
    private String problemColor;

    @Schema(description = "난이도 레이블 (선택)", example = "V3", nullable = true)
    @Size(max = 30, message = "난이도는 30자 이내여야 합니다.")
    private String gradeLabel;

    @Schema(description = "세션 시작 시각 (필수)", example = "2026-03-04T10:00:00")
    @NotNull(message = "세션 시작 시각은 필수입니다.")
    private LocalDateTime startedAt;
}
