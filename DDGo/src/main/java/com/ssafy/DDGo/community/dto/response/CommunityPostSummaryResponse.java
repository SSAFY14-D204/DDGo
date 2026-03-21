package com.ssafy.DDGo.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "커뮤니티 게시글 목록 응답")
public class CommunityPostSummaryResponse {

    private Long id;
    private String title;
    private String excerpt;
    private Long gymId;
    private String gymName;
    private String authorNickname;
    private LocalDateTime createdAt;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private int videoCount;
    private boolean liked;
    private boolean mine;
}
