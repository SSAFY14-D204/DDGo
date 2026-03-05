package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttemptFeedbackRepository extends JpaRepository<AttemptFeedback, Long> {
    Optional<AttemptFeedback> findByAttemptId(Long attemptId);
}
