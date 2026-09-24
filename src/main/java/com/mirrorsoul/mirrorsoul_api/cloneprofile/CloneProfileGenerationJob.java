package com.mirrorsoul.mirrorsoul_api.cloneprofile;

import com.mirrorsoul.mirrorsoul_api.domain.BaseTimeEntity;
import com.mirrorsoul.mirrorsoul_api.domain.Clone;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "clone_profile_generation_jobs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CloneProfileGenerationJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clone_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_clone_profile_generation_jobs_clone"))
    private Clone clone;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private CloneProfileTrigger triggerType;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "prompt_version", nullable = false, length = 50)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CloneProfileGenerationJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private CloneProfileGenerationJob(Clone clone, CloneProfileTrigger triggerType,
                                      String sourceHash, String promptVersion) {
        this.clone = clone;
        this.triggerType = triggerType;
        this.sourceHash = sourceHash;
        this.promptVersion = promptVersion;
        this.status = CloneProfileGenerationJobStatus.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = LocalDateTime.now();
    }

    public static CloneProfileGenerationJob create(Clone clone, CloneProfileTrigger triggerType,
                                                   String sourceHash, String promptVersion) {
        return new CloneProfileGenerationJob(clone, triggerType, sourceHash, promptVersion);
    }

    public void replacePendingRequest(CloneProfileTrigger triggerType, String sourceHash, String promptVersion) {
        if (status != CloneProfileGenerationJobStatus.PENDING) {
            throw new IllegalStateException("Only a pending clone profile job can be replaced");
        }
        this.triggerType = triggerType;
        this.sourceHash = sourceHash;
        this.promptVersion = promptVersion;
        this.nextAttemptAt = LocalDateTime.now();
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void start(LocalDateTime now) {
        this.status = CloneProfileGenerationJobStatus.PROCESSING;
        this.attemptCount++;
        this.startedAt = now;
        this.nextAttemptAt = null;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void complete(LocalDateTime now) {
        this.status = CloneProfileGenerationJobStatus.COMPLETED;
        this.completedAt = now;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markStale(LocalDateTime now) {
        this.status = CloneProfileGenerationJobStatus.STALE;
        this.completedAt = now;
    }

    public void retryAt(LocalDateTime nextAttemptAt, String errorCode, String errorMessage) {
        this.status = CloneProfileGenerationJobStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage);
    }

    public void fail(LocalDateTime now, String errorCode, String errorMessage) {
        this.status = CloneProfileGenerationJobStatus.FAILED;
        this.completedAt = now;
        this.errorCode = errorCode;
        this.errorMessage = truncate(errorMessage);
    }

    public void recover(LocalDateTime now) {
        if (status == CloneProfileGenerationJobStatus.PROCESSING) {
            this.status = CloneProfileGenerationJobStatus.PENDING;
            this.nextAttemptAt = now;
            this.errorCode = "WORKER_RECOVERED";
            this.errorMessage = "Recovered an abandoned processing job";
        }
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
