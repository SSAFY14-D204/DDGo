package com.ssafy.DDGo.community.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
@Schema(description = "커뮤니티 게시글 페이지 응답")
public class CommunityPostPageResponse {

    private List<CommunityPostSummaryResponse> items;
    private int page;
    private int size;
    private int totalPages;
    private long totalElements;
    private boolean hasNext;

    public static CommunityPostPageResponse from(Page<?> pageResult, List<CommunityPostSummaryResponse> items) {
        return CommunityPostPageResponse.builder()
                .items(items)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalPages(pageResult.getTotalPages())
                .totalElements(pageResult.getTotalElements())
                .hasNext(pageResult.hasNext())
                .build();
    }
}
