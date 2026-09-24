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

    public static RagProfileJob create(Long cloneId, LocalDateTime now) {
        RagProfileJob job = new RagProfileJob();
        job.cloneId = cloneId;
        job.nextAttemptAt = now;
        return job;
    }

    public void request(LocalDateTime now) {
        requestedRevision++;
        attempts = 0;
        nextAttemptAt = now;
    }

    public boolean canClaim(LocalDateTime now) {
        return requestedRevision > deliveredRevision && !nextAttemptAt.isAfter(now)
                && (leaseUntil == null || !leaseUntil.isAfter(now));
    }

    public String claim(LocalDateTime now) {
        leaseToken = UUID.randomUUID().toString();
        leaseUntil = now.plusMinutes(10);
        return leaseToken;
    }

    public void finish(String token, long revision, String error, LocalDateTime now) {
        if (!token.equals(leaseToken)) return; // Ignore a worker whose lease was reclaimed.
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
