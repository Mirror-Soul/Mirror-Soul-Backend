package com.mirrorsoul.mirrorsoul_api.domain;

import com.mirrorsoul.mirrorsoul_api.domain.enums.Job;
import com.mirrorsoul.mirrorsoul_api.domain.enums.JobVerificationRequestStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_verification_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobVerificationRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_job_verification_requests_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "claimed_job", nullable = false, length = 30)
    private Job claimedJob;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobVerificationRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id",
            foreignKey = @ForeignKey(name = "fk_job_verification_requests_reviewer"))
    private User reviewer;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<JobVerificationRequestFile> files = new ArrayList<>();

    private JobVerificationRequest(User user, Job claimedJob) {
        this.user = user;
        this.claimedJob = claimedJob;
        this.status = JobVerificationRequestStatus.PENDING;
    }

    public static JobVerificationRequest pending(User user, Job claimedJob) {
        return new JobVerificationRequest(user, claimedJob);
    }

    public void addFile(String bucket, String objectKey, String versionId, String etag, int displayOrder) {
        files.add(JobVerificationRequestFile.create(this, bucket, objectKey, versionId, etag, displayOrder));
    }

    public void approve(User reviewer, LocalDateTime reviewedAt) {
        requirePending();
        this.status = JobVerificationRequestStatus.APPROVED;
        this.reviewer = reviewer;
        this.reviewedAt = reviewedAt;
    }

    public void reject(User reviewer, String reason, LocalDateTime reviewedAt) {
        requirePending();
        this.status = JobVerificationRequestStatus.REJECTED;
        this.reviewer = reviewer;
        this.rejectionReason = reason;
        this.reviewedAt = reviewedAt;
    }

    private void requirePending() {
        if (status != JobVerificationRequestStatus.PENDING) {
            throw new IllegalStateException("Job verification request has already been reviewed.");
        }
    }

    public boolean matchesSubmission(Job job, List<FileSnapshot> snapshots) {
        if (claimedJob != job || files.size() != snapshots.size()) {
            return false;
        }
        for (int i = 0; i < files.size(); i++) {
            JobVerificationRequestFile file = files.get(i);
            FileSnapshot snapshot = snapshots.get(i);
            if (!file.getBucket().equals(snapshot.bucket())
                    || !file.getObjectKey().equals(snapshot.objectKey())
                    || !java.util.Objects.equals(file.getObjectVersionId(), snapshot.versionId())
                    || !java.util.Objects.equals(file.getObjectEtag(), snapshot.etag())) {
                return false;
            }
        }
        return true;
    }

    public record FileSnapshot(String bucket, String objectKey, String versionId, String etag) {
    }
}
