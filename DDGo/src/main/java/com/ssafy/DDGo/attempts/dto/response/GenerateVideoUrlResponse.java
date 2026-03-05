package com.ssafy.DDGo.attempts.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GenerateVideoUrlResponse {

    @Schema(description = "MinIO 다이렉트 업로드를 위한 Presigned URL (15분 유효)", example = "http://localhost:9000/ddgo-videos/attempts/...")
    private String videoUrl;

    @Schema(description = "할당된 Object Key (저장 경로)", example = "attempts/1/123e4567-e89b-12d3...mp4")
    private String objectKey;
}
