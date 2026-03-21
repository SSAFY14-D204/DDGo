package com.ssafy.DDGo.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "커뮤니티 게시글 첨부 영상 정보")
public class CommunityPostVideoItemRequest {

    @NotBlank(message = "영상 object key는 필수입니다.")
    private String objectKey;

    @NotBlank(message = "원본 파일명은 필수입니다.")
    private String originalFileName;

    @NotBlank(message = "content type은 필수입니다.")
    private String contentType;

    @NotNull(message = "파일 크기는 필수입니다.")
    @Positive(message = "파일 크기는 0보다 커야 합니다.")
    private Long fileSize;

    @NotNull(message = "영상 길이는 필수입니다.")
    @Positive(message = "영상 길이는 0보다 커야 합니다.")
    private Long durationMs;

    @NotNull(message = "정렬 순서는 필수입니다.")
    @Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
    @Max(value = 2, message = "정렬 순서는 0~2까지만 허용됩니다.")
    private Integer sortOrder;
}
