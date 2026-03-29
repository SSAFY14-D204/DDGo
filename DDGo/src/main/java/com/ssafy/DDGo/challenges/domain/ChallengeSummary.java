package com.ssafy.DDGo.challenges.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "challenge_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE challenge_summaries SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ChallengeSummary extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "challenge_id", nullable = false)
    private Long challengeId;

    // AVG(attempt_metrics.center_stability_ratio)
    @Column(name = "average_center_stability_ratio", precision = 5, scale = 2)
    private BigDecimal averageCenterStabilityRatio;

    // 가장 많이 등장한 crux_hold_no
    @Column(name = "most_crux_hold_no")
    private Integer mostCruxHoldNo;

    // MAX(attempt_metrics.crux_duration_ms)
    @Column(name = "max_crux_duration_ms")
    private Integer maxCruxDurationMs;

    // AI가 나중에 채울 예정
    @Column(name = "final_comment", length = 500)
    private String finalComment;

    @Builder
    public ChallengeSummary(Long challengeId, BigDecimal averageCenterStabilityRatio,
            Integer mostCruxHoldNo, Integer maxCruxDurationMs) {
        this.challengeId = challengeId;
        this.averageCenterStabilityRatio = averageCenterStabilityRatio;
        this.mostCruxHoldNo = mostCruxHoldNo;
        this.maxCruxDurationMs = maxCruxDurationMs;
        this.finalComment = null;
    }

    // 기존 챌린지 요약 데이터 업데이트 로직 (Upsert 목적)
    public void updateSummary(BigDecimal averageCenterStabilityRatio, Integer mostCruxHoldNo,
                              Integer maxCruxDurationMs, String finalComment) {
        this.averageCenterStabilityRatio = averageCenterStabilityRatio;
        this.mostCruxHoldNo = mostCruxHoldNo;
        this.maxCruxDurationMs = maxCruxDurationMs;
        this.finalComment = finalComment;
    }
}
