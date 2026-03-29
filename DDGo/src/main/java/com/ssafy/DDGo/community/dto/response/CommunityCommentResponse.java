package com.ssafy.DDGo.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
@Schema(description = "커뮤니티 댓글 응답")
public class CommunityCommentResponse {

    private Long id;
    private Long parentCommentId;
    private int depth;
    private String content;
    private String authorNickname;
    private LocalDateTime createdAt;
    private int likeCount;
    private boolean liked;
    private boolean mine;

    @Builder.Default
    private List<CommunityCommentResponse> replies = new ArrayList<>();
}
