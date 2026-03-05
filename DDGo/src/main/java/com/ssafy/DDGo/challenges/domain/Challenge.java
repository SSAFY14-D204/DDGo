package com.ssafy.DDGo.challenges.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
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

    @Column(name = "gym_name", length = 50)
    private String gymName;

    @Column(name = "problem_color", nullable = false, length = 30)
    private String problemColor;

    @Column(name = "grade_label", length = 30)
    private String gradeLabel;

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
    public Challenge(User user, String gymName, String problemColor, String gradeLabel,
                     ChallengeStatus challengeStatus, LocalDateTime startedAt, String holdsJson) {
        this.user = user;
        this.gymName = gymName;
        this.problemColor = problemColor;
        this.gradeLabel = gradeLabel;
        this.challengeStatus = challengeStatus;
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
