package com.mirrorsoul.mirrorsoul_api.billing;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name = "billing_audits")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "payment_id") private Long paymentId;
    @Column(name = "ledger_id") private Long ledgerId;
    @Column(name = "job_id") private Long jobId;
    @Column(name = "actor_id") private Long actorId;
    @Column(nullable = false, length = 40) private String action;
    @Column(name = "operation_key", unique = true, length = 100) private String operationKey;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    public BillingAudit(Long paymentId, Long ledgerId, Long jobId, Long actorId, String action, String key) {
        this.paymentId = paymentId; this.ledgerId = ledgerId; this.jobId = jobId; this.actorId = actorId;
        this.action = action; this.operationKey = key;
    }
}
