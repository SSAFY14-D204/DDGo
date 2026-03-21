package com.ssafy.DDGo.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "커뮤니티 게시글 상세 응답")
public class CommunityPostDetailResponse {

    private Long id;
    private String title;
    private String content;
    private Long gymId;
    private String gymName;
    private String authorNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private int videoCount;
    private boolean liked;
    private boolean mine;
    private List<CommunityPostVideoResponse> videos;
    private List<CommunityCommentResponse> comments;
}
