package com.ssafy.DDGo.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "커뮤니티 영상 presigned URL 발급 응답")
public class CommunityVideoUploadUrlResponse {

    private List<VideoUploadTicket> videos;

    @Getter
    @Builder
    public static class VideoUploadTicket {
        private String originalFileName;
        private String objectKey;
        private String uploadUrl;
    }
}
