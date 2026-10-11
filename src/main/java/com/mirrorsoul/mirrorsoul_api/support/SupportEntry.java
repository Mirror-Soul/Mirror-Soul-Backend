package com.mirrorsoul.mirrorsoul_api.support;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.mirrorsoul.mirrorsoul_api.support.SupportTypes.*;
@Entity @Table(name="support_entries",uniqueConstraints=@UniqueConstraint(columnNames={"ticket_id","request_key"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SupportEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="ticket_id",nullable=false) private Long ticketId;
 @Column(name="author_id",nullable=false) private Long authorId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private EntryType type;
 @Column(nullable=false,columnDefinition="TEXT") private String body;
 @Column(name="request_key",nullable=false,length=140) private String requestKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Delivery delivery;
 @Column(name="failure_code",length=40) private String failureCode;
 @Column(nullable=false) private int attempts;
 @Column(name="attempt_token",length=36) private String attemptToken;
 @Column(name="attempted_at") private LocalDateTime attemptedAt;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 public SupportEntry(Long ticketId,Long author,EntryType type,String body,String key,String hash,LocalDateTime now) {
  this.ticketId=ticketId;authorId=author;this.type=type;this.body=body;requestKey=key;requestHash=hash;
  delivery=type==EntryType.REPLY?Delivery.PENDING:Delivery.NONE;createdAt=now;
 }
 public void claim(String token,LocalDateTime now) {delivery=Delivery.SENDING;attempts++;attemptToken=token;attemptedAt=now;failureCode=null;}
 public void finish(Delivery next,String code) {delivery=next;failureCode=code;}
 public void retry() {delivery=Delivery.PENDING;failureCode=null;}
}
