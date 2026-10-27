package com.mirrorsoul.mirrorsoul_api.access;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="admin_reauthentications") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AdminReauthentication {
 @Id @Column(name="token_hash",length=64) private String tokenHash;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(name="session_version",nullable=false) private Long sessionVersion;
 @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
 @Column(nullable=false) private boolean used;
 public AdminReauthentication(String hash,Long user,Long version,LocalDateTime expires) {tokenHash=hash;userId=user;sessionVersion=version;expiresAt=expires;}
 public void consume() {used=true;}
}
