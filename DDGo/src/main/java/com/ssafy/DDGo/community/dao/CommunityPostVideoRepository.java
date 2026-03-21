package com.ssafy.DDGo.community.dao;

import com.ssafy.DDGo.community.domain.CommunityPostVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityPostVideoRepository extends JpaRepository<CommunityPostVideo, Long> {

    List<CommunityPostVideo> findAllByPostIdOrderBySortOrderAsc(Long postId);
}
