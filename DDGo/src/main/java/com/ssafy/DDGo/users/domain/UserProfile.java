package com.ssafy.DDGo.users.domain;

import com.ssafy.DDGo.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE user_profiles SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class UserProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(length = 6)
    private String sex;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "weight_kg")
    private Integer weightKg;

    @Column(name = "wingspan_cm")
    private Integer wingspanCm;

    @Builder
    public UserProfile(Long userId, String sex, Integer heightCm, Integer weightKg, Integer wingspanCm) {
        this.userId = userId;
        this.sex = sex;
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.wingspanCm = wingspanCm;
    }

    public void updateSex(String sex) {
        if (sex != null) this.sex = sex;
    }

    public void updateHeightCm(Integer heightCm) {
        if (heightCm != null) this.heightCm = heightCm;
    }

    public void updateWeightKg(Integer weightKg) {
        if (weightKg != null) this.weightKg = weightKg;
    }

    public void updateWingspanCm(Integer wingspanCm) {
        if (wingspanCm != null) this.wingspanCm = wingspanCm;
    }

    public void updateProfile(String sex, Integer heightCm, Integer weightKg, Integer wingspanCm) {
        updateSex(sex);
        updateHeightCm(heightCm);
        updateWeightKg(weightKg);
        updateWingspanCm(wingspanCm);
    }
}
