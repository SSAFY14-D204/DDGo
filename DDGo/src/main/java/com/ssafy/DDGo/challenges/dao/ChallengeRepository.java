package com.ssafy.DDGo.challenges.dao;

import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.users.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    List<Challenge> findAllByUserOrderByCreatedAtDesc(User user);

    Optional<Challenge> findByIdAndUser(Long id, User user);

    @Query("""
            SELECT c.id
            FROM Challenge c
            WHERE c.challengeStatus = :status
              AND c.createdAt <= :cutoff
            ORDER BY c.createdAt ASC, c.id ASC
            """)
    List<Long> findStaleActiveChallengeIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("status") ChallengeStatus status,
            Pageable pageable);

    default List<Long> findStaleActiveChallengeIds(LocalDateTime cutoff, Pageable pageable) {
        return findStaleActiveChallengeIds(cutoff, ChallengeStatus.ACTIVE, pageable);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Challenge c
            SET c.challengeStatus = com.ssafy.DDGo.challenges.domain.ChallengeStatus.CLOSED,
                c.challengeResult = :result,
                c.endedAt = :endedAt
            WHERE c.id = :challengeId
              AND c.challengeStatus = com.ssafy.DDGo.challenges.domain.ChallengeStatus.ACTIVE
            """)
    int closeIfActive(
            @Param("challengeId") Long challengeId,
            @Param("result") ChallengeResult result,
            @Param("endedAt") LocalDateTime endedAt);
}
