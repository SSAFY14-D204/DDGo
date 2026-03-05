package com.ssafy.DDGo.challenges.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "홀드 좌표 항목")
public class HoldItem {

    @Schema(description = "홀드 번호", example = "1")
    @NotNull(message = "홀드 번호는 필수입니다.")
    private Integer holdNo;

    @Schema(description = "x축 시작 위치", example = "100")
    @NotNull(message = "x1은 필수입니다.")
    private Integer x1;

    @Schema(description = "x축 끝 위치", example = "150")
    @NotNull(message = "x2는 필수입니다.")
    private Integer x2;

    @Schema(description = "y축 시작 위치", example = "200")
    @NotNull(message = "y1은 필수입니다.")
    private Integer y1;

    @Schema(description = "y축 끝 위치", example = "250")
    @NotNull(message = "y2는 필수입니다.")
    private Integer y2;
}
