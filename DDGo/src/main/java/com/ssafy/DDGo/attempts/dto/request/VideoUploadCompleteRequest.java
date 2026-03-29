package com.ssafy.DDGo.attempts.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "영상 업로드 완료 요청 (Presigned URL을 통한 업로드 성공 후 호출)")
public class VideoUploadCompleteRequest {

    @Schema(description = "클라이언트에서 S3/MinIO에 업로드 후 응답받은 ETag 헤더 값 (옵션)", example = "\"d41d8cd98f00b204e9800998ecf8427e\"", nullable = true)
    private String etag;
}
