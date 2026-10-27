package com.mirrorsoul.mirrorsoul_api.safety;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="safety_evidence_audits") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetyEvidenceAudit {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="report_id",nullable=false) private Long reportId;
    @Column(name="actor_id") private Long actorId;
    @Column(nullable=false,length=20) private String operation;
    @Column(nullable=false,length=200) private String reason;
    @Column(nullable=false) private boolean allowed;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    public SafetyEvidenceAudit(Long reportId,Long actorId,String operation,String reason,boolean allowed,LocalDateTime now) {
        this.reportId=reportId;this.actorId=actorId;this.operation=operation;this.reason=reason;this.allowed=allowed;createdAt=now;
    }
}
