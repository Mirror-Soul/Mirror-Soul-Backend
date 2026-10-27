package com.mirrorsoul.mirrorsoul_api.support;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="support_attachments") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SupportAttachment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="ticket_id",nullable=false) private Long ticketId;
 @Column(name="owner_id",nullable=false) private Long ownerId;
 @Column(name="object_key",nullable=false,length=250) private String objectKey;
 @Column(name="file_name",nullable=false,length=150) private String fileName;
 @Column(name="content_type",nullable=false,length=50) private String contentType;
 @Column(nullable=false) private long size;
 @Column(nullable=false) private boolean confirmed;
 @Column(nullable=false) private boolean deleted;
 @Column(name="object_version",length=200) private String objectVersion;
 @Column(length=200) private String etag;
 @Column(name="upload_expires_at",nullable=false) private LocalDateTime uploadExpiresAt;
 @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
 public SupportAttachment(Long ticket,Long owner,SupportDtos.Upload upload,String key,LocalDateTime now,int days) {
  ticketId=ticket;ownerId=owner;fileName=upload.fileName();contentType=upload.contentType();size=upload.size();objectKey=key;
  uploadExpiresAt=now.plusMinutes(5);expiresAt=now.plusDays(days);
 }
 public void confirm(String version,String etag) {confirmed=true;objectVersion=version;this.etag=etag;}
 public void deleted() {deleted=true;}
}
