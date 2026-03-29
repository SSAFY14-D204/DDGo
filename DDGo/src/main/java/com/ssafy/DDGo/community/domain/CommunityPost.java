package com.ssafy.DDGo.community.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.users.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE community_posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CommunityPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id")
    private ClimbingGym gym;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_nickname_snapshot", nullable = false, length = 30)
    private String authorNicknameSnapshot;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "video_count", nullable = false)
    private int videoCount;

    @Builder
    public CommunityPost(User user, ClimbingGym gym, String title, String content, String authorNicknameSnapshot) {
        this.user = user;
        this.gym = gym;
        this.title = title;
        this.content = content;
        this.authorNicknameSnapshot = authorNicknameSnapshot;
        this.viewCount = 0;
        this.commentCount = 0;
        this.likeCount = 0;
        this.videoCount = 0;
    }

    public void update(String title, String content, ClimbingGym gym) {
        this.title = title;
        this.content = content;
        this.gym = gym;
    }

    public void incrementViewCount() {
        this.viewCount += 1;
    }

    public void adjustCommentCount(int delta) {
        this.commentCount = Math.max(0, this.commentCount + delta);
    }

    public void adjustLikeCount(int delta) {
        this.likeCount = Math.max(0, this.likeCount + delta);
    }

    public void syncVideoCount(int videoCount) {
        this.videoCount = Math.max(0, videoCount);
    }
}
