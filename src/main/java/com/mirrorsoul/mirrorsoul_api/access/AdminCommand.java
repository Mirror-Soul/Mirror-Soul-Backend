package com.mirrorsoul.mirrorsoul_api.access;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="admin_commands",uniqueConstraints=@UniqueConstraint(columnNames={"actor_id","request_key"})) @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AdminCommand {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="actor_id",nullable=false) private Long actorId;
 @Column(name="request_key",nullable=false,length=100) private String requestKey;
 @Column(name="request_hash",nullable=false,length=64) private String requestHash;
 @Column(name="target_id",nullable=false) private Long targetId;
 public AdminCommand(Long actor,String key,String hash,Long target) {actorId=actor;requestKey=key.toLowerCase(java.util.Locale.ROOT);requestHash=hash;targetId=target;}
}
