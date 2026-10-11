package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="safety_decisions",uniqueConstraints=@UniqueConstraint(columnNames={"report_id","request_key"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class SafetyDecision {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="report_id",nullable=false) private Long reportId;
    @Column(name="actor_id",nullable=false) private Long actorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Action action;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Reason reason;
    @Column(nullable=false,length=1000) private String rationale;
    @Column(name="request_key",nullable=false,length=100) private String requestKey;
    @Column(name="request_hash",nullable=false,length=64) private String requestHash;
    @Column(name="result_version",nullable=false) private Long resultVersion=0L;
    @Column(name="target_version",nullable=false) private Long targetVersion=0L;
    @Column(name="sanction_id") private Long sanctionId;
    @Column(name="reporter_notice_id") private Long reporterNoticeId;
    @Column(name="target_notice_id") private Long targetNoticeId;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    public SafetyDecision(Long reportId,Long actorId,Action action,Reason reason,String rationale,String key,String hash,LocalDateTime now) {
        this.reportId=reportId;this.actorId=actorId;this.action=action;this.reason=reason;
        this.rationale=rationale;requestKey=key;requestHash=hash;createdAt=now;
    }
    public void result(Long reportVersion,Long userVersion,Long sanction,Long reporterNotice,Long targetNotice) {
        resultVersion=reportVersion;targetVersion=userVersion;sanctionId=sanction;
        reporterNoticeId=reporterNotice;targetNoticeId=targetNotice;
    }
}
