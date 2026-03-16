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
@Table(name = "climbing_brands")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE climbing_brands SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ClimbingBrand extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

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

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Builder
    public ClimbingBrand(String name, String displayName, String logoBucket, String logoObjectKey,
                         String logoContentType, String logoEtag, String sourceNote, Boolean isActive) {
        this.name = name;
        this.displayName = displayName;
        this.logoBucket = logoBucket;
        this.logoObjectKey = logoObjectKey;
        this.logoContentType = logoContentType;
        this.logoEtag = logoEtag;
        this.sourceNote = sourceNote;
        this.isActive = isActive != null ? isActive : true;
    }
}
