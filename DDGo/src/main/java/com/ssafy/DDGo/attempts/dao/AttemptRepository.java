package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
    List<Attempt> findByChallengeIdOrderByAttemptNoAsc(Long challengeId);
}
