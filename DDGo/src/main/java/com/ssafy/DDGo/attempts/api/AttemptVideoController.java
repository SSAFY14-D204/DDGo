package com.ssafy.DDGo.attempts.api;

import com.ssafy.DDGo.attempts.application.AttemptVideoService;
import com.ssafy.DDGo.attempts.dto.request.GenerateVideoUrlRequest;
import com.ssafy.DDGo.attempts.dto.response.GenerateVideoUrlResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Attempts", description = "등반 시도(Attempt) 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/attempts/{attemptId}")
@RequiredArgsConstructor
public class AttemptVideoController {

    private final AttemptVideoService attemptVideoService;

    @Operation(summary = "영상 업로드용 Presigned URL 발급", description = "MinIO에 직접 영상을 업로드할 수 있는 15분짜리 단기 URL을 발급받습니다.")
    @PostMapping("/video-url")
    public ResponseEntity<ApiResponse<GenerateVideoUrlResponse>> generateVideoUrl(
            Authentication authentication,
            @PathVariable("attemptId") Long attemptId,
            @Validated @RequestBody GenerateVideoUrlRequest request) {

        GenerateVideoUrlResponse response = attemptVideoService.generatePresignedUrl(attemptId,
                authentication.getName(), request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Presigned URL 발급이 완료되었습니다.", response));
    }

    @Operation(summary = "영상 업로드 완료 처리", description = "클라이언트에서 Presigned URL을 통한 데이터 업로드를 마친 뒤 이 API를 호출하면 시도 상태가 분석 중(PROCESSING)으로 변경됩니다.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "application/json",
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "정상 업로드 완료 (선택적 eTag 포함)",
                            value = "{\n  \"etag\": \"d41d8cd98f00b204e9800998ecf8427e\"\n}"
                    )
            )
    )
    @PatchMapping("/video-upload-complete")
    public ResponseEntity<ApiResponse<com.ssafy.DDGo.attempts.dto.response.VideoUploadCompleteResponse>> completeVideoUpload(
            Authentication authentication,
            @PathVariable("attemptId") Long attemptId,
            @RequestBody(required = false) com.ssafy.DDGo.attempts.dto.request.VideoUploadCompleteRequest request) {
        
        com.ssafy.DDGo.attempts.dto.response.VideoUploadCompleteResponse response = 
                attemptVideoService.completeVideoUpload(attemptId, authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("영상 업로드 상태가 완료로 변경되었습니다.", response));
    }
}
