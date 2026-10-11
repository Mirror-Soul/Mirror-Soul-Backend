package com.mirrorsoul.mirrorsoul_api.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Durable, coalescing outbox: only the latest committed profile needs to be sent. */
@Entity
@Table(name = "rag_profile_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RagProfileJob {
    @Id
    private Long cloneId;
    @Column(nullable = false)
    private long requestedRevision;
    @Column(nullable = false)
    private long deliveredRevision;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    @Column(length = 36)
    private String leaseToken;
    @Column(length = 100)
    private String lastError;

    @Column(name = "created_at") private LocalDateTime createdAt;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "finished_at") private LocalDateTime finishedAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "base_clone_version") private Long baseCloneVersion;
    @Column(nullable = false) private int retryCount;
    @Column(length = 20) private String trainingStatus;
    @Column(length = 80) private String trainingFailureCode;
    private Boolean trainingRetryAllowed;
    private LocalDateTime trainingFinishedAt;
    private Long resultCloneVersion;

    public void trainingStarted(LocalDateTime at) {
        trainingStatus = "PROCESSING"; startedAt = at; updatedAt = LocalDateTime.now();
    }
    public void trainingFailed(String code,boolean retryAllowed,LocalDateTime at) {
        trainingStatus = "FAILED"; trainingFailureCode = code; trainingRetryAllowed = retryAllowed;
        trainingFinishedAt = at; updatedAt = LocalDateTime.now();
    }
    public void trainingCompleted(long version,LocalDateTime at) {
        trainingStatus = "COMPLETED"; resultCloneVersion = version;
        trainingFailureCode = null; trainingRetryAllowed = false;
        trainingFinishedAt = at; updatedAt = LocalDateTime.now();
    }

    public void traceVersion(long version) { baseCloneVersion = version; }

    public static RagProfileJob create(Long cloneId, LocalDateTime now) {
        RagProfileJob job = new RagProfileJob();
        job.cloneId = cloneId;
        job.nextAttemptAt = now;
        job.createdAt = now; job.updatedAt = now;
        return job;
    }

    public void request(LocalDateTime now) {
        retryCount = 0;
        nextRevision(now);
    }

    public void retryRequest(LocalDateTime now) {
        retryCount++;
        leaseUntil = null; leaseToken = null;
        nextRevision(now);
    }

    private void nextRevision(LocalDateTime now) {
        now = now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        requestedRevision++;
        createdAt = now; updatedAt = now; startedAt = null; finishedAt = null; lastError = null;
        trainingStatus = null; trainingFailureCode = null; trainingRetryAllowed = null;
        trainingFinishedAt = null; resultCloneVersion = null;
        attempts = 0;
        nextAttemptAt = now;
    }

    public boolean canClaim(LocalDateTime now) {
        return requestedRevision > deliveredRevision && !nextAttemptAt.isAfter(now)
                && (leaseUntil == null || !leaseUntil.isAfter(now));
    }

    public String claim(LocalDateTime now) {
        now = now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        startedAt = now; updatedAt = now;
        leaseToken = UUID.randomUUID().toString();
        leaseUntil = now.plusMinutes(10);
        return leaseToken;
    }

    public void finish(String token, long revision, String error, LocalDateTime now) {
        if (!token.equals(leaseToken)) return; // Ignore a worker whose lease was reclaimed.
        now = now.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        updatedAt = now; finishedAt = now;
        leaseToken = null;
        leaseUntil = null;
        lastError = error;
        if (error == null) {
            deliveredRevision = revision;
            attempts = 0;
            nextAttemptAt = now;
        } else if (requestedRevision > revision) {
            nextAttemptAt = now;
        } else {
            attempts++;
            nextAttemptAt = now.plusSeconds(Math.min(3600L, 30L << Math.min(attempts - 1, 7)));
        }
    }
}
