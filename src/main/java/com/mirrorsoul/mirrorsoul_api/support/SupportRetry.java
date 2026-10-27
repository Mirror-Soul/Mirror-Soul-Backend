package com.mirrorsoul.mirrorsoul_api.support;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="support_retries",uniqueConstraints=@UniqueConstraint(columnNames={"entry_id","request_key"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SupportRetry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="entry_id",nullable=false) private Long entryId;
 @Column(name="request_key",nullable=false,length=100) private String requestKey;
 @Column(name="author_id",nullable=false) private Long authorId;
 @Column(nullable=false) private boolean acknowledged;
 public SupportRetry(Long entry,Long author,SupportDtos.Retry request) {entryId=entry;authorId=author;requestKey=SupportAccess.key(request.idempotencyKey());acknowledged=request.acknowledgePossibleDuplicate();}
}
