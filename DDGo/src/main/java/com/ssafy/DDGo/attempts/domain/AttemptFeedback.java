package com.ssafy.DDGo.attempts.domain;

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
@Table(name = "attempt_feedbacks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attempt_feedbacks_attempt_id", columnNames = { "attempt_id" })
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE attempt_feedbacks SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AttemptFeedback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "risk_alert", length = 200)
    private String riskAlert;

    @Column(name = "next_mission", length = 200)
    private String nextMission;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Builder
    public AttemptFeedback(Attempt attempt, String failureReason, String riskAlert, String nextMission,
            String modelVersion, String promptVersion, LocalDateTime generatedAt) {
        this.attempt = attempt;
        this.failureReason = failureReason;
        this.riskAlert = riskAlert;
        this.nextMission = nextMission;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.generatedAt = generatedAt;
    }

    public void updateFeedback(String failureReason, String riskAlert, String nextMission) {
        if (failureReason != null) this.failureReason = failureReason;
        if (riskAlert != null) this.riskAlert = riskAlert;
        if (nextMission != null) this.nextMission = nextMission;
    }
}
