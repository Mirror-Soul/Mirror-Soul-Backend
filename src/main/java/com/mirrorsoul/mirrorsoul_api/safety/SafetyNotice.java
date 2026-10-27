package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="safety_notices") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetyNotice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="report_id",nullable=false) private Long reportId;
    @Column(name="decision_id",nullable=false) private Long decisionId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Action action;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Reason reason;
    @Column(nullable=false,length=1000) private String message;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    @Column(name="read_at") private LocalDateTime readAt;
    public SafetyNotice(Long userId,Long reportId,Long decisionId,Action action,Reason reason,String message,LocalDateTime now) {
        this.userId=userId;this.reportId=reportId;this.decisionId=decisionId;this.action=action;
        this.reason=reason;this.message=message;createdAt=now;
    }
    public void read(LocalDateTime now) { if(readAt==null) readAt=now; }
}
