package com.ssafy.DDGo.gyms.domain;

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
@Getter
@Table(name = "climbing_gyms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE climbing_gyms SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClimbingGym extends BaseTimeEntity {

    public enum MapProvider {
        KAKAO, NAVER, GOOGLE, MANUAL
    }

    public enum GymSource {
        CURATED, MAP_IMPORTED
    }

    public enum GradeSource {
        CURATED, STANDARD_FALLBACK
    }

    public enum MatchStatus {
        VERIFIED, AUTO_MATCHED, UNMATCHED_IMPORTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private ClimbingBrand brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "map_provider", length = 20)
    private MapProvider mapProvider;

    @Column(name = "external_place_id", length = 120)
    private String externalPlaceId;

    @Column(name = "place_name_raw", length = 150)
    private String placeNameRaw;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "branch_name", length = 50)
    private String branchName;

    @Column(name = "display_name", length = 120, nullable = false, unique = true)
    private String displayName;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "address_name", length = 255)
    private String addressName;

    @Column(name = "road_address_name", length = 255)
    private String roadAddressName;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "logo_bucket", length = 50)
    private String logoBucket;

    @Column(name = "logo_object_key", length = 255)
    private String logoObjectKey;

    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    @Column(name = "logo_etag", length = 50)
    private String logoEtag;

    @Column(name = "source_note", length = 255)
    private String sourceNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "gym_source", length = 20, nullable = false)
    private GymSource gymSource = GymSource.CURATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade_source", length = 30, nullable = false)
    private GradeSource gradeSource = GradeSource.CURATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", length = 30, nullable = false)
    private MatchStatus matchStatus = MatchStatus.VERIFIED;

    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder
    public ClimbingGym(ClimbingBrand brand, MapProvider mapProvider, String externalPlaceId,
                       String placeNameRaw, String name, String branchName, String displayName,
                       String region, String addressName, String roadAddressName,
                       BigDecimal latitude, BigDecimal longitude, GymSource gymSource,
                       GradeSource gradeSource, MatchStatus matchStatus, Boolean needsReview, Boolean isActive) {
        this.brand = brand;
        this.mapProvider = mapProvider;
        this.externalPlaceId = externalPlaceId;
        this.placeNameRaw = placeNameRaw;
        this.name = name;
        this.branchName = branchName;
        this.displayName = displayName;
        this.region = region;
        this.addressName = addressName;
        this.roadAddressName = roadAddressName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.gymSource = gymSource != null ? gymSource : GymSource.CURATED;
        this.gradeSource = gradeSource != null ? gradeSource : GradeSource.CURATED;
        this.matchStatus = matchStatus != null ? matchStatus : MatchStatus.VERIFIED;
        this.needsReview = needsReview != null ? needsReview : false;
        this.isActive = isActive != null ? isActive : true;
    }

    public void updateMapInfo(MapProvider mapProvider, String externalPlaceId, String addressName, 
                              String roadAddressName, BigDecimal latitude, BigDecimal longitude) {
        if (this.mapProvider == null) this.mapProvider = mapProvider;
        if (this.externalPlaceId == null) this.externalPlaceId = externalPlaceId;
        if (this.addressName == null) this.addressName = addressName;
        if (this.roadAddressName == null) this.roadAddressName = roadAddressName;
        if (this.latitude == null) this.latitude = latitude;
        if (this.longitude == null) this.longitude = longitude;
        
        if (this.matchStatus != MatchStatus.VERIFIED) {
            this.matchStatus = MatchStatus.AUTO_MATCHED;
        }
    }
}
