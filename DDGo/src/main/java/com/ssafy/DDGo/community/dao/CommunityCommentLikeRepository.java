package com.ssafy.DDGo.community.dao;

import com.ssafy.DDGo.community.domain.CommunityCommentLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Set;

@Repository
public interface CommunityCommentLikeRepository extends JpaRepository<CommunityCommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    void deleteByCommentIdAndUserId(Long commentId, Long userId);

    void deleteAllByCommentIdIn(Collection<Long> commentIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CommunityCommentLike l WHERE l.comment.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    @Query("SELECT l.comment.id FROM CommunityCommentLike l WHERE l.user.id = :userId AND l.comment.id IN :commentIds")
    Set<Long> findLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") Collection<Long> commentIds);
}
