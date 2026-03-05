package com.ssafy.DDGo.challenges.dao;

import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeSummaryRepository extends JpaRepository<ChallengeSummary, Long> {

    Optional<ChallengeSummary> findByChallengeId(Long challengeId);

    // AVG(centerStabilityRatio), MAX(cruxDurationMs) 집계
    // @SQLRestriction으로 deleted_at IS NULL 이 자동 적용됨
    @Query("""
            SELECT AVG(am.centerStabilityRatio), MAX(am.cruxDurationMs)
            FROM AttemptMetrics am
            WHERE am.attempt.challenge.id = :challengeId
            """)
    List<Object[]> aggregateMetrics(@Param("challengeId") Long challengeId);

    // 가장 많이 등장한 cruxHoldNo (null 제외)
    @Query("""
            SELECT am.cruxHoldNo
            FROM AttemptMetrics am
            WHERE am.attempt.challenge.id = :challengeId
              AND am.cruxHoldNo IS NOT NULL
            GROUP BY am.cruxHoldNo
            ORDER BY COUNT(am.cruxHoldNo) DESC
            """)
    List<Integer> findCruxHoldNos(@Param("challengeId") Long challengeId, Pageable pageable);
}
