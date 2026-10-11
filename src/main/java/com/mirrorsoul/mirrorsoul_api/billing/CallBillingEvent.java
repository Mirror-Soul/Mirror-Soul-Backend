package com.mirrorsoul.mirrorsoul_api.billing;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name = "call_billing_events")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CallBillingEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_key", unique = true, nullable = false, length = 64) private String eventKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;
    @Column(name = "call_id", nullable = false) private Long callId;
    @Column(nullable = false, length = 10) private String kind;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    public CallBillingEvent(String key, String hash, Long callId, String kind, LocalDateTime at) {
        this.eventKey = key; this.requestHash = hash; this.callId = callId; this.kind = kind; this.occurredAt = at;
    }
}
