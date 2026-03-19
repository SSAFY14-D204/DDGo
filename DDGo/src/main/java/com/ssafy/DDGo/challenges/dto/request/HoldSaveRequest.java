package com.ssafy.DDGo.challenges.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "홀드 좌표 저장 요청 (세그멘테이션 폴리곤)")
public class HoldSaveRequest {

    @Schema(description = "홀드 목록 (각 홀드는 폴리곤 좌표 배열을 포함)")
    @NotEmpty(message = "홀드 목록은 비어있을 수 없습니다.")
    @Valid
    private List<HoldItem> holds;
}
