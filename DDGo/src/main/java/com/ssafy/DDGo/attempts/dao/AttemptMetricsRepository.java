package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttemptMetricsRepository extends JpaRepository<AttemptMetrics, Long> {

    Optional<AttemptMetrics> findByAttemptId(Long attemptId);

    // 챌린지에 속한 모든 attempt_metrics를 attempt와 함께 조회 (N+1 방지)
    @Query("""
            SELECT am FROM AttemptMetrics am
            JOIN FETCH am.attempt a
            WHERE a.challenge.id = :challengeId
            ORDER BY a.attemptNo ASC
            """)
    List<AttemptMetrics> findByChallengeIdOrderByAttemptNo(@Param("challengeId") Long challengeId);
}
