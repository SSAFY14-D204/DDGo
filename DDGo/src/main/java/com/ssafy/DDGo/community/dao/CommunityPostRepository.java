package com.ssafy.DDGo.community.dao;

import com.ssafy.DDGo.community.domain.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    @EntityGraph(attributePaths = { "user", "gym" })
    @Query(value = """
            SELECT p
            FROM CommunityPost p
            WHERE (:keyword IS NULL
                OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:gymId IS NULL OR p.gym.id = :gymId)
            """, countQuery = """
            SELECT COUNT(p)
            FROM CommunityPost p
            WHERE (:keyword IS NULL
                OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:gymId IS NULL OR p.gym.id = :gymId)
            """)
    Page<CommunityPost> search(@Param("keyword") String keyword, @Param("gymId") Long gymId, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "gym" })
    @Query("SELECT p FROM CommunityPost p WHERE p.id = :postId")
    Optional<CommunityPost> findDetailById(@Param("postId") Long postId);
}
