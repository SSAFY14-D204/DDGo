package com.ssafy.DDGo.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "커뮤니티 영상 presigned URL 발급 요청")
public class CommunityVideoUploadUrlRequest {

    @Valid
    @NotNull(message = "영상 목록은 필수입니다.")
    @Size(min = 1, max = 3, message = "영상은 1개 이상 3개 이하로 요청해야 합니다.")
    private List<VideoUploadRequestItem> videos = new ArrayList<>();

    @Getter
    @NoArgsConstructor
    public static class VideoUploadRequestItem {

        @NotBlank(message = "원본 파일명은 필수입니다.")
        private String originalFileName;

        @NotBlank(message = "content type은 필수입니다.")
        private String contentType;

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        private Long fileSize;
    }
}
