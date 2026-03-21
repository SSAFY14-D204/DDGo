package com.ssafy.DDGo.community.api;

import com.ssafy.DDGo.community.application.CommunityPostService;
import com.ssafy.DDGo.community.dto.request.CommunityPostCreateRequest;
import com.ssafy.DDGo.community.dto.request.CommunityPostUpdateRequest;
import com.ssafy.DDGo.community.dto.response.CommunityLikeResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostDetailResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostPageResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Community", description = "커뮤니티 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/community/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @Operation(summary = "커뮤니티 게시글 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<CommunityPostDetailResponse>> createPost(
            Authentication authentication,
            @Valid @RequestBody CommunityPostCreateRequest request) {
        CommunityPostDetailResponse response = communityPostService.createPost(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("커뮤니티 게시글이 생성되었습니다.", response));
    }

    @Operation(summary = "커뮤니티 게시글 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<CommunityPostPageResponse>> getPosts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @Parameter(description = "LATEST 또는 POPULAR")
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(required = false) Long gymId) {
        CommunityPostPageResponse response = communityPostService.getPosts(
                authentication.getName(), page, size, keyword, sort, gymId);
        return ResponseEntity.ok(ApiResponse.success("커뮤니티 게시글 목록 조회가 완료되었습니다.", response));
    }

    @Operation(summary = "커뮤니티 게시글 상세 조회")
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponse>> getPostDetail(
            Authentication authentication,
            @PathVariable Long postId) {
        CommunityPostDetailResponse response = communityPostService.getPostDetail(authentication.getName(), postId);
        return ResponseEntity.ok(ApiResponse.success("커뮤니티 게시글 상세 조회가 완료되었습니다.", response));
    }

    @Operation(summary = "커뮤니티 게시글 수정")
    @PatchMapping("/{postId}")
    public ResponseEntity<ApiResponse<CommunityPostDetailResponse>> updatePost(
            Authentication authentication,
            @PathVariable Long postId,
            @Valid @RequestBody CommunityPostUpdateRequest request) {
        CommunityPostDetailResponse response = communityPostService.updatePost(authentication.getName(), postId, request);
        return ResponseEntity.ok(ApiResponse.success("커뮤니티 게시글이 수정되었습니다.", response));
    }

    @Operation(summary = "커뮤니티 게시글 삭제")
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(Authentication authentication, @PathVariable Long postId) {
        communityPostService.deletePost(authentication.getName(), postId);
        return ResponseEntity.ok(ApiResponse.success("커뮤니티 게시글이 삭제되었습니다.", null));
    }

    @Operation(summary = "게시글 좋아요")
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<CommunityLikeResponse>> likePost(
            Authentication authentication,
            @PathVariable Long postId) {
        CommunityLikeResponse response = communityPostService.likePost(authentication.getName(), postId);
        return ResponseEntity.ok(ApiResponse.success("게시글 좋아요가 반영되었습니다.", response));
    }

    @Operation(summary = "게시글 좋아요 취소")
    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<CommunityLikeResponse>> unlikePost(
            Authentication authentication,
            @PathVariable Long postId) {
        CommunityLikeResponse response = communityPostService.unlikePost(authentication.getName(), postId);
        return ResponseEntity.ok(ApiResponse.success("게시글 좋아요가 취소되었습니다.", response));
    }
}
