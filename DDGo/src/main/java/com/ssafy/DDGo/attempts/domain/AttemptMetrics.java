package com.ssafy.DDGo.attempts.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Table(name = "attempt_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attempt_metrics_attempt_id", columnNames = { "attempt_id" })
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE attempt_metrics SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AttemptMetrics extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(name = "center_stability_ratio")
    private Double centerStabilityRatio;

    @Column(name = "crux_hold_no")
    private Integer cruxHoldNo;

    @Column(name = "crux_duration_ms")
    private Integer cruxDurationMs;

    @Column(name = "danger_event_count")
    private Integer dangerEventCount;

    @Builder
    public AttemptMetrics(Attempt attempt, Double centerStabilityRatio, Integer cruxHoldNo, Integer cruxDurationMs,
            Integer dangerEventCount) {
        this.attempt = attempt;
        this.centerStabilityRatio = centerStabilityRatio;
        this.cruxHoldNo = cruxHoldNo;
        this.cruxDurationMs = cruxDurationMs;
        this.dangerEventCount = dangerEventCount;
    }

    public void updateMetrics(Double centerStabilityRatio, Integer cruxHoldNo, Integer cruxDurationMs, Integer dangerEventCount) {
        if (centerStabilityRatio != null) this.centerStabilityRatio = centerStabilityRatio;
        if (cruxHoldNo != null) this.cruxHoldNo = cruxHoldNo;
        if (cruxDurationMs != null) this.cruxDurationMs = cruxDurationMs;
        if (dangerEventCount != null) this.dangerEventCount = dangerEventCount;
    }
}
