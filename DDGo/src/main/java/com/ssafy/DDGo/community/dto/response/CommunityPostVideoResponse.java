package com.ssafy.DDGo.community.dto.response;

import com.ssafy.DDGo.community.domain.CommunityPostVideo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "커뮤니티 게시글 영상 응답")
public class CommunityPostVideoResponse {

    private Long id;
    private String objectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private Long durationMs;
    private Integer sortOrder;
    private String playbackUrl;

    public static CommunityPostVideoResponse from(CommunityPostVideo video, String playbackUrl) {
        return CommunityPostVideoResponse.builder()
                .id(video.getId())
                .objectKey(video.getObjectKey())
                .originalFileName(video.getOriginalFileName())
                .contentType(video.getContentType())
                .fileSize(video.getFileSize())
                .durationMs(video.getDurationMs())
                .sortOrder(video.getSortOrder())
                .playbackUrl(playbackUrl)
                .build();
    }
}
