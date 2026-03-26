package com.ssafy.DDGo.community.application;

import com.ssafy.DDGo.community.dao.CommunityCommentLikeRepository;
import com.ssafy.DDGo.community.dao.CommunityCommentRepository;
import com.ssafy.DDGo.community.dao.CommunityPostLikeRepository;
import com.ssafy.DDGo.community.dao.CommunityPostRepository;
import com.ssafy.DDGo.community.dao.CommunityPostVideoRepository;
import com.ssafy.DDGo.community.domain.CommunityPost;
import com.ssafy.DDGo.community.domain.CommunityPostLike;
import com.ssafy.DDGo.community.domain.CommunityPostSort;
import com.ssafy.DDGo.community.domain.CommunityPostVideo;
import com.ssafy.DDGo.community.dto.request.CommunityPostCreateRequest;
import com.ssafy.DDGo.community.dto.request.CommunityPostUpdateRequest;
import com.ssafy.DDGo.community.dto.request.CommunityPostVideoItemRequest;
import com.ssafy.DDGo.community.dto.response.CommunityCommentResponse;
import com.ssafy.DDGo.community.dto.response.CommunityLikeResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostDetailResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostPageResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostSummaryResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostVideoResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.gyms.dao.ClimbingGymRepository;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityPostService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostVideoRepository communityPostVideoRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentLikeRepository communityCommentLikeRepository;
    private final UserRepository userRepository;
    private final ClimbingGymRepository climbingGymRepository;
    private final CommunityMediaService communityMediaService;
    private final CommunityCommentService communityCommentService;

    @Transactional
    public CommunityPostDetailResponse createPost(String username, CommunityPostCreateRequest request) {
        User user = getUser(username);
        validateVideos(user.getId(), request.getVideos());
        ClimbingGym gym = resolveGym(request.getGymId());

        CommunityPost post = communityPostRepository.save(CommunityPost.builder()
                .user(user)
                .gym(gym)
                .title(request.getTitle())
                .content(request.getContent())
                .authorNicknameSnapshot(user.getNickname())
                .build());
        saveVideos(post, request.getVideos());
        return buildDetailResponse(post, user);
    }

    public CommunityPostPageResponse getPosts(String username, int page, int size, String keyword, String sort, Long gymId) {
        User user = getUser(username);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 50),
                CommunityPostSort.from(sort) == CommunityPostSort.POPULAR
                        ? Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"))
                        : Sort.by(Sort.Order.desc("createdAt")));

        Page<CommunityPost> result = communityPostRepository.search(normalizeKeyword(keyword), gymId, pageable);
        List<Long> postIds = result.getContent().stream().map(CommunityPost::getId).toList();
        Set<Long> likedPostIds = postIds.isEmpty()
                ? Set.of()
                : communityPostLikeRepository.findLikedPostIds(user.getId(), postIds);
        Map<Long, String> thumbnailUrls = resolveThumbnailUrls(postIds);

        List<CommunityPostSummaryResponse> items = result.getContent().stream()
                .map(post -> CommunityPostSummaryResponse.builder()
                        .id(post.getId())
                        .title(post.getTitle())
                        .excerpt(excerpt(post.getContent()))
                        .gymId(post.getGym() != null ? post.getGym().getId() : null)
                        .gymName(post.getGym() != null ? post.getGym().getDisplayName() : null)
                        .authorNickname(post.getAuthorNicknameSnapshot())
                        .createdAt(post.getCreatedAt())
                        .viewCount(post.getViewCount())
                        .likeCount(post.getLikeCount())
                        .commentCount(post.getCommentCount())
                        .videoCount(post.getVideoCount())
                        .thumbnailUrl(thumbnailUrls.get(post.getId()))
                        .liked(likedPostIds.contains(post.getId()))
                        .mine(post.getUser().getId().equals(user.getId()))
                        .build())
                .toList();
        return CommunityPostPageResponse.from(result, items);
    }

    @Transactional
    public CommunityPostDetailResponse getPostDetail(String username, Long postId) {
        User user = getUser(username);
        CommunityPost post = getPostDetailEntity(postId);
        post.incrementViewCount();
        return buildDetailResponse(post, user);
    }

    @Transactional
    public CommunityPostDetailResponse updatePost(String username, Long postId, CommunityPostUpdateRequest request) {
        User user = getUser(username);
        CommunityPost post = getPostDetailEntity(postId);
        validatePostOwner(user, post);
        validateVideos(user.getId(), request.getVideos());
        post.update(request.getTitle(), request.getContent(), resolveGym(request.getGymId()));
        replaceVideos(post, request.getVideos());
        return buildDetailResponse(post, user);
    }

    @Transactional
    public void deletePost(String username, Long postId) {
        User user = getUser(username);
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND, "게시글을 찾을 수 없습니다."));
        validatePostOwner(user, post);

        List<CommunityPostVideo> videos = communityPostVideoRepository.findAllByPostIdOrderBySortOrderAsc(postId);
        List<com.ssafy.DDGo.community.domain.CommunityComment> comments = communityCommentRepository
                .findVisibleByPostIdOrderByCreatedAtAsc(postId);
        if (!videos.isEmpty()) {
            communityPostVideoRepository.deleteHardByPostId(postId);
        }
        if (!comments.isEmpty()) {
            List<Long> commentIds = comments.stream().map(comment -> comment.getId()).toList();
            communityCommentLikeRepository.deleteAllByCommentIdIn(commentIds);
            communityCommentRepository.deleteAll(comments);
        }
        communityPostLikeRepository.deleteAllByPostId(postId);
        communityPostRepository.delete(post);
    }

    @Transactional
    public CommunityLikeResponse likePost(String username, Long postId) {
        User user = getUser(username);
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND, "게시글을 찾을 수 없습니다."));
        if (!communityPostLikeRepository.existsByPostIdAndUserId(postId, user.getId())) {
            communityPostLikeRepository.save(CommunityPostLike.builder()
                    .post(post)
                    .user(user)
                    .build());
            post.adjustLikeCount(1);
        }
        return CommunityLikeResponse.builder()
                .targetId(postId)
                .liked(true)
                .likeCount(post.getLikeCount())
                .build();
    }

    @Transactional
    public CommunityLikeResponse unlikePost(String username, Long postId) {
        User user = getUser(username);
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND, "게시글을 찾을 수 없습니다."));
        if (communityPostLikeRepository.existsByPostIdAndUserId(postId, user.getId())) {
            communityPostLikeRepository.deleteByPostIdAndUserId(postId, user.getId());
            post.adjustLikeCount(-1);
        }
        return CommunityLikeResponse.builder()
                .targetId(postId)
                .liked(false)
                .likeCount(post.getLikeCount())
                .build();
    }

    private CommunityPostDetailResponse buildDetailResponse(CommunityPost post, User user) {
        List<CommunityPostVideoResponse> videoResponses = communityPostVideoRepository.findAllByPostIdOrderBySortOrderAsc(post.getId())
                .stream()
                .map(video -> CommunityPostVideoResponse.from(video, communityMediaService.getPlaybackUrl(video.getObjectKey())))
                .toList();
        List<CommunityCommentResponse> comments = communityCommentService.getComments(user.getUsername(), post.getId());

        boolean liked = communityPostLikeRepository.existsByPostIdAndUserId(post.getId(), user.getId());
        return CommunityPostDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .content(post.getContent())
                .gymId(post.getGym() != null ? post.getGym().getId() : null)
                .gymName(post.getGym() != null ? post.getGym().getDisplayName() : null)
                .authorNickname(post.getAuthorNicknameSnapshot())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .videoCount(post.getVideoCount())
                .liked(liked)
                .mine(post.getUser().getId().equals(user.getId()))
                .videos(videoResponses)
                .comments(comments)
                .build();
    }

    private void replaceVideos(CommunityPost post, List<CommunityPostVideoItemRequest> videos) {
        communityPostVideoRepository.deleteHardByPostId(post.getId());
        saveVideos(post, videos);
    }

    private void saveVideos(CommunityPost post, List<CommunityPostVideoItemRequest> videos) {
        List<CommunityPostVideo> entities = videos.stream()
                .map(video -> CommunityPostVideo.builder()
                        .post(post)
                        .originalFileName(video.getOriginalFileName())
                        .bucket(communityMediaService.getBucket())
                        .objectKey(video.getObjectKey())
                        .contentType(video.getContentType())
                        .fileSize(video.getFileSize())
                        .durationMs(video.getDurationMs())
                        .sortOrder(video.getSortOrder())
                        .build())
                .toList();
        if (!entities.isEmpty()) {
            communityPostVideoRepository.saveAll(entities);
            entities.stream()
                    .min(Comparator.comparingInt(CommunityPostVideo::getSortOrder))
                    .ifPresent(video -> communityMediaService.prepareThumbnail(video.getObjectKey()));
        }
        post.syncVideoCount(entities.size());
    }

    private Map<Long, String> resolveThumbnailUrls(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> thumbnailUrls = new HashMap<>();
        List<CommunityPostVideo> videos = communityPostVideoRepository.findAllByPostIdInOrderByPostIdAscSortOrderAsc(postIds);
        for (CommunityPostVideo video : videos) {
            Long postId = video.getPost().getId();
            if (thumbnailUrls.containsKey(postId)) {
                continue;
            }
            thumbnailUrls.put(postId, communityMediaService.getThumbnailUrl(video.getObjectKey()));
        }
        return thumbnailUrls;
    }

    private void validateVideos(Long userId, List<CommunityPostVideoItemRequest> videos) {
        if (videos == null || videos.size() > 3) {
            throw new CustomException(ErrorCode.INVALID_COMMUNITY_MEDIA, "영상은 최대 3개까지 첨부할 수 있습니다.");
        }
        long uniqueSortOrders = videos.stream().map(CommunityPostVideoItemRequest::getSortOrder).distinct().count();
        if (uniqueSortOrders != videos.size()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "영상의 정렬 순서(sortOrder)는 중복될 수 없습니다.");
        }
        communityMediaService.validateOwnedUploadedVideos(userId, videos);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private CommunityPost getPostDetailEntity(Long postId) {
        return communityPostRepository.findDetailById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND, "게시글을 찾을 수 없습니다."));
    }

    private ClimbingGym resolveGym(Long gymId) {
        if (gymId == null) {
            return null;
        }
        ClimbingGym gym = climbingGymRepository.findById(gymId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 암장입니다."));
        if (!Boolean.TRUE.equals(gym.getIsActive())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비활성화된 암장입니다.");
        }
        return gym;
    }

    private void validatePostOwner(User user, CommunityPost post) {
        if (!post.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.POST_ACCESS_DENIED, "해당 게시글에 대한 권한이 없습니다.");
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "...";
    }
}
