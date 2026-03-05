package com.ssafy.DDGo.attempts.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "attempt_video")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class AttemptVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private Attempt attempt;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "bucket", length = 100)
    private String bucket;

    @Column(name = "object_key", length = 1024)
    private String objectKey;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "etag", length = 64)
    private String etag;

    @Column(name = "is_uploaded", nullable = false)
    private boolean isUploaded = false;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public AttemptVideo(Attempt attempt, String originalFileName, String bucket, String objectKey, String contentType,
            Long fileSize) {
        this.attempt = attempt;
        this.originalFileName = originalFileName;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.isUploaded = false;
    }

    public void markAsUploaded(String etag) {
        this.isUploaded = true;
        this.etag = etag;
        this.uploadedAt = LocalDateTime.now();
    }

    public void updateMetadata(String originalFileName, String objectKey, String contentType, Long fileSize) {
        this.originalFileName = originalFileName;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.isUploaded = false;
    }
}
