package com.ssafy.DDGo.community.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.DDGo.community.dao.CommunityCommentLikeRepository;
import com.ssafy.DDGo.community.dao.CommunityCommentRepository;
import com.ssafy.DDGo.community.dao.CommunityPostLikeRepository;
import com.ssafy.DDGo.community.dao.CommunityPostRepository;
import com.ssafy.DDGo.community.dao.CommunityPostVideoRepository;
import com.ssafy.DDGo.community.domain.CommunityPost;
import com.ssafy.DDGo.community.domain.CommunityPostSort;
import com.ssafy.DDGo.community.domain.CommunityPostVideo;
import com.ssafy.DDGo.community.dto.response.CommunityPostPageResponse;
import com.ssafy.DDGo.community.dto.response.CommunityPostSummaryResponse;
import com.ssafy.DDGo.gyms.dao.ClimbingGymRepository;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceTest {

    @Mock
    private CommunityPostRepository communityPostRepository;

    @Mock
    private CommunityPostVideoRepository communityPostVideoRepository;

    @Mock
    private CommunityPostLikeRepository communityPostLikeRepository;

    @Mock
    private CommunityCommentRepository communityCommentRepository;

    @Mock
    private CommunityCommentLikeRepository communityCommentLikeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClimbingGymRepository climbingGymRepository;

    @Mock
    private CommunityMediaService communityMediaService;

    @Mock
    private CommunityCommentService communityCommentService;

    @InjectMocks
    private CommunityPostService communityPostService;

    @Test
    @DisplayName("getPosts attaches thumbnailUrl from the first video per post")
    void getPosts_attachesThumbnailUrlFromFirstVideo() {
        User user = user(100L);
        CommunityPost firstPost = post(1L, user, "first title", "first content", 2);
        CommunityPost secondPost = post(2L, user, "second title", "second content", 0);

        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(communityPostRepository.search(eq("keyword"), eq(9L), eq(PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))))))
                .thenReturn(new PageImpl<>(List.of(firstPost, secondPost), PageRequest.of(0, 20), 2));
        when(communityPostLikeRepository.findLikedPostIds(user.getId(), List.of(1L, 2L)))
                .thenReturn(Set.of(1L));
        when(communityPostVideoRepository.findAllByPostIdInOrderByPostIdAscSortOrderAsc(List.of(1L, 2L)))
                .thenReturn(List.of(
                        video(firstPost, "post-1-video-0", 0),
                        video(firstPost, "post-1-video-1", 1)
                ));
        when(communityMediaService.getThumbnailUrl("post-1-video-0"))
                .thenReturn("https://cdn.example/post-1-video-0.jpg");

        CommunityPostPageResponse response = communityPostService.getPosts("tester", 0, 20, "keyword", CommunityPostSort.LATEST.name(), 9L);

        CommunityPostSummaryResponse firstSummary = response.getItems().get(0);
        CommunityPostSummaryResponse secondSummary = response.getItems().get(1);

        assertThat(firstSummary.getThumbnailUrl()).isEqualTo("https://cdn.example/post-1-video-0.jpg");
        assertThat(secondSummary.getThumbnailUrl()).isNull();
        assertThat(firstSummary.isLiked()).isTrue();
        assertThat(secondSummary.isLiked()).isFalse();

        verify(communityPostVideoRepository).findAllByPostIdInOrderByPostIdAscSortOrderAsc(List.of(1L, 2L));
        verify(communityMediaService).getThumbnailUrl("post-1-video-0");
        verify(communityMediaService, never()).getThumbnailUrl("post-1-video-1");
    }

    @Test
    @DisplayName("deletePost uses bulk deletes when the post has no comments")
    void deletePost_withoutComments_usesBulkDeletes() {
        deletePost_usesBulkDeletes(false);
    }

    @Test
    @DisplayName("deletePost uses bulk deletes when the post has comments")
    void deletePost_withComments_usesBulkDeletes() {
        deletePost_usesBulkDeletes(true);
    }

    private void deletePost_usesBulkDeletes(boolean withComments) {
        User user = user(100L);
        CommunityPost post = post(1L, user, "title", "content", 0);
        if (withComments) {
            post.adjustCommentCount(2);
        }

        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(communityPostRepository.findById(1L)).thenReturn(Optional.of(post));

        communityPostService.deletePost("tester", 1L);

        InOrder inOrder = inOrder(
                communityPostVideoRepository,
                communityCommentLikeRepository,
                communityCommentRepository,
                communityPostLikeRepository,
                communityPostRepository);
        inOrder.verify(communityPostVideoRepository).deleteHardByPostId(1L);
        inOrder.verify(communityCommentLikeRepository).deleteAllByPostId(1L);
        inOrder.verify(communityCommentRepository).softDeleteAllByPostId(1L);
        inOrder.verify(communityPostLikeRepository).deleteAllByPostId(1L);
        inOrder.verify(communityPostRepository).delete(post);

        verify(communityCommentRepository, never()).findVisibleByPostIdOrderByCreatedAtAsc(1L);
        verify(communityCommentRepository, never()).deleteAll(any());
        verify(communityCommentLikeRepository, never()).deleteAllByCommentIdIn(anyList());
    }

    private User user(Long id) {
        User user = User.builder()
                .username("tester")
                .email("tester@example.com")
                .password("password")
                .nickname("tester-nickname")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private CommunityPost post(Long id, User user, String title, String content, int videoCount) {
        CommunityPost post = CommunityPost.builder()
                .user(user)
                .gym(null)
                .title(title)
                .content(content)
                .authorNicknameSnapshot(user.getNickname())
                .build();
        ReflectionTestUtils.setField(post, "id", id);
        post.syncVideoCount(videoCount);
        return post;
    }

    private CommunityPostVideo video(CommunityPost post, String objectKey, int sortOrder) {
        CommunityPostVideo video = CommunityPostVideo.builder()
                .post(post)
                .originalFileName(objectKey + ".mp4")
                .bucket("community-bucket")
                .objectKey(objectKey)
                .contentType("video/mp4")
                .fileSize(1024L)
                .durationMs(3000L)
                .sortOrder(sortOrder)
                .build();
        ReflectionTestUtils.setField(video, "id", (long) (sortOrder + 1));
        return video;
    }
}
