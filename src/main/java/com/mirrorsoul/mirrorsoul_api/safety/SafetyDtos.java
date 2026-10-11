package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;
public final class SafetyDtos {
    private SafetyDtos() {}
    public record Submit(@NotNull UUID targetUserUuid,@NotNull ReportType type,
            @NotBlank @Size(max=2000) String description,@Positive Long callId,@Positive Long messageId,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
    public record Assign(@NotNull @Positive Long assigneeId,@NotNull @PositiveOrZero Long expectedVersion) {}
    public record Decide(@NotNull Action action,@NotNull Reason reasonCode,@NotBlank @Size(max=1000) String rationale,
            @NotNull @PositiveOrZero Long expectedVersion,@PositiveOrZero Long expectedMemberVersion,
            @Min(1) @Max(30) Integer restrictionDays,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
    public record Appeal(@NotNull @Positive Long decisionId,@NotBlank @Size(max=2000) String explanation,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String idempotencyKey) {}
    public record EvidenceAccess(@NotBlank @Pattern(regexp="REPORT_REVIEW|APPEAL_REVIEW|SECURITY_AUDIT") String reason) {}
    public record Diagnostic(@NotNull DiagnosticCode code,
            @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String eventKey) {}
    public record ReportView(Long id,Long reporterId,Long targetId,ReportType type,Priority priority,ReportState state,
            Long assigneeId,Long callId,Long messageId,Long version,Long latestDecisionId,LocalDateTime createdAt,
            LocalDateTime updatedAt,LocalDateTime evidenceExpiresAt,boolean evidencePurged) {
        public static ReportView from(SafetyReport r) { return new ReportView(r.getId(),r.getReporterId(),r.getTargetId(),
            r.getType(),r.getPriority(),r.getState(),r.getAssigneeId(),r.getCallId(),r.getMessageId(),r.getVersion(),
            r.getLatestDecisionId(),r.getCreatedAt(),r.getUpdatedAt(),r.getEvidenceExpiresAt(),r.isEvidencePurged()); }
    }
    public record DecisionView(Long id,Long reportId,Long actorId,Action action,Reason reasonCode,String rationale,
            Long reportVersion,Long targetVersion,Long sanctionId,Long reporterNoticeId,Long targetNoticeId,
            String notificationResult,LocalDateTime createdAt) {
        public static DecisionView from(SafetyDecision d) { return new DecisionView(d.getId(),d.getReportId(),d.getActorId(),
            d.getAction(),d.getReason(),d.getRationale(),d.getResultVersion(),d.getTargetVersion(),d.getSanctionId(),
            d.getReporterNoticeId(),d.getTargetNoticeId(),"INBOX_STORED",d.getCreatedAt()); }
    }
    public record AppealView(Long id,Long reportId,Long decisionId,AppealState state,LocalDateTime createdAt,LocalDateTime resolvedAt) {
        public static AppealView from(SafetyAppeal a) { return new AppealView(a.getId(),a.getReportId(),a.getDecisionId(),a.getState(),a.getCreatedAt(),a.getResolvedAt()); }
    }
    public record Detail(ReportView report,Long targetVersion,DecisionView latestDecision,AppealView pendingAppeal) {}
    public record DiagnosticView(Long id,Long callId,DiagnosticCode code,LocalDateTime createdAt) {
        public static DiagnosticView from(SafetyCallDiagnostic d) { return new DiagnosticView(d.getId(),d.getCallId(),d.getCode(),d.getCreatedAt()); }
    }
    public record Evidence(Long reportId,String description,String messageSnapshot,String appealExplanation,
            List<DiagnosticView> callDiagnostics,LocalDateTime expiresAt) {}
    public record NoticeView(Long id,Long reportId,Long decisionId,Action action,Reason reasonCode,String message,
            LocalDateTime createdAt,LocalDateTime readAt) {
        public static NoticeView from(SafetyNotice n) { return new NoticeView(n.getId(),n.getReportId(),n.getDecisionId(),n.getAction(),n.getReason(),n.getMessage(),n.getCreatedAt(),n.getReadAt()); }
    }
    public record SanctionView(Long id,Long reportId,Long decisionId,LocalDateTime startedAt,LocalDateTime expiresAt,
            LocalDateTime releasedAt,boolean active) {
        public static SanctionView from(SafetySanction s,LocalDateTime now) { return new SanctionView(s.getId(),s.getReportId(),s.getDecisionId(),s.getStartedAt(),s.getExpiresAt(),s.getReleasedAt(),s.active(now)); }
    }
    public record Permission(UUID userUuid,boolean accountAllowed,boolean communicationAllowed,
            boolean aiWorkAllowed,LocalDateTime restrictedUntil) {}
    public record AuditView(Long id,Long reportId,Long actorId,String operation,String reason,boolean allowed,LocalDateTime createdAt) {
        public static AuditView from(SafetyEvidenceAudit a) { return new AuditView(a.getId(),a.getReportId(),a.getActorId(),a.getOperation(),a.getReason(),a.isAllowed(),a.getCreatedAt()); }
    }
}
