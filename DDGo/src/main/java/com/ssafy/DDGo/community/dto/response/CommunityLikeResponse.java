package com.ssafy.DDGo.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "좋아요 토글 응답")
public class CommunityLikeResponse {

    private Long targetId;
    private boolean liked;
    private int likeCount;
}
