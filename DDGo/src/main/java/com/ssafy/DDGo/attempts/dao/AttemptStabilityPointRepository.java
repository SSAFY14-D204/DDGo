package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptStabilityPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptStabilityPointRepository extends JpaRepository<AttemptStabilityPoint, Long> {

    List<AttemptStabilityPoint> findByAttemptIdOrderByPointOrderAsc(Long attemptId);

    void deleteByAttemptId(Long attemptId);
}
