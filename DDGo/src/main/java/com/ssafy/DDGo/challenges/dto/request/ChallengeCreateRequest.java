package com.ssafy.DDGo.challenges.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Schema(description = "챌린지 생성 요청")
public class ChallengeCreateRequest {

    @Schema(description = "선택한 클라이밍장 ID (필수)", example = "1")
    @NotNull(message = "암장 선택은 필수입니다.")
    private Long gymId;

    @Schema(description = "선택한 클라이밍장의 난이도 ID (필수)", example = "101")
    @NotNull(message = "난이도 선택은 필수입니다.")
    private Long gymGradeId;

    @Schema(description = "세션 시작 시각 (필수)", example = "2026-03-04T10:00:00")
    @NotNull(message = "세션 시작 시각은 필수입니다.")
    private LocalDateTime startedAt;
}
