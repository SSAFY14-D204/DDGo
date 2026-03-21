package com.ssafy.DDGo.community.dao;

import com.ssafy.DDGo.community.domain.CommunityComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    @EntityGraph(attributePaths = { "user", "parentComment" })
    @Query("SELECT c FROM CommunityComment c WHERE c.post.id = :postId ORDER BY c.createdAt ASC")
    List<CommunityComment> findVisibleByPostIdOrderByCreatedAtAsc(@Param("postId") Long postId);

    @EntityGraph(attributePaths = { "user", "parentComment" })
    List<CommunityComment> findAllByParentCommentIdOrderByCreatedAtAsc(Long parentCommentId);
}
