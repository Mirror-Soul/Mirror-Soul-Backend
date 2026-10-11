package com.mirrorsoul.mirrorsoul_api.access;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="admin_audit_events") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AdminAuditEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="staff_id") private Long staffId;
 @Column(nullable=false,length=120) private String action;
 @Column(nullable=false,length=120) private String target;
 @Column(name="occurred_at",nullable=false) private LocalDateTime occurredAt;
 @Column(nullable=false,length=20) private String result;
 @Column(name="reason_code",nullable=false,length=60) private String reasonCode;
 @Column(name="correlation_id",nullable=false,length=36) private String correlationId;
 public AdminAuditEvent(Long staff,String action,String target,String result,String reason,String correlation,LocalDateTime now) {
  staffId=staff;this.action=action;this.target=target;this.result=result;reasonCode=reason;correlationId=correlation;occurredAt=now;
 }
}
