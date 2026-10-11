package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="safety_reports",uniqueConstraints=@UniqueConstraint(columnNames={"reporter_id","request_key"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetyReport {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Version @Column(nullable=false) private Long version=0L;
    @Column(name="reporter_id",nullable=false) private Long reporterId;
    @Column(name="target_id",nullable=false) private Long targetId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private ReportType type;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Priority priority;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReportState state;
    @Column(name="call_id") private Long callId;
    @Column(name="message_id") private Long messageId;
    @Column(columnDefinition="TEXT") private String description;
    @Column(name="message_snapshot",columnDefinition="TEXT") private String messageSnapshot;
    @Column(name="request_key",nullable=false,length=100) private String requestKey;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="context_hash",nullable=false,length=64) private String contextHash;
    @Column(name="assignee_id") private Long assigneeId;
    @Column(name="latest_decision_id") private Long latestDecisionId;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Column(name="evidence_expires_at",nullable=false) private LocalDateTime evidenceExpiresAt;
    @Column(name="evidence_purged",nullable=false) private boolean evidencePurged;
    public SafetyReport(Long reporterId,Long targetId,ReportType type,Priority priority,Long callId,Long messageId,
            String description,String snapshot,String key,String hash,String context,LocalDateTime now,int retentionDays) {
        this.reporterId=reporterId;this.targetId=targetId;this.type=type;this.priority=priority;
        this.callId=callId;this.messageId=messageId;this.description=description;messageSnapshot=snapshot;
        requestKey=key;requestHash=hash;contextHash=context;state=ReportState.RECEIVED;
        createdAt=now;updatedAt=now;evidenceExpiresAt=now.plusDays(retentionDays);
    }
    public void assign(Long actorId,LocalDateTime now) { assigneeId=actorId;if(state==ReportState.RECEIVED) state=ReportState.REVIEWING;updatedAt=now; }
    public void decide(Long decisionId,LocalDateTime now) { latestDecisionId=decisionId;state=ReportState.DECIDED;updatedAt=now; }
    public void appeal(LocalDateTime now) { state=ReportState.APPEALED;assigneeId=null;updatedAt=now; }
    public void retainThrough(LocalDateTime deadline) {
        if(!evidencePurged && deadline.isAfter(evidenceExpiresAt)) evidenceExpiresAt=deadline;
    }
    public void purge(LocalDateTime now) { description=null;messageSnapshot=null;evidencePurged=true;updatedAt=now; }
}
