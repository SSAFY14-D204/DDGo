package com.ssafy.DDGo.challenges.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "challenge_attempt_counters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeAttemptCounter {

    @Id
    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    @Column(name = "next_attempt_no", nullable = false)
    private Integer nextAttemptNo = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ChallengeAttemptCounter(Long challengeId) {
        this.challengeId = challengeId;
    }
}
