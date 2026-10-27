package com.mirrorsoul.mirrorsoul_api.billing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import static com.mirrorsoul.mirrorsoul_api.billing.BillingTypes.*;

@Entity @Table(name = "billing_jobs")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_key", nullable = false, unique = true, length = 64) private String eventKey;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "payment_id") private Payment payment;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Action action;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private JobState state = JobState.PENDING;
    @Column(name = "occurred_at", nullable = false) private long occurredAt;
    @Column(name = "store_transaction_id", length = 200) private String transactionId;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private LocalDateTime nextAttemptAt = LocalDateTime.now();
    @Column(name = "lease_until") private LocalDateTime leaseUntil;
    @Column(name = "claim_token", length = 36) private String claimToken;
    @Column(name = "failure_code", length = 80) private String failureCode;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();

    public BillingJob(String key, Payment payment, Action action, long occurredAt) {
        this.eventKey = key; this.payment = payment; this.action = action; this.occurredAt = occurredAt;
    }
    void transaction(String id) { this.transactionId = id; }
    boolean claimable(LocalDateTime now) {
        return state == JobState.PENDING && !nextAttemptAt.isAfter(now)
                || state == JobState.PROCESSING && !leaseUntil.isAfter(now);
    }
    String claim(LocalDateTime now) {
        attempts++; state = JobState.PROCESSING; claimToken = UUID.randomUUID().toString();
        leaseUntil = now.plusMinutes(5); return claimToken;
    }
    boolean owns(String token) { return state == JobState.PROCESSING && claimToken.equals(token); }
    void complete() { state = JobState.COMPLETED; leaseUntil = null; failureCode = null; }
    void failed(String code) {
        failureCode = code; leaseUntil = null;
        state = attempts >= 10 ? JobState.DEAD : JobState.PENDING;
        nextAttemptAt = LocalDateTime.now().plusSeconds(Math.min(3600, 15L << Math.min(attempts, 8)));
    }
    void retry() { state = JobState.PENDING; attempts = 0; nextAttemptAt = LocalDateTime.now(); leaseUntil = null; }
}
