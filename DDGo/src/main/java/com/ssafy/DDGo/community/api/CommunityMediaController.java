package com.ssafy.DDGo.community.api;

import com.ssafy.DDGo.community.application.CommunityMediaService;
import com.ssafy.DDGo.community.dto.request.CommunityVideoUploadUrlRequest;
import com.ssafy.DDGo.community.dto.response.CommunityVideoUploadUrlResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Community", description = "커뮤니티 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/community/media")
@RequiredArgsConstructor
public class CommunityMediaController {

    private final CommunityMediaService communityMediaService;

    @Operation(summary = "커뮤니티 영상 presigned URL 발급")
    @PostMapping("/video-urls")
    public ResponseEntity<ApiResponse<CommunityVideoUploadUrlResponse>> issueVideoUploadUrls(
            Authentication authentication,
            @Valid @RequestBody CommunityVideoUploadUrlRequest request) {
        CommunityVideoUploadUrlResponse response = communityMediaService.generateVideoUploadUrls(
                authentication.getName(),
                request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("커뮤니티 영상 업로드 URL 발급이 완료되었습니다.", response));
    }
}
