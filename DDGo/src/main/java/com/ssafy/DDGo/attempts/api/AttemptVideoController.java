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
}
