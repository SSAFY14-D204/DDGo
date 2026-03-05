package com.ssafy.DDGo.attempts.domain;

import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attempts_challenge_attempt_no", columnNames = { "challenge_id", "attempt_no" })
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE attempts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Attempt extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 20)
    private AttemptStatus attemptStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_result", length = 10)
    private AttemptResult attemptResult;

    @Column(name = "analysis_started_at")
    private LocalDateTime analysisStartedAt;

    @Column(name = "analysis_ended_at")
    private LocalDateTime analysisEndedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "max_hold_no")
    private Integer maxHoldNo;

    @Builder
    public Attempt(Challenge challenge, Integer attemptNo) {
        this.challenge = challenge;
        this.attemptNo = attemptNo;
        this.attemptStatus = AttemptStatus.UPLOADING; // 최초 생성 시 상태
    }

    public void updateStatus(AttemptStatus status) {
        this.attemptStatus = status;
    }
}
