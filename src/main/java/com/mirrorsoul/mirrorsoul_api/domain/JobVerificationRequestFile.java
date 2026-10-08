package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

@Getter
@Entity
@Table(name = "job_verification_request_files")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobVerificationRequestFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_job_verification_request_files_request"))
    private JobVerificationRequest request;

    @Column(nullable = false, length = 255)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "object_version_id", length = 255)
    private String objectVersionId;

    @Column(name = "object_etag", length = 255)
    private String objectEtag;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private JobVerificationRequestFile(
            JobVerificationRequest request,
            String bucket,
            String objectKey,
            String objectVersionId,
            String objectEtag,
            int displayOrder
    ) {
        this.request = request;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.objectVersionId = objectVersionId;
        this.objectEtag = objectEtag;
        this.displayOrder = displayOrder;
    }

    public static JobVerificationRequestFile create(
            JobVerificationRequest request,
            String bucket,
            String objectKey,
            String objectVersionId,
            String objectEtag,
            int displayOrder
    ) {
        return new JobVerificationRequestFile(request, bucket, objectKey, objectVersionId, objectEtag, displayOrder);
    }
}
