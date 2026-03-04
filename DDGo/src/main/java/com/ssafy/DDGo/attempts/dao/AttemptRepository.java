package com.ssafy.DDGo.attempts.dao;

import com.ssafy.DDGo.attempts.domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {
}
