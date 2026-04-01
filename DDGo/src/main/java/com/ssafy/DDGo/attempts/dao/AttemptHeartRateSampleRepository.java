package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptHeartRateSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptHeartRateSampleRepository extends JpaRepository<AttemptHeartRateSample, Long> {

    List<AttemptHeartRateSample> findByAttemptIdOrderBySampleOrderAsc(Long attemptId);

    void deleteByAttemptId(Long attemptId);
}
