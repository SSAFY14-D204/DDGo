package com.ssafy.DDGo.community.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "커뮤니티 게시글 생성 요청")
public class CommunityPostCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 150, message = "제목은 150자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "본문은 필수입니다.")
    @Size(max = 5000, message = "본문은 5000자 이하여야 합니다.")
    private String content;

    private Long gymId;

    @Valid
    @Size(max = 3, message = "영상은 최대 3개까지 첨부할 수 있습니다.")
    private List<CommunityPostVideoItemRequest> videos = new ArrayList<>();
}
