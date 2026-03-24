package com.ssafy.DDGo.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "중복 확인 결과")
public class DuplicateCheckResponse {

    @Schema(description = "사용 가능 여부", example = "true")
    private boolean available;
}
