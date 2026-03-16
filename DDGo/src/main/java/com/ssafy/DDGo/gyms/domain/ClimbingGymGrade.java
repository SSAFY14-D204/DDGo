package com.ssafy.DDGo.gyms.domain;

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
@Table(name = "climbing_gym_grades")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE climbing_gym_grades SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClimbingGymGrade extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gym_id", nullable = false)
    private ClimbingGym gym;

    @Column(name = "color_name", nullable = false, length = 30)
    private String colorName;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "grade_label", length = 30)
    private String gradeLabel;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;

    @Builder
    public ClimbingGymGrade(ClimbingGym gym, String colorName, Integer sortOrder, 
                            String colorHex, String gradeLabel, Boolean isEnabled) {
        this.gym = gym;
        this.colorName = colorName;
        this.sortOrder = sortOrder;
        this.colorHex = colorHex;
        this.gradeLabel = gradeLabel;
        this.isEnabled = isEnabled != null ? isEnabled : true;
    }
}
