package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttemptMetricsRepository extends JpaRepository<AttemptMetrics, Long> {
    Optional<AttemptMetrics> findByAttemptId(Long attemptId);
}
