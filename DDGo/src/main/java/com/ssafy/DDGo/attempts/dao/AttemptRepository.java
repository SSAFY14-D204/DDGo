package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    List<Attempt> findByChallengeIdOrderByAttemptNoAsc(Long challengeId);

    @Query("""
            SELECT a.challenge.id AS challengeId, COUNT(a) AS doneAttemptCount
            FROM Attempt a
            WHERE a.challenge.id IN :challengeIds
              AND a.attemptStatus = com.ssafy.DDGo.attempts.domain.AttemptStatus.DONE
            GROUP BY a.challenge.id
            """)
    List<ChallengeDoneAttemptCountProjection> countDoneAttemptsByChallengeIds(
            @Param("challengeIds") List<Long> challengeIds);
}
