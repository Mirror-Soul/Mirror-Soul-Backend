package com.mirrorsoul.mirrorsoul_api.safety;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="safety_sanctions",uniqueConstraints=@UniqueConstraint(columnNames="report_id"))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetySanction {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="report_id",nullable=false) private Long reportId;
    @Column(name="decision_id",nullable=false) private Long decisionId;
    @Column(name="user_id",nullable=false) private Long userId;
    @Column(name="started_at",nullable=false) private LocalDateTime startedAt;
    @Column(name="expires_at",nullable=false) private LocalDateTime expiresAt;
    @Column(name="released_at") private LocalDateTime releasedAt;
    @Column(name="released_by") private Long releasedBy;
    public SafetySanction(Long reportId,Long decisionId,Long userId,LocalDateTime now,int days) {
        this.reportId=reportId;this.decisionId=decisionId;this.userId=userId;startedAt=now;expiresAt=now.plusDays(days);
    }
    public boolean active(LocalDateTime now) { return releasedAt==null && now.isBefore(expiresAt); }
    public void release(Long actorId,LocalDateTime now) { releasedBy=actorId;releasedAt=now; }
}
