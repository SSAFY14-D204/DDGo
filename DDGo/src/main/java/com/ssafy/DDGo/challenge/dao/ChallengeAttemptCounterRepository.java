package com.ssafy.DDGo.challenge.dao;

import com.ssafy.DDGo.challenge.domain.ChallengeAttemptCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChallengeAttemptCounterRepository extends JpaRepository<ChallengeAttemptCounter, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChallengeAttemptCounter c SET c.nextAttemptNo = c.nextAttemptNo + 1, c.updatedAt = CURRENT_TIMESTAMP WHERE c.challengeId = :challengeId")
    int incrementAttemptNo(@Param("challengeId") Long challengeId);

    @Query("SELECT c.nextAttemptNo FROM ChallengeAttemptCounter c WHERE c.challengeId = :challengeId")
    Optional<Integer> findNextAttemptNo(@Param("challengeId") Long challengeId);
}
