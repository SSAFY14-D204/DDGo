package com.ssafy.DDGo.community.dao;

import com.ssafy.DDGo.community.domain.CommunityPostVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface CommunityPostVideoRepository extends JpaRepository<CommunityPostVideo, Long> {

    List<CommunityPostVideo> findAllByPostIdOrderBySortOrderAsc(Long postId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CommunityPostVideo v WHERE v.post.id = :postId")
    void deleteHardByPostId(@Param("postId") Long postId);
}
