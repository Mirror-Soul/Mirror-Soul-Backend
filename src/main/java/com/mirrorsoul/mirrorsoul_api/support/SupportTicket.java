package com.mirrorsoul.mirrorsoul_api.support;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
@Entity @Table(name="support_tickets",uniqueConstraints={@UniqueConstraint(columnNames={"member_id","request_key"}),@UniqueConstraint(columnNames="external_message_id")})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SupportTicket {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Version private Long version=0L;
 @Column(name="activity_sequence",nullable=false) private long activitySequence;
 @Column(name="member_id",nullable=false) private Long memberId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Channel channel;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Category category;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
 @Column(nullable=false,length=200) private String subject;
 @Column(name="assignee_id") private Long assigneeId;
 @Column(name="waiting_since") private LocalDateTime waitingSince;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @Column(name="transaction_id") private Long transactionId;
 @Column(name="call_id") private Long callId;
 @Enumerated(EnumType.STRING) @Column(name="job_type",length=30) private JobType jobType;
 @Column(name="job_id") private Long jobId;
 @Column(name="request_key",nullable=false,length=100) private String requestKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="external_message_id",length=200) private String externalMessageId;
 public SupportTicket(Long memberId,Channel channel,SupportDtos.Create c,String hash,String externalId,LocalDateTime now) {
  this.memberId=memberId;this.channel=channel;category=c.category();subject=c.subject();status=Status.RECEIVED;
  transactionId=c.transactionId();callId=c.callId();jobType=c.jobType();jobId=c.jobId();requestKey=SupportAccess.key(c.idempotencyKey());
  requestHash=hash;externalMessageId=externalId==null?null:SupportAccess.hash(externalId);createdAt=now;updatedAt=now;waitingSince=now;
 }
 public void assign(Long actor,LocalDateTime now) { activitySequence++;assigneeId=actor;updatedAt=now;if(status==Status.RECEIVED) status=Status.IN_PROGRESS; }
 public void status(Status next,LocalDateTime now) {
  activitySequence++;status=next;updatedAt=now;waitingSince=(next==Status.RECEIVED||next==Status.IN_PROGRESS)?now:null;
 }
 public void customerMessage(LocalDateTime now) {activitySequence++; if(waitingSince==null) waitingSince=now;status=Status.IN_PROGRESS;updatedAt=now; }
 public void touch(LocalDateTime now) {activitySequence++;updatedAt=now;}
}
