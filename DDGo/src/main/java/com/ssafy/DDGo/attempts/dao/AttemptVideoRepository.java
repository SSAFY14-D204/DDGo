package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttemptVideoRepository extends JpaRepository<AttemptVideo, Long> {
    Optional<AttemptVideo> findByAttemptId(Long attemptId);
}
