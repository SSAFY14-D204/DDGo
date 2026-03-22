package com.ssafy.DDGo.challenges.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "홀드 좌표 항목")
public class HoldItem {

    @Schema(description = "홀드 번호", example = "1")
    @NotNull(message = "홀드 번호는 필수입니다.")
    @PositiveOrZero(message = "홀드 번호는 0 이상이어야 합니다.")
    private Integer holdNo;

    @Schema(description = "바운딩 박스 (정규화 0~1)")
    @NotNull(message = "바운딩 박스는 필수입니다.")
    @Valid
    private BoundingBox boundingBox;

    @Schema(description = "세그멘테이션 폴리곤 좌표 목록 (정규화 0~1)")
    @NotEmpty(message = "폴리곤 좌표는 비어있을 수 없습니다.")
    @Valid
    private List<PointItem> polygon;

    @Getter
    @NoArgsConstructor
    @Schema(description = "바운딩 박스 좌표")
    public static class BoundingBox {

        @Schema(description = "좌측 x 좌표 (정규화 0~1)", example = "0.12")
        @NotNull(message = "x1 좌표는 필수입니다.")
        @DecimalMin(value = "0.0", message = "x1 좌표는 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "x1 좌표는 1.0 이하여야 합니다.")
        private Float x1;

        @Schema(description = "우측 x 좌표 (정규화 0~1)", example = "0.24")
        @NotNull(message = "x2 좌표는 필수입니다.")
        @DecimalMin(value = "0.0", message = "x2 좌표는 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "x2 좌표는 1.0 이하여야 합니다.")
        private Float x2;

        @Schema(description = "상단 y 좌표 (정규화 0~1)", example = "0.31")
        @NotNull(message = "y1 좌표는 필수입니다.")
        @DecimalMin(value = "0.0", message = "y1 좌표는 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "y1 좌표는 1.0 이하여야 합니다.")
        private Float y1;

        @Schema(description = "하단 y 좌표 (정규화 0~1)", example = "0.42")
        @NotNull(message = "y2 좌표는 필수입니다.")
        @DecimalMin(value = "0.0", message = "y2 좌표는 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "y2 좌표는 1.0 이하여야 합니다.")
        private Float y2;
    }

    @Getter
    @NoArgsConstructor
    @Schema(description = "폴리곤 좌표 점")
    public static class PointItem {

        @Schema(description = "x 좌표 (정규화 0~1)", example = "0.45")
        @NotNull(message = "x 좌표는 필수입니다.")
        @DecimalMin(value = "0.0", message = "x 좌표는 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "x 좌표는 1.0 이하여야 합니다.")
        private Float x;

        @Schema(description = "y 좌표 (정규화 0~1)", example = "0.32")
        @NotNull(message = "y 좌표는 필수입니다.")
        @DecimalMin(value = "0.0", message = "y 좌표는 0.0 이상이어야 합니다.")
        @DecimalMax(value = "1.0", message = "y 좌표는 1.0 이하여야 합니다.")
        private Float y;
    }
}
