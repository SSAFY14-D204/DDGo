package com.ssafy.DDGo.challenge.dto.request;

import com.ssafy.DDGo.challenge.domain.ChallengeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "챌린지 종료 요청")
public class ChallengeCloseRequest {

    @Schema(description = "챌린지 결과 (SUCCESS / FAIL / UNKNOWN). 미입력 시 UNKNOWN으로 처리됩니다.",
            example = "SUCCESS", nullable = true,
            allowableValues = {"SUCCESS", "FAIL", "UNKNOWN"})
    private ChallengeResult challengeResult;
}
