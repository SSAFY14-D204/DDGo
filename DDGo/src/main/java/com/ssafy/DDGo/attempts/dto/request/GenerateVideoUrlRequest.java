package com.ssafy.DDGo.attempts.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class GenerateVideoUrlRequest {

    @NotBlank(message = "원본 파일명은 필수입니다.")
    @Schema(description = "원본 파일명", example = "climbing_attempt.mp4")
    private String originalFileName;

    @NotBlank(message = "컨텐츠 타입은 필수입니다.")
    @Schema(description = "파일 MIME 타입", example = "video/mp4")
    private String contentType;

    @NotNull(message = "파일 크기는 필수입니다.")
    @Schema(description = "파일 크기(바이트 단위)", example = "15000000")
    private Long fileSize;
}
