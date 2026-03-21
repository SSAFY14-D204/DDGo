package com.ssafy.DDGo.community.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
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
@Table(name = "community_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE community_comments SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CommunityComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPost post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private CommunityComment parentComment;

    @Column(name = "depth", nullable = false)
    private int depth;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;

    @Column(name = "author_nickname_snapshot", nullable = false, length = 30)
    private String authorNicknameSnapshot;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Builder
    public CommunityComment(CommunityPost post, User user, CommunityComment parentComment, int depth, String content,
            String authorNicknameSnapshot) {
        this.post = post;
        this.user = user;
        this.parentComment = parentComment;
        this.depth = depth;
        this.content = content;
        this.authorNicknameSnapshot = authorNicknameSnapshot;
        this.likeCount = 0;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void adjustLikeCount(int delta) {
        this.likeCount = Math.max(0, this.likeCount + delta);
    }
}
