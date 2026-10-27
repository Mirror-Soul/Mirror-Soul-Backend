package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.AppealState;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="safety_appeals",uniqueConstraints=@UniqueConstraint(columnNames="decision_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetyAppeal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="report_id",nullable=false) private Long reportId;
    @Column(name="decision_id",nullable=false) private Long decisionId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(nullable=false,length=2000) private String explanation;
    @Column(name="request_key",nullable=false,length=100) private String requestKey;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AppealState state;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="resolved_at") private LocalDateTime resolvedAt;
    @Column(name="resolved_by") private Long resolvedBy;
    public SafetyAppeal(Long reportId,Long decisionId,Long userId,String explanation,String key,LocalDateTime now) {
        this.reportId=reportId;this.decisionId=decisionId;this.userId=userId;this.explanation=explanation;
        requestKey=key;requestHash=SafetySupport.hash(explanation);createdAt=now;state=AppealState.PENDING;
    }
    public void purgeExplanation() { explanation="[보관 기간 만료]"; }
    public void resolve(AppealState state,Long actorId,LocalDateTime now) { this.state=state;resolvedBy=actorId;resolvedAt=now; }
}
