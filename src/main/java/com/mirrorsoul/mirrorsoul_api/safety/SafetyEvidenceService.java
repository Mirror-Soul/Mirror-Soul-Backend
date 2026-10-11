package com.mirrorsoul.mirrorsoul_api.safety;

import com.mirrorsoul.mirrorsoul_api.common.apiPayload.code.GeneralErrorCode;
import com.mirrorsoul.mirrorsoul_api.common.apiPayload.exception.GeneralException;
import com.mirrorsoul.mirrorsoul_api.domain.User;
import com.mirrorsoul.mirrorsoul_api.domain.enums.*;
import com.mirrorsoul.mirrorsoul_api.repository.*;
import com.mirrorsoul.mirrorsoul_api.service.AdminMemberService;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyTypes.*;
import com.mirrorsoul.mirrorsoul_api.safety.SafetyDtos.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j @Service @RequiredArgsConstructor
public class SafetyEvidenceService {
    private final SafetyReportRepository reports;
    private final SafetyAppealRepository appeals;
    private final SafetyCallDiagnosticRepository diagnostics;
    private final SafetyEvidenceAuditRepository audits;
    private final SafetyAuditWriter auditWriter;
    private final UserRepository users;
    private final VideoCallRepository calls;
    private final SafetyPolicy policy;

    @Transactional(readOnly=true)
    public Evidence access(Long actorId,Long reportId,String reason) {
        if(!Set.of("REPORT_REVIEW","APPEAL_REVIEW","SECURITY_AUDIT").contains(reason))
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        SafetyReport r=reports.findById(reportId).orElseThrow(() -> new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND));
        User actor=users.findById(actorId).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        boolean allowed=actor.getStatus()==UserStatus.ACTIVE && (actor.getRole()==UserRole.SUPER_ADMIN
            || (actor.getRole()==UserRole.ADMIN && Objects.equals(r.getAssigneeId(),actorId)));
        allowed=allowed && !r.isEvidencePurged() && SafetySupport.now().isBefore(r.getEvidenceExpiresAt());
        auditWriter.access(reportId,actorId,reason,allowed);
        if(!allowed) throw new GeneralException(GeneralErrorCode.FORBIDDEN,"Evidence access denied or retention expired.");
        List<DiagnosticView> events=r.getCallId()==null ? List.of() : diagnostics.findByCallIdOrderByIdDesc(r.getCallId(),PageRequest.of(0,100)).stream().map(DiagnosticView::from).toList();
        String explanation=appeals.findByReportIdAndState(reportId,AppealState.PENDING).map(SafetyAppeal::getExplanation).orElse(null);
        return new Evidence(reportId,r.getDescription(),r.getMessageSnapshot(),explanation,events,r.getEvidenceExpiresAt());
    }
    @Transactional(readOnly=true)
    public Page<AuditView> audits(Long actorId,Long reportId,int page,int size) {
        User actor=users.findById(actorId).orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        SafetySupport.operator(actor);
        if(actor.getRole()!=UserRole.SUPER_ADMIN) throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        if(!reports.existsById(reportId)) throw new GeneralException(GeneralErrorCode.REPORT_NOT_FOUND);
        return audits.findByReportId(reportId,AdminMemberService.pageable(page,size)).map(AuditView::from);
    }
    @Transactional
    public int purgeExpired() {
        var now=SafetySupport.now();
        List<SafetyReport> expired=reports.findByEvidencePurgedFalseAndEvidenceExpiresAtLessThanEqual(now,PageRequest.of(0,100));
        for(SafetyReport r:expired) {
            r.purge(now);
            appeals.findByReportId(r.getId()).forEach(SafetyAppeal::purgeExplanation);
            audits.save(new SafetyEvidenceAudit(r.getId(),null,"PURGE","RETENTION_EXPIRED",true,now));
        }
        diagnostics.deleteExpired(now.minusDays(policy.retentionDays()));
        return expired.size();
    }
    @Transactional
    public DiagnosticView diagnostic(Long callId,Diagnostic request) {
        calls.findByIdForUpdate(callId).orElseThrow(() -> new GeneralException(GeneralErrorCode.CALL_NOT_FOUND));
        String key=SafetySupport.key(request.eventKey());
        var prior=diagnostics.findByCallIdAndEventKey(callId,key);
        if(prior.isPresent()) {
            if(prior.get().getCode()!=request.code()) throw new GeneralException(GeneralErrorCode.REPORT_IDEMPOTENCY_CONFLICT);
            return DiagnosticView.from(prior.get());
        }
        if(diagnostics.countByCallId(callId)>=1000) throw new GeneralException(GeneralErrorCode.REPORT_RATE_LIMITED);
        var event=diagnostics.saveAndFlush(new SafetyCallDiagnostic(callId,key,request.code(),SafetySupport.now()));
        if(request.code()==DiagnosticCode.AI_ERROR || request.code()==DiagnosticCode.CONNECTION_FAILED)
            log.warn("Safety call diagnostic callId={} eventId={} code={}",callId,event.getId(),request.code());
        return DiagnosticView.from(event);
    }
}
