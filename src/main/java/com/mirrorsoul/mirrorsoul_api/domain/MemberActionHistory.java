package com.mirrorsoul.mirrorsoul_api.domain;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name = "member_action_history", uniqueConstraints =
        @UniqueConstraint(columnNames = {"member_id", "idempotency_key"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberActionHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "member_id", nullable = false) private Long memberId;
    @Column(name = "actor_id", nullable = false) private Long actorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private MemberAction action;
    @Enumerated(EnumType.STRING) @Column(name = "reason_code", nullable = false, length = 30) private MemberReasonCode reasonCode;
    @Enumerated(EnumType.STRING) @Column(name = "before_status", nullable = false, length = 30) private UserStatus beforeStatus;
    @Enumerated(EnumType.STRING) @Column(name = "after_status", nullable = false, length = 30) private UserStatus afterStatus;
    @Column(name = "expected_version", nullable = false) private Long expectedVersion;
    @Column(name = "result_version", nullable = false) private Long resultVersion;
    @Column(name = "idempotency_key", nullable = false, length = 100) private String idempotencyKey;
    @Column(name = "acted_at", nullable = false) private LocalDateTime actedAt;
    public MemberActionHistory(Long memberId, Long actorId, MemberAction action, MemberReasonCode reasonCode,
            UserStatus beforeStatus, UserStatus afterStatus, Long expectedVersion, Long resultVersion,
            String idempotencyKey, LocalDateTime actedAt) {
        this.memberId=memberId; this.actorId=actorId; this.action=action; this.reasonCode=reasonCode;
        this.beforeStatus=beforeStatus; this.afterStatus=afterStatus; this.expectedVersion=expectedVersion;
        this.resultVersion=resultVersion; this.idempotencyKey=idempotencyKey; this.actedAt=actedAt;
    }
}
