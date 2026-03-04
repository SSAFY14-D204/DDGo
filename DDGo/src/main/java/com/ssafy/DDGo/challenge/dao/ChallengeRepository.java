package com.ssafy.DDGo.challenge.dao;

import com.ssafy.DDGo.challenge.domain.Challenge;
import com.ssafy.DDGo.users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    // 내 챌린지 목록 (최신순)
    List<Challenge> findAllByUserOrderByCreatedAtDesc(User user);

    // 단건 조회 (본인 소유 확인)
    Optional<Challenge> findByIdAndUser(Long id, User user);
}
