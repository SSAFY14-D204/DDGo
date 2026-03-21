package com.ssafy.DDGo.community.api;

import com.ssafy.DDGo.community.application.CommunityCommentService;
import com.ssafy.DDGo.community.dto.request.CommunityCommentCreateRequest;
import com.ssafy.DDGo.community.dto.request.CommunityCommentUpdateRequest;
import com.ssafy.DDGo.community.dto.response.CommunityCommentResponse;
import com.ssafy.DDGo.community.dto.response.CommunityLikeResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Community", description = "커뮤니티 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/community")
@RequiredArgsConstructor
public class CommunityCommentController {

    private final CommunityCommentService communityCommentService;

    @Operation(summary = "게시글 댓글 목록 조회")
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<List<CommunityCommentResponse>>> getComments(
            Authentication authentication,
            @PathVariable Long postId) {
        List<CommunityCommentResponse> response = communityCommentService.getComments(authentication.getName(), postId);
        return ResponseEntity.ok(ApiResponse.success("댓글 목록 조회가 완료되었습니다.", response));
    }

    @Operation(summary = "게시글 댓글 생성")
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommunityCommentResponse>> createComment(
            Authentication authentication,
            @PathVariable Long postId,
            @Valid @RequestBody CommunityCommentCreateRequest request) {
        CommunityCommentResponse response = communityCommentService.createComment(authentication.getName(), postId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("댓글이 생성되었습니다.", response));
    }

    @Operation(summary = "댓글 수정")
    @PatchMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommunityCommentResponse>> updateComment(
            Authentication authentication,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommunityCommentUpdateRequest request) {
        CommunityCommentResponse response = communityCommentService.updateComment(
                authentication.getName(), postId, commentId, request);
        return ResponseEntity.ok(ApiResponse.success("댓글이 수정되었습니다.", response));
    }

    @Operation(summary = "댓글 삭제")
    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            Authentication authentication,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        communityCommentService.deleteComment(authentication.getName(), postId, commentId);
        return ResponseEntity.ok(ApiResponse.success("댓글이 삭제되었습니다.", null));
    }

    @Operation(summary = "댓글 좋아요")
    @PostMapping("/comments/{commentId}/likes")
    public ResponseEntity<ApiResponse<CommunityLikeResponse>> likeComment(
            Authentication authentication,
            @PathVariable Long commentId) {
        CommunityLikeResponse response = communityCommentService.likeComment(authentication.getName(), commentId);
        return ResponseEntity.ok(ApiResponse.success("댓글 좋아요가 반영되었습니다.", response));
    }

    @Operation(summary = "댓글 좋아요 취소")
    @DeleteMapping("/comments/{commentId}/likes")
    public ResponseEntity<ApiResponse<CommunityLikeResponse>> unlikeComment(
            Authentication authentication,
            @PathVariable Long commentId) {
        CommunityLikeResponse response = communityCommentService.unlikeComment(authentication.getName(), commentId);
        return ResponseEntity.ok(ApiResponse.success("댓글 좋아요가 취소되었습니다.", response));
    }
}
