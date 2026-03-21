package com.ssafy.DDGo.community.application;

import com.ssafy.DDGo.community.dao.CommunityCommentLikeRepository;
import com.ssafy.DDGo.community.dao.CommunityCommentRepository;
import com.ssafy.DDGo.community.dao.CommunityPostRepository;
import com.ssafy.DDGo.community.domain.CommunityComment;
import com.ssafy.DDGo.community.domain.CommunityCommentLike;
import com.ssafy.DDGo.community.domain.CommunityPost;
import com.ssafy.DDGo.community.dto.request.CommunityCommentCreateRequest;
import com.ssafy.DDGo.community.dto.request.CommunityCommentUpdateRequest;
import com.ssafy.DDGo.community.dto.response.CommunityCommentResponse;
import com.ssafy.DDGo.community.dto.response.CommunityLikeResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityCommentService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentLikeRepository communityCommentLikeRepository;
    private final UserRepository userRepository;

    public List<CommunityCommentResponse> getComments(String username, Long postId) {
        User user = getUser(username);
        getPost(postId);
        List<CommunityComment> comments = communityCommentRepository.findVisibleByPostIdOrderByCreatedAtAsc(postId);
        Set<Long> likedIds = findLikedCommentIds(user.getId(), comments);
        return buildCommentTree(comments, likedIds, user.getId());
    }

    @Transactional
    public CommunityCommentResponse createComment(String username, Long postId, CommunityCommentCreateRequest request) {
        User user = getUser(username);
        CommunityPost post = getPost(postId);
        CommunityComment parent = resolveParentComment(postId, request.getParentCommentId());
        int depth = parent == null ? 0 : 1;

        CommunityComment comment = communityCommentRepository.save(CommunityComment.builder()
                .post(post)
                .user(user)
                .parentComment(parent)
                .depth(depth)
                .content(request.getContent())
                .authorNicknameSnapshot(user.getNickname())
                .build());
        post.adjustCommentCount(1);
        return toResponse(comment, false, true, new ArrayList<>());
    }

    @Transactional
    public CommunityCommentResponse updateComment(String username, Long postId, Long commentId,
            CommunityCommentUpdateRequest request) {
        User user = getUser(username);
        CommunityComment comment = getComment(commentId);
        validateSamePost(comment, postId);
        validateCommentOwner(user, comment);
        comment.updateContent(request.getContent());
        return toResponse(comment, communityCommentLikeRepository.existsByCommentIdAndUserId(commentId, user.getId()), true,
                new ArrayList<>());
    }

    @Transactional
    public void deleteComment(String username, Long postId, Long commentId) {
        User user = getUser(username);
        CommunityComment comment = getComment(commentId);
        validateSamePost(comment, postId);
        validateCommentOwner(user, comment);

        List<CommunityComment> targets = new ArrayList<>();
        targets.add(comment);
        if (comment.getDepth() == 0) {
            targets.addAll(communityCommentRepository.findAllByParentCommentIdOrderByCreatedAtAsc(commentId));
        }

        communityCommentLikeRepository.deleteAllByCommentIdIn(targets.stream().map(CommunityComment::getId).toList());
        communityCommentRepository.deleteAll(targets);
        comment.getPost().adjustCommentCount(-targets.size());
    }

    @Transactional
    public CommunityLikeResponse likeComment(String username, Long commentId) {
        User user = getUser(username);
        CommunityComment comment = getComment(commentId);
        if (!communityCommentLikeRepository.existsByCommentIdAndUserId(commentId, user.getId())) {
            communityCommentLikeRepository.save(CommunityCommentLike.builder()
                    .comment(comment)
                    .user(user)
                    .build());
            comment.adjustLikeCount(1);
        }
        return CommunityLikeResponse.builder()
                .targetId(commentId)
                .liked(true)
                .likeCount(comment.getLikeCount())
                .build();
    }

    @Transactional
    public CommunityLikeResponse unlikeComment(String username, Long commentId) {
        User user = getUser(username);
        CommunityComment comment = getComment(commentId);
        if (communityCommentLikeRepository.existsByCommentIdAndUserId(commentId, user.getId())) {
            communityCommentLikeRepository.deleteByCommentIdAndUserId(commentId, user.getId());
            comment.adjustLikeCount(-1);
        }
        return CommunityLikeResponse.builder()
                .targetId(commentId)
                .liked(false)
                .likeCount(comment.getLikeCount())
                .build();
    }

    public List<CommunityCommentResponse> buildCommentTree(List<CommunityComment> comments, Set<Long> likedCommentIds,
            Long currentUserId) {
        Map<Long, CommunityCommentResponse> roots = new LinkedHashMap<>();
        for (CommunityComment comment : comments) {
            CommunityCommentResponse response = toResponse(
                    comment,
                    likedCommentIds.contains(comment.getId()),
                    comment.getUser().getId().equals(currentUserId),
                    new ArrayList<>());
            if (comment.getParentComment() == null) {
                roots.put(comment.getId(), response);
                continue;
            }
            CommunityCommentResponse parent = roots.get(comment.getParentComment().getId());
            if (parent != null) {
                parent.getReplies().add(response);
            }
        }
        return new ArrayList<>(roots.values());
    }

    private Set<Long> findLikedCommentIds(Long userId, Collection<CommunityComment> comments) {
        if (comments.isEmpty()) {
            return Set.of();
        }
        return communityCommentLikeRepository.findLikedCommentIds(
                userId,
                comments.stream().map(CommunityComment::getId).toList());
    }

    private CommunityCommentResponse toResponse(CommunityComment comment, boolean liked, boolean mine,
            List<CommunityCommentResponse> replies) {
        return CommunityCommentResponse.builder()
                .id(comment.getId())
                .parentCommentId(comment.getParentComment() != null ? comment.getParentComment().getId() : null)
                .depth(comment.getDepth())
                .content(comment.getContent())
                .authorNickname(comment.getAuthorNicknameSnapshot())
                .createdAt(comment.getCreatedAt())
                .likeCount(comment.getLikeCount())
                .liked(liked)
                .mine(mine)
                .replies(replies)
                .build();
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private CommunityPost getPost(Long postId) {
        return communityPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND, "게시글을 찾을 수 없습니다."));
    }

    private CommunityComment getComment(Long commentId) {
        return communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMENT_NOT_FOUND, "댓글을 찾을 수 없습니다."));
    }

    private CommunityComment resolveParentComment(Long postId, Long parentCommentId) {
        if (parentCommentId == null) {
            return null;
        }
        CommunityComment parent = getComment(parentCommentId);
        validateSamePost(parent, postId);
        if (parent.getDepth() != 0 || parent.getParentComment() != null) {
            throw new CustomException(ErrorCode.INVALID_COMMENT_DEPTH, "대댓글의 대댓글은 작성할 수 없습니다.");
        }
        return parent;
    }

    private void validateCommentOwner(User user, CommunityComment comment) {
        if (!comment.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.COMMENT_ACCESS_DENIED, "해당 댓글에 대한 권한이 없습니다.");
        }
    }

    private void validateSamePost(CommunityComment comment, Long postId) {
        if (!comment.getPost().getId().equals(postId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 게시글의 댓글이 아닙니다.");
        }
    }
}
