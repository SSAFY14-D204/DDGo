package com.ssafy.DDGo.attempts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "attempt_stability_points")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AttemptStabilityPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(name = "point_order", nullable = false)
    private Integer pointOrder;

    @Column(name = "timestamp_ms", nullable = false)
    private Long timestampMs;

    @Column(name = "stability_score", nullable = false)
    private Double stabilityScore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public AttemptStabilityPoint(Attempt attempt, Integer pointOrder, Long timestampMs, Double stabilityScore) {
        this.attempt = attempt;
        this.pointOrder = pointOrder;
        this.timestampMs = timestampMs;
        this.stabilityScore = stabilityScore;
    }
}
