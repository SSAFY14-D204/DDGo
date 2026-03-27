package com.ssafy.DDGo.attempts.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Table(name = "attempt_metrics", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attempt_metrics_attempt_id", columnNames = {"attempt_id"})
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

    @Column(name = "stability_recovery_score")
    private Integer stabilityRecoveryScore;

    @Column(name = "stable_contact_ratio")
    private Double stableContactRatio;

    @Column(name = "lower_body_drive_score")
    private Integer lowerBodyDriveScore;

    @Column(name = "overall_movement_score")
    private Integer overallMovementScore;

    @Column(name = "crux_hold_no")
    private Integer cruxHoldNo;

    @Column(name = "crux_duration_ms")
    private Integer cruxDurationMs;

    @Column(name = "danger_event_count")
    private Integer dangerEventCount;

    @Column(name = "load_focus_label", length = 100)
    private String loadFocusLabel;

    @Builder
    public AttemptMetrics(
            Attempt attempt,
            Double centerStabilityRatio,
            Integer stabilityRecoveryScore,
            Double stableContactRatio,
            Integer lowerBodyDriveScore,
            Integer overallMovementScore,
            Integer cruxHoldNo,
            Integer cruxDurationMs,
            Integer dangerEventCount,
            String loadFocusLabel) {
        this.attempt = attempt;
        this.centerStabilityRatio = centerStabilityRatio;
        this.stabilityRecoveryScore = stabilityRecoveryScore;
        this.stableContactRatio = stableContactRatio;
        this.lowerBodyDriveScore = lowerBodyDriveScore;
        this.overallMovementScore = overallMovementScore;
        this.cruxHoldNo = cruxHoldNo;
        this.cruxDurationMs = cruxDurationMs;
        this.dangerEventCount = dangerEventCount;
        this.loadFocusLabel = loadFocusLabel;
    }

    public void updateMetrics(
            Double centerStabilityRatio,
            Integer stabilityRecoveryScore,
            Double stableContactRatio,
            Integer lowerBodyDriveScore,
            Integer overallMovementScore,
            Integer cruxHoldNo,
            Integer cruxDurationMs,
            Integer dangerEventCount,
            String loadFocusLabel) {
        if (centerStabilityRatio != null) {
            this.centerStabilityRatio = centerStabilityRatio;
        }
        if (stabilityRecoveryScore != null) {
            this.stabilityRecoveryScore = stabilityRecoveryScore;
        }
        if (stableContactRatio != null) {
            this.stableContactRatio = stableContactRatio;
        }
        if (lowerBodyDriveScore != null) {
            this.lowerBodyDriveScore = lowerBodyDriveScore;
        }
        if (overallMovementScore != null) {
            this.overallMovementScore = overallMovementScore;
        }
        if (cruxHoldNo != null) {
            this.cruxHoldNo = cruxHoldNo;
        }
        if (cruxDurationMs != null) {
            this.cruxDurationMs = cruxDurationMs;
        }
        if (dangerEventCount != null) {
            this.dangerEventCount = dangerEventCount;
        }
        if (loadFocusLabel != null) {
            this.loadFocusLabel = loadFocusLabel;
        }
    }
}
