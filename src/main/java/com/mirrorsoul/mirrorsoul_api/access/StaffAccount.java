package com.mirrorsoul.mirrorsoul_api.access;
import jakarta.persistence.*;
import lombok.*;
import java.util.*;
import java.time.LocalDateTime;
@Entity @Table(name="admin_staff") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class StaffAccount {
 public enum State { INVITED, ACTIVE, REVOKED }
 @Id @Column(name="user_id") private Long userId;
 @Version private Long version;
 @Column(name="change_sequence",nullable=false) private long changeSequence;
 @Column(name="display_name",nullable=false,length=100) private String displayName;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="admin_staff_permissions",joinColumns=@JoinColumn(name="user_id"))
 @Enumerated(EnumType.STRING) @Column(name="permission",length=30) private Set<AdminPermission> permissions=new HashSet<>();
 @Column(name="invite_hash",length=64) private String inviteHash;
 @Column(name="invite_cipher",length=512) private String inviteCipher;
 @Column(name="invite_expires_at") private LocalDateTime inviteExpiresAt;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 public StaffAccount(Long user,String name,Set<AdminPermission> permissions,State state,LocalDateTime now) {userId=user;displayName=name;this.permissions.addAll(permissions);this.state=state;updatedAt=now;}
 public void invite(String hash,String cipher,LocalDateTime expires) {inviteHash=hash;inviteCipher=cipher;inviteExpiresAt=expires;}
 public void reinvite(String name,Set<AdminPermission> next,LocalDateTime now) {changeSequence++;displayName=name;permissions.clear();permissions.addAll(next);state=State.INVITED;updatedAt=now;}
 public void activate(LocalDateTime now) {changeSequence++;state=State.ACTIVE;inviteHash=null;inviteCipher=null;inviteExpiresAt=null;updatedAt=now;}
 public void permissions(Set<AdminPermission> next,LocalDateTime now) {changeSequence++;permissions.clear();permissions.addAll(next);updatedAt=now;}
 public void revoke(LocalDateTime now) {changeSequence++;state=State.REVOKED;permissions.clear();inviteHash=null;inviteCipher=null;inviteExpiresAt=null;updatedAt=now;}
}
