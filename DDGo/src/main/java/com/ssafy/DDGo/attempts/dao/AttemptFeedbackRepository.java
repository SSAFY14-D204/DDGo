package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttemptFeedbackRepository extends JpaRepository<AttemptFeedback, Long> {
    Optional<AttemptFeedback> findByAttemptId(Long attemptId);

    @Query("""
            SELECT af FROM AttemptFeedback af
            JOIN FETCH af.attempt a
            WHERE a.challenge.id = :challengeId
            """)
    List<AttemptFeedback> findByChallengeId(@Param("challengeId") Long challengeId);
}
