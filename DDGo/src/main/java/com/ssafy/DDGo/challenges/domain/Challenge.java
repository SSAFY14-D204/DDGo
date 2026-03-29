package com.ssafy.DDGo.challenges.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import com.ssafy.DDGo.users.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE challenges SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Challenge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private ClimbingGym gym;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_grade_id", nullable = false)
    private ClimbingGymGrade gymGrade;

    @Column(name = "gym_name_snapshot", length = 120, nullable = false)
    private String gymNameSnapshot;

    @Column(name = "problem_color_snapshot", length = 30, nullable = false)
    private String problemColorSnapshot;

    @Column(name = "grade_label_snapshot", length = 30)
    private String gradeLabelSnapshot;

    @Column(name = "sort_order_snapshot", nullable = false)
    private Integer sortOrderSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_status", nullable = false, length = 10)
    private ChallengeStatus challengeStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "challenge_result", length = 10)
    private ChallengeResult challengeResult;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "holds_json", columnDefinition = "json")
    private String holdsJson;

    @Builder
    public Challenge(User user, ClimbingGym gym, ClimbingGymGrade gymGrade,
            String gymNameSnapshot, String problemColorSnapshot, String gradeLabelSnapshot,
            Integer sortOrderSnapshot, ChallengeStatus challengeStatus, LocalDateTime startedAt, String holdsJson) {
        this.user = user;
        this.gym = gym;
        this.gymGrade = gymGrade;
        this.gymNameSnapshot = gymNameSnapshot;
        this.problemColorSnapshot = problemColorSnapshot;
        this.gradeLabelSnapshot = gradeLabelSnapshot;
        this.sortOrderSnapshot = sortOrderSnapshot;
        this.challengeStatus = challengeStatus != null ? challengeStatus : ChallengeStatus.ACTIVE;
        this.startedAt = startedAt;
        this.holdsJson = holdsJson;
    }

    public void close(ChallengeResult result) {
        this.challengeStatus = ChallengeStatus.CLOSED;
        this.challengeResult = result;
        this.endedAt = LocalDateTime.now();
    }

    public void updateHoldsJson(String holdsJson) {
        this.holdsJson = holdsJson;
    }
}
