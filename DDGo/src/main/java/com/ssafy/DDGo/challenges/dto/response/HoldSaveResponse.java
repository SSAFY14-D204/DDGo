package com.ssafy.DDGo.challenges.dto.response;

import com.ssafy.DDGo.challenges.dto.request.HoldItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "홀드 좌표 저장 응답")
public class HoldSaveResponse {

    @Schema(description = "챌린지 ID", example = "1")
    private Long challengeId;

    @Schema(description = "저장된 홀드 수", example = "5")
    private int holdCount;

    @Schema(description = "저장된 홀드 목록")
    private List<HoldItem> holds;
}
